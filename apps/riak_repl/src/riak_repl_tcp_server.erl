-module(riak_repl_tcp_server).
-include("riak_repl.hrl").
-include_lib("kernel/include/file.hrl").
-behaviour(gen_fsm).
-export([start/2]).
-export([init/1, 
         handle_event/3,
         handle_sync_event/4, 
         handle_info/3, 
         terminate/3, 
         code_change/4]).
-export([wait_peerinfo/2,
         merkle_send/2,
         merkle_wait_ack/2,
         connected/2]).
-record(state, 
        {
          socket,
          sitename,
          client,
          my_pi,
          merkle_fp,
          work_dir,
          partitions=[]
         }
       ).

start(Socket, SiteName) -> gen_fsm:start(?MODULE, [Socket, SiteName], []).

init([Socket, SiteName]) ->
    process_flag(trap_exit, true),
    %%io:format("~p starting, sock=~p~n", [?MODULE, Socket]),
    ok = inet:setopts(Socket, [{active, once}, {packet, 4}]),
    {ok, Client} = riak:local_client(),
    MyPeerInfo = riak_repl_util:make_peer_info(),
    Partitions = riak_repl_util:get_partitions(MyPeerInfo),
    {ok, WorkRoot} = application:get_env(riak_repl, work_dir),
    WorkDir = filename:join(WorkRoot, SiteName),
    ok = filelib:ensure_dir(filename:join(WorkDir, "empty")),
    case maybe_redirect(Socket, MyPeerInfo) of
        ok ->
            riak_repl_leader:add_receiver_pid(self()),
            {ok, wait_peerinfo, 
             #state{socket=Socket,
                    sitename=SiteName,
                    client=Client,
                    work_dir=WorkDir,
                    partitions=Partitions,
                    my_pi=MyPeerInfo}};
        redirect ->
            ignore
    end.

maybe_redirect(Socket, PeerInfo) ->
    LeaderNode = riak_repl_leader:leader_node(),
    case LeaderNode =:= node() of
        true ->
            send(Socket, term_to_binary({peerinfo, PeerInfo})),
            ok;
        false ->
            send(Socket, term_to_binary({redirect, "127.0.0.1", 9011})),
            redirect
    end.


wait_peerinfo({peerinfo, TheirPeerInfo}, State=#state{my_pi=MyPeerInfo, socket=_Socket}) ->
    case riak_repl_util:validate_peer_info(TheirPeerInfo, MyPeerInfo) of
        true ->
            %%io:format("peer info ok!~n"),
            {next_state, merkle_send, State, 0};
        false ->
            {stop, normal, State}
    end.

merkle_send(timeout, State=#state{socket=_Socket, partitions=[]}) ->
    io:format("sent last merkle~n"),
    {next_state, connected, State};
merkle_send(timeout, State=#state{socket=Socket, 
                                  partitions=[Partition|T],
                                  work_dir=WorkDir}) ->
    X =  riak_repl_util:make_merkle(State#state.my_pi, Partition, WorkDir),
    case X of
        {error, Reason} ->
            io:format("get_merkle error ~p for partition ~p~n", [Reason, Partition]),
            {next_state, merkle_send, State, 0};
        {ok, MerkleFile} ->
            {ok, FileInfo} = file:read_file_info(MerkleFile),
            FileSize = FileInfo#file_info.size,
            {ok, FP} = file:open(MerkleFile, [read, raw, binary, read_ahead]),
            ok = send(Socket, term_to_binary({merkle, FileSize, Partition})),
            io:format("sending merkle tree for partition ~p~n", [Partition]),
            ok = send_chunks(FP, Socket),
            file:delete(MerkleFile),
            {next_state, merkle_wait_ack, State#state{partitions=T}}
    end.

send_chunks(FP, Socket) ->
    case file:read(FP, 8192) of
        {ok, Data} ->
            ok = send(Socket, term_to_binary({merk_chunk, Data}, [compressed])),
            send_chunks(FP, Socket);
        eof ->
            file:close(FP),
            ok
    end.

merkle_wait_ack({ack, _Partition, []}, State) ->
    {next_state, merkle_send, State, 0};
merkle_wait_ack({ack, Partition, DiffVClocks}, State=#state{client=Client, socket=Socket}) ->
    Actions = vclock_diff(Partition, DiffVClocks, State),
    vclock_diff_response(Actions, Client, Socket, [], []),
    %io:format("sending ~p get reqs and ~p send reqs~n", [length(element(2, _GetMsg)), length(element(2, SendMsg))]),
    ok = send(Socket, term_to_binary({diff_response, Partition, {send, []}})),
    {next_state, merkle_wait_ack, State}.

connected(_E, State) -> {next_state, connected, State}.
handle_event(_Event, StateName, State) -> {next_state, StateName, State}.
handle_sync_event(_Event,_From,StateName,State) -> {reply,ok,StateName,State}.

handle_info({tcp_closed, Socket}, _StateName, State=#state{socket=Socket}) ->
    io:format("tcp socket closed ~p~n", [Socket]),
    {stop, normal, State};
handle_info({tcp, Socket, Data}, StateName, State=#state{socket=Socket}) ->
    R = ?MODULE:StateName(binary_to_term(Data), State),
    ok = inet:setopts(Socket, [{active, once}]),            
    R;
handle_info({repl, RObj}, StateName, State=#state{socket=Socket}) ->
    ok = send(Socket, term_to_binary({diff_obj, RObj}, [compressed])),
    ok = inet:setopts(Socket, [{active, once}]),
    {next_state, StateName, State};
handle_info(_I, StateName, State) -> {next_state, StateName, State}.
terminate(_Reason, _StateName, _State) -> ok.
code_change(_OldVsn, StateName, State, _Extra) -> {ok, StateName, State}.

send(Socket, Data) ->  ok = gen_tcp:send(Socket, Data).
    
should_get(V1, V2) -> vclock:descends(V1, V2) =:= false.
should_send(V1, V2) -> vclock:descends(V2, V1) =:= false.

vclock_diff(Partition, DiffVClocks, #state{my_pi=#peer_info{ring=Ring}}) ->
    OwnerNode = riak_core_ring:index_owner(Ring, Partition),
    Keys = [K || {K, _V} <- DiffVClocks],
    case riak_repl_util:vnode_master_call(OwnerNode, {get_vclocks, Partition, Keys}) of
        {error, Reason} ->
            io:format("~p:error getting vclocks for ~p from ~p: ~p~n",
                      [?MODULE, Partition, OwnerNode, Reason]),
            [];
        OurVClocks ->
            vclock_diff1(DiffVClocks, OurVClocks, [])
    end.

vclock_diff1([], _, Acc) ->
    lists:reverse(Acc);
vclock_diff1([{K,VC}|T], OurVClocks, Acc) ->
    case proplists:get_value(K, OurVClocks) of
        undefined ->
            vclock_diff1(T, OurVClocks, Acc);
        VC ->
            vclock_diff1(T, OurVClocks, Acc);
        OurVClock ->
            case should_get(OurVClock, VC) of
                true ->
                    case should_send(OurVClock, VC) of
                        true ->
                            vclock_diff1(T, OurVClocks, lists:append([{get,K},{send,K}],Acc));
                        false ->
                            vclock_diff1(T, OurVClocks, [{get, K}|Acc])
                    end;
                false ->
                    case should_send(OurVClock, VC) of
                        true ->
                            vclock_diff1(T, OurVClocks, [{send,K}|Acc]);
                        false  ->
                            vclock_diff1(T, OurVClocks, Acc)
                    end
            end
    end.


vclock_diff_response([], _Client, _Socket, Gets, Sends) ->
    {{get, lists:reverse(Gets)}, {send, lists:reverse(Sends)}};
vclock_diff_response([{get,{_B,_K}}|T], Client, Socket, Gets, Sends) ->
    vclock_diff_response(T, Client, Socket, Gets, Sends);
vclock_diff_response([{send,{B,K}}|T], Client, Socket, Gets, Sends) ->
    case Client:get(B, K, 1, ?REPL_FSM_TIMEOUT) of
        {ok, Obj} ->
            ok = send(Socket, term_to_binary({diff_obj, Obj})),
            ok = inet:setopts(Socket, [{active, once}]),
            vclock_diff_response(T, Client, Socket, Gets, Sends);
        _ ->
            vclock_diff_response(T, Client, Socket, Gets, Sends)
    end.    

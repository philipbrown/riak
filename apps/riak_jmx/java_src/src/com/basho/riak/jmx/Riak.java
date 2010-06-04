package com.basho.riak.jmx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

public class Riak implements RiakMBean {
	private class RiakStatsFetcher {
		private String host;
		private int port;
		private String statsURL;

		public RiakStatsFetcher(String host, int port) {
			super();
			this.host = host;
			this.port = port;
			this.statsURL = "http://" + this.host + ":" + this.port + "/stats";
		}

		public void update() throws Exception {
			JSONObject stats = fetch();
			Riak.this.setCPUNProcs(stats.getInt("cpu_nprocs"));
			Riak.this.setMemAllocated((float) stats.getDouble("mem_allocated"));
			Riak.this.setMemTotal((float) stats.getDouble("mem_total"));
			Riak.this.setNodeGets(stats.getInt("node_gets"));
			Riak.this.setNodeGetsTotal(stats.getInt("node_gets_total"));
			Riak.this.setNodePuts(stats.getInt("node_puts"));
			Riak.this.setNodePutsTotal(stats.getInt("node_puts_total"));
			Riak.this.setVnodeGets(stats.getInt("vnode_gets"));
			Riak.this.setVnodeGetsTotal(stats.getInt("vnode_gets_total"));
			Riak.this.setVnodePuts(stats.getInt("vnode_puts"));
			Riak.this.setVnodePutsTotal(stats.getInt("vnode_puts_total"));
			Riak.this.setPbcActive(stats.getInt("pbc_active"));
			Riak.this.setPbcConnects(stats.getInt("pbc_connects"));
			Riak.this.setPbcConnectsTotal(stats.getInt("pbc_connects_total"));
			Riak.this.setNodeName(stats.getString("nodename"));
			Riak.this.setRingCreationSize(stats.getInt("ring_creation_size"));
			Riak.this.setCpuAvg1(stats.getInt("cpu_avg1"));
			Riak.this.setCpuAvg5(stats.getInt("cpu_avg5"));			
			Riak.this.setCpuAvg15(stats.getInt("cpu_avg15"));
			Riak.this.setNodeGetFsmTime95(getStat(stats, "node_get_fsm_time_95"));
			Riak.this.setNodeGetFsmTime99(getStat(stats, "node_get_fsm_time_99"));
			Riak.this.setNodeGetFsmTimeMax(getStat(stats, "node_get_fsm_time_100"));			
			Riak.this.setNodeGetFsmTimeMean(getStat(stats, "node_get_fsm_time_mean"));						
			Riak.this.setNodeGetFsmTimeMedian(getStat(stats, "node_get_fsm_time_median"));			
			Riak.this.setNodePutFsmTime95(getStat(stats, "node_put_fsm_time_95"));
			Riak.this.setNodePutFsmTime99(getStat(stats, "node_put_fsm_time_99"));
			Riak.this.setNodePutFsmTimeMax(getStat(stats, "node_put_fsm_time_100"));			
			Riak.this.setNodePutFsmTimeMean(getStat(stats, "node_put_fsm_time_mean"));						
			Riak.this.setNodePutFsmTimeMedian(getStat(stats, "node_put_fsm_time_median"));						
		}
		
		private float getStat(JSONObject obj, String key) throws Exception {
			Object val = obj.get(key);
			if (val.equals("undefined")) {
				return -1;
			}
			return Float.parseFloat(val.toString());
		}
		
		protected JSONObject fetch() throws IOException, JSONException {
            URL url = new URL(this.statsURL);
            StringBuilder sb = new StringBuilder();
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            InputStream input = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String json = null;
            while ((json = reader.readLine()) != null) {
                    sb.append(json);
            }
            //System.out.println(new JSONObject(sb.toString()).toString(4));
            return new JSONObject(sb.toString());
		}
	}

	private RiakStatsFetcher fetcher;
	
	// JMX variables
	int cpuNProcs;
	float memAllocated;
	float memTotal;
	int nodeGets;
	int nodeGetsTotal;
	int nodePuts;
	int nodePutsTotal;
	int vnodeGets;
	int vnodeGetsTotal;
	int vnodePuts;
	int vnodePutsTotal;
	int pbcActive;
	int pbcConnects;
	int pbcConnectsTotal;
	String nodeName;
	int ringCreationSize;
	int cpuAvg1;
	int cpuAvg5;
	int cpuAvg15;
	float nodeGetFsmTimeMax;
	float nodeGetFsmTimeMean;
	float nodeGetFsmTimeMedian;
	float nodeGetFsmTime95;
	float nodeGetFsmTime99;
	float nodePutFsmTimeMax;
	float nodePutFsmTimeMean;
	float nodePutFsmTimeMedian;
	float nodePutFsmTime95;
	float nodePutFsmTime99;	
	
	

	public Riak(String host, int port) throws Exception {
		super();
		this.fetcher = new RiakStatsFetcher(host, port);
		this.update();
	}
	
	public void update() {
		try {
			this.fetcher.update();
		} catch (Exception e) {
			System.exit(1);
		}
	}

	@Override
	public int getCPUNProcs() {
		return cpuNProcs;
	}

	@Override
	synchronized public void setCPUNProcs(int nprocs) {
		this.cpuNProcs = nprocs;
	}	
	
	@Override
	public float getMemAllocated() {
		return memAllocated;
	}

	@Override
	synchronized public void setMemAllocated(float memAllocated) {
		this.memAllocated = memAllocated;
	}

	@Override
	synchronized public void setNodeGets(int nodeGets) {
		this.nodeGets = nodeGets;
	}

	@Override
	public int getNodeGets() {
		return nodeGets;
	}

	@Override
	synchronized public void setNodeGetsTotal(int nodeGetsTotal) {
		this.nodeGetsTotal = nodeGetsTotal;
	}

	@Override
	public int getNodeGetsTotal() {
		return nodeGetsTotal;
	}

	@Override
	synchronized public void setNodePuts(int nodePuts) {
		this.nodePuts = nodePuts;
	}

	@Override
	public int getNodePuts() {
		return nodePuts;
	}

	@Override
	synchronized public void setNodePutsTotal(int nodePutsTotal) {
		this.nodePutsTotal = nodePutsTotal;
	}

	@Override
	public int getNodePutsTotal() {
		return nodePutsTotal;
	}

	@Override
	synchronized public void setMemTotal(float memTotal) {
		this.memTotal = memTotal;
	}

	@Override
	public float getMemTotal() {
		return memTotal;
	}
	
	@Override
	public int getVnodeGets() {
		return vnodeGets;
	}

	@Override
	synchronized public void setVnodeGets(int vnodeGets) {
		this.vnodeGets = vnodeGets;
	}

	@Override
	public int getVnodeGetsTotal() {
		return vnodeGetsTotal;
	}

	@Override
	synchronized public void setVnodeGetsTotal(int vnodeGetsTotal) {
		this.vnodeGetsTotal = vnodeGetsTotal;
	}

	@Override
	public int getVnodePuts() {
		return vnodePuts;
	}

	@Override
	synchronized public void setVnodePuts(int vnodePuts) {
		this.vnodePuts = vnodePuts;
	}

	@Override
	public int getVnodePutsTotal() {
		return vnodePutsTotal;
	}

	@Override
	synchronized public void setVnodePutsTotal(int vnodePutsTotal) {
		this.vnodePutsTotal = vnodePutsTotal;
	}

	@Override
	public int getPbcActive() {
		return pbcActive;
	}

	@Override
	synchronized public void setPbcActive(int pbcActive) {
		this.pbcActive = pbcActive;
	}

	@Override
	public int getPbcConnects() {
		return pbcConnects;
	}

	@Override
	synchronized public void setPbcConnects(int pbcConnects) {
		this.pbcConnects = pbcConnects;
	}

	@Override
	public int getPbcConnectsTotal() {
		return pbcConnectsTotal;
	}

	@Override
	synchronized public void setPbcConnectsTotal(int pbcConnectsTotal) {
		this.pbcConnectsTotal = pbcConnectsTotal;
	}

	@Override
	public String getNodeName() {
		return nodeName;
	}

	@Override
	synchronized public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	@Override
	public int getRingCreationSize() {
		return ringCreationSize;
	}

	@Override
	synchronized public void setRingCreationSize(int ringCreationSize) {
		this.ringCreationSize = ringCreationSize;
	}
	
	@Override
	public int getCpuAvg1() {
		return cpuAvg1;
	}

	@Override
	synchronized public void setCpuAvg1(int cpuAvg1) {
		this.cpuAvg1 = cpuAvg1;
	}

	@Override
	public int getCpuAvg5() {
		return cpuAvg5;
	}

	@Override
	synchronized public void setCpuAvg5(int cpuAvg5) {
		this.cpuAvg5 = cpuAvg5;
	}

	@Override
	public int getCpuAvg15() {
		return cpuAvg15;
	}

	@Override
	synchronized public void setCpuAvg15(int cpuAvg15) {
		this.cpuAvg15 = cpuAvg15;
	}
	
	@Override
	public float getNodeGetFsmTimeMax() {
		return nodeGetFsmTimeMax;
	}

	@Override
	synchronized public void setNodeGetFsmTimeMax(float nodeGetFsmTimeMax) {
		this.nodeGetFsmTimeMax = nodeGetFsmTimeMax;
	}

	@Override
	public float getNodeGetFsmTimeMean() {
		return nodeGetFsmTimeMean;
	}

	@Override
	synchronized public void setNodeGetFsmTimeMean(float nodeGetFsmTimeMean) {
		this.nodeGetFsmTimeMean = nodeGetFsmTimeMean;
	}

	@Override
	public float getNodeGetFsmTimeMedian() {
		return nodeGetFsmTimeMedian;
	}

	@Override
	synchronized public void setNodeGetFsmTimeMedian(float nodeGetFsmTimeMedian) {
		this.nodeGetFsmTimeMedian = nodeGetFsmTimeMedian;
	}

	@Override
	public float getNodeGetFsmTime95() {
		return nodeGetFsmTime95;
	}

	@Override
	synchronized public void setNodeGetFsmTime95(float nodeGetFsmTime95) {
		this.nodeGetFsmTime95 = nodeGetFsmTime95;
	}

	@Override
	public float getNodeGetFsmTime99() {
		return nodeGetFsmTime99;
	}
	
	@Override
	synchronized public void setNodeGetFsmTime99(float nodeGetFsmTime99) {
		this.nodeGetFsmTime99 = nodeGetFsmTime99;
	}
	
	@Override
	public float getNodePutFsmTimeMax() {
		return nodePutFsmTimeMax;
	}

	@Override
	synchronized public void setNodePutFsmTimeMax(float nodePutFsmTimeMax) {
		this.nodePutFsmTimeMax = nodePutFsmTimeMax;
	}

	@Override
	public float getNodePutFsmTimeMean() {
		return nodePutFsmTimeMean;
	}
	
	@Override
	synchronized public void setNodePutFsmTimeMean(float nodePutFsmTimeMean) {
		this.nodePutFsmTimeMean = nodePutFsmTimeMean;
	}

	@Override
	public float getNodePutFsmTimeMedian() {
		return nodePutFsmTimeMedian;
	}

	@Override
	synchronized public void setNodePutFsmTimeMedian(float nodePutFsmTimeMedian) {
		this.nodePutFsmTimeMedian = nodePutFsmTimeMedian;
	}

	@Override
	public float getNodePutFsmTime95() {
		return nodePutFsmTime95;
	}

	@Override
	synchronized public void setNodePutFsmTime95(float nodePutFsmTime95) {
		this.nodePutFsmTime95 = nodePutFsmTime95;
	}

	@Override
	public float getNodePutFsmTime99() {
		return nodePutFsmTime99;
	}
	
	@Override
	synchronized public void setNodePutFsmTime99(float nodePutFsmTime99) {
		this.nodePutFsmTime99 = nodePutFsmTime99;
	}
}
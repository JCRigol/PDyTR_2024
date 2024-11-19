package ej3;

import java.io.Serializable;

public class ResObj implements Serializable {
	private double processingLoadAvg;
	private long availableMemory;
	private String hostName;
	
	ResObj(double pLA, long aM, String hN) {
		this.processingLoadAvg = pLA;
		this.availableMemory = aM;
		this.hostName = hN;
	}
	
	@Override
	public String toString() {
		return "ResObj{" +
				"hostName='" + this.getHostName() + '\'' +
				", memory='" + this.getAvailableMemory() + '\'' +
				", loadAvg='" + this.getProcessingLoadAvg() + '\'' +
				'}';
	}
	
	public double getProcessingLoadAvg() {
		return this.processingLoadAvg;
	}
	
	public long getAvailableMemory() {
		return this.availableMemory;
	}
	
	public String getHostName() {
		return this.hostName;
	}
	
	public void setProcessingLoadAvg(double pLA) {
		this.processingLoadAvg = pLA;
	}
	
	public void setAvailableMemory(long aM) {
		this.availableMemory = aM;
	}
	
	public void setHostName(String hN) {
		this.hostName = hN;
	}
}

package ej3;

import jade.core.Location;
import jade.core.ContainerID;

import java.io.Serializable;
import java.util.HashSet;

public class InitObj implements Serializable {
	private HashSet<ContainerID> containerList;
	private String agentLocalName;
	private ContainerID origin;
	
	InitObj(HashSet<ContainerID> cL, String aLN, ContainerID og) {
		this.containerList = cL;
		this.agentLocalName = aLN;
		this.origin = og;
	}
	
	public HashSet<ContainerID> getContainerList() {
		return this.containerList;
	}
	
	public String getAgentLocalName() {
		return this.agentLocalName;
	}
	
	public ContainerID getOrigin() {
		return this.origin;
	}
	
	public void setContainerList(HashSet<ContainerID> cL) {
		this.containerList = cL;
	}
	
	public void setAgentLocalName(String aLN) {
		this.agentLocalName = aLN;
	}
	
	public void setOrigin(ContainerID og) {
		this.origin = og;
	}
}

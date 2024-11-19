package ej3;

import jade.core.Location;
import jade.core.ContainerID;

import java.io.Serializable;
import java.util.ArrayList;

public class InitObj implements Serializable {
	private ArrayList<ContainerID> containerList;
	private String agentLocalName;
	private ContainerID origin;
	
	InitObj(ArrayList<ContainerID> cL, String aLN, ContainerID og) {
		this.containerList = cL;
		this.agentLocalName = aLN;
		this.origin = og;
	}
	
	public ArrayList<ContainerID> getContainerList() {
		return this.containerList;
	}
	
	public String getAgentLocalName() {
		return this.agentLocalName;
	}
	
	public ContainerID getOrigin() {
		return this.origin;
	}
	
	public void setContainerList(ArrayList<ContainerID> cL) {
		this.containerList = cL;
	}
	
	public void setAgentLocalName(String aLN) {
		this.agentLocalName = aLN;
	}
	
	public void setOrigin(ContainerID og) {
		this.origin = og;
	}
}

package ej3;

import jade.lang.acl.ACLMessage;
import jade.core.Agent;

import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;

import jade.core.ContainerID;
import jade.core.Location;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;

public class AGatherer extends Agent {
	
	private HashSet<ContainerID> containerList;
	private Iterator<ContainerID> containerIter;
	
	private ContainerID origin;
	private String centralAgentLName;
	
	private HashMap<String,ResObj> results = new HashMap<String,ResObj>();

	protected void setup() {	
		SequentialBehaviour lifeCycle = new SequentialBehaviour(this);
		
		lifeCycle.addSubBehaviour(new SetupBehaviour(this));
		lifeCycle.addSubBehaviour(new TraverseBehaviour(this));
		lifeCycle.addSubBehaviour(new CleanupBehaviour(this));
		
		addBehaviour(lifeCycle);
	}
	
	protected void afterMove() {		
		try {
			InetAddress localHost = InetAddress.getLocalHost();
			OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
			
			String containerName = here().getID();
			ResObj containerResults = new ResObj(
				osBean.getSystemLoadAverage(),
				osBean.getTotalPhysicalMemorySize(),
				localHost.getHostName()
			);
			
			this.results.put(containerName, containerResults);
			
		} catch (UnknownHostException e) {
			System.out.println(e.getMessage());
		}
	}

	/* Behaviours (in order, sequence ran cyclically):
		- Receive iterator of Containers (blocking reception)
		- Traverse iterator, gathering data
		- Return to origin Container (should be Main-Container)
		- Send data to Central Agent
		- Die */
	public class SetupBehaviour extends OneShotBehaviour {
		SetupBehaviour(Agent a) {
			super(a);
		}
		
		@Override
		public void action() {
			ACLMessage message = blockingReceive();
			
			System.out.println("2");
			
			try {
				InitObj content = (InitObj) message.getContentObject();
				System.out.println("3");
				
				containerList = content.getContainerList();
				centralAgentLName = content.getAgentLocalName();
				origin = content.getOrigin();
				
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
			System.out.println("1");
			
			containerIter = containerList.iterator();
		}
	}
	
	public class TraverseBehaviour extends SimpleBehaviour {
		private boolean finished = false;
		
		TraverseBehaviour(Agent a) {
			super(a);
		}
		
		@Override
		public void action() {
			if (containerIter.hasNext()) {
				ContainerID nextContainer = containerIter.next();
				containerIter.remove();
				
				try {
					doMove(nextContainer);
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}			
			} else {
				this.finished = true;
			}
		}
		
		@Override
		public boolean done() {
			return finished;
		}
	}
	
	public class CleanupBehaviour extends OneShotBehaviour {
		CleanupBehaviour(Agent a) {
			super(a);
		}
		
		@Override
		public void action() {
			try {
				doMove(origin);
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
			AID receiver = new AID(centralAgentLName, AID.ISLOCALNAME);
			ACLMessage message = new ACLMessage(ACLMessage.INFORM);
			
			message.addReceiver(receiver);
			try {
				message.setContentObject(results);
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
			send(message);
			
			doDelete();
		}
	}
	
}

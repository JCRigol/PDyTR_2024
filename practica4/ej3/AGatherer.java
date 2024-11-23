package ej3;

import jade.lang.acl.ACLMessage;
import jade.core.Agent;

import jade.domain.introspection.AMSSubscriber;
import jade.domain.introspection.Event;
import jade.domain.introspection.MovedAgent;
import jade.domain.introspection.IntrospectionVocabulary;

import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.Behaviour;

import jade.core.ContainerID;
import jade.core.Location;
import jade.core.AID;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import java.time.LocalDateTime;

public class AGatherer extends Agent {
	
	private ArrayList<ContainerID> containerList;
	
	private ContainerID origin;
	private String centralAgentLName;
	
	private HashMap<String,ResObj> results = new HashMap<String,ResObj>();
	
	private boolean returnOrigin;
	private TraverseState tState;

	protected void setup() {
		// Debug
		System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[95m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "Creation" + "\u001B[0m");
		// addBehaviour(new AMSListenerBehaviour());
		
		// Strings
		String setupState = "setupState";
		String traverseState = "traverseState";
		String cleanupState = "cleanupState";
	
		tState = new TraverseState(this);
		FSMBehaviour lifeCycleFSM = new FSMBehaviour(this);
		
		lifeCycleFSM.registerFirstState(new SetupState(this), setupState);
		lifeCycleFSM.registerState(tState, traverseState);
		lifeCycleFSM.registerLastState(new CleanupState(this), cleanupState);
		
		lifeCycleFSM.registerDefaultTransition(setupState, traverseState);	
		
		lifeCycleFSM.registerTransition(traverseState, traverseState, 0);
		lifeCycleFSM.registerTransition(traverseState, cleanupState, 1);
		
		addBehaviour(lifeCycleFSM);
	}
	
	protected void afterMove() {
		if (!returnOrigin) {
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
				
				// Debug
				System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[95m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "AfterMove" + "\u001B[33m" + " :: " + "\u001B[31m" + containerName + " " + containerResults.toString() + "\u001B[0m");
			} catch (UnknownHostException e) {
				System.out.println(e.getMessage());
			}
		}
		
		tState.wakeUp();
	}
	
	@Override
	protected void takeDown() {
		// Debug
		System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[95m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "Destruction" + "\u001B[0m");
	}

	/* Behaviours (in order, sequence ran cyclically):
		- Receive iterator of Containers (blocking reception)
		- Traverse iterator, gathering data
		- Return to origin Container (should be Main-Container)
		- Send data to Central Agent
		- Die */
	public class SetupState extends OneShotBehaviour {
		SetupState(Agent a) {
			super(a);
		}
		
		@Override
		public void action() {
			// Debug
			System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[95m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "SetupState" + "\u001B[33m" + " :: " + "\u001B[31m" + "WaitingInitMessage" + "\u001B[0m");
		
			// Patch
			MessageTemplate mt = MessageTemplate.and(
				MessageTemplate.MatchSender(new AID("ACentral", AID.ISLOCALNAME)),
				MessageTemplate.MatchPerformative(ACLMessage.INFORM)
			);
			ACLMessage message = blockingReceive(mt);
			
			// Debug
			System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[95m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "SetupState" + "\u001B[33m" + " :: " + "\u001B[31m" + "ReceivedInitMessage" + "\u001B[0m");
			
			try {
				InitObj content = (InitObj) message.getContentObject();
				
				containerList = content.getContainerList();
				centralAgentLName = content.getAgentLocalName();
				origin = content.getOrigin();	
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}
	}
	
	public class TraverseState extends Behaviour {
		private boolean isDone = false;
		private boolean movingOk = true;
		private int nextState = -1;
		
		TraverseState(Agent a) {
			super(a);
			returnOrigin = isDone;
		}
		
		@Override
		public void action() {		
			if ((movingOk) && (!containerList.isEmpty())) {
				movingOk = false;
				ContainerID container = containerList.get(0);
				
				if (((container.equals(here())) && (containerList.size() > 1))) {
					container = containerList.get(1);
				} else if (container.equals(here())) {
					// Container List empty, current location == doMove() location
					// Debug
					System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[95m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "TraverseState" + "\u001B[33m" + " :: " + "\u001B[31m" + "Destination " + container + "\u001B[0m");
					
					edgeCase();
					containerList.remove(container);
					return;
				}
				
				// Debug
				System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[95m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "TraverseState" + "\u001B[33m" + " :: " + "\u001B[31m" + "Destination " + container + "\u001B[0m");
		            
				try {
					doMove(container);
					block();
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
		        
				containerList.remove(container);
			} else if (movingOk) {
				try {
					returnOrigin = true;
					doMove(origin);
					block();
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
				
				isDone = true;
			}
		}
		
		@Override
		public boolean done() {
			return isDone;
		}
		
		@Override
		public int onEnd() {
			if (isDone) {
				return 1;
			}
			return 0;
		}
		
		private void edgeCase() {
			// Edge Case: First Container to travel is here()
			// Methodology: Execute afterMove() logic (no wakeUp() needed since no block() was called)
			try {
				InetAddress localHost = InetAddress.getLocalHost();
				OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
				
				String containerName = here().getID();
				ResObj containerResults = new ResObj(
					osBean.getSystemLoadAverage(),
					osBean.getTotalPhysicalMemorySize(),
					localHost.getHostName()
				);
				
				results.put(containerName, containerResults);
				
				// Debug
				System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[95m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "AfterMove(EdgeCase)" + "\u001B[33m" + " :: " + "\u001B[31m" + containerName + " " + containerResults.toString() + "\u001B[0m");		
			} catch (UnknownHostException e) {
				System.out.println(e.getMessage());
			}
			
			movingOk = true;
		}
		
		public void wakeUp() {
			movingOk = true;
			restart();
		}
	}
	
	public class CleanupState extends OneShotBehaviour {
		CleanupState(Agent a) {
			super(a);
		}
		
		@Override
		public void action() {						
			AID receiver = new AID(centralAgentLName, AID.ISLOCALNAME);
			ACLMessage message = new ACLMessage(ACLMessage.INFORM);
			
			message.addReceiver(receiver);
			try {
				message.setContentObject(results);
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
			// Debug
			System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[95m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "CleanupState" + "\u001B[33m" + " :: " + "\u001B[31m" + "SendingResults" + "\u001B[0m");
			
			send(message);
			
			doDelete();
		}
	}
	
	/*
	public class AMSListenerBehaviour extends AMSSubscriber {
		@Override
		public void installHandlers(Map handlersTable) {
			handlersTable.put(IntrospectionVocabulary.MOVEDAGENT, new MovedAgentHandler());
		}
		
		public final class MovedAgentHandler implements EventHandler {
			@Override
			public void handle(Event ev) {
				MovedAgent event = (MovedAgent) ev;
				
				// Debug
				// System.out.println(event.getAgent().getName());
				
				traverseBehaviour.wakeUp();
			}
		}
	}
	*/
	
}

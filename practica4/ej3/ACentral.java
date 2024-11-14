package ej3;

import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import jade.domain.introspection.AMSSubscriber;
import jade.domain.introspection.Event;
import jade.domain.introspection.AddedContainer;
import jade.domain.introspection.RemovedContainer;
import jade.domain.introspection.IntrospectionVocabulary;

import jade.core.Agent;
import jade.core.AID;
import jade.core.ContainerID;
import jade.core.Location;

import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.WakerBehaviour;

import jade.lang.acl.ACLMessage;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;


import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

public class ACentral extends Agent {

	private ContainerID initialContainer;
	private HashSet<ContainerID> containerList = new HashSet<ContainerID>();
	private HashMap<String,ResObj> results = new HashMap<String,ResObj>();

	protected void setup() {
		// AMS Events listener
		addBehaviour(new AMSListenerBehaviour());

		// Strings
		String setupState = "setupState";
		String receptionState = "receptionState";
		String sleepState = "sleepState";
		String storageState = "storageState";
	
		FSMBehaviour containerDataRetrievalFSM = new FSMBehaviour(this);
		
		containerDataRetrievalFSM.registerFirstState(new SetupState(this), setupState);
		containerDataRetrievalFSM.registerState(new ReceptionState(this), receptionState);
		containerDataRetrievalFSM.registerState(new SleepState(this, 15000), sleepState); // Randint(10000, 15000);
		containerDataRetrievalFSM.registerLastState(new StorageState(this), storageState); // Por ahora vacío, a ser utilizado mas tarde para automatizar esto.
		
		containerDataRetrievalFSM.registerDefaultTransition(setupState, receptionState);
		containerDataRetrievalFSM.registerDefaultTransition(sleepState, setupState);
		
		containerDataRetrievalFSM.registerTransition(receptionState, receptionState, 0);
		containerDataRetrievalFSM.registerTransition(receptionState, sleepState, 1);
		containerDataRetrievalFSM.registerTransition(receptionState, storageState, 2); // Determine if gathering loop is done in reception? Maybe add "DataProcessingState"
	
		addBehaviour(containerDataRetrievalFSM);
	}

	// Can be called directly from Behaviour (or just move the logic to bhvr instead)
	public void addContainer(ContainerID c) {
		if (c.getName().equals(here().getName())) {
			initialContainer = c;
		}
		
		this.containerList.add(c);
	}
	
	// Same as addBehaviour()
	public void removeContainer(ContainerID c) {
		if (this.containerList.contains(c)) {
			this.containerList.remove(c);
		}
	}
	

	public class SetupState extends OneShotBehaviour {
		SetupState(Agent a) {
			super(a);
		}
	
		@Override
		public void action() {
			// Obtain current Container control
			AgentContainer container = getContainerController();
			
			try {
				AgentController agentController = container.createNewAgent("Gatherer", "ej3.AGatherer", null);
				agentController.start();
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
			// Create a message for the agent
			AID receiver = new AID("Gatherer", AID.ISLOCALNAME);
			ACLMessage message = new ACLMessage(ACLMessage.INFORM);
						
			InitObj content = new InitObj(
				containerList,
				getLocalName(),
				initialContainer
			);
			
			// Serializability test https://stackoverflow.com/questions/20286340/is-there-any-way-to-check-whether-an-object-is-serializable-or-not-in-java
			try {
		        ByteArrayOutputStream bf = new ByteArrayOutputStream();
		        ObjectOutputStream oos = new ObjectOutputStream(bf);
		        oos.writeObject(content);
		        oos.close();

		        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bf.toByteArray()));
		        Object o = ois.readObject();
		        System.out.println(o.toString() + "\n" + o.getClass());
		        InitObj co = (InitObj) o;
		        System.out.println(co.getContainerList() + "\n" + co.getOrigin() + "\n" + co.getAgentLocalName());
		    } catch (Exception e) {
		        System.out.println("Not exactly Serializable");
		        e.printStackTrace();
		    }
			
			message.addReceiver(receiver);
			try {
				message.setContentObject(content);
				System.out.println("4");
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
			send(message);
		}
	}
	
	public class ReceptionState extends OneShotBehaviour {
		private int exitValue;
	
		ReceptionState(Agent a) {
			super(a);
			this.exitValue = 9;
		}
	
		@Override
		public void action() {
			ACLMessage message = blockingReceive();
			
			if (message != null) {
				try {
					results = (HashMap<String,ResObj>) message.getContentObject();
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
				
				this.decide();
			
			} else {
				this.exitValue = 0;
			}
		}
		
		@Override
		public int onEnd() {
			return exitValue;
		}
		
		private void decide() {
			if (this.exitValue == 9) {
				this.exitValue = 1;
			} else {
				this.exitValue = 2;
			}
		}
	}
	
	public class SleepState extends WakerBehaviour {
		SleepState(Agent a, long tms) {
			super(a, tms);
		}
		
		@Override
		protected void onWake() {
			return;
		}
	}
	
	public class StorageState extends OneShotBehaviour {
		StorageState(Agent a) {
			super(a);
		}
		
		@Override
		public void action() {
			return;
		}
	}
	
	
	// Container addition detection as in https://stackoverflow.com/questions/508477/jade-agent-containers
	public class AMSListenerBehaviour extends AMSSubscriber {
		@Override
		public void installHandlers(Map handlersTable) {
			handlersTable.put(IntrospectionVocabulary.ADDEDCONTAINER, new AddedContainerHandler());
			handlersTable.put(IntrospectionVocabulary.REMOVEDCONTAINER, new RemovedContainerHandler());
		}
		
		public final class AddedContainerHandler implements EventHandler {
			@Override
			public void handle(Event ev) {
				AddedContainer event = (AddedContainer) ev;
				ContainerID addedContainer = event.getContainer();
				addContainer(addedContainer);
			}
		}
		
		public final class RemovedContainerHandler implements EventHandler {
			@Override
			public void handle(Event ev) {
				RemovedContainer event = (RemovedContainer) ev;
				ContainerID removedContainer = event.getContainer();
				removeContainer(removedContainer);
			}
		}
	}

}

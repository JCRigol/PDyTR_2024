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

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;

public class ACentral extends Agent {

	private ContainerID initialContainer;
	private volatile ArrayList<ContainerID> containerList = new ArrayList<>();
	private HashMap<String,ResObj> results = new HashMap<String,ResObj>();
	
	private File measurementsStorage;
	private Instant startMeasureTime;
	private Instant endMeasureTime;
	private int platformSize;
	private int iters;
	
	private DummyState dummySt;
	private FSMBehaviour containerDataRetrievalFSM;

	protected void setup() {
		iters = 0;
		
		try {
			// Setup of results storage
			measurementsStorage = new File("ej3/measurements.csv");
			measurementsStorage.delete();
			measurementsStorage.createNewFile();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	
		// AMS Events listener
		addBehaviour(new AMSListenerBehaviour());

		// Strings
		String setupState = "setupState";
		String receptionState = "receptionState";
		String sleepState = "sleepState";
		String storageState = "storageState";
		String dummyState = "dummyState";
		String dummierState = "dummierState";
		
		dummySt = new DummyState(this);
	
		containerDataRetrievalFSM = new FSMBehaviour(this);
		
		containerDataRetrievalFSM.registerFirstState(new SetupState(this), setupState);
		containerDataRetrievalFSM.registerState(new SleepState(this), sleepState); // Ideally Randint(10000, 15000);
		containerDataRetrievalFSM.registerState(new ReceptionState(this), receptionState);
		containerDataRetrievalFSM.registerState(new StorageState(this), storageState);
		containerDataRetrievalFSM.registerState(dummySt, dummyState); // DummyState necessary because FSMBehaviour is dumb and doesn't account for endless FSM.
		containerDataRetrievalFSM.registerLastState(new DummierState(this), dummierState); // DummyState necessary because FSMBehaviour is dumb and doesn't account for endless FSM.
		
		containerDataRetrievalFSM.registerDefaultTransition(setupState, receptionState);
		containerDataRetrievalFSM.registerDefaultTransition(receptionState, storageState);
		containerDataRetrievalFSM.registerDefaultTransition(sleepState, setupState);
		
		containerDataRetrievalFSM.registerTransition(storageState, sleepState, 0);
		containerDataRetrievalFSM.registerTransition(storageState, dummyState, 1);
		containerDataRetrievalFSM.registerTransition(storageState, dummierState, 2);
		
		containerDataRetrievalFSM.registerDefaultTransition(dummyState, setupState);
		
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
	
	public void signal() {
		if (iters == 10) {
			iters = 0;
			dummySt.wakeUp();
		}
	}

	public class SetupState extends OneShotBehaviour {
		SetupState(Agent a) {
			super(a);
		}
	
		@Override
		public void action() {	
			InitObj content;
			synchronized(this) {
				content = new InitObj(
					containerList,
					getLocalName(),
					initialContainer
				);
				
				platformSize = containerList.size();
			}
		
			// Measure startTime contemplates Agent Gatherer creation and initialization of variables.
			startMeasureTime = Instant.now();
		
			// Obtain current Container control
			AgentContainer container = getContainerController();
			
			try {
				AgentController agentController = container.createNewAgent("AGatherer", "ej3.AGatherer", null);
				agentController.start();
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
			/*		
			// Debug
			// Serializability test https://stackoverflow.com/questions/20286340/is-there-any-way-to-check-whether-an-object-is-serializable-or-not-in-java
			try {
		        ByteArrayOutputStream bf = new ByteArrayOutputStream();
		        ObjectOutputStream oos = new ObjectOutputStream(bf);
		        oos.writeObject(content);
		        oos.close();

		        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bf.toByteArray()));
		        Object o = ois.readObject();
		        System.out.println("DEBUG :: ACentral.121\n" + o.toString() + "\n" + o.getClass());
		        InitObj co = (InitObj) o;
		        System.out.println("DEBUG :: ACentral.123\n" + co.getContainerList() + "\n" + co.getOrigin() + "\n" + co.getAgentLocalName());
		    } catch (Exception e) {
		        System.out.println("Not exactly Serializable");
		        e.printStackTrace();
		    }			
			*/
			
			// Debug
			System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[92m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "SetupState" + "\u001B[33m" + " :: " + "\u001B[31m" + "KnownPlatform " + content.getContainerList() + "\u001B[0m");
			
			// Create a message for the agent
			AID receiver = new AID("AGatherer", AID.ISLOCALNAME);
			ACLMessage message = new ACLMessage(ACLMessage.INFORM);
			message.addReceiver(receiver);
			
			try {
				message.setContentObject(content);
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
			// Debug
			System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[92m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "SetupState" + "\u001B[33m" + " :: " + "\u001B[31m" + "SendingInitMessage" + "\u001B[0m");
			
			send(message);
		}
	}
	
	public class ReceptionState extends OneShotBehaviour {
		ReceptionState(Agent a) {
			super(a);
		}
	
		@Override
		public void action() {
			// Debug
			System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[92m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "ReceptionState" + "\u001B[33m" + " :: " + "\u001B[31m" + "WaitingResults" + "\u001B[0m");
		
			MessageTemplate mt = MessageTemplate.and(
				MessageTemplate.MatchSender(new AID("AGatherer", AID.ISLOCALNAME)),
				MessageTemplate.MatchPerformative(ACLMessage.INFORM)
			);
			ACLMessage message = blockingReceive(mt);
			
			// Measure endTime contemplates successfull reception of measurements from Agent Gatherer only
			endMeasureTime = Instant.now();

			if (message != null) {
				try {
					results = (HashMap<String,ResObj>) message.getContentObject();

					for (String key : results.keySet()) {
						ResObj r = results.get(key);
					}
					
					// Debug
					System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[92m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "ReceptionState" + "\u001B[33m" + " :: " + "\u001B[31m" + "ReceivedResults" + "\u001B[0m");
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}
		}
	}
	
	public class SleepState extends OneShotBehaviour {
		SleepState(Agent a) {
			super(a);
		}
		
		@Override
		public void action() {
			// Debug
			System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[92m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "SleepState" + "\u001B[0m");
		
			block(1000);
		}
	}
	
	public class StorageState extends OneShotBehaviour {
		private int max_iters;
	
		StorageState(Agent a) {
			super(a);
			this.max_iters = 10;
		}
		
		@Override
		public void action() {
			long timeElapsed = Duration.between(startMeasureTime, endMeasureTime).toMillis();
			
			// Debug
			System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[92m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "StorageState" + "\u001B[33m" + " :: " + "\u001B[31m" + "TimeElapsed " + timeElapsed + "ms" + "\u001B[0m");
			
			try (FileOutputStream oFile = new FileOutputStream(measurementsStorage, true)) {
				String row = platformSize + "," + timeElapsed + "\n";
				oFile.write(row.getBytes());
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			
			iters++;
		}
		
		@Override
		public int onEnd() {
			return (iters / this.max_iters);
		}
	}
	
	public class DummyState extends OneShotBehaviour {
		DummyState(Agent a) {
			super(a);
		}
		
		@Override
		public void action() {
			// Debug
			System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[92m" + getLocalName() + "\u001B[33m" + " :: " + "\u001B[34m" + "DummyState" + "\u001B[0m");
			block();
		}
		
		public void wakeUp() {
			restart();
		}
	}
	
	public class DummierState extends OneShotBehaviour {
		DummierState(Agent a) {
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
				signal();
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

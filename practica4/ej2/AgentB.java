package ej2;

import jade.lang.acl.ACLMessage;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.core.ContainerID;
import jade.core.Location;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.time.LocalDateTime;

public class AgentB extends Agent {
	
	private Location origin;
	private String father;

	protected void setup() {
		Object[] args = getArguments();
		origin = (Location) args[0];
		father = args[1].toString();
		
		// Initial report of Agent details
		report(origin.getID(), getLocalName(), getName(), "SETUP");
		
		// Message reception
		addBehaviour(new OneShotBehaviour(this) {
			@Override
			public void action() {
				// Blocking reception of message
				ACLMessage message = blockingReceive();
				
				if (message != null) {
					String content = message.getContent();
					
					switch (content) {
						case "MIGRAR":
							move();
							break;
							
						default:
							System.out.println("Mensaje recibido: " + content);
							break;
					}
				}	
			}
		});
	}
	
	protected void afterMove() {
		// Report after move of Agent details
		report(here().getID(), getLocalName(), getName(), "AFTERMOVE");
		
		// Return to origin behaviour		
		if (!here().equals(origin)) {
			addBehaviour(new WakerBehaviour(this, 2000) {
				@Override
				protected void handleElapsedTimeout() {
					try {
						move(origin);
					} catch (Exception e) {
						System.err.println("Migration failed: " + e.getMessage());
					}
				}
			});
		}
	}

	private void move() {
		// Move command
		try {
			ContainerID destination = new ContainerID("Main-Container", null);
			doMove(destination);
		} catch (Exception e) {
			System.err.println("Migration failed: " + e.getMessage());
		}
	}
	
	private void move(Location destination) {
		// Move command
		try {
			doMove(destination);
		} catch (Exception e) {
			reportError(father, e.getMessage());
		}
	}
	
	private void reportError(String agent_name, String error) {
		// Create a message for the agent
		AID receiver = new AID(agent_name, AID.ISLOCALNAME);
		ACLMessage message = new ACLMessage(ACLMessage.INFORM);
		
		message.addReceiver(receiver);
		message.setContent(error);
		
		send(message);
	}

	private void report(String location, String local_name, String full_name, String op) {
		// Debug
		System.out.println("\u001B[1m" + "\u001B[33m" + "[" + "\u001B[36m" + LocalDateTime.now() + "\u001B[33m" + "] " + "DEBUG :: " + "\u001B[92m" + local_name + "\u001B[33m" + " :: Method => " + "\u001B[34m" + op + "\u001B[33m" + " :: Location => " + "\u001B[34m" + location + "\u001B[0m");
	}
	
}

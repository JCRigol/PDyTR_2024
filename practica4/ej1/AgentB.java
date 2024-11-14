package ej1;

import jade.lang.acl.ACLMessage;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.ContainerID;
import jade.core.Location;

public class AgentB extends Agent {

	protected void setup() {
		// Initial report of Agent details
		report(here().getID(), getLocalName(), getName(), "SETUP");
		
		// Message reception
		addBehaviour(new OneShotBehaviour() {
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
			
			private void move() {
				// Move command
				try {
					ContainerID destination = new ContainerID("Main-Container", null);
					doMove(destination);
				} catch (Exception e) {
					System.err.println("Migration failed: " + e.getMessage());
				}
			}
		});
	}
	
	protected void afterMove() {
		// Report after move of Agent details
		report(here().getID(), getLocalName(), getName(), "AFTERMOVE");
	}

	private void report(String location, String local_name, String full_name, String op) {
		System.out.println("Agent " + local_name + " (" + full_name + ") in location " + location + " reports from " + op);
	}
	
}

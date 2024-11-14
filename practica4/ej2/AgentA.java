package ej2;

import jade.core.Agent;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.Location;
import jade.core.ContainerID;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

public class AgentA extends Agent {

	protected void setup() {
		// AgentB local name
		String lname = "AgenteB";
	
		// Obtain current Container control
		AgentContainer container = getContainerController();

		// Try to create and start a new agent
		Object[] args = new Object[2];
		args[0] = here();
		args[1] = getLocalName();
		try {
			AgentController agentController = container.createNewAgent(lname, "ej2.AgentB", args);
			agentController.start();
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
		
		// Create a message for the agent
		AID receiver = new AID(lname, AID.ISLOCALNAME);
		ACLMessage message = new ACLMessage(ACLMessage.INFORM);
		
		message.addReceiver(receiver);
		message.setContent("MIGRAR");
		
		send(message);
		
		// Message reception
		addBehaviour(new OneShotBehaviour(this) {
			@Override
			public void action() {
				// Blocking reception of message
				ACLMessage message = blockingReceive();
				
				if (message != null) {
					String content = message.getContent();
					System.out.println(content);
				}
			}
		});
	}

}

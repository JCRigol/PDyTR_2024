package ej1;

import jade.core.Agent;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
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
		try {
			AgentController agentController = container.createNewAgent(lname, "ej1.AgentB", null);
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
	}

}

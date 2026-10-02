package kaptainwutax.tungsten.client.helpers;

import kaptainwutax.tungsten.client.sim.AgentEntity;

/**
 * Helper class to check agent for certain conditions.
 */
public class AgentChecker {

	/**
     * Checks if agent is on ground and has less velocity then a given value.
     * 
     * @param agent Agent to be checked
     * @param minVelocity Agent needs to have less velocity then this to be considered stationary
     * @return true if agent is on ground and has less velocity then given value.
     */
	public static boolean isAgentStationary(AgentEntity agent, double minVelocity) {
        return agent.getDeltaMovement().x() < minVelocity && agent.getDeltaMovement().x() > -minVelocity &&
               agent.getDeltaMovement().z() < minVelocity && agent.getDeltaMovement().z() > -minVelocity &&
               agent.onGround();
    }
	
	
}

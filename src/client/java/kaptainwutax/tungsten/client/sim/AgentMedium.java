package kaptainwutax.tungsten.client.sim;

/**
 * High-level classification of the medium the simulated agent was in during the last travel
 * tick. Exactly one value is reported per tick. Priority order matches the repo's
 * {@code Medium.fromFlags}: web &gt; water &gt; lava &gt; ladder &gt; soul-sand &gt; air.
 */
public enum AgentMedium {
    AIR,
    WEB,
    WATER,
    LAVA,
    LADDER,
    SOULSAND
}

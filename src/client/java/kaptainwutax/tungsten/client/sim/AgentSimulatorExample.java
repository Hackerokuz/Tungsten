package kaptainwutax.tungsten.client.sim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/**
 * Tiny driver showing the typical {@link AgentSimulator} call sites. Not wired into any
 * mod — keep it as a copy-paste reference when you're calling the API from your own code.
 */
public final class AgentSimulatorExample {

    private AgentSimulatorExample() {}

    /** 1. Build a simulator that mirrors the live player and run one tick of forward sprint. */
    public static AgentStatus oneTickFromPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        AgentSimulator sim = AgentSimulator.fromLocalPlayer(player, player.getDeltaMovement());
        return sim.simulate(new AgentInput(
            /* forward */ true, /* back */ false, /* left */ false, /* right */ false,
            /* jump   */ false, /* sneak*/ false, /* sprint*/ true,
            /* pitch  */ player.getXRot(),
            /* yaw    */ player.getXRot()
        ));
    }

    /** 2. Replay a fixed input sequence and collect every status. */
    public static List<AgentStatus> scriptedWalk(AgentSimulator sim) {
        AgentInput n = new AgentInput(false,  false, false, false, false, false, true, 0f, sim.getAgent().startYaw);
        AgentInput f = new AgentInput(true,  false, false, false, false, false, true, 0f, sim.getAgent().startYaw);
        AgentInput r = new AgentInput(false, false, false, true,  false, false, true, 0f, 180f);
        AgentInput j = new AgentInput(true,  false, false, false, true,  false, true,  0f, 180f);
        return sim.simulate(List.of(n, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f));
    }

    /** 3. Branching: clone the agent's state and try two inputs from the same starting point. */
    public static void branchAndCompare(AgentSimulator base) {
        AgentStatus s0 = base.snapshot();

        AgentSimulator a = new AgentSimulator(cloneFrom(base.getAgent(), s0));
        AgentSimulator b = new AgentSimulator(cloneFrom(base.getAgent(), s0));
        AgentInput fwd     = new AgentInput(true, false, false, false, false, false, true, 0f, 180f);
        AgentInput jumpFwd = new AgentInput(true, false, false, false, true,  false, true, 0f, 180f);

        AgentStatus afterA = a.simulate(fwd);
        AgentStatus afterB = b.simulate(jumpFwd);

        // Compare positions, velocities, onGround, etc.
        double dy = afterA.position.y - afterB.position.y;
    }

    private static AgentEntity cloneFrom(AgentEntity src, AgentStatus s) {
        AgentEntity clone = new AgentEntity(
            src.level(), src.getGameProfile(),
            s.position, s.velocity, s.yaw
        );
        clone.setXRot(s.pitch);
        clone.setOnGround(s.onGround);
        clone.horizontalCollision = s.horizontalCollision;
        clone.minorHorizontalCollision = s.softCollision;
        clone.setSprinting(s.sprinting);
        clone.setSwimming(s.swimming);
        return clone;
    }
}

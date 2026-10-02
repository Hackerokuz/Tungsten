package kaptainwutax.tungsten.client.sim;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

/**
 * Immutable input fed to {@link AgentSimulator} for one tick.
 *
 * <p>Movement flags and rotation fields are absolute values for the tick (i.e. yaw/pitch are
 * the desired orientation after applying the input, not deltas). Use the entity's current
 * yaw/pitch if you want a "no rotation change" tick — {@link #keysOnly} does that for you.
 *
 * <p>This file has no dependencies on anything outside Minecraft and the Java standard library.
 */
public final class AgentInput {

    public final boolean forward;
    public final boolean back;
    public final boolean left;
    public final boolean right;
    public final boolean jump;
    public final boolean sneak;
    public final boolean sprint;
    public final float pitch;
    public final float yaw;

    public static final AgentInput NONE = new AgentInput(
        false, false, false, false,
        false, false, false,
        0f, 0f
    );

    public AgentInput(
        boolean forward,
        boolean back,
        boolean left,
        boolean right,
        boolean jump,
        boolean sneak,
        boolean sprint,
        float pitch,
        float yaw
    ) {
        this.forward = forward;
        this.back = back;
        this.left = left;
        this.right = right;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;
        this.pitch = pitch;
        this.yaw = yaw;
    }

    /** Returns a copy with yaw replaced. */
    public AgentInput withYaw(float newYaw) {
        return new AgentInput(forward, back, left, right, jump, sneak, sprint, pitch, newYaw);
    }

    /** Returns a copy with pitch replaced. */
    public AgentInput withPitch(float newPitch) {
        return new AgentInput(forward, back, left, right, jump, sneak, sprint, newPitch, yaw);
    }

    /** Returns a copy with yaw offset (existing yaw + delta). */
    public AgentInput withYawDelta(float delta) {
        return withYaw(yaw + delta);
    }

    /**
     * Convenience factory that copies pitch/yaw from the given entity — handy when the caller
     * only wants to vary keys, not look direction.
     */
    public static AgentInput keysOnly(
        boolean forward, boolean back, boolean left, boolean right,
        boolean jump, boolean sneak, boolean sprint,
        AgentEntity agent
    ) {
        return new AgentInput(forward, back, left, right, jump, sneak, sprint,
            agent.getXRot(), agent.getYRot());
    }

    public Input toInput() {
        return new Input(forward, back, left, right, jump, sneak, sprint);
    }

}

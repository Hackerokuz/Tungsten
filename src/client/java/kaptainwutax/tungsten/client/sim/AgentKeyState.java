package kaptainwutax.tungsten.client.sim;

import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Per-tick key state for an {@link AgentEntity}. Mirrors the role of {@code SimulatorInput}
 * from the repo but is built directly on top of vanilla {@link Input} — no project-internal
 * {@code InputRow} dependency.
 *
 * <p>The lifecycle is:
 * <ol>
 *   <li>{@link AgentEntity#feed(AgentInput)} stores the desired input via {@link #setCurrent}.</li>
 *   <li>{@link AgentEntity#aiStep()} calls {@link #tick()} at its start, which rebuilds
 *       {@link #keyPresses} (the vanilla {@code Input} record MC's movement code reads) and
 *       {@link #moveVector} (the {@code Vec2} that {@code applyInput} projects onto
 *       {@code xxa} / {@code zza}).</li>
 * </ol>
 * Callers can read {@link #keyPresses} and {@link #moveVector} after a tick to inspect
 * what the entity saw.
 */
public final class AgentKeyState {

    public Input keyPresses = Input.EMPTY;
    public Vec2 moveVector = Vec2.ZERO;

    private AgentInput current = AgentInput.NONE;

    /** Queue an input for the next {@link #tick()} call. */
    public void setCurrent(AgentInput in) {
        this.current = in != null ? in : AgentInput.NONE;
    }

    /** Rebuild {@link #keyPresses} and {@link #moveVector} from the latest input. */
    public void tick() {
        AgentInput in = this.current;
        this.keyPresses = new Input(
            in.forward,   // forward
            in.back,      // backward
            in.left,      // left
            in.right,     // right
            in.jump,      // jump
            in.sneak,     // shift
            in.sprint     // sprint
        );
        float forward = axisValue(in.forward, in.back);
        float strafe = axisValue(in.left, in.right);
        this.moveVector = new Vec2(strafe, forward).normalized();
    }

    /**
     * Force the keypress snapshot without going through the queued input. Useful for
     * restore-from-checkpoint paths.
     */
    public void seedKeyPresses(Input presses) {
        this.keyPresses = presses != null ? presses : Input.EMPTY;
        float forward = axisValue(this.keyPresses.forward(), this.keyPresses.backward());
        float strafe = axisValue(this.keyPresses.left(), this.keyPresses.right());
        this.moveVector = new Vec2(strafe, forward).normalized();
    }

    /** True if the entity currently has any forward-axis input. */
    public boolean hasForwardImpulse() {
        return this.moveVector.y > 0.0F;
    }

    private static float axisValue(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }
}

package kaptainwutax.tungsten.client.sim;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a simulated agent's state after a tick.
 *
 * <p>Captured from {@link AgentEntity} right after its tick completes. All Vec3 fields
 * are defensive copies of the entity's mutable vectors, so it's safe to retain and inspect
 * this object after further ticks.
 */
public final class AgentStatus {

    // --- position / motion ---
    public final Vec3 position;
    public final Vec3 velocity;
    public final float yaw;
    public final float pitch;

    // --- collision / contact ---
    public final boolean onGround;
    public final boolean horizontalCollision;
    public final boolean softCollision;            // minor horizontal collision
    public final boolean verticalCollision;
    public final double fallDistance;
    /** NaN when the entity had no meaningful motion this tick. */
    public final double collisionAngleDegrees;

    // --- locomotion state ---
    public final boolean sprinting;
    public final boolean sneaking;                // crouching (pose + shiftKeyDown)
    public final boolean swimming;
    public final boolean fallFlying;
    public final Pose pose;
    public final int sprintTriggerTime;           // double-tap sprint window
    public final int jumpCooldown;                // LivingEntity.noJumpDelay

    // --- medium ---
    public final boolean inWater;
    public final boolean underWater;
    public final boolean inLava;
    public final boolean onClimbable;
    /** Web / soul-sand / etc. Null when not stuck. */
    public final Vec3 stuckMultiplier;
    /** High-level medium classification for the last travel() — see {@link AgentMedium}. */
    public final AgentMedium medium;
    /** Block friction under the entity; NaN when airborne. */
    public final double groundFriction;

    // --- vitals ---
    public final float health;
    public final float maxHealth;
    public final boolean dead;

    public final int foodLevel;
    public final float foodSaturation;

    public final int airSupply;                   // breath
    public final int maxAirSupply;
    public final boolean onFire;

    // --- effects ---
    public final List<MobEffectInstance> activeEffects;

    public AgentStatus(
        Vec3 position, Vec3 velocity, float yaw, float pitch,
        boolean onGround, boolean horizontalCollision, boolean softCollision,
        boolean verticalCollision, double fallDistance, double collisionAngleDegrees,
        boolean sprinting, boolean sneaking, boolean swimming, boolean fallFlying,
        Pose pose, int sprintTriggerTime, int jumpCooldown,
        boolean inWater, boolean underWater, boolean inLava, boolean onClimbable,
        Vec3 stuckMultiplier, AgentMedium medium, double groundFriction,
        float health, float maxHealth, boolean dead,
        int foodLevel, float foodSaturation,
        int airSupply, int maxAirSupply, boolean onFire,
        List<MobEffectInstance> activeEffects
    ) {
        this.position = position;
        this.velocity = velocity;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
        this.softCollision = softCollision;
        this.verticalCollision = verticalCollision;
        this.fallDistance = fallDistance;
        this.collisionAngleDegrees = collisionAngleDegrees;
        this.sprinting = sprinting;
        this.sneaking = sneaking;
        this.swimming = swimming;
        this.fallFlying = fallFlying;
        this.pose = pose;
        this.sprintTriggerTime = sprintTriggerTime;
        this.jumpCooldown = jumpCooldown;
        this.inWater = inWater;
        this.underWater = underWater;
        this.inLava = inLava;
        this.onClimbable = onClimbable;
        this.stuckMultiplier = stuckMultiplier;
        this.medium = medium;
        this.groundFriction = groundFriction;
        this.health = health;
        this.maxHealth = maxHealth;
        this.dead = dead;
        this.foodLevel = foodLevel;
        this.foodSaturation = foodSaturation;
        this.airSupply = airSupply;
        this.maxAirSupply = maxAirSupply;
        this.onFire = onFire;
        this.activeEffects = activeEffects == null
            ? Collections.emptyList()
            : List.copyOf(activeEffects);
    }

    @Override
    public String toString() {
        return "AgentStatus{" +
            "pos=" + position +
            ", vel=" + velocity +
            ", yaw=" + yaw +
            ", pitch=" + pitch +
            ", onGround=" + onGround +
            ", hColl=" + horizontalCollision +
            ", sColl=" + softCollision +
            ", sprinting=" + sprinting +
            ", sneaking=" + sneaking +
            ", swimming=" + swimming +
            ", inWater=" + inWater +
            ", underWater=" + underWater +
            ", inLava=" + inLava +
            ", air=" + airSupply + "/" + maxAirSupply +
            ", food=" + foodLevel + "/" + foodSaturation +
            ", hp=" + health + "/" + maxHealth +
            ", fallDist=" + fallDistance +
            ", medium=" + medium +
            "}";
    }

    // ------------------------------------------------------------------
    // Equality / comparison
    // ------------------------------------------------------------------

    /**
     * Exact equality across all fields. Useful for tests and {@code ==} checks.
     * For pathfinding caches use {@link #epsilonEquals(AgentStatus, double)} instead —
     * float drift between two sim runs that should be "the same state" will defeat this
     * method.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentStatus s)) return false;
        return onGround == s.onGround
                && horizontalCollision == s.horizontalCollision
                && softCollision == s.softCollision
                && verticalCollision == s.verticalCollision
                && sprinting == s.sprinting
                && sneaking == s.sneaking
                && swimming == s.swimming
                && fallFlying == s.fallFlying
                && dead == s.dead
                && inWater == s.inWater
                && underWater == s.underWater
                && inLava == s.inLava
                && onClimbable == s.onClimbable
                && onFire == s.onFire
                && sprintTriggerTime == s.sprintTriggerTime
                && jumpCooldown == s.jumpCooldown
                && foodLevel == s.foodLevel
                && airSupply == s.airSupply
                && maxAirSupply == s.maxAirSupply
                && pose == s.pose
                && medium == s.medium
                && Float.compare(yaw, s.yaw) == 0
                && Float.compare(pitch, s.pitch) == 0
                && Double.compare(fallDistance, s.fallDistance) == 0
                && Double.compare(health, s.health) == 0
                && Double.compare(maxHealth, s.maxHealth) == 0
                && Double.compare(foodSaturation, s.foodSaturation) == 0
                && nanEq(collisionAngleDegrees, s.collisionAngleDegrees)
                && nanEq(groundFriction, s.groundFriction)
                && position.equals(s.position)
                && velocity.equals(s.velocity)
                && Objects.equals(stuckMultiplier, s.stuckMultiplier)
                && activeEffects.equals(s.activeEffects);
    }

    @Override
    public int hashCode() {
        int h = 1;
        h = 31 * h + position.hashCode();
        h = 31 * h + velocity.hashCode();
        h = 31 * h + Float.hashCode(yaw);
        h = 31 * h + Float.hashCode(pitch);
        h = 31 * h + Boolean.hashCode(onGround);
        h = 31 * h + Boolean.hashCode(horizontalCollision);
        h = 31 * h + Boolean.hashCode(softCollision);
        h = 31 * h + Boolean.hashCode(verticalCollision);
        h = 31 * h + Boolean.hashCode(sprinting);
        h = 31 * h + Boolean.hashCode(sneaking);
        h = 31 * h + Boolean.hashCode(swimming);
        h = 31 * h + Boolean.hashCode(fallFlying);
        h = 31 * h + Boolean.hashCode(dead);
        h = 31 * h + Boolean.hashCode(inWater);
        h = 31 * h + Boolean.hashCode(underWater);
        h = 31 * h + Boolean.hashCode(inLava);
        h = 31 * h + Boolean.hashCode(onClimbable);
        h = 31 * h + Boolean.hashCode(onFire);
        h = 31 * h + Double.hashCode(fallDistance);
        h = 31 * h + Double.hashCode(collisionAngleDegrees);
        h = 31 * h + Double.hashCode(health);
        h = 31 * h + Double.hashCode(maxHealth);
        h = 31 * h + Float.hashCode(foodSaturation);
        h = 31 * h + sprintTriggerTime;
        h = 31 * h + jumpCooldown;
        h = 31 * h + foodLevel;
        h = 31 * h + airSupply;
        h = 31 * h + maxAirSupply;
        h = 31 * h + (pose != null ? pose.hashCode() : 0);
        h = 31 * h + (medium != null ? medium.hashCode() : 0);
        h = 31 * h + (stuckMultiplier != null ? stuckMultiplier.hashCode() : 0);
        h = 31 * h + activeEffects.hashCode();
        return h;
    }

    // ------------------------------------------------------------------
    // Tolerance-based equality (for transposition tables)
    // ------------------------------------------------------------------

    /**
     * Two statuses are equal if every numeric field is within {@code epsilon} of the
     * other, every boolean / int / enum field matches exactly, and {@code stuckMultiplier}
     * is either both null or component-wise within epsilon.
     *
     * <p>NaN handling: if a {@code double} field is NaN on one side and a number on the
     * other, they're considered unequal. If both are NaN, equal. If both are numbers,
     * the epsilon test applies.
     *
     * <p>Use this for transposition-table keys in a pathfinder. {@code epsilon} around
     * {@code 1.0E-3} is a reasonable default for position; for velocity the same value
     * works.
     */
    public boolean epsilonEquals(AgentStatus other, double epsilon) {
        if (this == other) return true;
        if (other == null) return false;
        if (onGround != other.onGround) return false;
        if (horizontalCollision != other.horizontalCollision) return false;
        if (softCollision != other.softCollision) return false;
        if (verticalCollision != other.verticalCollision) return false;
        if (sprinting != other.sprinting) return false;
        if (sneaking != other.sneaking) return false;
        if (swimming != other.swimming) return false;
        if (fallFlying != other.fallFlying) return false;
        if (dead != other.dead) return false;
        if (inWater != other.inWater) return false;
        if (underWater != other.underWater) return false;
        if (inLava != other.inLava) return false;
        if (onClimbable != other.onClimbable) return false;
        if (onFire != other.onFire) return false;
        if (sprintTriggerTime != other.sprintTriggerTime) return false;
        if (jumpCooldown != other.jumpCooldown) return false;
        if (foodLevel != other.foodLevel) return false;
        if (airSupply != other.airSupply) return false;
        if (maxAirSupply != other.maxAirSupply) return false;
        if (pose != other.pose) return false;
        if (medium != other.medium) return false;
        if (Math.abs(yaw - other.yaw) > epsilon) return false;
        if (Math.abs(pitch - other.pitch) > epsilon) return false;
        if (!vecEpsilonEquals(position, other.position, epsilon)) return false;
        if (!vecEpsilonEquals(velocity, other.velocity, epsilon)) return false;
        if (!nanEpsilonEquals(collisionAngleDegrees, other.collisionAngleDegrees, epsilon)) return false;
        if (!nanEpsilonEquals(groundFriction, other.groundFriction, epsilon)) return false;
        if (Math.abs(fallDistance - other.fallDistance) > epsilon) return false;
        if (Math.abs(health - other.health) > epsilon) return false;
        if (Math.abs(maxHealth - other.maxHealth) > epsilon) return false;
        if (Math.abs(foodSaturation - other.foodSaturation) > epsilon) return false;
        if ((stuckMultiplier == null) != (other.stuckMultiplier == null)) return false;
        if (stuckMultiplier != null
                && !vecEpsilonEquals(stuckMultiplier, other.stuckMultiplier, epsilon)) return false;
        // activeEffects: order is stable (we copyOf in the ctor), so exact equality is fine.
        return activeEffects.equals(other.activeEffects);
    }

    /**
     * Hash code consistent with {@link #epsilonEquals(AgentStatus, double)}: every
     * continuous field is quantized onto an epsilon grid before hashing, so two
     * epsilon-equal statuses produce the same bucket.
     */
    public int epsilonHashCode(double epsilon) {
        int h = 1;
        h = 31 * h + position.hashCode();   // Vec3 equality is already exact; quantization handled in epsilonEquals
        h = 31 * h + velocity.hashCode();
        h = 31 * h + Float.hashCode(quantize(yaw, epsilon));
        h = 31 * h + Float.hashCode(quantize(pitch, epsilon));
        h = 31 * h + Boolean.hashCode(onGround);
        h = 31 * h + Boolean.hashCode(horizontalCollision);
        h = 31 * h + Boolean.hashCode(softCollision);
        h = 31 * h + Boolean.hashCode(verticalCollision);
        h = 31 * h + Boolean.hashCode(sprinting);
        h = 31 * h + Boolean.hashCode(sneaking);
        h = 31 * h + Boolean.hashCode(swimming);
        h = 31 * h + Boolean.hashCode(fallFlying);
        h = 31 * h + Boolean.hashCode(dead);
        h = 31 * h + Boolean.hashCode(inWater);
        h = 31 * h + Boolean.hashCode(underWater);
        h = 31 * h + Boolean.hashCode(inLava);
        h = 31 * h + Boolean.hashCode(onClimbable);
        h = 31 * h + Boolean.hashCode(onFire);
        h = 31 * h + Double.hashCode(quantize(fallDistance, epsilon));
        h = 31 * h + Double.hashCode(quantize(collisionAngleDegrees, epsilon));
        h = 31 * h + Double.hashCode(quantize(health, epsilon));
        h = 31 * h + Double.hashCode(quantize(maxHealth, epsilon));
        h = 31 * h + Float.hashCode(quantize(foodSaturation, epsilon));
        h = 31 * h + sprintTriggerTime;
        h = 31 * h + jumpCooldown;
        h = 31 * h + foodLevel;
        h = 31 * h + airSupply;
        h = 31 * h + maxAirSupply;
        h = 31 * h + (pose != null ? pose.hashCode() : 0);
        h = 31 * h + (medium != null ? medium.hashCode() : 0);
        h = 31 * h + (stuckMultiplier != null ? stuckMultiplier.hashCode() : 0);
        h = 31 * h + activeEffects.hashCode();
        return h;
    }

    // ------------------------------------------------------------------
    // Diff (for debugging "what changed between two states?")
    // ------------------------------------------------------------------

    /**
     * Per-field diff between this status and {@code other}. Convenience: useful for
     * "why did this candidate score higher?" debugging.
     */
    public Diff diff(AgentStatus other) {
        return new Diff(this, other);
    }

    /** Snapshot of the field-by-field differences between two {@link AgentStatus}es. */
    public static final class Diff {
        public final AgentStatus from;
        public final AgentStatus to;

        public final double positionDelta;   // |to.pos - from.pos|
        public final double velocityDelta;   // |to.vel - from.vel|
        public final float  yawDelta;        // shortest signed delta, normalized to (-180, 180]
        public final float  pitchDelta;
        public final double fallDistanceDelta;
        public final double healthDelta;
        public final int    foodDelta;
        public final int    airDelta;

        /** Names of boolean fields that flipped between the two statuses. */
        public final List<String> changedBooleans;
        /** Names of int fields whose value changed. */
        public final List<String> changedInts;

        public Diff(AgentStatus from, AgentStatus to) {
            this.from = from;
            this.to = to;
            this.positionDelta = to.position.distanceTo(from.position);
            this.velocityDelta = to.velocity.distanceTo(from.velocity);
            this.yawDelta = shortestYawDelta(from.yaw, to.yaw);
            this.pitchDelta = to.pitch - from.pitch;
            this.fallDistanceDelta = to.fallDistance - from.fallDistance;
            this.healthDelta = to.health - from.health;
            this.foodDelta = to.foodLevel - from.foodLevel;
            this.airDelta = to.airSupply - from.airSupply;

            List<String> bools = new ArrayList<>();
            if (from.onGround != to.onGround)             bools.add("onGround");
            if (from.horizontalCollision != to.horizontalCollision) bools.add("horizontalCollision");
            if (from.softCollision != to.softCollision)   bools.add("softCollision");
            if (from.verticalCollision != to.verticalCollision) bools.add("verticalCollision");
            if (from.sprinting != to.sprinting)           bools.add("sprinting");
            if (from.sneaking != to.sneaking)             bools.add("sneaking");
            if (from.swimming != to.swimming)             bools.add("swimming");
            if (from.fallFlying != to.fallFlying)         bools.add("fallFlying");
            if (from.dead != to.dead)                     bools.add("dead");
            if (from.inWater != to.inWater)               bools.add("inWater");
            if (from.underWater != to.underWater)         bools.add("underWater");
            if (from.inLava != to.inLava)                 bools.add("inLava");
            if (from.onClimbable != to.onClimbable)       bools.add("onClimbable");
            if (from.onFire != to.onFire)                 bools.add("onFire");
            if (from.pose != to.pose)                     bools.add("pose");
            if (from.medium != to.medium)                 bools.add("medium");
            this.changedBooleans = List.copyOf(bools);

            List<String> ints = new ArrayList<>();
            if (from.sprintTriggerTime != to.sprintTriggerTime) ints.add("sprintTriggerTime");
            if (from.jumpCooldown != to.jumpCooldown)           ints.add("jumpCooldown");
            if (from.foodLevel != to.foodLevel)                 ints.add("foodLevel");
            if (from.airSupply != to.airSupply)                 ints.add("airSupply");
            if (from.maxAirSupply != to.maxAirSupply)           ints.add("maxAirSupply");
            this.changedInts = List.copyOf(ints);
        }

        @Override
        public String toString() {

            StringWriter stringWriter = new StringWriter();

            stringWriter.write("Diff{");

            if (positionDelta > 0) {
                stringWriter.write("Δpos=" + positionDelta);
            }
            if (velocityDelta > 0) {
                stringWriter.write(", Δvel=" + velocityDelta);
            }
            if (yawDelta > 0) {
                stringWriter.write(", Δyaw=" + yawDelta);
            }
            if (pitchDelta > 0) {
                stringWriter.write(", Δpitch=" + pitchDelta);
            }
            if (healthDelta > 0) {
                stringWriter.write(", Δhp=" + healthDelta);
            }
            if (foodDelta > 0) {
                stringWriter.write(", Δfood=" + foodDelta);
            }
            if (airDelta > 0) {
                stringWriter.write(", Δair=" + airDelta);
            }
            if (airDelta > 0) {
                stringWriter.write(", Δair=" + airDelta);
            }
            if (!changedBooleans.isEmpty()) {
                stringWriter.write(", bools=" + changedBooleans);
            }
            if (!changedInts.isEmpty()) {
                stringWriter.write(", ints=" + changedInts);
            }
            stringWriter.write( "}");

            return stringWriter.toString();
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static boolean vecEpsilonEquals(Vec3 a, Vec3 b, double epsilon) {
        return Math.abs(a.x - b.x) <= epsilon
                && Math.abs(a.y - b.y) <= epsilon
                && Math.abs(a.z - b.z) <= epsilon;
    }

    /** True if both are NaN, or both finite and within epsilon. */
    private static boolean nanEpsilonEquals(double a, double b, double epsilon) {
        if (Double.isNaN(a) && Double.isNaN(b)) return true;
        if (Double.isNaN(a) || Double.isNaN(b)) return false;
        return Math.abs(a - b) <= epsilon;
    }

    private static boolean nanEq(double a, double b) {
        if (Double.isNaN(a) && Double.isNaN(b)) return true;
        if (Double.isNaN(a) || Double.isNaN(b)) return false;
        return a == b;
    }

    /** Snap a continuous value to the nearest epsilon-grid point. */
    private static float quantize(float v, double epsilon) {
        return (float) (Math.round(v / epsilon) * epsilon);
    }

    private static double quantize(double v, double epsilon) {
        return Math.round(v / epsilon) * epsilon;
    }

    /** Shortest signed angular delta in (-180, 180]. */
    private static float shortestYawDelta(float from, float to) {
        float d = (to - from) % 360.0F;
        if (d > 180.0F)  d -= 360.0F;
        if (d <= -180.0F) d += 360.0F;
        return d;
    }
}

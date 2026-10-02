package kaptainwutax.tungsten.client.sim;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;

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
}

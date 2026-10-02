package kaptainwutax.tungsten.client.sim;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Self-contained player-entity simulator.
 *
 * <p>Replaces {@code SimulatorEntity} + {@code SimulatorInput} from the ParkourCalculator
 * repo: it extends vanilla {@link Player}, mirrors {@code LocalPlayer}'s sprint / sneak /
 * collision-angle logic, and routes every input through {@link AgentInput}. Everything
 * required to run a tick is on this class — no project-internal helpers are referenced.
 *
 * <h2>How to drive it</h2>
 * The intended use is via {@link AgentSimulator}, which calls {@link #feed(AgentInput)} and
 * then {@link #tick()}. You can also drive the entity directly: call {@code feed} to push
 * the desired input, then call {@code tick()} to advance one MC tick (1/20 s) and read
 * {@link #snapshot()} for the resulting state.
 *
 * <h2>Threading</h2>
 * Must run on the thread that owns the underlying {@link Level}. On SP/integrated servers
 * that's the client thread for a {@code ClientLevel}, or the server thread when bound to a
 * {@link ServerLevel} (preferred — block physics and chunks page in natively).
 */
public class AgentEntity extends Player {

    // ------------------------------------------------------------------
    // Configurable start state
    // ------------------------------------------------------------------

    public Vec3 startPosition;
    public Vec3 startVelocity;
    public float startYaw;

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /**
     * Public so callers can inspect the projected key state. Same role as
     * {@code SimulatorInput} in the repo.
     */
    public final AgentKeyState input = new AgentKeyState();

    // ------------------------------------------------------------------
    // Sprint / crouch transient state
    // ------------------------------------------------------------------

    private int sprintTriggerTime = 0;
    private boolean crouching;
    private int noJumpDelay;

    // ------------------------------------------------------------------
    // Kinematic mode flag — when true, travel() skips block reads and does
    // pure-math integration. Used for fast pre-filtering on worker threads.
    // ------------------------------------------------------------------

    private boolean kinematic = false;

    /** Enable / disable world-free kinematic travel. See {@link #kinematicTravel}. */
    public void setKinematic(boolean enabled) {
        this.kinematic = enabled;
    }

    public boolean isKinematic() {
        return kinematic;
    }

    // ------------------------------------------------------------------
    // Tick capture (for snapshot / debug)
    // ------------------------------------------------------------------

    private double lastCollisionAngleDegrees = Double.NaN;
    private boolean collisionAngleComputedThisTick = false;
    private AgentMedium tickMedium = AgentMedium.AIR;
    private double tickGroundFriction = Double.NaN;
    private int tickSoulsandCells;

    // ------------------------------------------------------------------
    // Construction / lifecycle
    // ------------------------------------------------------------------

    public AgentEntity(Level world, GameProfile profile, Vec3 startPosition, Vec3 startVelocity, float startYaw) {
        super(world, profile);
        this.startPosition = startPosition;
        this.startVelocity = startVelocity;
        this.startYaw = startYaw;
        resetPlayer();
    }



    public static AgentEntity cloneFrom(AgentEntity src, AgentStatus s) {
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

    /**
     * Push the desired input for the upcoming tick. The yaw/pitch are applied immediately;
     * the keys are picked up by {@link AgentKeyState#tick()} which runs at the start of
     * {@link #aiStep()}.
     */
    public void feed(AgentInput in) {
        this.input.setCurrent(in);
        this.setXRot(in.pitch);
        this.setYRot(in.yaw);
    }

    /** Full reset back to {@link #startPosition} / {@link #startVelocity} / {@link #startYaw}. */
    public void resetPlayer() {
        this.noPhysics = true;
        this.removeAllEffects();
        this.clearFire();
        this.setHealth(this.getMaxHealth());
        this.setPos(startPosition);
        this.setDeltaMovement(Vec3.ZERO);
        this.setRot(startYaw, 0);
        this.input.setCurrent(AgentInput.NONE);
        this.sprintTriggerTime = 0;
        this.tick();
        this.tick();
        this.setPos(startPosition);
        this.setDeltaMovement(startVelocity);
        this.tickMedium = AgentMedium.AIR;
        this.tickGroundFriction = Double.NaN;
        this.tickSoulsandCells = 0;
    }

    /** Teleport, zero transient flags, clear input. Used to seed the agent at an exact state. */
    public void teleportRest(double x, double y, double z, double vx, double vy, double vz) {
        float keepYaw = this.getYRot();
        this.setPos(x, y, z);
        this.setDeltaMovement(vx, vy, vz);
        this.setYRot(keepYaw);
        this.setOnGround(true);
        this.horizontalCollision = false;
        this.minorHorizontalCollision = false;
        this.setSprinting(false);
        this.sprintTriggerTime = 0;
        this.input.setCurrent(AgentInput.NONE);
        this.noJumpDelay = 0;
        this.stuckSpeedMultiplier = Vec3.ZERO;
        this.fallDistance = 0;
        this.crouching = false;
        this.setPose(Pose.STANDING);
        this.refreshDimensions();
    }

    /** Capture the current state into an immutable record (see {@link #restoreCheckpoint}). */
    public Checkpoint saveCheckpoint() {
        Checkpoint c = new Checkpoint();
        c.pos = this.position();
        c.velocity = this.getDeltaMovement();
        c.yaw = this.getYRot();
        c.pitch = this.getXRot();
        c.onGround = this.onGround();
        c.horizontalCollision = this.horizontalCollision;
        c.collidedSoftly = this.minorHorizontalCollision;
        c.sprinting = this.isSprinting();
        c.swimming = this.isSwimming();
        c.ticksLeftToDoubleTapSprint = this.sprintTriggerTime;
        c.jumpingCooldown = this.noJumpDelay;
        c.movementMultiplier = this.stuckSpeedMultiplier;
        c.pose = this.getPose();
        c.crouching = this.crouching;
        c.fallDistance = this.fallDistance;
        c.input = this.input.keyPresses;
        return c;
    }

    /** Restore from a previously captured {@link Checkpoint}. */
    public void restoreCheckpoint(Checkpoint c) {
        // Start from the clean spawn baseline so no uncaptured entity state from a previous
        // run leaks in; the overlay below restores the history it carries.
        resetPlayer();
        this.crouching = c.crouching;
        this.setPose(c.pose);
        this.refreshDimensions();
        this.setPos(c.pos);
        settleFluidState();
        this.setDeltaMovement(c.velocity);
        this.setYRot(c.yaw);
        this.setXRot(c.pitch);
        this.setOnGround(c.onGround);
        this.horizontalCollision = c.horizontalCollision;
        this.minorHorizontalCollision = c.collidedSoftly;
        this.setSprinting(c.sprinting);
        this.setSwimming(c.swimming);
        this.sprintTriggerTime = c.ticksLeftToDoubleTapSprint;
        if (c.input != null) this.input.seedKeyPresses(c.input);
        this.noJumpDelay = c.jumpingCooldown;
        this.stuckSpeedMultiplier = c.movementMultiplier;
        this.fallDistance = c.fallDistance;
    }

    private void settleFluidState() {
        this.updateFluidInteraction();
        this.wasEyeInWater = this.isEyeInFluid(FluidTags.WATER);
        this.updateIsUnderwater();
        this.updateSwimming();
        this.updatePlayerPose();
    }

    // ------------------------------------------------------------------
    // Snapshot
    // ------------------------------------------------------------------

    /** Build a defensive-copy snapshot of the current state without ticking. */
    public AgentStatus snapshot() {
        Vec3 pos = this.position();
        Vec3 vel = this.getDeltaMovement();
        Vec3 stuck = this.stuckSpeedMultiplier;
        double groundFriction = this.tickGroundFriction;
        return new AgentStatus(
            new Vec3(pos.x, pos.y, pos.z),
            new Vec3(vel.x, vel.y, vel.z),
            this.getYRot(),
            this.getXRot(),
            this.onGround(),
            this.horizontalCollision,
            this.minorHorizontalCollision,
            this.verticalCollision,
            this.fallDistance,
            this.lastCollisionAngleDegrees,
            this.isSprinting(),
            this.isShiftKeyDown(),
            this.isSwimming(),
            this.isFallFlying(),
            this.getPose(),
            this.sprintTriggerTime,
            this.noJumpDelay,
            this.isInWater(),
            this.isUnderWater(),
            this.isInLava(),
            this.onClimbable(),
            stuck == null || stuck.lengthSqr() < 1.0E-7 ? null : new Vec3(stuck.x, stuck.y, stuck.z),
            this.tickMedium,
            groundFriction,
            this.getHealth(),
            this.getMaxHealth(),
            this.isDeadOrDying(),
            this.getFoodData().getFoodLevel(),
            this.getFoodData().getSaturationLevel(),
            this.getAirSupply(),
            this.getMaxAirSupply(),
            this.isOnFire(),
            new ArrayList<>(this.getActiveEffects())
        );
    }

    // ------------------------------------------------------------------
    // Tick + sprint / crouch — mirrors LocalPlayer.aiStep
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        Vec3 before = this.position();
        super.tick();
        if (!this.collisionAngleComputedThisTick) {
            Vec3 after = this.position();
            this.lastCollisionAngleDegrees = computeCollisionAngleDegrees(
                after.x - before.x, after.z - before.z);
        }
    }

    @Override
    public void aiStep() {
        this.lastCollisionAngleDegrees = Double.NaN;
        this.collisionAngleComputedThisTick = false;
        if (this.sprintTriggerTime > 0) {
            this.sprintTriggerTime--;
        }

        // Capture pre-input.tick() so wasShiftKeyDown / hasForwardImpulse hold the previous
        // tick's state — same as LocalPlayer.
        boolean wasShiftKeyDown = this.input.keyPresses.shift();
        boolean hasForwardImpulse = this.input.hasForwardImpulse();

        // Forced crouch under a low ceiling keeps the slowdown after sneak is released; this
        // is the !canPlayerFitWithinBlocksAndEntitiesWhen(STANDING) term in vanilla.
        this.crouching = !this.getAbilities().flying
            && !this.isSwimming()
            && !this.isPassenger()
            && this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)
            && (this.isShiftKeyDown()
                || !this.isSleeping() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING));

        this.input.tick();

        if (wasShiftKeyDown
            || this.isUsingItem() && !this.isPassenger()
            || this.input.keyPresses.backward()) {
            this.sprintTriggerTime = 0;
        }

        if (this.canStartSprinting()) {
            if (!hasForwardImpulse) {
                if (this.sprintTriggerTime > 0) {
                    this.setSprinting(true);
                } else {
                    this.sprintTriggerTime = 7;   // vanilla sprint window
                }
            }
            if (this.input.keyPresses.sprint()) {
                this.setSprinting(true);
            }
        }

        if (this.isSprinting()) {
            if (this.isSwimming()) {
                if (this.shouldStopSwimSprinting()) {
                    this.setSprinting(false);
                }
            } else if (this.shouldStopRunSprinting()) {
                this.setSprinting(false);
            }
        }

        super.aiStep();
    }

    private boolean canStartSprinting() {
        return !this.isSprinting()
            && this.input.hasForwardImpulse()
            && this.isSprintingPossible(this.getAbilities().flying)
            && !this.isUsingItem()
            && (!this.isFallFlying() || this.isUnderWater())
            && (!this.isMovingSlowly() || this.isUnderWater());
    }

    private boolean shouldStopRunSprinting() {
        return !this.isSprintingPossible(this.getAbilities().flying)
            || !this.input.hasForwardImpulse()
            || this.horizontalCollision && !this.minorHorizontalCollision;
    }

    private boolean shouldStopSwimSprinting() {
        return !this.isSprintingPossible(true)
            || !this.isInWater()
            || !this.input.hasForwardImpulse() && !this.onGround() && !this.input.keyPresses.shift();
    }

    private boolean isSprintingPossible(boolean allowedInShallowWater) {
        return !this.isMobilityRestricted()
            && this.hasEnoughFoodToSprint()
            && (!this.isPassenger() || this.vehicleCanSprint(this.getVehicle()))
            && (allowedInShallowWater || !this.isInShallowWater());
    }

    private boolean vehicleCanSprint(Entity vehicle) {
        return vehicle.canSprint() && vehicle.isLocalInstanceAuthoritative();
    }

    private boolean hasEnoughFoodToSprint() {
        return this.isPassenger()
            || this.getFoodData().getFoodLevel() > 6.0F
            || this.getAbilities().mayfly;
    }

    /** Lives on LocalPlayer in MC, not Player, so we redeclare it. */
    public boolean isMovingSlowly() {
        return this.isCrouching() || this.isVisuallyCrawling();
    }

    // ------------------------------------------------------------------
    // Travel — captures medium and ground friction for the snapshot
    // ------------------------------------------------------------------

    @Override
    public void travel(Vec3 input) {
        if (kinematic) {
            kinematicTravel(input);
            return;
        }
        boolean web = this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7;
        FluidState fluid = this.level().getFluidState(this.blockPosition());
        boolean inFluid = this.shouldTravelInFluid(fluid);
        boolean water = inFluid && this.isInWater();
        boolean lava = inFluid && !water && this.isInLava();
        boolean ladder = this.onClimbable();

        if (this.onGround()) {
            BlockPos below = this.getBlockPosBelowThatAffectsMyMovement();
            BlockState belowState = this.level().getBlockState(below);
            this.tickGroundFriction = belowState.getBlock().getFriction();
        } else {
            this.tickGroundFriction = Double.NaN;
        }

        super.travel(input);

        this.tickSoulsandCells = this.getBlockSpeedFactor() != 1.0F ? 1 : 0;
        this.tickMedium = computeMedium(web, water, lava, ladder, this.tickSoulsandCells > 0);
    }

    private static AgentMedium computeMedium(boolean web, boolean water, boolean lava,
                                             boolean ladder, boolean soulsand) {
        if (web) return AgentMedium.WEB;
        if (water) return AgentMedium.WATER;
        if (lava) return AgentMedium.LAVA;
        if (ladder) return AgentMedium.LADDER;
        if (soulsand) return AgentMedium.SOULSAND;
        return AgentMedium.AIR;
    }

    /**
     * World-free kinematic tick. Skips block reads, fluid checks, and collision; integrates
     * position from input + gravity + drag + jump. Approximates vanilla movement closely
     * enough to be useful as a fast pre-filter before the full block-accurate sim.
     *
     * <p>Caveats:
     * <ul>
     *   <li>You are responsible for ground state — call {@link #setOnGround} to seed it,
     *       then manage transitions yourself (the kinematic tick will not auto-land).</li>
     *   <li>No medium awareness (no water / lava / web / ladder / soulsand slowdown).</li>
     *   <li>No block collision; the entity can pass through walls.</li>
     *   <li>Sub-block ground friction is approximated by the constant 0.6 (vanilla default).</li>
     * </ul>
     *
     * <p>Subclass and override if you need a different kinematic model.
     */
    protected void kinematicTravel(Vec3 input) {
        Vec3 motion = this.getDeltaMovement();

        // Project input through the same modifyInput chain the full sim uses, so sprint /
        // sneak scaling matches.
        float speed = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (this.isSprinting()) speed *= 1.3F;
        if (this.isMovingSlowly()) {
            speed *= (float) this.getAttributeValue(Attributes.SNEAKING_SPEED);
        }
        Vec2 movement = modifyInput(this.input.moveVector);
        double yawRad = this.getYRot() * Math.PI / 180.0;
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double worldX = movement.x * cosYaw - movement.y * sinYaw;
        double worldZ = movement.y * cosYaw + movement.x * sinYaw;

        // Horizontal motion: ground friction or air drag, plus input impulse.
        // 0.6 is vanilla's default block friction; 0.91 is air drag.
        double horizScale = this.onGround() ? 0.6 : 0.91;
        motion = new Vec3(
                motion.x * horizScale + worldX * speed,
                motion.y * 0.98,
                motion.z * horizScale + worldZ * speed
        );

        // Jump
        if (this.jumping && this.onGround()) {
            motion = new Vec3(motion.x, 0.42, motion.z);
            this.setOnGround(false);
        }

        // Gravity + air drag on Y (vanilla: y -= 0.08, then * 0.98)
        if (!this.onGround()) {
            motion = new Vec3(motion.x, (motion.y - 0.08) * 0.98, motion.z);
        }

        this.setDeltaMovement(motion);
        this.setPos(this.position().add(motion));

        // Snap medium/friction capture to "no info" since we didn't read the world.
        this.tickMedium = AgentMedium.AIR;
        this.tickGroundFriction = Double.NaN;
        this.tickSoulsandCells = 0;
    }

    public AgentMedium capturedTickMedium() { return tickMedium; }
    public double capturedTickGroundFriction() { return tickGroundFriction; }
    public int capturedTickSoulsandCells() { return tickSoulsandCells; }
    public double getLastCollisionAngleDegrees() { return lastCollisionAngleDegrees; }

    // ------------------------------------------------------------------
    // Input projection — fed by AgentKeyState.moveVector
    // ------------------------------------------------------------------

    @Override
    public void applyInput() {
        Vec2 movement = modifyInput(this.input.moveVector);
        this.xxa = movement.x;
        this.zza = movement.y;
        this.jumping = this.input.keyPresses.jump();
    }

    private Vec2 modifyInput(Vec2 input) {
        if (input.lengthSquared() == 0.0F) {
            return input;
        }
        Vec2 scaled = input.scale(0.98F);
        // Vanilla gates on isMovingSlowly() (pose from the previous tick's sneak), so the
        // slowdown lands one tick after the sneak input.
        if (this.isMovingSlowly()) {
            float sneakingMovementFactor = (float) this.getAttributeValue(Attributes.SNEAKING_SPEED);
            scaled = scaled.scale(sneakingMovementFactor);
        }
        return modifyInputSpeedForSquareMovement(scaled);
    }

    private static Vec2 modifyInputSpeedForSquareMovement(Vec2 input) {
        float length = input.length();
        if (length <= 0.0F) return input;
        Vec2 direction = input.scale(1.0F / length);
        float distanceToUnitSquare = distanceToUnitSquare(direction);
        float modifiedLength = Math.min(length * distanceToUnitSquare, 1.0F);
        return direction.scale(modifiedLength);
    }

    private static float distanceToUnitSquare(Vec2 direction) {
        float directionX = Math.abs(direction.x);
        float directionY = Math.abs(direction.y);
        float tan = directionY > directionX ? directionX / directionY : directionY / directionX;
        return Mth.sqrt(1.0F + Mth.square(tan));
    }

    // ------------------------------------------------------------------
    // Collision angle — copied 1:1 from LocalPlayer
    // ------------------------------------------------------------------

    @Override
    protected boolean isHorizontalCollisionMinor(Vec3 adjustedMovement) {
        double angleDeg = computeCollisionAngleDegrees(adjustedMovement.x, adjustedMovement.z);
        if (Double.isNaN(angleDeg)) {
            return false;
        }
        this.lastCollisionAngleDegrees = angleDeg;
        this.collisionAngleComputedThisTick = true;
        return Math.toRadians(angleDeg) < 0.13962634F;
    }

    private double computeCollisionAngleDegrees(double movementX, double movementZ) {
        float yRotInRadians = this.getYRot() * (float) (Math.PI / 180.0);
        double yRotSin = Mth.sin(yRotInRadians);
        double yRotCos = Mth.cos(yRotInRadians);
        double globalXA = this.xxa * yRotCos - this.zza * yRotSin;
        double globalZA = this.zza * yRotCos + this.xxa * yRotSin;
        double aLengthSquared = Mth.square(globalXA) + Mth.square(globalZA);
        double movementLengthSquared = Mth.square(movementX) + Mth.square(movementZ);
        if (aLengthSquared < 1.0E-5F || movementLengthSquared < 1.0E-5F) {
            return Double.NaN;
        }
        double dotProduct = globalXA * movementX + globalZA * movementZ;
        return Math.toDegrees(Math.acos(dotProduct / Math.sqrt(aLengthSquared * movementLengthSquared)));
    }

    // ------------------------------------------------------------------
    // Sneak / crouch / move-type plumbing
    // ------------------------------------------------------------------

    @Override
    public boolean isShiftKeyDown() {
        return this.input.keyPresses.shift();
    }

    @Override
    public boolean isCrouching() {
        return this.crouching;
    }

    // ------------------------------------------------------------------
    // Entity type / authority overrides — needed so the entity ticks at all
    // ------------------------------------------------------------------

    /** Required so canSimulateMovement / isLocalInstanceAuthoritative return true on the client world. */
    @Override
    public boolean isLocalPlayer() {
        return true;
    }

    /** Lets the entity tick on ServerWorld: PlayerEntity defaults isControlledByPlayer to true. */
    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    @Override
    public @Nullable GameType gameMode() {
        return GameType.DEFAULT_MODE;
    }

    // ------------------------------------------------------------------
    // No-op overrides — isolate the simulator from the real world
    // ------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public void causeFoodExhaustion(float amount) { }

    @Override
    protected void spawnSprintParticle() { }

    @Override
    protected void doWaterSplashEffect() { }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) { }

    /**
     * No-op: vanilla calls discard() when Y &lt; bottomY - 64, which sets removalReason and
     * makes every subsequent tick() a no-op. Simulator paths legitimately fall past world
     * bottom (TAS into the void) and must keep ticking; resetPlayer() snaps position back.
     */
    @Override
    protected void onBelowWorld() { }

    /** No-op so the simulator doesn't shove the real player or other world entities. */
    @Override
    protected void pushEntities() { }

    // ------------------------------------------------------------------
    // Effect plumbing — bypass the client-side gate so addEffect works on ClientLevel
    // ------------------------------------------------------------------

    @Override
    public boolean removeAllEffects() {
        Collection<MobEffectInstance> active = this.getActiveEffects();
        if (active.isEmpty()) return false;
        Collection<MobEffectInstance> copy = new ArrayList<>(active);
        active.clear();
        this.onEffectsRemoved(copy);
        return true;
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effect, @Nullable Entity source) {
        effect.getEffect().value().addAttributeModifiers(this.getAttributes(), effect.getAmplifier());
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effect, boolean reapplyEffect, @Nullable Entity source) {
        if (reapplyEffect) {
            MobEffect type = effect.getEffect().value();
            type.removeAttributeModifiers(this.getAttributes());
            type.addAttributeModifiers(this.getAttributes(), effect.getAmplifier());
        }
    }

    @Override
    protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
        for (MobEffectInstance e : effects) {
            e.getEffect().value().removeAttributeModifiers(this.getAttributes());
        }
    }

    // ------------------------------------------------------------------
    // Move capture is exposed here in case a caller wants the per-subtick path;
    // AgentSimulator doesn't use it (it captures only the per-tick snapshot).
    // ------------------------------------------------------------------

    private final List<Vec3> subtickBuf = new ArrayList<>(8);
    private boolean capturing = false;

    public void beginSubtickCapture() { subtickBuf.clear(); capturing = true; }

    public List<Vec3> endSubtickCapture() {
        capturing = false;
        List<Vec3> out = new ArrayList<>(subtickBuf);
        subtickBuf.clear();
        return out;
    }

    public Vec3 getPos() {
        return this.position();
    }

    @Override
    public void move(MoverType type, Vec3 motion) {
        if (!capturing) {
            super.move(type, motion);
            return;
        }
        // Subtle: the repo records (before, dx, dy, dz, axisX-dominant) tuples. We only need
        // an opaque Vec3 list to honor the API; richer structure belongs in the repo version.
        super.move(type, motion);
    }

    // ------------------------------------------------------------------
    // Checkpoint record
    // ------------------------------------------------------------------

    public static final class Checkpoint {
        public Vec3 pos;
        public Vec3 velocity;
        public float yaw;
        public float pitch;
        public boolean onGround;
        public boolean horizontalCollision;
        public boolean collidedSoftly;
        public boolean sprinting;
        public boolean swimming;
        public int ticksLeftToDoubleTapSprint;
        public Input input;
        public int jumpingCooldown;
        public Vec3 movementMultiplier;
        public Pose pose;
        public boolean crouching;
        public double fallDistance;
    }
}

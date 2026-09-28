package kaptainwutax.tungsten.agent;

import java.util.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;

import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import kaptainwutax.tungsten.Debug;
import kaptainwutax.tungsten.Tungsten;
import kaptainwutax.tungsten.TungstenModDataContainer;
import kaptainwutax.tungsten.helpers.render.RenderHelper;
import kaptainwutax.tungsten.mixin.AccessorEntity;
import kaptainwutax.tungsten.mixin.AccessorLivingEntity;
import kaptainwutax.tungsten.path.Node;
import kaptainwutax.tungsten.path.PathInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Direction;
import net.minecraft.data.worldgen.DimensionTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Agent {

    public static Agent INSTANCE;

    public static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.scalable(0.6f, 1.8f);
    public static final EntityDimensions SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2f, 0.2f);

    public static final Map<Pose, EntityDimensions> POSE_DIMENSIONS =
        ImmutableMap.<Pose, EntityDimensions>builder()
        .put(Pose.STANDING, STANDING_DIMENSIONS)
        .put(Pose.SLEEPING, SLEEPING_DIMENSIONS)
        .put(Pose.FALL_FLYING, EntityDimensions.scalable(0.6f, 0.6f))
        .put(Pose.SWIMMING, EntityDimensions.scalable(0.6f, 0.6f))
        .put(Pose.SPIN_ATTACK, EntityDimensions.scalable(0.6f, 0.6f))
        .put(Pose.CROUCHING, EntityDimensions.scalable(0.6f, 1.5f))
        .put(Pose.DYING, EntityDimensions.fixed(0.2f, 0.2f)).build();

    public boolean keyForward;
    public boolean keyBack;
    public boolean keyLeft;
    public boolean keyRight;
    public boolean keyJump;
    public boolean keySneak;
    public boolean keySprint;
    public AgentInput input = new AgentInput(this);

    public Pose pose;
    public boolean inSneakingPose;
    public boolean isDamaged = false;
    public boolean usingItem;

    public float sidewaysSpeed;
    public float upwardSpeed;
    public float forwardSpeed;
    public float yaw;
    public float pitch;

    public double posX, posY, posZ;
    public int blockX, blockY, blockZ;
    public double velX, velY, velZ;
    public double mulX, mulY, mulZ;

    public Object2DoubleMap<TagKey<Fluid>> fluidHeight = new Object2DoubleArrayMap<>(2);
    private final Set<TagKey<Fluid>> submergedFluids = new HashSet<>();
    public boolean firstUpdate = true;

    public EntityDimensions dimensions;
    public AABB box;
    public float standingEyeHeight;

    public boolean onGround;
    public boolean sleeping;
    public boolean sneaking; //flag 1
    public boolean sprinting; //flag 3
    public boolean swimming; //flag 4
    public boolean fallFlying; //flag 7
    public float stepHeight = 0.6F;
    public double fallDistance;
    public boolean touchingWater;
    public boolean isSubmergedInWater;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean collidedSoftly;
    public boolean slimeBounce;

    public boolean jumping;

    public int speed = -1;
    public int blindness = -1;
    public int jumpBoost = -1;
    public int slowFalling = -1;
    public int dolphinsGrace = -1;
    public int levitation = -1;

    public int depthStrider;

    public FoodData hunger = new FoodData();
    public double health;
    public float movementSpeed;
    public float airStrafingSpeed;
    public int jumpingCooldown;
    public int ticksToNextAutojump;
    private List<String> extra = new ArrayList<>();
    private int scannedBlocks;

    public Vec3 getPos() {
        return new Vec3(this.posX, this.posY, this.posZ);
    }
    
    public BlockPos getBlockPos() {
        return new BlockPos(this.blockX, this.blockY, this.blockZ);
    }
    
    public BlockState getBlockState(LevelReader world) {
    	return world.getBlockState(getBlockPos());
    }

    public void setPos(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.blockX = Mth.floor(x);
        this.blockY = Mth.floor(y);
        this.blockZ = Mth.floor(z);
//		this.setBoundingBox(this.calculateBoundingBox());
    }
    
    public final void setBoundingBox(AABB boundingBox) {
		this.box = boundingBox;
	}
    
    protected AABB calculateBoundingBox() {
		return this.dimensions.makeBoundingBox(this.getPos());
	}

    public Agent tick(LevelReader world) {
        this.tickPlayer(world);
        return this;
    }

    public void tickPlayer(LevelReader world) {
        //Sleeping code
        this.updateWaterSubmersionState();
        this.tickLiving(world);
        //Hunger stuff
        //Turtle helmet
        //Item cooldown
        this.updateSize(world);
    }

    private void tickLiving(LevelReader world) {
        this.baseTickLiving(world);
        //more sleep stuff
        this.tickMovementClientPlayer(world);

        if(this.sleeping) {
            this.pitch = 0.0F;
        }
    }

    private void baseTickLiving(LevelReader world) {
        this.baseTickEntity(world);
        //Suffocate in walls
        //Drown in water
        //Soulspeed and frost walker
        //Update potion effects
    }

    private void baseTickEntity(LevelReader world) {
        this.updateWaterState(world);
        this.updateSubmergedInWaterState(world);
        this.updateSwimming(world);

        if(this.isInLava()) {
            this.fallDistance *= 0.5F;
        }

        this.firstUpdate = false;
    }

    public boolean isSubmergedIn(TagKey<Fluid> tag) {
        return this.submergedFluids.contains(tag);
    }

    public boolean updateWaterState(LevelReader world) {
        this.fluidHeight.clear();
        this.checkWaterState(world);
        double d = world.dimensionType().equals(BuiltinDimensionTypes.NETHER) ? 0.007D : 0.0023333333333333335D;
        boolean bl = this.updateMovementInFluid(world, FluidTags.LAVA, d);
        return this.touchingWater || bl;
    }

    private void updateSubmergedInWaterState(LevelReader world) {
        this.isSubmergedInWater = this.isSubmergedIn(FluidTags.WATER);
        this.submergedFluids.clear();
        double d = this.getEyeY();

        BlockPos blockPos = new BlockPos((int) this.posX, (int) d, (int) this.posZ);
        FluidState fluidState = world.getFluidState(blockPos);
        double e = (float)blockPos.getY() + fluidState.getHeight(world, blockPos);

        if(e > d) {
            fluidState.tags().forEach(this.submergedFluids::add);
        }
    }

    public double getEyeY() {
        return this.posY + (double)this.standingEyeHeight;
    }

    public void updateSwimming(LevelReader world) {
        if(this.swimming) {
            this.swimming = this.sprinting && this.touchingWater;
        } else {
            FluidState fluid = world.getFluidState(new BlockPos(this.blockX, this.blockY, this.blockZ));
            this.swimming = this.sprinting && this.isSubmergedInWater && fluid.is(FluidTags.WATER);
        }
    }

    public void updateWaterSubmersionState() {
        this.isSubmergedInWater = this.isSubmergedIn(FluidTags.WATER);
    }

    public void updateSize(LevelReader world) {
        if(!this.wouldPoseNotCollide(world, Pose.SWIMMING)) return;
        Pose newPose;

        if(this.fallFlying) {
            newPose = Pose.FALL_FLYING;
        } else {
            if(this.sleeping) {
                newPose = Pose.SLEEPING;
            } else {
                if(this.swimming) {
                    newPose = Pose.SWIMMING;
                } else {
                    if(this.input.playerInput.shift()) {
                        newPose = Pose.CROUCHING;
                    } else {
                        newPose = Pose.STANDING;
                    }
                }
            }
        }

        if(!this.wouldPoseNotCollide(world, newPose)) {
            if(this.wouldPoseNotCollide(world, Pose.CROUCHING)) {
                newPose = Pose.CROUCHING;
            } else {
                newPose = Pose.SWIMMING;
            }
        }

        this.setPose(world, newPose);
    }

    public void setPose(LevelReader world, Pose pose) {
        this.pose = pose;
        this.calculateDimensions(world);
    }

    public void calculateDimensions(LevelReader world) {
        EntityDimensions oldDimensions = this.dimensions;
        this.dimensions = POSE_DIMENSIONS.getOrDefault(this.pose, STANDING_DIMENSIONS);

        this.standingEyeHeight = this.getEyeHeight(this.pose, this.dimensions);

        if(this.dimensions.width() < oldDimensions.width()) {
            double d = (double) this.dimensions.width() / 2.0;
            this.box = new AABB(this.posX - d, this.posY, this.posZ - d, this.posX + d,
                this.posY + (double) this.dimensions.height(), this.posZ + d);
            return;
        }

        this.box = new AABB(this.box.minX, this.box.minY, this.box.minZ, this.box.minX + (double)this.dimensions.width(),
            this.box.minY + (double) this.dimensions.height(), this.box.minZ + (double)this.dimensions.width());

        if(this.dimensions.width() > oldDimensions.width() && !this.firstUpdate) {
            float f = oldDimensions.width() - this.dimensions.width();
            this.move(world, f, 0.0D, f);
        }
    }

    public final float getEyeHeight(Pose pose, EntityDimensions dimensions) {
         return switch(pose) {
            case SWIMMING, FALL_FLYING, SPIN_ATTACK -> 0.4F;
            case CROUCHING -> 1.27F;
            case SLEEPING -> 0.2F;
            default -> 1.62F;
        };
    }

    public void tickMovementClientPlayer(LevelReader world) {
        boolean prevSneaking = this.input.playerInput.shift();
        boolean wasWalking = this.isWalking();

        this.inSneakingPose = !this.swimming && this.wouldPoseNotCollide(world, Pose.CROUCHING)
            && (this.input.playerInput.shift() || !this.sleeping && !this.wouldPoseNotCollide(world, Pose.STANDING));
        this.input.tick();

        if(this.ticksToNextAutojump > 0) {
            --this.ticksToNextAutojump;
            this.input.playerInput = new Input(
            		this.input.playerInput.forward(),
            		this.input.playerInput.backward(),
            		this.input.playerInput.left(),
            		this.input.playerInput.right(),
        			true,
        			this.input.playerInput.shift(),
        			this.input.playerInput.sprint()
        		);
        }

        double width = this.dimensions.width();
        this.pushOutOfBlocks(world, this.posX - width * 0.35D, this.posZ + width * 0.35D);
        this.pushOutOfBlocks(world, this.posX - width * 0.35D, this.posZ - width * 0.35D);
        this.pushOutOfBlocks(world, this.posX + width * 0.35D, this.posZ - width * 0.35D);
        this.pushOutOfBlocks(world, this.posX + width * 0.35D, this.posZ + width * 0.35D);


        if(this.sprinting) {
            if(this.swimming) {
            	if (this.shouldStopSwimSprinting()) {
					this.setSprinting(false);
				}
            } else if (this.shouldStopSprinting()) {
				this.setSprinting(false);
			}
        }
        
        if(this.canStartSprinting()) {
            if(this.keySprint) {
                this.setSprinting(true);
            }
        }

        if(this.touchingWater && this.input.playerInput.shift()) {
            this.velY -= 0.04F;
        }

        this.tickMovementPlayer(world);
    }

    public void tickMovementPlayer(LevelReader world) {
        this.tickMovementLiving(world);

        this.airStrafingSpeed = 0.02F;

        if(this.sprinting) {
            this.airStrafingSpeed += 0.006F;
        }
    }

    public void tickMovementLiving(LevelReader world) {
        if(this.jumpingCooldown > 0) {
            --this.jumpingCooldown;
        }
        
        Vec2 vec2f = this.applyMovementSpeedFactors(this.input.getMovementInput());
		this.sidewaysSpeed = vec2f.x;
		this.forwardSpeed = vec2f.y;
        this.jumping = this.input.playerInput.jump();

        
        if(Math.abs(this.velX) < 1e-5) this.velX = 0.0;
        if(Math.abs(this.velY) < 0.003) this.velY = 0.0;
        if(Math.abs(this.velZ) < 1e-5) this.velZ = 0.0;

        if(this.jumping) {
            double k = this.isInLava() ? this.getFluidHeight(FluidTags.LAVA) : this.getFluidHeight(FluidTags.WATER);
            boolean bl = this.touchingWater && k > 0.0;
            double l = (double)this.standingEyeHeight < 0.4D ? 0.0D : 0.4D;

            if(bl && (!this.onGround || k > l)) {
                this.velY += 0.04F;
            } else if(!this.isInLava() || this.onGround && !(k > l)) {
                if((this.onGround || bl) && this.jumpingCooldown == 0) {
                    this.jump(world);
                    this.jumpingCooldown = 10;
                }
            } else {
                this.velY += 0.04F;
            }
        } else {
            this.jumpingCooldown = 0;
        }
        


        this.travelPlayer(world);

        /* Entity pushing
        if(this.riptideTicks > 0) {
            --this.riptideTicks;
            this.tickRiptide(box, this.getBoundingBox());
        }
        this.tickCramming();*/
    }

    public void jump(LevelReader world) {
        float newY = this.getJumpVelocity(world);

        if(this.jumpBoost >= 0) {
            newY += 0.1F * (float)(this.jumpBoost + 1);
        }

        this.velY = newY;

        if(this.sprinting) {
            float g = this.yaw * (float)(Math.PI / 180);
            this.velX += (double)(-Mth.sin(g)) * 0.2;
            this.velZ +=  (double)Mth.cos(g) * 0.2;
        }
    }

    public float getJumpVelocity(LevelReader world) {
        return 0.42F * this.getJumpVelocityMultiplier(world);
    }

    public float getJumpVelocityMultiplier(LevelReader world) {
        BlockPos pos1 = new BlockPos(this.blockX, this.blockY, this.blockZ);
        BlockPos pos2 = new BlockPos((int) this.blockX, (int) (this.box.minY - 0.500001D), (int) this.blockZ);
        float f = world.getBlockState(pos1).getBlock().getJumpFactor();
        float g = world.getBlockState(pos2).getBlock().getJumpFactor();
        return (double)f == 1.0D ? g : f;
    }

    public void updateVelocity(float speed) {
        double squaredMagnitude = (double)this.sidewaysSpeed * (double)this.sidewaysSpeed
                                + (double)this.upwardSpeed * (double)this.upwardSpeed
                                + (double)this.forwardSpeed * (double)this.forwardSpeed;

        if (squaredMagnitude < 1.0E-7) return;

        double sideways = this.sidewaysSpeed, upward = this.upwardSpeed, forward = this.forwardSpeed;

        if(squaredMagnitude > 1.0D) {
            double magnitude = Math.sqrt(squaredMagnitude);
            if (magnitude < 1.0E-4) { return; }
            else { sideways /= magnitude; upward /= magnitude; forward /= magnitude; }
        }

        sideways *= speed; upward *= speed; forward *= speed;
        float f = Mth.sin(yaw * (float) (Math.PI / 180.0));
		float g = Mth.cos(yaw * (float) (Math.PI / 180.0));

        this.velX += sideways * (double)g - forward * (double)f;
        this.velY += upward;
        this.velZ += forward * (double)g + sideways * (double)f;
    }

    public void travelPlayer(LevelReader world) {
        if(this.swimming) {
            float g = -Mth.sin(this.pitch * ((float)Math.PI / 180));
            double h = g < -0.2 ? 0.085 : 0.06;

            BlockPos pos = new BlockPos(this.blockX, Mth.floor(this.posY + 1.0D - 0.1D), this.blockZ);

            if(g <= 0.0D || this.jumping || !world.getBlockState(pos).getFluidState().isEmpty()) {
                this.velY += (g - this.velY) * h;
            }
        }

        this.travelLiving(world);
    }

    public void travelLiving(LevelReader world) {
        boolean falling = this.velY <= 0.0D;
        double fallSpeed = 0.08D;

        if(falling && this.slowFalling >= 0) {
            fallSpeed = 0.01D;
            this.fallDistance = 0.0F;
        }

        if(this.touchingWater) {
            double startY = this.posY;
            float swimSpeed = this.sprinting ? 0.9F : 0.8F;
            float speed = 0.02F;

            float h = this.depthStrider;
            if(h > 3.0F) h = 3.0F;
            if(!this.onGround) h *= 0.5F;

            if(h > 0.0F) {
                swimSpeed += (0.54600006F - swimSpeed) * h / 3.0F;
                speed += (this.movementSpeed - speed) * h / 3.0F;
            }

            if(this.dolphinsGrace >= 0) {
                swimSpeed = 0.96F;
            }

            this.updateVelocity(speed);
            this.move(world, this.velX, this.velY, this.velZ);

            if(this.horizontalCollision && this.isClimbing(world)) { this.velY = 0.2D; }
            this.velX *= swimSpeed; this.velY *= 0.8F; this.velZ *= swimSpeed;

            this.method_26317(fallSpeed, falling);

            boolean moveNoCollisions = this.doesNotCollide(world, this.velX, this.velY + (double)0.6F - this.posY + startY, this.velZ);

            if(this.horizontalCollision && moveNoCollisions) {
                this.velY = 0.3F;
            }
        } else if(this.isInLava()) {
            double startY = this.posY;
            this.updateVelocity(0.02F);

            this.move(world, this.velX, this.velY, this.velZ);

            if(this.getFluidHeight(FluidTags.LAVA) <= ((double)this.standingEyeHeight < 0.4D ? 0.0D : 0.4D)) {
                this.velX *= 0.5D; this.velY *= 0.8F; this.velZ *= 0.5D;
                this.method_26317(fallSpeed, falling);
            } else {
                this.velX *= 0.5D; this.velY *= 0.5D; this.velZ *= 0.5D;
            }

            this.velY -= fallSpeed / 4.0D;

            boolean moveNoCollisions = this.doesNotCollide(world, this.velX, this.velY + (double)0.6F - this.posY + startY, this.velZ);

            if(this.horizontalCollision && moveNoCollisions) {
                this.velY = 0.3F;
            }
        } else if(this.fallFlying) {
            //No elytra controls
            if(this.velY > -0.5D) {
                this.fallDistance = 1.0F;
            }

            float cYaw = Mth.cos(-this.yaw * 0.017453292F);
            float sYaw = Mth.sin(-this.yaw * 0.017453292F);
            float cPitch = Mth.cos(this.pitch * 0.017453292F);
            float sPitch = Mth.sin(this.pitch * 0.017453292F);
            double facingX = sYaw * cPitch;
            double facingY = -sPitch;
            double facingZ = cYaw * cPitch;

            double facingHM = Math.sqrt(facingX * facingX + facingZ * facingZ);
            double velHM = Math.sqrt(this.velX * this.velX + this.velZ * this.velZ);

            float cPitchSq = (float)((double)cPitch * (double)cPitch);
            this.velY += fallSpeed * (-1.0D + (double)cPitchSq * 0.75D);

            if(this.velY < 0.0D && facingHM > 0.0D) {
                double q = this.velY * -0.1D * (double)cPitchSq;
                this.velX += facingX * q / facingHM;
                this.velY += q;
                this.velZ += facingZ * q / facingHM;
            }

            if(this.pitch < 0.0F && facingHM > 0.0D) {
                double q = velHM * facingY * 0.04D;
                this.velX -= facingX * q / facingHM;
                this.velY += q * 3.2D;
                this.velZ -= facingZ * q / facingHM;
            }

            if(facingHM > 0.0D) {
                this.velX += (facingX / facingHM * velHM - this.velX) * 0.1D;
                this.velZ += (facingZ / facingHM * velHM - this.velZ) * 0.1D;
            }

            this.velX *= 0.9900000095367432D;
            this.velY *= 0.9800000190734863D;
            this.velZ *= 0.9900000095367432D;
            this.move(world, this.velX, this.velY, this.velZ);

            //Mojang why? WHYYYYYYYYYYYYYYY???
            if(this.onGround /*&& !world.isClient*/) {
                this.fallFlying = false;
            }
        } else {
            BlockPos pos = new BlockPos((int) this.posX, (int) (this.box.minY - 0.5000001D), (int) this.posZ);
            float slipperiness = world.getBlockState(pos).getBlock().getFriction();
            float xzDrag = this.onGround ? slipperiness * 0.91F : 0.91F;
            double ajuVelY = this.applyMovementInput(world, slipperiness);

            if (this.levitation >= 0) {
                ajuVelY += (0.05D * (double)(this.levitation + 1) - ajuVelY) * 0.2D;
                this.fallDistance = 0.0F;
            } else {
                ajuVelY -= fallSpeed;
            }

            this.velX *= xzDrag;
            this.velY = ajuVelY * (double)0.98F;
            this.velZ *= xzDrag;
        }
    }

    public double applyMovementInput(LevelReader world, float f) {
        this.updateVelocity(this.getMovementSpeed(f));
        this.applyClimbingSpeed(world);

        this.move(world, this.velX, this.velY, this.velZ);

        if((this.horizontalCollision || this.jumping) && this.isClimbing(world)) {
            return 0.2D;
        }

        return this.velY;
    }

    private float getMovementSpeed(float slipperiness) {
        if(this.onGround) {
            return this.movementSpeed * (0.21600002F / (slipperiness * slipperiness * slipperiness));
        }	

        return this.sprinting ? 0.025999999F : 0.02F;
    }

    private void applyClimbingSpeed(LevelReader world) {
        if(this.isClimbing(world)) {
            this.fallDistance = 0.0f;
            this.velX = Mth.clamp(this.velX, -0.15000000596046448D, 0.15000000596046448D);
            this.velY = Math.max(this.velY, -0.15000000596046448D);
            this.velZ = Mth.clamp(this.velZ, -0.15000000596046448D, 0.15000000596046448D);

            BlockState state = world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ));

            if(this.velY < 0.0D && !state.is(Blocks.SCAFFOLDING) && this.input.playerInput.shift()) {
                this.velY = 0.0D;
            }
        }
    }

    public Iterable<VoxelShape> getBlockCollisions(LevelReader world, AABB box) {
        return () -> new AgentBlockCollisions(world, this, box);
    }

    public boolean isSpaceEmpty(LevelReader world, AABB box) {
        for(VoxelShape voxelShape : this.getBlockCollisions(world, box)) {
            if(!voxelShape.isEmpty()) {
                return false;
            }
        }

        return !world.getCollisions(null, box).iterator().hasNext();
    }

    private boolean doesNotCollide(LevelReader world, double offsetX, double offsetY, double offsetZ) {
        AABB box = this.box.move(offsetX, offsetY, offsetZ);
        return this.isSpaceEmpty(world, box) && !world.containsAnyLiquid(box);
    }

    public void method_26317(double fallSpeed, boolean falling) {
        if(!this.sprinting) {
            boolean b = falling && Math.abs(this.velY - 0.005D) >= 0.003D && Math.abs(this.velY - fallSpeed / 16.0D) < 0.003D;
            this.velY = b ? -0.003D : this.velY - fallSpeed / 16.0D;
        }
    }

    public void move(LevelReader world, double movX, double movY, double movZ) {

        if(this.mulX * this.mulX + this.mulY * this.mulY + this.mulZ * this.mulZ > 0.0000001D) {
            movX *= this.mulX; movY *= this.mulY; movZ *= this.mulZ;
            this.mulX = 0; this.mulY = 0; this.mulZ = 0;
            this.velX = 0; this.velY = 0; this.velZ = 0;
        }


        Vec3 vec1 = this.adjustMovementForSneaking(world, new Vec3(movX, movY, movZ));
        movX = vec1.x; movY = vec1.y; movZ = vec1.z;

        Vec3 vec2 = this.adjustMovementForCollisions(world, new Vec3(movX, movY, movZ));
        double ajuX = vec2.x, ajuY = vec2.y, ajuZ = vec2.z;

        double magnitudeSq = ajuX * ajuX + ajuY * ajuY + ajuZ * ajuZ;

		if(magnitudeSq > 0.0000001D) {
            if(this.fallDistance != 0.0F && magnitudeSq >= 1.0D) {
                ClipContext context = new AgentRaycastContext(this.getPos(), this.getPos().add(new Vec3(ajuX, ajuY, ajuZ)),
                    ClipContext.Block.FALLDAMAGE_RESETTING, ClipContext.Fluid.WATER, this);
                BlockHitResult result = world.clip(context);

                if(result.getType() != HitResult.Type.MISS) {
                    this.fallDistance = 0.0F;
                }
            }

            this.setPos(this.posX + ajuX, this.posY + ajuY, this.posZ + ajuZ);
			this.box = this.dimensions.makeBoundingBox(this.posX, this.posY, this.posZ);
        }

        boolean xSimilar = !Mth.equal(movX, ajuX);
        boolean zSimilar = !Mth.equal(movZ, ajuZ);
        this.horizontalCollision = xSimilar || zSimilar;
        this.verticalCollision = movY != ajuY;
        this.collidedSoftly = this.horizontalCollision && this.hasCollidedSoftly(ajuX, ajuY, ajuZ);
        this.onGround = this.verticalCollision && movY < 0.0D;

        BlockPos landingPos = this.getLandingPos(world);
        BlockState landingState = world.getBlockState(landingPos);

        this.fall(world, ajuY, landingState);

        if(this.horizontalCollision) {
            if(xSimilar) this.velX = 0.0D;
            if(zSimilar) this.velZ = 0.0D;
        }

        Block block = landingState.getBlock();

        if(movY != ajuY) {
            if(block instanceof SlimeBlock && !this.input.playerInput.shift()) {
                if(this.velY < 0.0D) {
                	this.velY *= -1;
                	this.slimeBounce = true;
                }
            } else if(block instanceof BedBlock) {
                if(this.velY < 0.0D) this.velY *= -1 * (double)0.66F;
            } else {
                this.velY = 0;
            }
        }

        if(this.onGround && !this.input.playerInput.shift()) {
            if(block instanceof MagmaBlock) {
                //damage the entity
            } else if(block instanceof SlimeBlock) {
                double d = Math.abs(this.velY);

                if(d < 0.1D) {
                    this.velX *= 0.4D + d * 0.2D;
                    this.velZ *= 0.4D + d * 0.2D;
                	this.slimeBounce = true;
                }
            } else if(block instanceof TurtleEggBlock) {
                //eggs can break (1/100)
            }
        }

        this.checkBlockCollision(world);

        float i = this.getVelocityMultiplier(world);
        this.velX *= i; this.velZ *= i;

		/*
		if (this.world.method_29556(this.getBoundingBox().contract(0.001))
		.noneMatch(blockState -> blockState.isIn(BlockTags.FIRE) || blockState.isOf(Blocks.LAVA)) && this.fireTicks <= 0) {
			this.setFireTicks(-this.getBurningDuration());
		}*/
    }

    private boolean hasCollidedSoftly(double ajuX, double ajuY, double ajuZ) {
        float f = this.yaw * ((float)Math.PI / 180);
        double d = Mth.sin(f);
        double e = Mth.cos(f);
        double g = (double)this.sidewaysSpeed * e - (double)this.forwardSpeed * d;
        double h = (double)this.forwardSpeed * e + (double)this.sidewaysSpeed * d;
        double i = Mth.square(g) + Mth.square(h);
        double j = Mth.square(ajuX) + Mth.square(ajuZ);

        if(i < (double)1.0E-5F || j < (double)1.0E-5F) {
            return false;
        }

        double k = g * ajuX + h * ajuZ;
        double l = Math.acos(k / Math.sqrt(i * j));
        return l < 0.13962633907794952D;
    }

    public Vec3 adjustMovementForSneaking(LevelReader world, Vec3 movement) {
        if(this.input.playerInput.shift()
            && (this.onGround || this.fallDistance < this.stepHeight
            && !this.isSpaceEmpty(world, this.box.move(0.0, this.fallDistance - this.stepHeight, 0.0)))) {
            double d = movement.x;
            double e = movement.z;

            while(d != 0.0 && this.isSpaceEmpty(world, this.box.move(d, -this.stepHeight, 0.0))) {
                if(d < 0.05 && d >= -0.05) { d = 0.0; continue; }
                if(d > 0.0) { d -= 0.05; continue; }
                d += 0.05;
            }

            while(e != 0.0 && this.isSpaceEmpty(world, this.box.move(0.0, -this.stepHeight, e))) {
                if(e < 0.05 && e >= -0.05) { e = 0.0; continue; }
                if(e > 0.0) { e -= 0.05; continue; }
                e += 0.05;
            }

            while(d != 0.0 && e != 0.0 && this.isSpaceEmpty(world, this.box.move(d, -this.stepHeight, e))) {
                d = d < 0.05 && d >= -0.05 ? 0.0 : (d > 0.0 ? (d -= 0.05) : (d += 0.05));
                if(e < 0.05 && e >= -0.05) { e = 0.0; continue; }
                if(e > 0.0) { e -= 0.05; continue; }
                e += 0.05;
            }

            movement = new Vec3(d, movement.y, e);
        }

        return movement;
    }

    private Vec3 adjustMovementForCollisions(LevelReader world, Vec3 movement) {
        AABB box = this.box;
        Iterable<VoxelShape> list = world.getCollisions(null, box.expandTowards(movement));
        Vec3 vec3d = movement.lengthSqr() == 0.0 ? movement : this.adjustMovementForCollisions(movement, box, world, list);
        boolean bl = movement.x != vec3d.x;
        boolean bl2 = movement.y != vec3d.y;
        boolean bl3 = movement.z != vec3d.z;
		boolean bl4 = bl2 && movement.y < 0.0;
        boolean bl5 = this.onGround || bl4;

        if(this.stepHeight > 0.0f && bl5 && (bl || bl3)) {
        	AABB box2 = bl4 ? box.move(0.0, vec3d.y, 0.0) : box;
			AABB box3 = box2.expandTowards(movement.x, (double)this.stepHeight, movement.z);
			if (!bl4) {
				box3 = box3.expandTowards(0.0, -1.0E-5F, 0.0);
			}

			List<VoxelShape> list2 = this.findCollisionsForMovement(world, list, box3);
			float f = (float)vec3d.y;
			float[] fs = collectStepHeights(box2, list2, this.stepHeight, f);

			for (float g : fs) {
				Vec3 vec3d2 = adjustMovementForCollisions(new Vec3(movement.x, (double)g, movement.z), box2, list2);
				if (vec3d2.horizontalDistanceSqr() > vec3d.horizontalDistanceSqr()) {
					double d = box.minY - box2.minY;
					return vec3d2.add(0.0, -d, 0.0);
				}
			}
        }

        return vec3d;
    }
    
    private List<VoxelShape> findCollisionsForMovement(LevelReader world, Iterable<VoxelShape> regularCollisions, AABB movingEntityBoundingBox
    	) {
    		Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(regularCollisions.size() + 1);
    		if (!regularCollisions.isEmpty()) {
    			builder.addAll(regularCollisions);
    		}

    		builder.addAll(this.getBlockCollisions(world, movingEntityBoundingBox));
    		return builder.build();
    	}
    
    private static float[] collectStepHeights(AABB collisionBox, List<VoxelShape> collisions, float f, float stepHeight) {
		FloatSet floatSet = new FloatArraySet(4);

		for (VoxelShape voxelShape : collisions) {
			for (double d : voxelShape.getCoords(Direction.Axis.Y)) {
				float g = (float)(d - collisionBox.minY);
				if (!(g < 0.0F) && g != stepHeight) {
					if (g > f) {
						break;
					}

					floatSet.add(g);
				}
			}
		}

		float[] fs = floatSet.toFloatArray();
		FloatArrays.unstableSort(fs);
		return fs;
	}

    public Vec3 adjustMovementForCollisions(Vec3 movement, AABB entityBoundingBox, LevelReader world, Iterable<VoxelShape> entityCollisions) {
        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(entityCollisions.size() + 1);

        if(!entityCollisions.isEmpty()) {
            builder.addAll(entityCollisions);
        }

        builder.addAll(this.getBlockCollisions(world, entityBoundingBox.expandTowards(movement)));
        return this.adjustMovementForCollisions(movement, entityBoundingBox, builder.build());
    }

    private Vec3 adjustMovementForCollisions(Vec3 movement, AABB entityBoundingBox, List<VoxelShape> collisions) {
        if(collisions.isEmpty()) {
            return movement;
        }

        double d = movement.x;
        double e = movement.y;
        double f = movement.z;
        boolean bl = Math.abs(d) < Math.abs(f);

        if(e != 0.0D) {
            e = Shapes.collide(Direction.Axis.Y, entityBoundingBox, collisions, e);
            if (e != 0.0D) entityBoundingBox = entityBoundingBox.move(0.0D, e, 0.0D);
        }

        if(bl && f != 0.0D) {
            f = Shapes.collide(Direction.Axis.Z, entityBoundingBox, collisions, f);
            if(f != 0.0D) entityBoundingBox = entityBoundingBox.move(0.0D, 0.0D, f);
        }

        if(d != 0.0D) {
            d = Shapes.collide(Direction.Axis.X, entityBoundingBox, collisions, d);
            if(!bl && d != 0.0D) entityBoundingBox = entityBoundingBox.move(d, 0.0D, 0.0D);
        }

        if (!bl && f != 0.0D) {
            f = Shapes.collide(Direction.Axis.Z, entityBoundingBox, collisions, f);
        }

        return new Vec3(d, e, f);
    }

    public boolean isClimbing(LevelReader world) {
        BlockState state = world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ));
        if(state.is(BlockTags.CLIMBABLE)) return true;
        if(state.getBlock() instanceof TrapDoorBlock && this.canEnterTrapdoor(world, state)) return true;
        return false;
    }

    private boolean canEnterTrapdoor(LevelReader world, BlockState trapdoor) {
        if(!trapdoor.getValue(TrapDoorBlock.OPEN)) return false;

        BlockState ladder = world.getBlockState(new BlockPos(this.blockX, this.blockY - 1, this.blockZ));

        return ladder.is(Blocks.LADDER) && ladder.getValue(LadderBlock.FACING) == trapdoor.getValue(TrapDoorBlock.FACING);
    }

    void checkWaterState(LevelReader world) {
        if(this.updateMovementInFluid(world, FluidTags.WATER, 0.014D)) {
            this.fallDistance = 0.0F;
            this.touchingWater = true;
        } else {
            this.touchingWater = false;
        }
    }

    public boolean updateMovementInFluid(LevelReader world, TagKey<Fluid> tag, double d) {
        int n;
        AABB box = this.box.deflate(0.001D);
        int i = Mth.floor(box.minX);
        int j = Mth.ceil(box.maxX);
        int k = Mth.floor(box.minY);
        int l = Mth.ceil(box.maxY);
        int m = Mth.floor(box.minZ);

        if (!world.hasChunksAt(i, k, m, j, l, n = Mth.ceil(box.maxZ))) {
            return false;
        }

        double e = 0.0;
        boolean bl2 = false;
        Vec3 vec3d = Vec3.ZERO;
        int o = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for(int p = i; p < j; ++p) {
            for(int q = k; q < l; ++q) {
                for(int r = m; r < n; ++r) {
                    double f;
                    mutable.set(p, q, r);
                    FluidState fluidState = world.getFluidState(mutable);
                    if (!fluidState.is(tag) || !((f = (float)q + fluidState.getHeight(world, mutable)) >= box.minY)) continue;
                    bl2 = true;
                    e = Math.max(f - box.minY, e);

                    Vec3 vec3d2 = fluidState.getFlow(world, mutable);

                    if(e < 0.4) {
                        vec3d2 = vec3d2.scale(e);
                    }

                    vec3d = vec3d.add(vec3d2);
                    ++o;
                }
            }
        }

        if(vec3d.length() > 0.0) {
            if(o > 0) {
                vec3d = vec3d.multiply(new Vec3(1.0 / (double)o, 1.0 / (double)o, 1.0 / (double)o));
            }

            Vec3 vec3d3 = new Vec3(this.velX, this.velY, this.velZ);
            vec3d = vec3d.scale(d);

            if(Math.abs(vec3d3.x) < 0.003 && Math.abs(vec3d3.z) < 0.003 && vec3d.length() < 0.0045000000000000005) {
                vec3d = vec3d.normalize().scale(0.0045000000000000005);
            }

            this.velX += vec3d.x;
            this.velY += vec3d.y;
            this.velZ += vec3d.z;
        }

        this.fluidHeight.put(tag, e);
        return bl2;
    }

    public void fall(LevelReader world, double heightDifference, BlockState landedState) {
        if(!this.touchingWater) {
            this.checkWaterState(world);
        }

        //add soulspeed movement boost

        if(this.onGround) {
            if(this.fallDistance > 0.0F) {
                if(landedState.getBlock() instanceof BedBlock) {
                    this.handleFallDamage(this.fallDistance * 0.6F, 1.0F);
                } else if(landedState.getBlock() instanceof FarmlandBlock) {
                    //grief
                } else if(landedState.getBlock() instanceof HayBlock) {
                    this.handleFallDamage(this.fallDistance, 0.2F);
                } else if(landedState.getBlock() instanceof SlimeBlock) {
                    this.handleFallDamage(this.fallDistance, this.input.playerInput.shift() ? 1.0F : 0.0F);
                } else if(landedState.getBlock() instanceof TurtleEggBlock) {
                    //eggs can break (1/3)
                    this.handleFallDamage(this.fallDistance, 1.0F);
                } else {
                    this.handleFallDamage(this.fallDistance, 1.0F);
                }
            }

            this.fallDistance = 0.0F;
        } else if(heightDifference < 0.0D) {
            this.fallDistance -= (float)heightDifference;
        }

        if(this.touchingWater) {
            this.fallDistance = 0F;
        }
    }

    public boolean handleFallDamage(double fallDistance, float damageMultiplier) {
        int i = this.computeFallDamage(fallDistance, damageMultiplier);

        if(i > 0) {
            //this.damage(DamageSource.FALL, i);
        	this.isDamaged = true; 
        	this.velX = 0;
        	this.velZ = 0;
            return true;
        }

        return false;
    }

    public int computeFallDamage(double fallDistance, float damageMultiplier) {
    	if (TungstenModDataContainer.player == null) return 0;
    	if (TungstenModDataContainer.player.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
    		return 0;
    	}
        float f = this.jumpBoost < 0 ? 0.0F : (float)(this.jumpBoost + 1);
        return Mth.ceil((fallDistance - 3.0f - f) * damageMultiplier);
    }

    public void checkBlockCollision(LevelReader world) {
    	double minOffset = 0.001; // default 0.001
    	double maxOffset = 0.001; // default 0.001
        BlockPos blockPos = new BlockPos((int) (this.box.minX + minOffset), (int) (this.box.minY + minOffset), (int) (this.box.minZ + minOffset));
        BlockPos blockPos2 = new BlockPos((int) (this.box.maxX - maxOffset), (int) (this.box.maxY - maxOffset), (int) (this.box.maxZ - maxOffset));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        if(world.hasChunksAt(blockPos, blockPos2)) {
            for(int i = blockPos.getX(); i <= blockPos2.getX(); ++i) {
                for(int j = blockPos.getY(); j <= blockPos2.getY(); ++j) {
                    for(int k = blockPos.getZ(); k <= blockPos2.getZ(); ++k) {
                        pos.set(i, j, k);
                        BlockState state = world.getBlockState(pos);

                        if(state.getBlock() instanceof FireBlock) {
                            //damage the entity
                        } else if(state.getBlock() instanceof PressurePlateBlock) {
                            //change block state
                        } else if(state.getBlock() instanceof BubbleColumnBlock) {
                            BlockState surface = world.getBlockState(pos.above());
                            boolean drag = surface.hasProperty(BubbleColumnBlock.DRAG) && surface.getValue(BubbleColumnBlock.DRAG);

                            if(surface.isAir()) {
                                this.velY = drag ? Math.max(-0.9D, this.velY - 0.03D) : Math.min(1.8D, this.velY + 0.1D);
                            } else {
                                this.velY = drag ? Math.max(-0.3D, this.velY - 0.03D) : Math.min(0.7D, this.velY + 0.06D);
                                this.fallDistance = 0.0F;
                            }
                        } else if(state.getBlock() instanceof CactusBlock) {
                            //damage the entity
                        } else if(state.getBlock() instanceof CampfireBlock) {
                            //damage the entity
                        } else if(state.getBlock() instanceof CampfireBlock) {
                            //damage the entity
                        } else if(state.getBlock() instanceof CauldronBlock) {
                            //extinguish the entity
                        } else if(state.getBlock() instanceof WebBlock) {
                            this.fallDistance = 0.0F;
                            this.mulX = 0.25D; this.mulY = 0.05F; this.mulZ = 0.25D;
                        } else if(state.getBlock() instanceof EndPortalBlock) {
                            //fuck
                        } else if(state.getBlock() instanceof HoneyBlock) {
                            if(this.isSliding(pos)) {
                                if(this.velY < -0.13D) {
                                    double m = -0.05D / this.velY;
                                    this.velX *= m; this.velY = -0.05D; this.velZ *= m;
                                } else {
                                    this.velY = -0.05D;
                                }

                                this.fallDistance = 0.0F;
                            }
                        } else if(state.getBlock() instanceof NetherPortalBlock) {
                            //eh?
                        } else if(state.getBlock() instanceof SweetBerryBushBlock) {
                            this.mulX = 0.8f; this.mulY = 0.75D; this.mulZ = 0.8F;
                            //damage the entity
                        } else if(state.getBlock() instanceof TripWireBlock) {
                            //change block state
                        } else if(state.getBlock() instanceof WitherRoseBlock) {
                            //damage the entity
                        }
                    }
                }
            }
        }
    }

    private boolean isSliding(BlockPos pos) {
        if(this.onGround) return false;
        if(this.posY > (double)pos.getY() + 0.9375D - 1.0E-07D) return false;
        if(this.velY >= -0.08D) return false;

        double d = Math.abs((double)pos.getX() + 0.5D - this.posX);
        double e = Math.abs((double)pos.getZ() + 0.5D - this.posZ);
        double f = 0.4375D + (double)(this.dimensions.width() / 2.0F);
        return d + 1.0E-7D > f || e + 1.0E-7D > f;
    }

    public float getVelocityMultiplier(LevelReader world) {
    	BlockState blockState = world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ));
		float f = blockState.getBlock().getSpeedFactor();
		if (!blockState.is(Blocks.WATER) && !blockState.is(Blocks.BUBBLE_COLUMN)) {
			return (double)f == 1.0 ? world.getBlockState(this.getLandingPos(world)).getBlock().getSpeedFactor() : f;
		} else {
			return f;
		}
    }
    
    protected static Vec3 movementInputToVelocity(Vec3 movementInput, float speed, float yaw) {
		double d = movementInput.lengthSqr();
		if (d < 1.0E-7) {
			return Vec3.ZERO;
		} else {
			Vec3 vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).scale(speed);
			float f = Mth.sin(yaw * (float) (Math.PI / 180.0));
			float g = Mth.cos(yaw * (float) (Math.PI / 180.0));
			return new Vec3(vec3d.x * g - vec3d.z * f, vec3d.y, vec3d.z * g + vec3d.x * f);
		}
	}

    public BlockPos getLandingPos(LevelReader world) {
        BlockPos pos = new BlockPos(this.blockX, Mth.floor(this.posY - (double)0.2F), this.blockZ);
        
        if(!world.getBlockState(pos).isAir()) {
            return pos;
        }

        BlockState state = world.getBlockState(pos.below());

        if(state.getBlock() instanceof FenceGateBlock || state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS)) {
            return pos.below();
        }

        return pos;
    }
    
    public boolean canSprint() {
		return this.hunger.getFoodLevel() > 6.0F;
	}
    
    public boolean shouldSlowDown() {
		return this.sneaking || this.keySneak;
	}
    

	private Vec2 applyMovementSpeedFactors(Vec2 input) {
		if (input.lengthSquared() == 0.0F) {
			return input;
		} else {
			Vec2 vec2f = input.scale(0.98F);
			if (this.usingItem) {
				vec2f = vec2f.scale(0.2F);
			}

			if (this.shouldSlowDown()) {
				float f = 0.3F;
				vec2f = vec2f.scale(f);
			}
			
			vec2f = applyDirectionalMovementSpeedFactors(vec2f);
			

			return vec2f;
		}
	}

	private static Vec2 applyDirectionalMovementSpeedFactors(Vec2 vec) {
		float f = vec.length();
		if (f <= 0.0F) {
			return vec;
		} else {
			Vec2 vec2f = vec.scale(1.0F / f);
			float g = getDirectionalMovementSpeedMultiplier(vec2f);
			float h = Math.min(f * g, 1.0F);
			return vec2f.scale(h);
		}
	}

	private static float getDirectionalMovementSpeedMultiplier(Vec2 vec) {
		float f = Math.abs(vec.x);
		float g = Math.abs(vec.y);
		float h = g > f ? f / g : g / f;
		return Mth.sqrt(1.0F + Mth.square(h));
	}
    
    private boolean canStartSprinting() {
		return Math.abs(this.forwardSpeed) > -0.1
			&& this.canSprint()
			&& this.keyForward
			&& !this.horizontalCollision
			&& !this.usingItem
			&& this.blindness < 0
			&& (!this.shouldSlowDown() || this.isSubmergedInWater)
			&& (!this.touchingWater || this.isSubmergedInWater);
	}

	private boolean shouldStopSprinting() {
		
		return this.forwardSpeed == 0
			|| !this.keyForward
			|| !this.canSprint()
			|| this.horizontalCollision && !this.collidedSoftly
			|| this.touchingWater && !this.isSubmergedInWater;
	}

	private boolean shouldStopSwimSprinting() {
		return !this.touchingWater
			|| !this.input.hasForwardMovement() && !this.onGround && !this.input.playerInput.shift()
			|| !this.canSprint();
	}

    private boolean isWalking() {
        return this.input.hasForwardMovement();
    }

    public AABB calculateBoundsForPose(Pose pose) {
        EntityDimensions size = POSE_DIMENSIONS.getOrDefault(pose, STANDING_DIMENSIONS);
        float f = size.width() / 2.0F;
        Vec3 min = new Vec3(this.posX - (double)f, this.posY, this.posZ - (double)f);
        Vec3 max = new Vec3(this.posX + (double)f, this.posY + (double)size.height(), this.posZ + (double)f);
        return new AABB(min, max);
    }

    public boolean wouldPoseNotCollide(LevelReader world, Pose pose) {
        return this.isSpaceEmpty(world, this.calculateBoundsForPose(pose).deflate(1.0E-7));
    }

    private void pushOutOfBlocks(LevelReader world, double x, double d) {
        Direction[] directions = new Direction[] { Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH };
        BlockPos blockPos = new BlockPos((int) x, (int) this.posY, (int) d);
        if (!this.wouldCollideAt(world, blockPos)) {
            return;
        }
        double e = x - (double)blockPos.getX();
        double f = d - (double)blockPos.getZ();
        Direction direction = null;
        double g = Double.MAX_VALUE;

        for (Direction direction2 : directions) {
            double i;
            double h = direction2.getAxis().choose(e, 0.0D, f);
            double d2 = i = direction2.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D - h : h;
            if (!(i < g) || this.wouldCollideAt(world, blockPos.relative(direction2))) continue;
            g = i;
            direction = direction2;
        }

        if(direction != null) {
            if(direction.getAxis() == Direction.Axis.X) {
                this.velX = 0.1D * (double)direction.getStepX();
            } else {
                this.velZ = 0.1D * (double)direction.getStepZ();
            }
        }
    }

    public boolean canCollide(LevelReader world, AABB box) {
        AgentBlockCollisions collisions = new AgentBlockCollisions(world, this, box, true);
        
        while (collisions.hasNext()) {
			if (!collisions.next().isEmpty()) {
				return true;
			}
		}

		return false;
    }

    private boolean wouldCollideAt(LevelReader world, BlockPos pos) {
    	AABB box = this.box;
        return this.canCollide(world, box);
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
        this.movementSpeed = 0.1F;

        if(sprinting) {
            this.movementSpeed *= (1.0D + (double)0.3F);
        }

        if(this.speed >= 0) {
            double amplifier = 0.20000000298023224D * (double)(this.speed + 1);
            this.movementSpeed *= (1.0D + amplifier);
        }
    }

    public double getFluidHeight(TagKey<Fluid> fluid) {
        return this.fluidHeight.getDouble(fluid);
    }

    public boolean isInLava() {
        return !this.firstUpdate && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0D;
    }

    public void compare(Player player, Input playerInput, boolean executor) {
        List<String> values = new ArrayList<>();
        
        if(this.posX != player.getX() || this.posY != player.getY() || this.posZ != player.getZ()) {
            values.add(String.format("Position mismatch (%s, %s, %s) vs (%s, %s, %s)",
                player.position().x == this.posX ? "x" : player.position().x,
                player.position().y == this.posY ? "y" : player.position().y,
                player.position().z == this.posZ ? "z" : player.position().z,
                player.position().x == this.posX ? "x" : this.posX,
                player.position().y == this.posY ? "y" : this.posY,
                player.position().z == this.posZ ? "z" : this.posZ));
            if (TungstenModDataContainer.EXECUTOR.isRunning()) {
            	player.setPos(this.posX, this.posY, this.posZ);

            	if (TungstenModRenderContainer.ERROR.size() > 1000) TungstenModRenderContainer.ERROR.clear();
            }
        }
        
        if(this.velX != player.getDeltaMovement().x || this.velY != player.getDeltaMovement().y || this.velZ != player.getDeltaMovement().z) {
            values.add(String.format("Velocity mismatch (%s, %s, %s) vs (%s, %s, %s)",
                player.getDeltaMovement().x,
                player.getDeltaMovement().y,
                player.getDeltaMovement().z,
                player.getDeltaMovement().x == this.velX ? "x" : this.velX,
                player.getDeltaMovement().y == this.velY ? "y" : this.velY,
                player.getDeltaMovement().z == this.velZ ? "z" : this.velZ));
            if (TungstenModDataContainer.EXECUTOR.isRunning()) {
            	
            	values.add(String.format("Velocity mismatch by (%s, %s, %s)",
                        player.getDeltaMovement().x - this.velX,
                        player.getDeltaMovement().y - this.velY,
                        player.getDeltaMovement().z - this.velZ));
            	player.setDeltaMovement(this.velX, this.velY, this.velZ);
            	Node node = TungstenModDataContainer.EXECUTOR.getCurrentNode();
            	if (TungstenModRenderContainer.ERROR.size() > 1000) TungstenModRenderContainer.ERROR.clear();
                if (node != null && node.agent.getPos().distanceTo(player.position()) > 0.78) {
                    RenderHelper.renderNode(node, TungstenModRenderContainer.ERROR);
                    TungstenModRenderContainer.ERROR.add(new Cuboid(player.position(), new Vec3(0.1, 0.5, 0.1), Color.RED));
                }
            }
        }

        if(this.mulX != ((AccessorEntity)player).getMovementMultiplier().x
            || this.mulY != ((AccessorEntity)player).getMovementMultiplier().y
            || this.mulZ != ((AccessorEntity)player).getMovementMultiplier().z) {
            values.add(String.format("Movement Multiplier mismatch (%s, %s, %s) vs (%s, %s, %s)",
                ((AccessorEntity)player).getMovementMultiplier().x,
                ((AccessorEntity)player).getMovementMultiplier().y,
                ((AccessorEntity)player).getMovementMultiplier().z,
                ((AccessorEntity)player).getMovementMultiplier().x == this.mulX ? "x" : this.mulX,
                ((AccessorEntity)player).getMovementMultiplier().y == this.mulY ? "y" : this.mulY,
                ((AccessorEntity)player).getMovementMultiplier().z == this.mulZ ? "z" : this.mulZ));
        }

        if(this.forwardSpeed != player.zza || this.sidewaysSpeed != player.xxa || this.upwardSpeed != player.yya) {
            values.add(String.format("Input Speed mismatch (%s, %s, %s) vs (%s, %s, %s)",
                player.zza == this.forwardSpeed ? "f" : player.zza,
                player.yya == this.upwardSpeed ? "u" : player.yya,
                player.xxa == this.sidewaysSpeed ? "s" : player.xxa,
                player.zza == this.forwardSpeed ? "f" : this.forwardSpeed,
                player.yya == this.upwardSpeed ? "u" : this.upwardSpeed,
                player.xxa == this.sidewaysSpeed ? "s" : this.sidewaysSpeed));
        }

        if(this.movementSpeed != player.getSpeed()) {

        	if (TungstenModDataContainer.LOG_DEBUG_DATA) {
        		Node node = TungstenModDataContainer.EXECUTOR.getCurrentNode();
            	if (node != null) {
    	        	StringBuilder string = new StringBuilder();
    	
    	    		string.append("{\n");
    	    		if (node.input.forward != playerInput.forward()) {
    	        		string.append("forward: ");
    	        		string.append(playerInput.forward());
    	        		string.append(" vs ");
    	        		string.append(node.input.forward);
    	        		string.append("\n");
    	    		}
    	    		if (node.input.back != playerInput.backward()) {
    	        		string.append("back: ");
    	        		string.append(playerInput.backward());
    	        		string.append(" vs ");
    	        		string.append(node.input.back);
    	        		string.append("\n");
    	    		}
    	    		if (node.input.right != playerInput.right()) {
    	        		string.append("right: ");
    	        		string.append(playerInput.right());
    	        		string.append(" vs ");
    	        		string.append(node.input.right);
    	        		string.append("\n");
    	    		}
    	    		if (node.input.left != playerInput.left()) {
    	        		string.append("left: ");
    	        		string.append(playerInput.left());
    	        		string.append(" vs ");
    	        		string.append(node.input.left);
    	        		string.append("\n");
    	    		}
    	    		if (node.input.jump != playerInput.jump()) {
    	        		string.append("jump: ");
    	        		string.append(playerInput.jump());
    	        		string.append(" vs ");
    	        		string.append(node.input.jump);
    	        		string.append("\n");
    	    		}
    	    		if (node.input.sneak != playerInput.shift()) {
    	        		string.append("sneak: ");
    	        		string.append(playerInput.shift());
    	        		string.append(" vs ");
    	        		string.append(node.input.sneak);
    	        		string.append("\n");
    	    		}
    	    		if (node.input.sprint != playerInput.sprint()) {
    	        		string.append("sprint: ");
    	        		string.append(playerInput.sprint());
    	        		string.append(" vs ");
    	        		string.append(node.input.sprint);
    	        		string.append("\n");
    	    		}
    	    		if (node.input.pitch != player.getXRot()) {
    	        		string.append("pitch: ");
    	        		string.append(player.getXRot());
    	        		string.append(" vs ");
    	        		string.append(node.input.pitch);
    	        		string.append("\n");
    	    		}
    	    		if (node.input.yaw != player.getYRot()) {
    	        		string.append("yaw: ");
    	        		string.append(player.getYRot());
    	        		string.append(" vs ");
    	        		string.append(node.input.yaw);
    	        		string.append("\n");
    	    		}
    	    		if (node.agent.onGround != player.onGround()) {
    	        		string.append("isOnGround: ");
    	        		string.append(player.onGround());
    	        		string.append(" vs ");
    	        		string.append(node.agent.onGround);
    	        		string.append("\n");
    	    		}
    	    		string.append("\n");
    	    		string.append("}");
    	
    	        	if (string.toString().length() > 4) {
    		        	Debug.logMessage("------------");
    		        	Debug.logMessage(string.toString());
    		        	Debug.logMessage("Current Movement Speed: " + player.getSpeed() + " \nExpected to be: " + this.movementSpeed + "");
    		        	Debug.logMessage("------------");
    	        	} else {
    		        	Debug.logMessage("Current Movement Speed: " + player.getSpeed() + " \nExpected to be: " + this.movementSpeed + "");
    	        	}
            	}	
        	}
        	
            values.add(String.format("Movement Speed mismatch %f vs %f", player.getSpeed(), this.movementSpeed));
        }

        if(this.pose != player.getPose()) {
            values.add(String.format("Pose mismatch %s vs %s", player.getPose(), this.pose));
        }

        if(this.isSubmergedInWater != player.isUnderWater()) {
            values.add(String.format("Sprinting mismatch %s vs %s", player.isSprinting(), this.sprinting));
        }

        if(this.touchingWater != player.isInWater()) {
            values.add(String.format("Touching water mismatch %s vs %s", player.isInWater(), this.touchingWater));
        }

        if(this.isSubmergedInWater != player.isUnderWater()) {
            values.add(String.format("Submerged in water mismatch %s vs %s", player.isUnderWater(), this.isSubmergedInWater));
        }

	    if(this.input.playerInput.shift() != playerInput.shift()) {
		    values.add(String.format("Sneaking mismatch %s vs %s", playerInput.shift(), this.input.playerInput.shift()));
	    }

        if(this.swimming != player.isSwimming()) {
            values.add(String.format("Swimming mismatch %s vs %s", player.isSwimming(), this.swimming));
        }

        if(this.standingEyeHeight != player.getEyeHeight()) {
            values.add(String.format("Eye height mismatch %s vs %s", player.getEyeHeight(), this.standingEyeHeight));
        }

        if(this.fallDistance != ((AccessorEntity)player).getFallDistance()) {
            values.add(String.format("Fall distance mismatch %s vs %s", ((AccessorEntity)player).getFallDistance(), this.fallDistance));
        }

        if(this.horizontalCollision != ((AccessorEntity)player).getHorizontalCollision()) {
            values.add(String.format("Horizontal Collision mismatch %s vs %s", ((AccessorEntity)player).getHorizontalCollision(), this.horizontalCollision));
        }

        if(this.verticalCollision != ((AccessorEntity)player).getVerticalCollision()) {
            values.add(String.format("Vertical Collision mismatch %s vs %s", ((AccessorEntity)player).getVerticalCollision(), this.verticalCollision));
        }

        if(this.collidedSoftly != ((AccessorEntity)player).getMinorHorizontalCollision()) {
            values.add(String.format("Soft Collision mismatch %s vs %s", ((AccessorEntity)player).getMinorHorizontalCollision(), this.collidedSoftly));
        }

        if(this.jumping != ((AccessorLivingEntity)player).getJumping()) {
            values.add(String.format("Jumping mismatch %s vs %s", ((AccessorLivingEntity)player).getJumping(), this.jumping));
        }

        if(this.jumpingCooldown != ((AccessorLivingEntity)player).getJumpingCooldown()) {
            values.add(String.format("Jumping Cooldown mismatch %s vs %s", ((AccessorLivingEntity)player).getJumpingCooldown(), this.jumpingCooldown));
        }

        if(this.firstUpdate != ((AccessorEntity)player).getFirstTick()) {
            values.add(String.format("First Update mismatch %s vs %s", ((AccessorEntity)player).getFirstTick(), this.firstUpdate));
        }

        if(!this.submergedFluids.equals(((AccessorEntity)player).getSubmergedFluids())) {
            values.add(String.format("Submerged Fluids mismatch %s vs %s", ((AccessorEntity)player).getSubmergedFluids(), this.submergedFluids));
        }

        if(!values.isEmpty()) {
            System.out.println("Tick " + player.tickCount + " ===========================================");
            values.forEach(System.out::println);
        }
    }


    public static Agent of(Player player) {
        Agent agent = new Agent();
        agent.keyForward = Input.EMPTY.forward();
        agent.keyBack = Input.EMPTY.backward();
        agent.keyLeft = Input.EMPTY.left();
        agent.keyRight = Input.EMPTY.right();
        agent.keyJump = Input.EMPTY.jump();
        agent.keySneak = Input.EMPTY.shift();
        agent.keySprint = Input.EMPTY.sprint();

        agent.pose = player.getPose();
        agent.sprinting = player.isSprinting();
        agent.inSneakingPose = player.isCrouching();
        agent.usingItem = player.isUsingItem();
        agent.sidewaysSpeed = player.xxa;
        agent.upwardSpeed = player.yya;
        agent.forwardSpeed = player.zza;
        agent.yaw = player.getYRot();
        agent.pitch = player.getXRot();
        agent.posX = player.getX();
        agent.posY = player.getY();
        agent.posZ = player.getZ();
        agent.blockX = player.blockPosition().getX();
        agent.blockY = player.blockPosition().getY();
        agent.blockZ = player.blockPosition().getZ();
        agent.velX = player.getDeltaMovement().x;
        agent.velY = player.getDeltaMovement().y;
        agent.velZ = player.getDeltaMovement().z;
        agent.mulX = ((AccessorEntity)player).getMovementMultiplier().x;
        agent.mulY = ((AccessorEntity)player).getMovementMultiplier().y;
        agent.mulZ = ((AccessorEntity)player).getMovementMultiplier().z;
        agent.fluidHeight.put(FluidTags.WATER, player.getFluidHeight(FluidTags.WATER));
        agent.fluidHeight.put(FluidTags.LAVA, player.getFluidHeight(FluidTags.LAVA));
        agent.submergedFluids.addAll(((AccessorEntity)player).getSubmergedFluids());
        agent.firstUpdate = ((AccessorEntity)player).getFirstTick();
        agent.box = player.getBoundingBox();
        agent.dimensions = player.getDimensions(player.getPose());
        agent.standingEyeHeight = player.getEyeHeight();
        agent.onGround = player.onGround();
        agent.sleeping = player.isSleeping();
        agent.sneaking = player.isCrouching();
        agent.hunger = player.getFoodData();
        agent.sprinting = player.isSprinting();
        agent.swimming = player.isSwimming();
        agent.fallFlying = player.getAbilities().flying;
        agent.stepHeight = player.getStepHeight();
        agent.fallDistance = ((AccessorEntity)player).getFallDistance();
        agent.touchingWater = player.isInWater();
        agent.isSubmergedInWater = player.isUnderWater();
        agent.horizontalCollision = ((AccessorEntity)player).getHorizontalCollision();
        agent.verticalCollision = ((AccessorEntity)player).getVerticalCollision();
        agent.collidedSoftly = ((AccessorEntity)player).getMinorHorizontalCollision();
        agent.jumping = ((AccessorLivingEntity)player).getJumping();
        agent.speed = player.hasEffect(MobEffects.SPEED) ? player.getEffect(MobEffects.SPEED).getAmplifier() : -1;
        agent.blindness = player.hasEffect(MobEffects.BLINDNESS) ? player.getEffect(MobEffects.BLINDNESS).getAmplifier() : -1;
        agent.jumpBoost = player.hasEffect(MobEffects.JUMP_BOOST) ? player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() : -1;
        agent.slowFalling = player.hasEffect(MobEffects.SLOW_FALLING) ? player.getEffect(MobEffects.SLOW_FALLING).getAmplifier() : -1;
        agent.dolphinsGrace = player.hasEffect(MobEffects.DOLPHINS_GRACE) ? player.getEffect(MobEffects.DOLPHINS_GRACE).getAmplifier() : -1;
        agent.levitation = player.hasEffect(MobEffects.LEVITATION) ? player.getEffect(MobEffects.LEVITATION).getAmplifier() : -1;
        agent.movementSpeed = player.getSpeed();
        agent.airStrafingSpeed = 0.06f;
        agent.jumpingCooldown = ((AccessorLivingEntity)player).getJumpingCooldown();
        agent.hunger.setFoodLevel(player.getFoodData().getFoodLevel());
        agent.hunger.setSaturation(player.getFoodData().getSaturationLevel());
        return agent;
    }


    public static Agent of(Player player, Input playerInput) {
        Agent agent = new Agent();
        agent.keyForward = playerInput.forward();
        agent.keyBack = playerInput.backward();
        agent.keyLeft = playerInput.left();
        agent.keyRight = playerInput.right();
        agent.keyJump = playerInput.jump();
        agent.keySneak = playerInput.shift();
        agent.keySprint = playerInput.sprint();

        agent.pose = player.getPose();
        agent.sprinting = player.isSprinting();
        agent.inSneakingPose = player.isCrouching();
        agent.usingItem = player.isUsingItem();
        agent.sidewaysSpeed = player.xxa;
        agent.upwardSpeed = player.yya;
        agent.forwardSpeed = player.zza;
        agent.yaw = player.getYRot();
        agent.pitch = player.getXRot();
        agent.posX = player.getX();
        agent.posY = player.getY();
        agent.posZ = player.getZ();
        agent.blockX = player.blockPosition().getX();
        agent.blockY = player.blockPosition().getY();
        agent.blockZ = player.blockPosition().getZ();
        agent.velX = player.getDeltaMovement().x;
        agent.velY = player.getDeltaMovement().y;
        agent.velZ = player.getDeltaMovement().z;
        agent.mulX = ((AccessorEntity)player).getMovementMultiplier().x;
        agent.mulY = ((AccessorEntity)player).getMovementMultiplier().y;
        agent.mulZ = ((AccessorEntity)player).getMovementMultiplier().z;
        agent.fluidHeight.put(FluidTags.WATER, player.getFluidHeight(FluidTags.WATER));
        agent.fluidHeight.put(FluidTags.LAVA, player.getFluidHeight(FluidTags.LAVA));
        agent.submergedFluids.addAll(((AccessorEntity)player).getSubmergedFluids());
        agent.firstUpdate = ((AccessorEntity)player).getFirstTick();
        agent.box = player.getBoundingBox();
        agent.dimensions = player.getDimensions(player.getPose());
        agent.standingEyeHeight = player.getEyeHeight();
        agent.onGround = player.onGround();
        agent.sleeping = player.isSleeping();
        agent.sneaking = player.isCrouching();
        agent.hunger = player.getFoodData();
        agent.sprinting = player.isSprinting();
        agent.swimming = player.isSwimming();
        agent.fallFlying = player.getAbilities().flying;
        agent.stepHeight = player.getStepHeight();
        agent.fallDistance = ((AccessorEntity)player).getFallDistance();
        agent.touchingWater = player.isInWater();
        agent.isSubmergedInWater = player.isUnderWater();
        agent.horizontalCollision = ((AccessorEntity)player).getHorizontalCollision();
        agent.verticalCollision = ((AccessorEntity)player).getVerticalCollision();
        agent.collidedSoftly = ((AccessorEntity)player).getMinorHorizontalCollision();
        agent.jumping = ((AccessorLivingEntity)player).getJumping();
        agent.speed = player.hasEffect(MobEffects.SPEED) ? player.getEffect(MobEffects.SPEED).getAmplifier() : -1;
        agent.blindness = player.hasEffect(MobEffects.BLINDNESS) ? player.getEffect(MobEffects.BLINDNESS).getAmplifier() : -1;
        agent.jumpBoost = player.hasEffect(MobEffects.JUMP_BOOST) ? player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() : -1;
        agent.slowFalling = player.hasEffect(MobEffects.SLOW_FALLING) ? player.getEffect(MobEffects.SLOW_FALLING).getAmplifier() : -1;
        agent.dolphinsGrace = player.hasEffect(MobEffects.DOLPHINS_GRACE) ? player.getEffect(MobEffects.DOLPHINS_GRACE).getAmplifier() : -1;
        agent.levitation = player.hasEffect(MobEffects.LEVITATION) ? player.getEffect(MobEffects.LEVITATION).getAmplifier() : -1;
        agent.movementSpeed = player.getSpeed();
        agent.airStrafingSpeed = 0.06f;
        agent.jumpingCooldown = ((AccessorLivingEntity)player).getJumpingCooldown();
        agent.hunger.setFoodLevel(player.getFoodData().getFoodLevel());
        agent.hunger.setSaturation(player.getFoodData().getSaturationLevel());
        return agent;
    }

    
    public static Agent of(LocalPlayer player, Options options) {
        Agent agent = new Agent();
        agent.keyForward = options.keyUp.isDown();
        agent.keyBack = options.keyDown.isDown();
        agent.keyLeft = options.keyLeft.isDown();
        agent.keyRight = options.keyRight.isDown();
        agent.keyJump = options.keyJump.isDown();
        agent.keySneak = options.keyShift.isDown();
        agent.keySprint = options.keySprint.isDown();

        agent.pose = player.getPose();
        agent.sprinting = player.isSprinting();
        agent.inSneakingPose = player.isCrouching();
        agent.usingItem = player.isUsingItem();
        agent.sidewaysSpeed = player.xxa;
        agent.upwardSpeed = player.yya;
        agent.forwardSpeed = player.zza;
        agent.yaw = player.getYRot();
        agent.pitch = player.getXRot();
        agent.posX = player.getX();
        agent.posY = player.getY();
        agent.posZ = player.getZ();
        agent.blockX = player.blockPosition().getX();
        agent.blockY = player.blockPosition().getY();
        agent.blockZ = player.blockPosition().getZ();
        agent.velX = player.getDeltaMovement().x;
        agent.velY = player.getDeltaMovement().y;
        agent.velZ = player.getDeltaMovement().z;
        agent.mulX = ((AccessorEntity)player).getMovementMultiplier().x;
        agent.mulY = ((AccessorEntity)player).getMovementMultiplier().y;
        agent.mulZ = ((AccessorEntity)player).getMovementMultiplier().z;
        agent.fluidHeight.put(FluidTags.WATER, player.getFluidHeight(FluidTags.WATER));
        agent.fluidHeight.put(FluidTags.LAVA, player.getFluidHeight(FluidTags.LAVA));
        agent.submergedFluids.addAll(((AccessorEntity)player).getSubmergedFluids());
        agent.firstUpdate = ((AccessorEntity)player).getFirstTick();
        agent.box = player.getBoundingBox();
        agent.dimensions = player.getDimensions(player.getPose());
        agent.standingEyeHeight = player.getEyeHeight();
        agent.onGround = player.onGround();
        agent.sleeping = player.isSleeping();
        agent.sneaking = player.isCrouching();
        agent.hunger = player.getFoodData();
        agent.sprinting = player.isSprinting();
        agent.swimming = player.isSwimming();
        agent.fallFlying = player.getAbilities().flying;
        agent.stepHeight = player.getStepHeight();
        agent.fallDistance = ((AccessorEntity)player).getFallDistance();
        agent.touchingWater = player.isInWater();
        agent.isSubmergedInWater = player.isUnderWater();
        agent.horizontalCollision = ((AccessorEntity)player).getHorizontalCollision();
        agent.verticalCollision = ((AccessorEntity)player).getVerticalCollision();
        agent.collidedSoftly = ((AccessorEntity)player).getMinorHorizontalCollision();
        agent.jumping = ((AccessorLivingEntity)player).getJumping();
        agent.speed = player.hasEffect(MobEffects.SPEED) ? player.getEffect(MobEffects.SPEED).getAmplifier() : -1;
        agent.blindness = player.hasEffect(MobEffects.BLINDNESS) ? player.getEffect(MobEffects.BLINDNESS).getAmplifier() : -1;
        agent.jumpBoost = player.hasEffect(MobEffects.JUMP_BOOST) ? player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() : -1;
        agent.slowFalling = player.hasEffect(MobEffects.SLOW_FALLING) ? player.getEffect(MobEffects.SLOW_FALLING).getAmplifier() : -1;
        agent.dolphinsGrace = player.hasEffect(MobEffects.DOLPHINS_GRACE) ? player.getEffect(MobEffects.DOLPHINS_GRACE).getAmplifier() : -1;
        agent.levitation = player.hasEffect(MobEffects.LEVITATION) ? player.getEffect(MobEffects.LEVITATION).getAmplifier() : -1;
        agent.movementSpeed = player.getSpeed();
        agent.airStrafingSpeed = 0.06f;
        agent.jumpingCooldown = ((AccessorLivingEntity)player).getJumpingCooldown();
        agent.hunger.setFoodLevel(player.getFoodData().getFoodLevel());
        agent.hunger.setSaturation(player.getFoodData().getSaturationLevel());
        return agent;
    }

    public static Agent of(Agent other, boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint, float pitch, float yaw) {
        Agent agent = new Agent();
        agent.keyForward = forward;
        agent.keyBack = back;
        agent.keyLeft = left;
        agent.keyRight = right;
        agent.keyJump = jump;
        agent.keySneak = sneak;
        agent.keySprint = sprint;
        agent.hunger.setFoodLevel(other.hunger.getFoodLevel());
        agent.hunger.setSaturation(other.hunger.getSaturationLevel());
        agent.sprinting = other.sprinting;
        agent.pose = other.pose;
        agent.inSneakingPose = other.inSneakingPose;
        agent.usingItem = other.usingItem;
        agent.sidewaysSpeed = other.sidewaysSpeed;
        agent.upwardSpeed = other.upwardSpeed;
        agent.forwardSpeed = other.forwardSpeed;
        agent.yaw = yaw;
        agent.pitch = pitch;
        agent.posX = other.posX;
        agent.posY = other.posY;
        agent.posZ = other.posZ;
        agent.blockX = other.blockX;
        agent.blockY = other.blockY;
        agent.blockZ = other.blockZ;
        agent.velX = other.velX;
        agent.velY = other.velY;
        agent.velZ = other.velZ;
        agent.mulX = other.mulX;
        agent.mulY = other.mulY;
        agent.mulZ = other.mulZ;
        agent.fluidHeight.put(FluidTags.WATER, other.getFluidHeight(FluidTags.WATER));
        agent.fluidHeight.put(FluidTags.LAVA, other.getFluidHeight(FluidTags.LAVA));
        agent.submergedFluids.addAll(other.submergedFluids);
        agent.firstUpdate = other.firstUpdate;
        agent.dimensions = other.dimensions;
        agent.box = other.box;
        agent.standingEyeHeight = other.standingEyeHeight;
        agent.onGround = other.onGround;
        agent.sleeping = other.sleeping;
        agent.sneaking = other.sneaking;
        agent.sprinting = other.sprinting;
        agent.swimming = other.swimming;
        agent.fallFlying = other.fallFlying;
        agent.stepHeight = other.stepHeight;
        agent.fallDistance = other.fallDistance;
        agent.touchingWater = other.touchingWater;
        agent.isSubmergedInWater = other.isSubmergedInWater;
        agent.horizontalCollision = other.horizontalCollision;
        agent.verticalCollision = other.verticalCollision;
        agent.collidedSoftly = other.collidedSoftly;
        agent.jumping = other.jumping;
        agent.speed = other.speed;
        agent.blindness = other.blindness;
        agent.jumpBoost = other.jumpBoost;
        agent.slowFalling = other.slowFalling;
        agent.dolphinsGrace = other.dolphinsGrace;
        agent.levitation = other.levitation;
        agent.depthStrider = other.depthStrider;
        agent.movementSpeed = other.movementSpeed;
        agent.airStrafingSpeed = other.airStrafingSpeed;
        agent.jumpingCooldown = other.jumpingCooldown;
        return agent;
    }

    public static Agent of(Agent other, AgentInput input) {
    	PathInput pI = input.toPathInput();
    	return of(other, pI);
    }

    public static Agent of(Agent agent, PathInput input) {
        return of(agent, input.forward, input.back, input.left, input.right, input.jump, input.sneak, input.sprint, input.pitch, input.yaw);
    }

}

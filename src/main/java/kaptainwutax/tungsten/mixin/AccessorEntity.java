package kaptainwutax.tungsten.mixin;

import java.util.Set;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;



@Mixin(Entity.class)
public interface AccessorEntity {

	@Accessor
	Vec3 getMovementMultiplier();

	@Accessor
	boolean getFirstUpdate();

	@Accessor
	Set<TagKey<Fluid>> getSubmergedFluidTag();

	@Accessor
	boolean getMinorHorizontalCollision();

	@Accessor
	boolean getHorizontalCollision();

	@Accessor
	boolean getVerticalCollision();

	@Accessor
	double getFallDistance();

	@Accessor
	boolean getFirstTick();
}

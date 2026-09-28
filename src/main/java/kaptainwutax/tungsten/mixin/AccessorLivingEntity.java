package kaptainwutax.tungsten.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.world.entity.LivingEntity.class)
public interface AccessorLivingEntity {

	@Accessor
	boolean getJumping();

	@Accessor
	int getNoJumpDelay();

}

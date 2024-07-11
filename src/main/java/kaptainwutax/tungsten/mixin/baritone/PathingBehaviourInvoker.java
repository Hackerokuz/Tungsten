package kaptainwutax.tungsten.mixin.baritone;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import baritone.bs;
import baritone.bv;
import baritone.h;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import net.minecraft.util.math.BlockPos;

@Mixin(h.class)
public interface PathingBehaviourInvoker {
	@Invoker("a")
    bs createPathfinder(BlockPos var0, Goal var1, IPath var2, bv var3);
}

package kaptainwutax.tungsten.agent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.CollisionGetter;

import java.util.function.Predicate;

public class AgentShapeContext implements CollisionContext {

    protected static final CollisionContext ABSENT = new AgentShapeContext(false, -1.7976931348623157E308, ItemStack.EMPTY, fluidState -> false) {
        @Override
        public boolean isAbove(VoxelShape shape, BlockPos pos, boolean defaultValue) {
            return defaultValue;
        }
    };

    private final boolean descending;
    private final double minY;
    private final ItemStack heldItem;
    private final Predicate<FluidState> walkOnFluidPredicate;

    protected AgentShapeContext(boolean descending, double minY, ItemStack heldItem, Predicate<FluidState> walkOnFluidPredicate) {
        this.descending = descending;
        this.minY = minY;
        this.heldItem = heldItem;
        this.walkOnFluidPredicate = walkOnFluidPredicate;
    }

    protected AgentShapeContext(Agent agent) {
        this(agent.input.playerInput.shift(), agent.box.minY, ItemStack.EMPTY, fluidState -> false);
    }

    @Override
    public boolean isDescending() {
        return this.descending;
    }

    @Override
    public boolean isAbove(VoxelShape shape, BlockPos pos, boolean defaultValue) {
        return this.minY > (double)pos.getY() + shape.max(Direction.Axis.Y) - (double)1.0E-5f;
    }

    @Override
    public boolean isHoldingItem(Item item) {
        return false;
    }

    @Override
    public boolean alwaysCollideWithFluid() {
        return false;
    }

    @Override
    public boolean canStandOnFluid(FluidState fluidStateAbove, FluidState fluid) {
        return false;
    }

    @Override
	public VoxelShape getCollisionShape(BlockState state, CollisionGetter world, BlockPos pos) {
		// TODO Auto-generated method stub
		return null;
	}

    @Override
    public boolean isPlacement() {
        return false;
    }
}

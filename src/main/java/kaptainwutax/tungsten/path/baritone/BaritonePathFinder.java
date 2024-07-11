package kaptainwutax.tungsten.path.baritone;

import baritone.bv;
import baritone.h;
import baritone.api.BaritoneAPI;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.calc.IPathFinder;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.PathCalculationResult;
import kaptainwutax.tungsten.mixin.baritone.PathingBehaviourInvoker;
import net.minecraft.util.math.BlockPos;

public class BaritonePathFinder {
	private static bv calculationContext = null;

    private BaritonePathFinder() {

    }

    public static void initContext() {
        calculationContext = new bv(BaritoneAPI.getProvider().getPrimaryBaritone(),false);
    }

    public static IPath tryToFindPath(BlockPos startPos, Goal goal) {
        IPathingBehavior pathingBehaviour = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();

        IPathFinder pathFinder = null;
        if (pathingBehaviour instanceof h h) {
            pathFinder =((PathingBehaviourInvoker)((Object)h)).createPathfinder(startPos,goal,null,calculationContext);
        }
        if (pathFinder == null) {
            throw new IllegalStateException("baritone pathFinder is null");
        }


        PathCalculationResult calcResult = pathFinder.calculate(BaritoneAPI.getSettings().primaryTimeoutMS.value, BaritoneAPI.getSettings().failureTimeoutMS.value);

        if ((calcResult.getType() == PathCalculationResult.Type.SUCCESS_TO_GOAL || calcResult.getType() == PathCalculationResult.Type.SUCCESS_SEGMENT) && calcResult.getPath().isPresent()) {
            return calcResult.getPath().get();
        }

        return null;
    }
}

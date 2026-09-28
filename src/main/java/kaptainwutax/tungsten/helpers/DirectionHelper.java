package kaptainwutax.tungsten.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Helper class to easily complete direction related calculations.
 */
public class DirectionHelper {

	/**
     * Calculates the yaw from rotation.
     * 
     * @param rotation rotation from Direction::asRotation function
     * @return yaw value calculated from Direction::asRotation function.
     */
//	public static float calcYawFromRotation(Direction direction) {
//		return calcYawFromRotation(direction.asRotation());
//	}
//	
	
	/**
     * Calculates the yaw from rotation.
     * 
     * @param rotation rotation from Direction::asRotation function
     * @return yaw value calculated from Direction::asRotation function.
     */
	public static float calcYawFromRotation(float rotation) {
        float normalized = ((rotation % 360) + 360) % 360;
        
        return normalized;
    }

	/**
     * Calculates the yaw from origin Vec3d to destination Vec3d positions.
     * 
     * @param orig origin position
     * @param dest destination position
     * @return yaw value calculated from origin Vec3d to destination Vec3d positions.
     */
	public static double calcYawFromVec3d(Vec3 orig, Vec3 dest) {
        double[] delta = {orig.x - dest.x, orig.y - dest.y, orig.z - dest.z};
        double yaw = Math.atan2(delta[0], -delta[2]);
        return yaw * 180.0 / Math.PI;
    }
	
	/**
     * Calculates the pitch from origin Vec3d to destination Vec3d positions.
     * 
     * @param orig origin position
     * @param dest destination position
     * @return pitch value calculated from origin Vec3d to destination Vec3d positions.
     */
	public static double calcPitchFromVec3d(Vec3 orig, Vec3 dest) {
	    double deltaY = orig.y - dest.y;
	    double distance = Math.sqrt(Math.pow(orig.x - dest.x, 2) + Math.pow(orig.z - dest.z, 2));
	    double pitch = Math.atan2(deltaY, distance);
	    return pitch * 180.0 / Math.PI;
	}
	

	/**
     * Calculates the horizontal direction from origin Vec3d to destination Vec3d positions.
     * 
     * @param orig origin position
     * @param dest destination position
     * @return horizontal direction calculated from origin Vec3d to destination Vec3d positions.
     */
	public static Direction getHorizontalDirectionFromPos(BlockPos orig, BlockPos dest) {
        return getHorizontalDirectionFromYaw(calcYawFromVec3d(new Vec3(orig.getX(), orig.getY(), orig.getZ()), new Vec3(dest.getX(), dest.getY(), dest.getZ())));
    }
	
	/**
     * Calculates the horizontal direction from origin Vec3d to destination Vec3d positions.
     * 
     * @param orig origin position
     * @param dest destination position
     * @return horizontal direction calculated from origin Vec3d to destination Vec3d positions.
     */
	public static Direction getHorizontalDirectionFromPos(Vec3 orig, Vec3 dest) {
        return getHorizontalDirectionFromYaw(calcYawFromVec3d(orig, dest));
    }
	
	/**
     * Calculates the horizontal direction using the yaw value.
     * 
     * @param yaw value to be used to get a horizontal direction
     * @return horizontal direction calculated from the yaw value provided.
     */
	public static Direction getHorizontalDirectionFromYaw(double yaw) {
        yaw %= 360.0F;
        if (yaw < 0) {
            yaw += 360.0F;
        }

        if ((yaw >= 45 && yaw < 135) || (yaw >= -315 && yaw < -225)) {
            return Direction.WEST;
        } else if ((yaw >= 135 && yaw < 225) || (yaw >= -225 && yaw < -135)) {
            return Direction.NORTH;
        } else if ((yaw >= 225 && yaw < 315) || (yaw >= -135 && yaw < -45)) {
            return Direction.EAST;
        } else {
            return Direction.SOUTH;
        }
    }
}

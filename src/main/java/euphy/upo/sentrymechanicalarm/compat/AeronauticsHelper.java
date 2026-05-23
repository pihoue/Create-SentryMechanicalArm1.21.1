package euphy.upo.sentrymechanicalarm.compat;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Optional Aeronautics (Create: Aeronautics / Valkyrien Skies 2) integration.
 * Uses reflection to detect Aeronautics at runtime - no compile-time dependency.
 * All methods are safe to call regardless of whether Aeronautics is installed.
 */
public class AeronauticsHelper {

    private static Boolean aeronauticsLoaded = null;
    private static Class<?> shipClass;
    private static Class<?> shipDataClass;

    static {
        try {
            shipClass = Class.forName("org.valkyrienskies.core.api.ships.Ship");
            shipDataClass = Class.forName("org.valkyrienskies.core.api.ships.ShipTransform");
            aeronauticsLoaded = true;
            SentryMechanicalArm.LOGGER.info("Aeronautics/Valkyrien Skies detected - enabling ship rotation compensation");
        } catch (ClassNotFoundException e) {
            aeronauticsLoaded = false;
            shipClass = null;
            shipDataClass = null;
        }
    }

    public static boolean isAeronauticsLoaded() {
        return aeronauticsLoaded != null && aeronauticsLoaded;
    }

    /**
     * Get the ship's rotation quaternion for the given level position.
     * Returns null if Aeronautics is not installed or no ship is at this position.
     */
    public static Quaternionf getShipRotation(Level level, Vec3 worldPos) {
        if (!isAeronauticsLoaded() || level == null) return null;
        try {
            Object vsGame = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
                    .getMethod("getShipObjectManagingPos", Level.class, Vec3.class)
                    .invoke(null, level, worldPos);
            if (vsGame == null) return null;

            Object transform = shipClass.getMethod("getTransform").invoke(vsGame);
            if (transform == null) return null;

            double[] quatArray = (double[]) shipDataClass.getMethod("getShipToWorldRotation").invoke(transform);
            if (quatArray == null || quatArray.length < 4) return null;

            return new Quaternionf(
                    (float) quatArray[0], (float) quatArray[1],
                    (float) quatArray[2], (float) quatArray[3]
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the ship's origin position in world space (where the ship's (0,0,0) is simulated).
     * Returns null if Aeronautics is not installed or no ship is at this position.
     */
    public static Vec3 getShipPosition(Level level, Vec3 worldPos) {
        if (!isAeronauticsLoaded() || level == null) return null;
        try {
            Object vsGame = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
                    .getMethod("getShipObjectManagingPos", Level.class, Vec3.class)
                    .invoke(null, level, worldPos);
            if (vsGame == null) return null;

            Object transform = shipClass.getMethod("getTransform").invoke(vsGame);
            if (transform == null) return null;

            Object posInWorld = shipDataClass.getMethod("getPositionInWorld").invoke(transform);
            if (posInWorld == null) return null;

            double x = (double) posInWorld.getClass().getMethod("x").invoke(posInWorld);
            double y = (double) posInWorld.getClass().getMethod("y").invoke(posInWorld);
            double z = (double) posInWorld.getClass().getMethod("z").invoke(posInWorld);

            return new Vec3(x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Aeronautics coordinate conversion: transform a contraption-local position
     * to simulated world space using the ship's current ShipToWorld transform.
     * <p>
     * In Aeronautics, Create's toGlobalVector() uses the assembly-time transform
     * (source position), while the ship's VS2 transform tracks the actual
     * simulated position. This method applies the VS2 ShipToWorld transform
     * directly to the contraption-local coordinate, yielding the correct
     * simulated world position that matches entity positions.
     */
    public static Vec3 localToSimulatedWorld(Level level, Vec3 contraptionLocalPos, Vec3 queryWorldPos) {
        if (!isAeronauticsLoaded() || level == null) return contraptionLocalPos;
        try {
            Object vsGame = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
                    .getMethod("getShipObjectManagingPos", Level.class, Vec3.class)
                    .invoke(null, level, queryWorldPos);
            if (vsGame == null) return contraptionLocalPos;

            Object transform = shipClass.getMethod("getTransform").invoke(vsGame);
            if (transform == null) return contraptionLocalPos;

            double[] quat = (double[]) shipDataClass.getMethod("getShipToWorldRotation").invoke(transform);
            Object posInWorld = shipDataClass.getMethod("getPositionInWorld").invoke(transform);
            if (quat == null || quat.length < 4 || posInWorld == null) return contraptionLocalPos;

            double sx = (double) posInWorld.getClass().getMethod("x").invoke(posInWorld);
            double sy = (double) posInWorld.getClass().getMethod("y").invoke(posInWorld);
            double sz = (double) posInWorld.getClass().getMethod("z").invoke(posInWorld);

            Quaternionf rot = new Quaternionf((float)quat[0], (float)quat[1], (float)quat[2], (float)quat[3]);
            org.joml.Vector3f v = new org.joml.Vector3f(
                    (float)contraptionLocalPos.x, (float)contraptionLocalPos.y, (float)contraptionLocalPos.z);
            rot.transform(v);

            Vec3 result = new Vec3(sx + v.x, sy + v.y, sz + v.z);
            Vec3 sourcePos = new Vec3(
                    queryWorldPos.x + contraptionLocalPos.x,
                    queryWorldPos.y + contraptionLocalPos.y,
                    queryWorldPos.z + contraptionLocalPos.z);
            if (result.distanceTo(sourcePos) > 0.01) {
                SentryMechanicalArm.LOGGER.info("[AeroTransform] localToSimulatedWorld local=({},{},{}) shipOrigin=({},{},{}) result=({},{},{}) rawToGlobal=({},{},{})",
                    String.format("%.1f", contraptionLocalPos.x), String.format("%.1f", contraptionLocalPos.y), String.format("%.1f", contraptionLocalPos.z),
                    String.format("%.1f", sx), String.format("%.1f", sy), String.format("%.1f", sz),
                    String.format("%.1f", result.x), String.format("%.1f", result.y), String.format("%.1f", result.z),
                    String.format("%.1f", sourcePos.x), String.format("%.1f", sourcePos.y), String.format("%.1f", sourcePos.z));
            }
            return result;
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.warn("[AeroTransform] localToSimulatedWorld failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return contraptionLocalPos;
        }
    }

    /**
     * Debug: Find whether a position is managed by a VS2 ship.
     */
    public static boolean isPositionOnShip(Level level, Vec3 worldPos) {
        if (!isAeronauticsLoaded() || level == null) return false;
        try {
            Object vsGame = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
                    .getMethod("getShipObjectManagingPos", Level.class, Vec3.class)
                    .invoke(null, level, worldPos);
            if (vsGame == null) {
                SentryMechanicalArm.LOGGER.info("[AeroTransform] isPositionOnShip NO_SHIP at ({},{},{})", worldPos.x, worldPos.y, worldPos.z);
                return false;
            }
            SentryMechanicalArm.LOGGER.info("[AeroTransform] isPositionOnShip FOUND_SHIP at ({},{},{}) shipClass={}", worldPos.x, worldPos.y, worldPos.z, vsGame.getClass().getName());
            return true;
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.info("[AeroTransform] isPositionOnShip ERROR: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * Convert a world-space aim direction to ship-local space.
     * Returns the original vector if Aeronautics is not available.
     */
    public static Vec3 worldToShipSpace(Level level, Vec3 worldPos, Vec3 worldDirection) {
        if (!isAeronauticsLoaded() || level == null) return worldDirection;
        Quaternionf shipRot = getShipRotation(level, worldPos);
        if (shipRot == null) return worldDirection;

        org.joml.Vector3f dir = new org.joml.Vector3f(
                (float) worldDirection.x, (float) worldDirection.y, (float) worldDirection.z);
        shipRot.conjugate().transform(dir);
        return new Vec3(dir.x, dir.y, dir.z);
    }

    /**
     * Project a sub-level (contraption-local) position to world space.
     * Wraps localToSimulatedWorld for API clarity matching SableCompanion conventions.
     */
    public static Vec3 projectOutOfSubLevel(Level level, Vec3 localPos, Vec3 queryWorldPos) {
        return localToSimulatedWorld(level, localPos, queryWorldPos);
    }

    /**
     * Convert a world-space direction vector to ship-local space for LOS checks.
     * Wraps worldToShipSpace for API clarity matching SableCompanion conventions.
     */
    public static Vec3 toLocalVector(Level level, Vec3 worldPos, Vec3 worldDir) {
        return worldToShipSpace(level, worldPos, worldDir);
    }

    /**
     * Extract yaw offset from ship rotation for rendering compensation.
     */
    public static float getShipYaw(Level level, Vec3 worldPos) {
        if (!isAeronauticsLoaded() || level == null) return 0f;
        Quaternionf rot = getShipRotation(level, worldPos);
        if (rot == null) return 0f;
        org.joml.Vector3f angles = new org.joml.Vector3f();
        rot.getEulerAnglesZYX(angles);
        return (float) Math.toDegrees(angles.y);
    }

    /**
     * Extract roll offset from ship rotation for rendering compensation.
     */
    public static float getShipRoll(Level level, Vec3 worldPos) {
        if (!isAeronauticsLoaded() || level == null) return 0f;
        Quaternionf rot = getShipRotation(level, worldPos);
        if (rot == null) return 0f;
        org.joml.Vector3f angles = new org.joml.Vector3f();
        rot.getEulerAnglesZYX(angles);
        return (float) Math.toDegrees(angles.z);
    }
}

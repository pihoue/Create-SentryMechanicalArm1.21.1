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

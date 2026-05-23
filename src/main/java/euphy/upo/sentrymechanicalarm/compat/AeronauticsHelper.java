package euphy.upo.sentrymechanicalarm.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Optional Aeronautics (Valkyrien Skies 2) + Sable integration.
 * VS2 uses reflection (no compile-time dependency).
 * Sable uses SableCompanion API (compile-time safe dependency, bundled via jarJar).
 */
public class AeronauticsHelper {

    // ---- VS2 / Aeronautics (reflection) ----

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

    // ---- Sable Companion (compile-time API, safe defaults when Sable is absent) ----

    /** Check if a block position is inside a Sable sub-level plot. */
    public static boolean isInSableSubLevel(Level level, BlockPos pos) {
        if (level == null) return false;
        return SableCompanion.INSTANCE.isInPlotGrid(level, pos);
    }

    /** Project a sub-level position to global world space (safe no-op if not in sub-level). */
    public static Vec3 sableSubLevelToWorld(Level level, Vec3 localPos) {
        if (level == null) return localPos;
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, localPos);
    }

    /** Convert a world-space position back to sub-level space using the containing sub-level's inverse pose. */
    public static Vec3 sableWorldToSubLevel(Level level, Vec3 worldPos, BlockPos queryPos) {
        if (level == null) return worldPos;
        SubLevelAccess access = SableCompanion.INSTANCE.getContaining(level, queryPos);
        if (access == null) return worldPos;
        return access.logicalPose().transformPositionInverse(worldPos);
    }

    // ---- VS2 methods (reflection) ----

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
                SentryMechanicalArm.LOGGER.info("[AeroTransform] localToSimulatedWorld ...");
            }
            return result;
        } catch (Exception e) {
            return contraptionLocalPos;
        }
    }

    public static boolean isPositionOnShip(Level level, Vec3 worldPos) {
        if (!isAeronauticsLoaded() || level == null) return false;
        try {
            Object vsGame = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
                    .getMethod("getShipObjectManagingPos", Level.class, Vec3.class)
                    .invoke(null, level, worldPos);
            return vsGame != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static Vec3 worldToShipSpace(Level level, Vec3 worldPos, Vec3 worldDirection) {
        if (!isAeronauticsLoaded() || level == null) return worldDirection;
        Quaternionf shipRot = getShipRotation(level, worldPos);
        if (shipRot == null) return worldDirection;
        org.joml.Vector3f dir = new org.joml.Vector3f(
                (float) worldDirection.x, (float) worldDirection.y, (float) worldDirection.z);
        shipRot.conjugate().transform(dir);
        return new Vec3(dir.x, dir.y, dir.z);
    }

    public static Vec3 projectOutOfSubLevel(Level level, Vec3 localPos, Vec3 queryWorldPos) {
        return localToSimulatedWorld(level, localPos, queryWorldPos);
    }

    public static Vec3 toLocalVector(Level level, Vec3 worldPos, Vec3 worldDir) {
        return worldToShipSpace(level, worldPos, worldDir);
    }

    public static float getShipYaw(Level level, Vec3 worldPos) {
        if (!isAeronauticsLoaded() || level == null) return 0f;
        Quaternionf rot = getShipRotation(level, worldPos);
        if (rot == null) return 0f;
        org.joml.Vector3f angles = new org.joml.Vector3f();
        rot.getEulerAnglesZYX(angles);
        return (float) Math.toDegrees(angles.y);
    }

    public static float getShipRoll(Level level, Vec3 worldPos) {
        if (!isAeronauticsLoaded() || level == null) return 0f;
        Quaternionf rot = getShipRotation(level, worldPos);
        if (rot == null) return 0f;
        org.joml.Vector3f angles = new org.joml.Vector3f();
        rot.getEulerAnglesZYX(angles);
        return (float) Math.toDegrees(angles.z);
    }
}

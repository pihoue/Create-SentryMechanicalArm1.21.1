package euphy.upo.sentrymechanicalarm.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * Valkyrien Skies implementation - only loaded when VS is present
 * DO NOT reference this class directly! Use VSCompat instead.
 */
class VSCompatImpl {

    static Vec3 transformShipToWorld(Level level, BlockPos blockPos, Vec3 shipLocalPos) {
        LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, blockPos);
        if (ship != null) {
            return VectorConversionsMCKt.toMinecraft(
                ship.getTransform().getShipToWorld().transformPosition(
                    VectorConversionsMCKt.toJOML(shipLocalPos)
                )
            );
        }
        return shipLocalPos;
    }

    static float getShipYaw(Level level, BlockPos blockPos) {
        LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, blockPos);
        if (ship == null) {
            return 0f;
        }

        org.joml.Quaterniondc shipRotation = ship.getTransform().getShipToWorldRotation();

        // Convert quaternion to euler yaw
        double shipYawRadians = Math.atan2(
            2.0 * (shipRotation.w() * shipRotation.y() + shipRotation.x() * shipRotation.z()),
            1.0 - 2.0 * (shipRotation.y() * shipRotation.y() + shipRotation.z() * shipRotation.z())
        );

        return (float) Math.toDegrees(shipYawRadians);
    }
}

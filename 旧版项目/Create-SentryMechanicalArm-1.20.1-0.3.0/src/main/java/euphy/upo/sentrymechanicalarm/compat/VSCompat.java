package euphy.upo.sentrymechanicalarm.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * Valkyrien Skies compatibility layer
 * Handles coordinate transformations when VS is installed
 */
public class VSCompat {

    private static final boolean VS_LOADED = ModList.get().isLoaded("valkyrienskies");

    public static boolean isVSLoaded() {
        return VS_LOADED;
    }

    /**
     * Transform ship-local position to world position
     * Returns the input position unchanged if VS is not loaded
     */
    public static Vec3 transformShipToWorld(Level level, BlockPos blockPos, Vec3 shipLocalPos) {
        if (!VS_LOADED) {
            return shipLocalPos;
        }

        // Delegate to VS-specific implementation
        return VSCompatImpl.transformShipToWorld(level, blockPos, shipLocalPos);
    }

    /**
     * Get ship's yaw rotation in degrees
     * Returns 0 if VS is not loaded or block is not on a ship
     */
    public static float getShipYaw(Level level, BlockPos blockPos) {
        if (!VS_LOADED) {
            return 0f;
        }

        // Delegate to VS-specific implementation
        return VSCompatImpl.getShipYaw(level, blockPos);
    }
}

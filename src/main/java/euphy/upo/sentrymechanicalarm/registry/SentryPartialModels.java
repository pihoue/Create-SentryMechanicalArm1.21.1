package euphy.upo.sentrymechanicalarm.registry;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.resources.ResourceLocation;

public class SentryPartialModels {

    public static final PartialModel BLAZE_FIRE_CONTROLLER_HEAD = block("blaze_fire_controller_head");
    public static final PartialModel RING = block("ring");
    public static final PartialModel CLIPBOARD = block("clipboard");
    public static final PartialModel BASE = block("blaze_fire_control");
    public static final PartialModel SENTRU_BASE = block("sentry_base");
    public static final PartialModel SENTRU_COG = block("sentry_cog");

    private static PartialModel block(String path) {
        return PartialModel.of(
                ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID, "block/" + path)
        );
    }
    public static void init() {}
}
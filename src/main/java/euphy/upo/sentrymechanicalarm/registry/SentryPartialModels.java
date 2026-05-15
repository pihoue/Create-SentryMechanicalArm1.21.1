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
    public static final PartialModel ARM_LOWER_BODY = block("sentry_arm_lower_body");
    public static final PartialModel ARM_UPPER_BODY = block("sentry_arm_upper_body");
    public static final PartialModel ARM_CLAW_BASE = block("sentry_arm_claw_base");
    public static final PartialModel ARM_CLAW_GRIP_UPPER = block("sentry_arm_claw_grip_upper");
    public static final PartialModel ARM_CLAW_GRIP_LOWER = block("sentry_arm_claw_grip_lower");

    private static PartialModel block(String path) {
        return PartialModel.of(
                ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID, "block/" + path)
        );
    }
    public static void init() {}
}
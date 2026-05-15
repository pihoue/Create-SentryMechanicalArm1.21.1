package euphy.upo.sentrymechanicalarm.ponder;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class SMAPonderTags {

    public static final ResourceLocation SENTRY_TAG_ID =
            ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID, "sentry");

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(SENTRY_TAG_ID)
                .addToIndex()
                .item(SentryRegistry.SENTRY_ARM_BLOCK.get())
                .register();

        helper.addToTag(SENTRY_TAG_ID)
                .add(SentryRegistry.SENTRY_ARM_BLOCK.getId())
                .add(SentryRegistry.BLAZE_FIRE_CONTROL.getId())
                .add(SentryRegistry.FIRE_CONTROL_CLIPBOARD.getId())
                .add(SentryRegistry.APPLE_PIE.getId());
    }
}

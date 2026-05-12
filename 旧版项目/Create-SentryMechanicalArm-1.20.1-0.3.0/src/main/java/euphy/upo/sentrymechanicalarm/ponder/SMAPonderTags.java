package euphy.upo.sentrymechanicalarm.ponder;

import com.tterrag.registrate.util.entry.RegistryEntry;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class SMAPonderTags {

    public static final ResourceLocation SENTRY_TAG_ID =
            new ResourceLocation(SentryMechanicalArm.MODID, "sentry");

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {

        helper.registerTag(SENTRY_TAG_ID)
                .addToIndex()
                .item(SentryRegistry.SENTRY_ARM_BLOCK.get())
                .register();

        PonderTagRegistrationHelper<RegistryEntry<?>> ENTRY_HELPER = helper.withKeyFunction(RegistryEntry::getId);
        ENTRY_HELPER.addToTag(SENTRY_TAG_ID)
                .add(SentryRegistry.SENTRY_ARM_BLOCK)
                .add(SentryRegistry.BLAZE_FIRE_CONTROL)
                .add(SentryRegistry.FIRE_CONTROL_CLIPBOARD)
                .add(SentryRegistry.APPLE_PIE);;
    }
}

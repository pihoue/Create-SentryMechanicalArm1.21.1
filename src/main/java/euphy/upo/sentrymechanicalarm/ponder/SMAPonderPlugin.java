package euphy.upo.sentrymechanicalarm.ponder;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;

public class SMAPonderPlugin implements PonderPlugin {

    public static void register() {
        PonderIndex.addPlugin(new SMAPonderPlugin());
    }

    @Override
    public String getModId() {
        return SentryMechanicalArm.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(SentryRegistry.SENTRY_ARM_BLOCK.getId())
                .addStoryBoard("sentry_arm_intro", SMAPonderScenes::sentryArmIntro);

        helper.forComponents(SentryRegistry.BLAZE_FIRE_CONTROL.getId())
                .addStoryBoard("fire_control_intro", SMAPonderScenes::fireControlIntro);
    }
}
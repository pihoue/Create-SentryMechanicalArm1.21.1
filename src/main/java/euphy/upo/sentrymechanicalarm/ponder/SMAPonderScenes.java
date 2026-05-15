package euphy.upo.sentrymechanicalarm.ponder;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.ponder.scene.FireControlScene;
import euphy.upo.sentrymechanicalarm.ponder.scene.SentryMechanicalArmScene;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class SMAPonderScenes {

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(SentryRegistry.SENTRY_ARM_BLOCK.getId())
                .addStoryBoard("introduce", SentryMechanicalArmScene::introducing, AllCreatePonderTags.KINETIC_APPLIANCES)
                .addStoryBoard("supply", SentryMechanicalArmScene::supplying, AllCreatePonderTags.KINETIC_APPLIANCES)
                .addStoryBoard("ammo", SentryMechanicalArmScene::ammo, AllCreatePonderTags.KINETIC_APPLIANCES)
                .addStoryBoard("movement", SentryMechanicalArmScene::movement, AllCreatePonderTags.KINETIC_APPLIANCES);

        helper.forComponents(SentryRegistry.BLAZE_FIRE_CONTROL.getId())
                .addStoryBoard("fire_control", FireControlScene::control, AllCreatePonderTags.DECORATION);
    }
}

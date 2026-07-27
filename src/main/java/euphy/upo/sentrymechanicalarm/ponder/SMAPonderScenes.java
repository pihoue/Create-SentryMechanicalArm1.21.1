package euphy.upo.sentrymechanicalarm.ponder;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import euphy.upo.sentrymechanicalarm.ponder.scene.FireControlScene;
import euphy.upo.sentrymechanicalarm.ponder.scene.SentryMechanicalArmScene;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class SMAPonderScenes {
   public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
      helper.forComponents(new ResourceLocation[]{SentryRegistry.SENTRY_ARM_BLOCK.getId()})
         .addStoryBoard("introduce", SentryMechanicalArmScene::introducing, new ResourceLocation[]{AllCreatePonderTags.KINETIC_APPLIANCES})
         .addStoryBoard("supply", SentryMechanicalArmScene::supplying, new ResourceLocation[]{AllCreatePonderTags.KINETIC_APPLIANCES})
         .addStoryBoard("ammo", SentryMechanicalArmScene::ammo, new ResourceLocation[]{AllCreatePonderTags.KINETIC_APPLIANCES})
         .addStoryBoard("movement", SentryMechanicalArmScene::movement, new ResourceLocation[]{AllCreatePonderTags.KINETIC_APPLIANCES});
      helper.forComponents(new ResourceLocation[]{SentryRegistry.BLAZE_FIRE_CONTROL.getId()})
         .addStoryBoard("fire_control", FireControlScene::control, new ResourceLocation[]{AllCreatePonderTags.DECORATION});
   }
}

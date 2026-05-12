package euphy.upo.sentrymechanicalarm.ponder;

import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.ponder.scene.SentryMechanicalArmScene;
import euphy.upo.sentrymechanicalarm.ponder.scene.FireControlScene;

import static com.simibubi.create.infrastructure.ponder.AllCreatePonderTags.DECORATION;
import static com.simibubi.create.infrastructure.ponder.AllCreatePonderTags.KINETIC_APPLIANCES;


public class SMAPonderScenes {

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<RegistryEntry<?>> ENTRY_HELPER = helper.withKeyFunction(RegistryEntry::getId);

        ENTRY_HELPER.forComponents(SentryRegistry.SENTRY_ARM_BLOCK)
                .addStoryBoard("introduce", SentryMechanicalArmScene::introducing, KINETIC_APPLIANCES)
                .addStoryBoard("supply", SentryMechanicalArmScene::supplying, KINETIC_APPLIANCES)
                .addStoryBoard("ammo", SentryMechanicalArmScene::ammo, KINETIC_APPLIANCES)
                .addStoryBoard("movement", SentryMechanicalArmScene::movement, KINETIC_APPLIANCES);;

        ENTRY_HELPER.forComponents(SentryRegistry.BLAZE_FIRE_CONTROL)
                .addStoryBoard("fire_control", FireControlScene::control, DECORATION);
    }
}

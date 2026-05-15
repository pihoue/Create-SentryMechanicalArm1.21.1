package euphy.upo.sentrymechanicalarm.ponder;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class SMAPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return SentryMechanicalArm.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        SMAPonderScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        SMAPonderTags.register(helper);
    }
}

package euphy.upo.sentrymechanicalarm.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class SMAPonderPlugin implements PonderPlugin {
   public String getModId() {
      return "sentrymechanicalarm";
   }

   public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
      SMAPonderScenes.register(helper);
   }

   public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
      SMAPonderTags.register(helper);
   }
}

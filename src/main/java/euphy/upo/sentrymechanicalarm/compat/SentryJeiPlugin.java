package euphy.upo.sentrymechanicalarm.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@JeiPlugin
public class SentryJeiPlugin implements IModPlugin {
   private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "jei_plugin");

   public ResourceLocation getPluginUid() {
      return PLUGIN_ID;
   }

   public void registerRecipes(IRecipeRegistration registration) {
   }
}

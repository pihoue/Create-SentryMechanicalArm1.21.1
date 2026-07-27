package euphy.upo.sentrymechanicalarm.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class SentryMixinConfigPlugin implements IMixinConfigPlugin {
   private static final String APPLE_SKIN_MIXIN = "euphy.upo.sentrymechanicalarm.mixin.AppleSkinCompatMixin";

   public void onLoad(String mixinPackage) {
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      return "euphy.upo.sentrymechanicalarm.mixin.AppleSkinCompatMixin".equals(mixinClassName)
         ? FMLLoader.getLoadingModList().getModFileById("appleskin") != null
         : true;
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}

package euphy.upo.sentrymechanicalarm.client;

import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.item.ItemDescription.Modifier;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class SMATooltips {
   public static void init() {
      register(SentryRegistry.FIRE_CONTROL_CLIPBOARD.getId(), (Item)SentryRegistry.FIRE_CONTROL_CLIPBOARD.get());
      register(SentryRegistry.SENTRY_SCOPE.getId(), (Item)SentryRegistry.SENTRY_SCOPE.get());
   }

   private static void register(ResourceLocation id, Item item) {
      TooltipModifier.REGISTRY.register(item, new Modifier(item, Palette.STANDARD_CREATE));
   }
}

package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities.ItemHandler;

@EventBusSubscriber(
   modid = "sentrymechanicalarm"
)
public class CapabilityEvents {
   @SubscribeEvent
   public static void registerCapabilities(RegisterCapabilitiesEvent event) {
      event.registerBlockEntity(ItemHandler.BLOCK, SentryRegistry.BLAZE_FIRE_CONTROL_BE.get(), (be, side) -> be.getItemHandler());
      event.registerBlockEntity(ItemHandler.BLOCK, SentryRegistry.SENTRY_ARM_BE.get(), (be, side) -> be.getItemHandler());
   }
}

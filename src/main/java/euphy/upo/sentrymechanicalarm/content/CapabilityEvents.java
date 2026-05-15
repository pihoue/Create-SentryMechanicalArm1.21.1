package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class CapabilityEvents {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SentryRegistry.BLAZE_FIRE_CONTROL_BE.get(),
                (be, side) -> ((BlazeFireControlBlockEntity) be).getItemHandler()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SentryRegistry.SENTRY_ARM_BE.get(),
                (be, side) -> ((SentryArmBlockEntity) be).getItemHandler()
        );
    }
}
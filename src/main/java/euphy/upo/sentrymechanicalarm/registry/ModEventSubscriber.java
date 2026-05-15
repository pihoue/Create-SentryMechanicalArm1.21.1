package euphy.upo.sentrymechanicalarm.registry;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class ModEventSubscriber {

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        SentryMechanicalArm.LOGGER.info("BuildCreativeModeTabContentsEvent fired for tab: {}", event.getTabKey());

        ResourceLocation tabKey = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(SentryRegistry.SENTRY_TAB.get());
        if (event.getTabKey().equals(tabKey)) {
            SentryMechanicalArm.LOGGER.info("=== CreativeTab Build START (our tab) ===");

            ItemStack stack1 = new ItemStack(SentryRegistry.SENTRY_ARM_BLOCK.get(), 1);
            event.accept(stack1);

            ItemStack stack2 = new ItemStack(SentryRegistry.BLAZE_FIRE_CONTROL.get(), 1);
            event.accept(stack2);

            ItemStack stack3 = new ItemStack(SentryRegistry.FIRE_CONTROL_CLIPBOARD.get(), 1);
            event.accept(stack3);

            ItemStack stack4 = new ItemStack(SentryRegistry.APPLE_PIE.get(), 1);
            event.accept(stack4);

            SentryMechanicalArm.LOGGER.info("=== CreativeTab Build END ===");
        }

        if (event.getTabKey().toString().equals("minecraft:tools")) {
            SentryMechanicalArm.LOGGER.info("=== Adding to TOOLS tab ===");
            ItemStack stack = new ItemStack(SentryRegistry.SENTRY_ARM_BLOCK.get(), 1);
            event.accept(stack);
        }
    }
}

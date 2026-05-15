package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlRenderer;
import euphy.upo.sentrymechanicalarm.content.SentryArmRenderer;
import euphy.upo.sentrymechanicalarm.ponder.SMAPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentrySpriteShifts;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID, value = Dist.CLIENT)
public class SentryMechanicalArmClient {
    private static final Logger LOGGER = SentryMechanicalArm.LOGGER;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SentryPartialModels.init();
        LOGGER.info("SentryPartialModels initialized");

        event.enqueueWork(() -> {
            SentrySpriteShifts.init();
            SMATooltips.init();
            PonderIndex.addPlugin(new SMAPonderPlugin());
            LOGGER.info("Client setup completed, BlockEntityType: {}", SentryRegistry.SENTRY_ARM_BE.get());
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SentryRegistry.SENTRY_ARM_BE.get(), SentryArmRenderer::new);
        event.registerBlockEntityRenderer(SentryRegistry.BLAZE_FIRE_CONTROL_BE.get(), BlazeFireControlRenderer::new);
        LOGGER.info("SentryArmRenderer registered for SENTRY_ARM_BE");
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(SentryRegistry.FIRE_CONTROL_MENU.get(), FireControlScreen::new);
    }
}
package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentrySpriteShifts;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SentryMechanicalArmClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SentryPartialModels.init();

        event.enqueueWork(() -> {
            SentrySpriteShifts.init();
            SMATooltips.init();
        });
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(SentryRegistry.FIRE_CONTROL_MENU.get(), FireControlScreen::new);
    }
}
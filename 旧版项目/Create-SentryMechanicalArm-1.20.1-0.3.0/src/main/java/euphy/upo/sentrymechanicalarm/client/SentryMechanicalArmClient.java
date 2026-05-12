package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.ponder.SMAPonderPlugin;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentrySpriteShifts;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;


@Mod.EventBusSubscriber(modid = SentryMechanicalArm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SentryMechanicalArmClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SentryPartialModels.init();

        event.enqueueWork(() -> {
            SentrySpriteShifts.init();
            SMATooltips.init();
            PonderIndex.addPlugin(new SMAPonderPlugin());
            MenuScreens.register(SentryRegistry.FIRE_CONTROL_MENU.get(), FireControlScreen::new);
        });

    }

}

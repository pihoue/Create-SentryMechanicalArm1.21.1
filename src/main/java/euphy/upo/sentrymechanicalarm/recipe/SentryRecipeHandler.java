package euphy.upo.sentrymechanicalarm.recipe;

import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class SentryRecipeHandler {

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        // DISABLED: This approach had compilation issues
        // The DynamicRecipeManager via ServerAboutToStartEvent already handles recipe injection
        SentryMechanicalArm.LOGGER.info("SentryRecipeHandler: AddReloadListenerEvent fired (but injection handled by DynamicRecipeManager)");
    }
}
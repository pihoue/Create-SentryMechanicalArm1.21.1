package euphy.upo.sentrymechanicalarm.recipe;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(
   modid = "sentrymechanicalarm"
)
public class SentryRecipeHandler {
   @SubscribeEvent
   public static void onAddReloadListener(AddReloadListenerEvent event) {
      SentryMechanicalArm.LOGGER.info("SentryRecipeHandler: AddReloadListenerEvent fired (but injection handled by DynamicRecipeManager)");
   }
}

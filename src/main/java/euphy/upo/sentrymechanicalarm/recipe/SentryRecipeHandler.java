package euphy.upo.sentrymechanicalarm.recipe;

import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public class SentryRecipeHandler {

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener((preparationBarrier, resourceManager, profilerFiller, profilerFiller1, executor, executor1) ->
                preparationBarrier.wait(null).thenRunAsync(() -> {}, executor1)
        );
    }
}
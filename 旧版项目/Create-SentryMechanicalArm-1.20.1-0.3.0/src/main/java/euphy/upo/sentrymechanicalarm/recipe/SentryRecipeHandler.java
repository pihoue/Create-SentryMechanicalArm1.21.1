package euphy.upo.sentrymechanicalarm.recipe;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.recipe.DynamicRecipeManager;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class SentryRecipeHandler {

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener((preparationBarrier, resourceManager, profilerFiller, profilerFiller1, executor, executor1) ->
                preparationBarrier.wait(null).thenRunAsync(() -> {}, executor1)
        );
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        RecipeManager recipeManager = event.getServer().getRecipeManager();
        DynamicRecipeManager.injectCuttingRecipes(recipeManager);
    }
}
package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class DynamicRecipeManager {

    @SubscribeEvent
    public static void onResourceReload(AddReloadListenerEvent event) {
        event.addListener((preparationBarrier, resourceManager, profilerFiller, profilerFiller1, executor, executor1) ->
                preparationBarrier.wait(null).thenRunAsync(() -> {
                }, executor1)
        );
    }

    public static void injectCuttingRecipes(RecipeManager recipeManager) {
        var ammoEntries = TimelessAPI.getAllCommonAmmoIndex();
        if (ammoEntries.isEmpty()) return;

        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes = new HashMap<>();

        for (var entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            injectSingleCuttingRecipe(recipeManager, ammoId);
        }

    }

    private static void injectSingleCuttingRecipe(RecipeManager recipeManager, ResourceLocation ammoId) {
        String path = "dynamic_cutting/" + ammoId.getNamespace() + "/" + ammoId.getPath().replace("/", "_");
        ResourceLocation recipeId = new ResourceLocation(SentryMechanicalArm.MODID, path);

        ItemStack output = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
        CompoundTag tag = output.getOrCreateTag();
        tag.putString("AmmoId", ammoId.toString());

        CuttingRecipe recipe = new ProcessingRecipeBuilder<>(CuttingRecipe::new, recipeId)
                .withItemIngredients(Ingredient.of(AllItems.COPPER_SHEET.get()))
                .output(output)
                .duration(50)
                .build();

        addRecipeToManager(recipeManager, recipe);
    }

    private static void addRecipeToManager(RecipeManager manager, Recipe<?> recipe) {
        try {
            Field field = RecipeManager.class.getDeclaredField("recipes");
            field.setAccessible(true);
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipesMap = (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) field.get(manager);

            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> modifiableMap = new HashMap<>(recipesMap);
            Map<ResourceLocation, Recipe<?>> typeRecipes = new HashMap<>(modifiableMap.getOrDefault(recipe.getType(), Map.of()));

            typeRecipes.put(recipe.getId(), recipe);
            modifiableMap.put(recipe.getType(), typeRecipes);

            field.set(manager, modifiableMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
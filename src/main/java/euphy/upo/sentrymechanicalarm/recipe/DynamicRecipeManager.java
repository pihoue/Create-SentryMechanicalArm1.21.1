package euphy.upo.sentrymechanicalarm.recipe;

import com.google.common.collect.Multimap;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.tacz.guns.api.TimelessAPI;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.UnfinishedAmmoItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class DynamicRecipeManager {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        RecipeManager recipeManager = event.getServer().getRecipeManager();
        injectCuttingRecipes(recipeManager);
        injectSequencedAssemblyRecipes(recipeManager);
        injectPressingRecipes(recipeManager);
        verifyRecipes(recipeManager);
    }

    public static void injectCuttingRecipes(RecipeManager recipeManager) {
        var ammoEntries = TimelessAPI.getAllCommonAmmoIndex();
        if (ammoEntries.isEmpty()) return;

        List<RecipeHolder<?>> newRecipes = new ArrayList<>();
        for (var entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            String path = ammoId.getPath().replace("/", "_");
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID, "ammo_cutting/" + path);

            ItemStack output = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag tag = new CompoundTag();
            tag.putString("AmmoId", ammoId.toString());
            output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            CuttingRecipe recipe = new StandardProcessingRecipe.Builder<>(CuttingRecipe::new, recipeId)
                    .withItemIngredients(Ingredient.of(AllItems.COPPER_SHEET.get()))
                    .withSingleItemOutput(output)
                    .duration(50)
                    .build();

            newRecipes.add(new RecipeHolder<>(recipeId, recipe));
        }

        if (!newRecipes.isEmpty()) {
            injectRecipes(recipeManager, newRecipes);
        }
    }

    public static void injectSequencedAssemblyRecipes(RecipeManager recipeManager) {
        var ammoEntries = TimelessAPI.getAllCommonAmmoIndex();
        if (ammoEntries.isEmpty()) {
            SentryMechanicalArm.LOGGER.warn("No ammo entries found from TimelessAPI");
            return;
        }

        Item taczAmmoItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tacz", "ammo"));
        if (taczAmmoItem == null) {
            SentryMechanicalArm.LOGGER.error("Failed to find TacZ ammo item");
            return;
        }

        SentryMechanicalArm.LOGGER.info("Injecting {} sequenced assembly recipes for ammo", ammoEntries.size());

        List<RecipeHolder<?>> newRecipes = new ArrayList<>();
        for (var entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            String path = ammoId.getPath().replace("/", "_");
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID, "ammo_assembly/" + path);

            ItemStack inputUnfinished = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag inputTag = new CompoundTag();
            inputTag.putString("AmmoId", ammoId.toString());
            inputUnfinished.set(DataComponents.CUSTOM_DATA, CustomData.of(inputTag));

            ItemStack outputAmmo = new ItemStack(taczAmmoItem);
            CompoundTag outputTag = new CompoundTag();
            outputTag.putString("AmmoId", ammoId.toString());
            outputAmmo.set(DataComponents.CUSTOM_DATA, CustomData.of(outputTag));

            SequencedAssemblyRecipeBuilder builder = new SequencedAssemblyRecipeBuilder(recipeId);
            builder.require(Ingredient.of(inputUnfinished))
                    .transitionTo(SentryRegistry.UNFINISHED_AMMO.get())
                    .addOutput(outputAmmo, 1)
                    .loops(1)
                    .addStep(com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe::new, rb -> rb.require(AllItems.COPPER_SHEET.get()))
                    .addStep(com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe::new, rb -> rb.require(AllItems.COPPER_SHEET.get()))
                    .addStep(com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe::new, rb -> rb.require(AllItems.COPPER_SHEET.get()))
                    .addStep(com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe::new, rb -> rb.require(Items.GUNPOWDER))
                    .addStep(PressingRecipe::new, rb -> rb);

            newRecipes.add(builder.build());
        }

        if (!newRecipes.isEmpty()) {
            injectRecipes(recipeManager, newRecipes);
        }
    }

    public static void injectPressingRecipes(RecipeManager recipeManager) {
        var ammoEntries = TimelessAPI.getAllCommonAmmoIndex();
        if (ammoEntries.isEmpty()) return;

        Item taczAmmoItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tacz", "ammo"));
        if (taczAmmoItem == null) return;

        List<RecipeHolder<?>> newRecipes = new ArrayList<>();
        for (var entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            String path = ammoId.getPath().replace("/", "_");
            ResourceLocation pressRecipeId = ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID, "ammo_pressing/" + path);

            ItemStack inputComplete = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag inputTag = new CompoundTag();
            inputTag.putString("AmmoId", ammoId.toString());
            inputTag.putInt("CopperSheets", UnfinishedAmmoItem.MAX_COPPER_SHEETS);
            inputTag.putBoolean("GunpowderAdded", true);
            inputComplete.set(DataComponents.CUSTOM_DATA, CustomData.of(inputTag));

            ItemStack outputAmmo = new ItemStack(taczAmmoItem);
            CompoundTag outputTag = new CompoundTag();
            outputTag.putString("AmmoId", ammoId.toString());
            outputAmmo.set(DataComponents.CUSTOM_DATA, CustomData.of(outputTag));

            PressingRecipe recipe = new StandardProcessingRecipe.Builder<>(PressingRecipe::new, pressRecipeId)
                    .withItemIngredients(Ingredient.of(inputComplete))
                    .withSingleItemOutput(outputAmmo)
                    .duration(100)
                    .build();

            newRecipes.add(new RecipeHolder<>(pressRecipeId, recipe));
        }

        if (!newRecipes.isEmpty()) {
            injectRecipes(recipeManager, newRecipes);
        }
    }

    private static void injectRecipes(RecipeManager recipeManager, List<RecipeHolder<?>> newRecipes) {
        try {
            Field byTypeField = RecipeManager.class.getDeclaredField("byType");
            byTypeField.setAccessible(true);
            Multimap<RecipeType<?>, RecipeHolder<?>> byType =
                    (Multimap<RecipeType<?>, RecipeHolder<?>>) byTypeField.get(recipeManager);

            Collection<RecipeHolder<?>> allRecipes = new ArrayList<>(byType.values());
            allRecipes.addAll(newRecipes);
            recipeManager.replaceRecipes(allRecipes);

            SentryMechanicalArm.LOGGER.info("DynamicRecipeManager: Successfully injected {} recipes", newRecipes.size());
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.error("DynamicRecipeManager: Failed to inject recipes", e);
        }
    }

    private static void verifyRecipes(RecipeManager recipeManager) {
        int ourRecipes = 0;
        try {
            Field byTypeField = RecipeManager.class.getDeclaredField("byType");
            byTypeField.setAccessible(true);
            Multimap<RecipeType<?>, RecipeHolder<?>> byType =
                    (Multimap<RecipeType<?>, RecipeHolder<?>>) byTypeField.get(recipeManager);
            for (RecipeType<?> type : byType.keySet()) {
                for (RecipeHolder<?> holder : byType.get(type)) {
                    if (holder.id().getPath().startsWith("ammo_assembly")) {
                        ourRecipes++;
                    }
                }
            }
            SentryMechanicalArm.LOGGER.info("DynamicRecipeManager: Found {} our recipes in RecipeManager", ourRecipes);
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.error("DynamicRecipeManager: Failed to verify recipes", e);
        }
    }

}
package euphy.upo.sentrymechanicalarm.recipe;

import com.google.common.collect.Multimap;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
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

    private static Item taczAmmoItem;

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        RecipeManager recipeManager = event.getServer().getRecipeManager();

        taczAmmoItem = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("tacz", "ammo"));
        if (taczAmmoItem == null) {
            SentryMechanicalArm.LOGGER.error("Failed to find TacZ ammo item, aborting recipe injection");
            return;
        }

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
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                    SentryMechanicalArm.MODID, "ammo_cutting/" + path);

            ItemStack output = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag tag = new CompoundTag();
            tag.putString("AmmoId", ammoId.toString());
            tag.putInt("CopperSheets", 0);
            tag.putBoolean("GunpowderAdded", false);
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

        SentryMechanicalArm.LOGGER.info("Injecting {} sequenced assembly recipes", ammoEntries.size());

        List<RecipeHolder<?>> newRecipes = new ArrayList<>();
        for (var entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            AmmoRecipeConfig.Config config = getOrCreateConfig(ammoId, recipeManager);
            String path = ammoId.getPath().replace("/", "_");
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                    SentryMechanicalArm.MODID, "ammo_assembly/" + path);

            ItemStack inputUnfinished = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag inputTag = new CompoundTag();
            inputTag.putString("AmmoId", ammoId.toString());
            inputUnfinished.set(DataComponents.CUSTOM_DATA, CustomData.of(inputTag));

            ItemStack outputAmmo = new ItemStack(taczAmmoItem, config.outputCount());
            CompoundTag outputTag = new CompoundTag();
            outputTag.putString("AmmoId", ammoId.toString());
            outputAmmo.set(DataComponents.CUSTOM_DATA, CustomData.of(outputTag));

            SequencedAssemblyRecipeBuilder builder =
                    new SequencedAssemblyRecipeBuilder(recipeId);
            builder.require(Ingredient.of(inputUnfinished))
                    .transitionTo(SentryRegistry.UNFINISHED_AMMO.get())
                    .addOutput(outputAmmo, 1)
                    .loops(1);

            for (Item stepItem : config.assemblySteps()) {
                if (stepItem == Items.GUNPOWDER) {
                    builder.addStep(DeployerApplicationRecipe::new,
                            rb -> rb.require(Items.GUNPOWDER));
                } else {
                    builder.addStep(DeployerApplicationRecipe::new,
                            rb -> rb.require(stepItem));
                }
            }

            builder.addStep(PressingRecipe::new, rb -> rb);

            newRecipes.add(builder.build());
        }

        if (!newRecipes.isEmpty()) {
            injectRecipes(recipeManager, newRecipes);
        }
    }

    public static void injectPressingRecipes(RecipeManager recipeManager) {
        var ammoEntries = TimelessAPI.getAllCommonAmmoIndex();
        if (ammoEntries.isEmpty()) return;

        List<RecipeHolder<?>> newRecipes = new ArrayList<>();
        for (var entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            AmmoRecipeConfig.Config config = getOrCreateConfig(ammoId, recipeManager);
            String path = ammoId.getPath().replace("/", "_");
            ResourceLocation pressRecipeId = ResourceLocation.fromNamespaceAndPath(
                    SentryMechanicalArm.MODID, "ammo_pressing/" + path);

            ItemStack inputComplete = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag inputTag = new CompoundTag();
            inputTag.putString("AmmoId", ammoId.toString());
            long materialSteps = config.assemblySteps().stream()
                    .filter(item -> item != Items.GUNPOWDER)
                    .count();
            inputTag.putInt("CopperSheets", (int) materialSteps);
            inputTag.putBoolean("GunpowderAdded", true);
            inputComplete.set(DataComponents.CUSTOM_DATA, CustomData.of(inputTag));

            ItemStack outputAmmo = new ItemStack(taczAmmoItem, config.outputCount());
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

    private static AmmoRecipeConfig.Config getOrCreateConfig(ResourceLocation ammoId, RecipeManager recipeManager) {
        GunSmithTableRecipe recipe = findTaCZRecipe(ammoId, recipeManager);
        if (recipe != null) {
            AmmoRecipeConfig.Config fromRecipe = buildConfigFromRecipe(ammoId, recipe);
            if (fromRecipe != null) return fromRecipe;
        }

        AmmoRecipeConfig.Config fromOverride = AmmoRecipeConfig.getOverride(ammoId);
        if (fromOverride != null) return fromOverride;

        return AmmoRecipeConfig.getCategoryDefault(ammoId);
    }

    @SuppressWarnings("unchecked")
    private static GunSmithTableRecipe findTaCZRecipe(ResourceLocation ammoId, RecipeManager recipeManager) {
        try {
            Field byTypeField = RecipeManager.class.getDeclaredField("byType");
            byTypeField.setAccessible(true);
            Multimap<RecipeType<?>, RecipeHolder<?>> byType =
                    (Multimap<RecipeType<?>, RecipeHolder<?>>) byTypeField.get(recipeManager);
            for (var entry : byType.entries()) {
                RecipeHolder<?> holder = entry.getValue();
                if (!(holder.value() instanceof GunSmithTableRecipe recipe)) continue;
                ItemStack output = recipe.getOutput();
                if (output.getItem() != taczAmmoItem) continue;
                IAmmo ia = IAmmo.getIAmmoOrNull(output);
                if (ia != null && ammoId.equals(ia.getAmmoId(output))) {
                    return recipe;
                }
            }
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.debug("TaCZ recipe scan failed for {}", ammoId, e);
        }
        return null;
    }

    private static AmmoRecipeConfig.Config buildConfigFromRecipe(ResourceLocation ammoId, GunSmithTableRecipe recipe) {
        try {
            List<Item> assemblySteps = new ArrayList<>();

            for (GunSmithTableIngredient input : recipe.getInputs()) {
                Ingredient ingredient = input.getIngredient();
                int count = input.getCount();
                ItemStack[] items = ingredient.getItems();
                if (items.length == 0) continue;

                Item rawItem = items[0].getItem();
                if (rawItem == Items.AIR) continue;

                Item mappedItem = mapToAssemblyItem(rawItem);
                int steps = (count + 63) / 64;
                for (int i = 0; i < steps; i++) {
                    assemblySteps.add(mappedItem);
                }
            }

            List<Item> gunpowderSteps = new ArrayList<>();
            assemblySteps.removeIf(item -> {
                if (item == Items.GUNPOWDER) {
                    gunpowderSteps.add(item);
                    return true;
                }
                return false;
            });
            assemblySteps.addAll(gunpowderSteps);

            if (assemblySteps.stream().noneMatch(item -> item == Items.GUNPOWDER)) {
                assemblySteps.add(Items.GUNPOWDER);
            }

            int outputCount = recipe.getOutput().getCount();
            if (outputCount <= 0) {
                outputCount = AmmoRecipeConfig.getCategoryDefault(ammoId).outputCount();
            }

            return new AmmoRecipeConfig.Config(
                    AmmoRecipeConfig.AmmoCategory.DEFAULT,
                    List.copyOf(assemblySteps),
                    outputCount
            );
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.debug("Failed to build config from recipe", e);
            return null;
        }
    }

    private static Item mapToAssemblyItem(Item rawItem) {
        if (rawItem == Items.COPPER_INGOT) return AllItems.COPPER_SHEET.get();
        if (rawItem == Items.IRON_INGOT) return AllItems.IRON_SHEET.get();
        if (rawItem == Items.GOLD_INGOT) return AllItems.GOLDEN_SHEET.get();
        if (rawItem == Items.GUNPOWDER) return Items.GUNPOWDER;
        if (rawItem == AllItems.COPPER_SHEET.get()) return AllItems.COPPER_SHEET.get();
        if (rawItem == AllItems.IRON_SHEET.get()) return AllItems.IRON_SHEET.get();
        if (rawItem == AllItems.GOLDEN_SHEET.get()) return AllItems.GOLDEN_SHEET.get();
        return rawItem;
    }

    private static void injectRecipes(RecipeManager recipeManager, List<RecipeHolder<?>> newRecipes) {
        try {
            Field byTypeField = RecipeManager.class.getDeclaredField("byType");
            byTypeField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Multimap<RecipeType<?>, RecipeHolder<?>> byType =
                    (Multimap<RecipeType<?>, RecipeHolder<?>>) byTypeField.get(recipeManager);

            Collection<RecipeHolder<?>> allRecipes = new ArrayList<>(byType.values());
            allRecipes.addAll(newRecipes);
            recipeManager.replaceRecipes(allRecipes);

            SentryMechanicalArm.LOGGER.info(
                    "DynamicRecipeManager: Successfully injected {} recipes", newRecipes.size());
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.error(
                    "DynamicRecipeManager: Failed to inject recipes", e);
        }
    }

    private static void verifyRecipes(RecipeManager recipeManager) {
        int ourRecipes = 0;
        try {
            Field byTypeField = RecipeManager.class.getDeclaredField("byType");
            byTypeField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Multimap<RecipeType<?>, RecipeHolder<?>> byType =
                    (Multimap<RecipeType<?>, RecipeHolder<?>>) byTypeField.get(recipeManager);
            for (RecipeType<?> type : byType.keySet()) {
                for (RecipeHolder<?> holder : byType.get(type)) {
                    if (holder.id().getPath().startsWith("ammo_")) {
                        ourRecipes++;
                    }
                }
            }
            SentryMechanicalArm.LOGGER.info(
                    "DynamicRecipeManager: Found {} our recipes in RecipeManager", ourRecipes);
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.error(
                    "DynamicRecipeManager: Failed to verify recipes", e);
        }
    }
}

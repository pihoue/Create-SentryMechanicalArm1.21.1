package euphy.upo.sentrymechanicalarm.recipe;

import com.google.common.collect.Multimap;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe.Builder;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import euphy.upo.sentrymechanicalarm.SMAServerConfig;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@EventBusSubscriber(
   modid = "sentrymechanicalarm"
)
public class DynamicRecipeManager {
   private static Item taczAmmoItem;

   @SubscribeEvent
   public static void onServerAboutToStart(ServerAboutToStartEvent event) {
      RecipeManager recipeManager = event.getServer().getRecipeManager();
      taczAmmoItem = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tacz", "ammo"));
      if (taczAmmoItem == null) {
         SentryMechanicalArm.LOGGER.error("Failed to find TacZ ammo item, aborting recipe injection");
      } else if (!(Boolean)SMAServerConfig.ENABLE_DYNAMIC_RECIPES.get()) {
         SentryMechanicalArm.LOGGER.info("Dynamic recipes disabled by config");
      } else {
         injectCuttingRecipes(recipeManager);
         injectSequencedAssemblyRecipes(recipeManager);
         injectPressingRecipes(recipeManager);
         verifyRecipes(recipeManager);
      }
   }

   public static void injectCuttingRecipes(RecipeManager recipeManager) {
      Set<Entry<ResourceLocation, CommonAmmoIndex>> ammoEntries = TimelessAPI.getAllCommonAmmoIndex();
      if (!ammoEntries.isEmpty()) {
         List<RecipeHolder<?>> newRecipes = new ArrayList<>();

         for (Entry<ResourceLocation, CommonAmmoIndex> entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            String path = ammoId.getPath().replace("/", "_");
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "ammo_cutting/" + path);
            ItemStack output = new ItemStack((ItemLike)SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag tag = new CompoundTag();
            tag.putString("AmmoId", ammoId.toString());
            tag.putInt("CopperSheets", 0);
            tag.putBoolean("GunpowderAdded", false);
            output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            CuttingRecipe recipe = (CuttingRecipe)((Builder)((Builder)((Builder)new Builder(CuttingRecipe::new, recipeId)
                        .withItemIngredients(new Ingredient[]{Ingredient.of(new ItemLike[]{(ItemLike)AllItems.COPPER_SHEET.get()})}))
                     .withSingleItemOutput(output))
                  .duration(50))
               .build();
            newRecipes.add(new RecipeHolder(recipeId, recipe));
         }

         if (!newRecipes.isEmpty()) {
            injectRecipes(recipeManager, newRecipes);
         }
      }
   }

   public static void injectSequencedAssemblyRecipes(RecipeManager recipeManager) {
      Set<Entry<ResourceLocation, CommonAmmoIndex>> ammoEntries = TimelessAPI.getAllCommonAmmoIndex();
      if (ammoEntries.isEmpty()) {
         SentryMechanicalArm.LOGGER.warn("No ammo entries found from TimelessAPI");
      } else {
         SentryMechanicalArm.LOGGER.info("Injecting {} sequenced assembly recipes", ammoEntries.size());
         List<RecipeHolder<?>> newRecipes = new ArrayList<>();

         for (Entry<ResourceLocation, CommonAmmoIndex> entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            ResolvedConfig config = getOrCreateConfig(ammoId, recipeManager);
            String path = ammoId.getPath().replace("/", "_");
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "ammo_assembly/" + path);
            ItemStack inputUnfinished = new ItemStack((ItemLike)SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag inputTag = new CompoundTag();
            inputTag.putString("AmmoId", ammoId.toString());
            inputUnfinished.set(DataComponents.CUSTOM_DATA, CustomData.of(inputTag));
            ItemStack outputAmmo = new ItemStack(taczAmmoItem, config.outputCount());
            CompoundTag outputTag = new CompoundTag();
            outputTag.putString("AmmoId", ammoId.toString());
            outputAmmo.set(DataComponents.CUSTOM_DATA, CustomData.of(outputTag));
            SequencedAssemblyRecipeBuilder builder = new SequencedAssemblyRecipeBuilder(recipeId);
            builder.require(Ingredient.of(new ItemStack[]{inputUnfinished}))
               .transitionTo((ItemLike)SentryRegistry.UNFINISHED_AMMO.get())
               .addOutput(outputAmmo, 1.0F)
               .loops(1);

            for (AssemblyStep step : config.assemblySteps()) {
               builder.addStep(
                  DeployerApplicationRecipe::new,
                  rb -> (com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe.Builder)rb.require(step.ingredient())
               );
            }

            builder.addStep(PressingRecipe::new, rb -> rb);
            newRecipes.add(builder.build());
         }

         if (!newRecipes.isEmpty()) {
            injectRecipes(recipeManager, newRecipes);
         }
      }
   }

   public static void injectPressingRecipes(RecipeManager recipeManager) {
      Set<Entry<ResourceLocation, CommonAmmoIndex>> ammoEntries = TimelessAPI.getAllCommonAmmoIndex();
      if (!ammoEntries.isEmpty()) {
         List<RecipeHolder<?>> newRecipes = new ArrayList<>();

         for (Entry<ResourceLocation, CommonAmmoIndex> entry : ammoEntries) {
            ResourceLocation ammoId = entry.getKey();
            ResolvedConfig config = getOrCreateConfig(ammoId, recipeManager);
            String path = ammoId.getPath().replace("/", "_");
            ResourceLocation pressRecipeId = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "ammo_pressing/" + path);
            ItemStack inputComplete = new ItemStack((ItemLike)SentryRegistry.UNFINISHED_AMMO.get());
            CompoundTag inputTag = new CompoundTag();
            inputTag.putString("AmmoId", ammoId.toString());
            long materialSteps = config.assemblySteps().stream().filter(step -> step.representativeItem() != Items.GUNPOWDER).count();
            inputTag.putInt("CopperSheets", (int)materialSteps);
            inputTag.putBoolean("GunpowderAdded", true);
            inputComplete.set(DataComponents.CUSTOM_DATA, CustomData.of(inputTag));
            ItemStack outputAmmo = new ItemStack(taczAmmoItem, config.outputCount());
            CompoundTag outputTag = new CompoundTag();
            outputTag.putString("AmmoId", ammoId.toString());
            outputAmmo.set(DataComponents.CUSTOM_DATA, CustomData.of(outputTag));
            PressingRecipe recipe = (PressingRecipe)((Builder)((Builder)((Builder)new Builder(PressingRecipe::new, pressRecipeId)
                        .withItemIngredients(new Ingredient[]{Ingredient.of(new ItemStack[]{inputComplete})}))
                     .withSingleItemOutput(outputAmmo))
                  .duration(100))
               .build();
            newRecipes.add(new RecipeHolder(pressRecipeId, recipe));
         }

         if (!newRecipes.isEmpty()) {
            injectRecipes(recipeManager, newRecipes);
         }
      }
   }

   private static ResolvedConfig getOrCreateConfig(ResourceLocation ammoId, RecipeManager recipeManager) {
      if ((Boolean)SMAServerConfig.AUTO_MATCH_FROM_TACZ.get()) {
         GunSmithTableRecipe recipe = findTaCZRecipe(ammoId, recipeManager);
         if (recipe != null) {
            ResolvedConfig fromRecipe = buildConfigFromRecipe(ammoId, recipe);
            if (fromRecipe != null) {
               return fromRecipe;
            }
         }
      }

      AmmoRecipeConfig.Config fromOverride = AmmoRecipeConfig.getOverride(ammoId);
      return resolveStaticConfig(fromOverride != null ? fromOverride : AmmoRecipeConfig.getCategoryDefault(ammoId));
   }

   private static GunSmithTableRecipe findTaCZRecipe(ResourceLocation ammoId, RecipeManager recipeManager) {
      try {
         Field byTypeField = RecipeManager.class.getDeclaredField("byType");
         byTypeField.setAccessible(true);
         Multimap<RecipeType<?>, RecipeHolder<?>> byType = (Multimap<RecipeType<?>, RecipeHolder<?>>)byTypeField.get(recipeManager);

         for (Entry<RecipeType<?>, RecipeHolder<?>> entry : byType.entries()) {
            RecipeHolder<?> holder = entry.getValue();
            Recipe output = holder.value();
            if (output instanceof GunSmithTableRecipe) {
               GunSmithTableRecipe recipe = (GunSmithTableRecipe)output;
               ItemStack outputx = recipe.getOutput();
               if (outputx.getItem() == taczAmmoItem) {
                  IAmmo ia = IAmmo.getIAmmoOrNull(outputx);
                  if (ia != null && ammoId.equals(ia.getAmmoId(outputx))) {
                     return recipe;
                  }
               }
            }
         }
      } catch (Exception var10) {
         SentryMechanicalArm.LOGGER.debug("TaCZ recipe scan failed for {}", ammoId, var10);
      }

      return null;
   }

   private static ResolvedConfig buildConfigFromRecipe(ResourceLocation ammoId, GunSmithTableRecipe recipe) {
      try {
         List<DynamicRecipeManager.RawStep> rawSteps = new ArrayList<>();

         for (GunSmithTableIngredient input : recipe.getInputs()) {
            Ingredient ingredient = input.getIngredient();
            int count = input.getCount();
            ItemStack[] items = ingredient.getItems();
            if (items.length != 0) {
               Item rawItem = items[0].getItem();
               if (rawItem != Items.AIR) {
                  Item mappedItem = mapToAssemblyItem(rawItem);
                  // Preserve custom ingredients (notably TaCZ NBT/component ammo
                  // ingredients) unless this material is intentionally remapped.
                  Ingredient stepIngredient = mappedItem == rawItem ? ingredient : Ingredient.of(mappedItem);
                  rawSteps.add(new DynamicRecipeManager.RawStep(stepIngredient, mappedItem, count));
               }
            }
         }

         int outputCount = recipe.getOutput().getCount();
         if (outputCount <= 0) {
            outputCount = AmmoRecipeConfig.getCategoryDefault(ammoId).outputCount();
         }

         SMAServerConfig.StepScaling mode = (SMAServerConfig.StepScaling)SMAServerConfig.STEP_SCALING.get();
         if (mode == SMAServerConfig.StepScaling.GCD) {
            int gcd = outputCount;

            for (DynamicRecipeManager.RawStep s : rawSteps) {
               gcd = gcd(gcd, s.count());
            }

            if (gcd > 1) {
               for (int i = 0; i < rawSteps.size(); i++) {
                  DynamicRecipeManager.RawStep s = rawSteps.get(i);
                  rawSteps.set(i, new DynamicRecipeManager.RawStep(s.ingredient(), s.item(), s.count() / gcd));
               }

               outputCount /= gcd;
            }
         } else if (mode == SMAServerConfig.StepScaling.FIXED) {
            int factor = (Integer)SMAServerConfig.FIXED_SCALE_FACTOR.get();
            if (factor > 1) {
               for (int i = 0; i < rawSteps.size(); i++) {
                  DynamicRecipeManager.RawStep s = rawSteps.get(i);
                  rawSteps.set(i, new DynamicRecipeManager.RawStep(s.ingredient(), s.item(), Math.max(1, s.count() / factor)));
               }

               outputCount = Math.max(1, outputCount / factor);
            }
         }

         rawSteps = optimizeToBlocks(rawSteps);
         DynamicRecipeManager.ScaledResult scaled = scaleToFitSteps(rawSteps, 7, outputCount);
         rawSteps = scaled.steps();
         outputCount = scaled.outputCount();
         List<AssemblyStep> assemblySteps = new ArrayList<>();

         for (DynamicRecipeManager.RawStep s : rawSteps) {
            for (int i = 0; i < s.count(); i++) {
               assemblySteps.add(new AssemblyStep(s.ingredient(), s.item()));
            }
         }

         List<AssemblyStep> gunpowderSteps = new ArrayList<>();
         assemblySteps.removeIf(step -> {
            if (step.representativeItem() != Items.GUNPOWDER && step.representativeItem() != Items.TNT) {
               return false;
            } else {
               gunpowderSteps.add(step);
               return true;
            }
         });
         assemblySteps.addAll(gunpowderSteps);
         if (assemblySteps.stream().noneMatch(step -> step.representativeItem() == Items.GUNPOWDER || step.representativeItem() == Items.TNT)) {
            assemblySteps.add(AssemblyStep.of(Items.GUNPOWDER));
         }

         return new ResolvedConfig(AmmoRecipeConfig.AmmoCategory.DEFAULT, List.copyOf(assemblySteps), outputCount);
      } catch (Exception var10) {
         SentryMechanicalArm.LOGGER.debug("Failed to build config from recipe", var10);
         return null;
      }
   }

   private static ResolvedConfig resolveStaticConfig(AmmoRecipeConfig.Config config) {
      List<AssemblyStep> steps = config.assemblySteps().stream().map(AssemblyStep::of).toList();
      return new ResolvedConfig(config.category(), steps, config.outputCount());
   }

   private static int gcd(int a, int b) {
      while (b != 0) {
         int t = b;
         b = a % b;
         a = t;
      }

      return Math.abs(a);
   }

   private static Item mapToAssemblyItem(Item rawItem) {
      if (rawItem == Items.COPPER_INGOT) {
         return (Item)AllItems.COPPER_SHEET.get();
      } else if (rawItem == Items.IRON_INGOT) {
         return (Item)AllItems.IRON_SHEET.get();
      } else if (rawItem == Items.GOLD_INGOT) {
         return (Item)AllItems.GOLDEN_SHEET.get();
      } else if (rawItem == Items.GUNPOWDER) {
         return Items.GUNPOWDER;
      } else if (rawItem == AllItems.COPPER_SHEET.get()) {
         return (Item)AllItems.COPPER_SHEET.get();
      } else if (rawItem == AllItems.IRON_SHEET.get()) {
         return (Item)AllItems.IRON_SHEET.get();
      } else {
         return rawItem == AllItems.GOLDEN_SHEET.get() ? (Item)AllItems.GOLDEN_SHEET.get() : rawItem;
      }
   }

   private static DynamicRecipeManager.ScaledResult scaleToFitSteps(List<DynamicRecipeManager.RawStep> rawSteps, int maxSteps, int outputCount) {
      int totalSteps = 0;

      for (DynamicRecipeManager.RawStep s : rawSteps) {
         totalSteps += s.count();
      }

      boolean hasGunpowder = rawSteps.stream().anyMatch(sx -> sx.item() == Items.GUNPOWDER || sx.item() == Items.TNT);
      if (!hasGunpowder) {
         totalSteps++;
      }

      if (totalSteps <= maxSteps) {
         return new DynamicRecipeManager.ScaledResult(rawSteps, outputCount);
      } else {
         int factor = (int)Math.ceil((double)totalSteps / (double)maxSteps);
         List<DynamicRecipeManager.RawStep> scaled = new ArrayList<>();

         for (DynamicRecipeManager.RawStep s : rawSteps) {
            int newCount = Math.max(1, s.count() / factor);
            scaled.add(new DynamicRecipeManager.RawStep(s.ingredient(), s.item(), newCount));
         }

         int newOutput = Math.max(1, outputCount / factor);
         return new DynamicRecipeManager.ScaledResult(scaled, newOutput);
      }
   }

   private static List<DynamicRecipeManager.RawStep> optimizeToBlocks(List<DynamicRecipeManager.RawStep> rawSteps) {
      List<DynamicRecipeManager.RawStep> result = new ArrayList<>();

      for (DynamicRecipeManager.RawStep step : rawSteps) {
         Item item = step.item();
         int count = step.count();
         if (item == Items.GUNPOWDER) {
            if (count >= 4) {
               result.add(RawStep.of(Items.TNT, count / 4));
               int remainder = count % 4;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.GUNPOWDER, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.GLOWSTONE_DUST) {
            if (count >= 4) {
               result.add(RawStep.of(Items.GLOWSTONE, count / 4));
               int remainder = count % 4;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.GLOWSTONE_DUST, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.SNOWBALL) {
            if (count >= 4) {
               result.add(RawStep.of(Items.SNOW_BLOCK, count / 4));
               int remainder = count % 4;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.SNOWBALL, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.CLAY_BALL) {
            if (count >= 4) {
               result.add(RawStep.of(Items.CLAY, count / 4));
               int remainder = count % 4;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.CLAY_BALL, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.BRICK) {
            if (count >= 4) {
               result.add(RawStep.of(Items.BRICKS, count / 4));
               int remainder = count % 4;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.BRICK, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.NETHER_BRICK) {
            if (count >= 4) {
               result.add(RawStep.of(Items.NETHER_BRICKS, count / 4));
               int remainder = count % 4;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.NETHER_BRICK, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.AMETHYST_SHARD) {
            if (count >= 4) {
               result.add(RawStep.of(Items.AMETHYST_BLOCK, count / 4));
               int remainder = count % 4;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.AMETHYST_SHARD, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.QUARTZ) {
            if (count >= 4) {
               result.add(RawStep.of(Items.QUARTZ_BLOCK, count / 4));
               int remainder = count % 4;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.QUARTZ, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.HONEYCOMB) {
            if (count >= 4) {
               result.add(RawStep.of(Items.HONEYCOMB_BLOCK, count / 4));
               int remainder = count % 4;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.HONEYCOMB, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.BONE_MEAL) {
            if (count >= 9) {
               result.add(RawStep.of(Items.BONE_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.BONE_MEAL, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.LAPIS_LAZULI) {
            if (count >= 9) {
               result.add(RawStep.of(Items.LAPIS_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.LAPIS_LAZULI, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.REDSTONE) {
            if (count >= 9) {
               result.add(RawStep.of(Items.REDSTONE_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.REDSTONE, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.DIAMOND) {
            if (count >= 9) {
               result.add(RawStep.of(Items.DIAMOND_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.DIAMOND, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.EMERALD) {
            if (count >= 9) {
               result.add(RawStep.of(Items.EMERALD_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.EMERALD, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.COPPER_INGOT) {
            if (count >= 9) {
               result.add(RawStep.of(Items.COPPER_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.COPPER_INGOT, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.IRON_INGOT) {
            if (count >= 9) {
               result.add(RawStep.of(Items.IRON_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.IRON_INGOT, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == Items.GOLD_INGOT) {
            if (count >= 9) {
               result.add(RawStep.of(Items.GOLD_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of(Items.GOLD_INGOT, remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == AllItems.COPPER_SHEET.get()) {
            if (count >= 9) {
               result.add(RawStep.of(Items.COPPER_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of((Item)AllItems.COPPER_SHEET.get(), remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == AllItems.IRON_SHEET.get()) {
            if (count >= 9) {
               result.add(RawStep.of(Items.IRON_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of((Item)AllItems.IRON_SHEET.get(), remainder));
               }
            } else {
               result.add(step);
            }
         } else if (item == AllItems.GOLDEN_SHEET.get()) {
            if (count >= 9) {
               result.add(RawStep.of(Items.GOLD_BLOCK, count / 9));
               int remainder = count % 9;
               if (remainder > 0) {
                  result.add(RawStep.of((Item)AllItems.GOLDEN_SHEET.get(), remainder));
               }
            } else {
               result.add(step);
            }
         } else {
            result.add(step);
         }
      }

      return result;
   }

   private static void injectRecipes(RecipeManager recipeManager, List<RecipeHolder<?>> newRecipes) {
      try {
         Field byTypeField = RecipeManager.class.getDeclaredField("byType");
         byTypeField.setAccessible(true);
         Multimap<RecipeType<?>, RecipeHolder<?>> byType = (Multimap<RecipeType<?>, RecipeHolder<?>>)byTypeField.get(recipeManager);
         Collection<RecipeHolder<?>> allRecipes = new ArrayList<>(byType.values());
         allRecipes.addAll(newRecipes);
         recipeManager.replaceRecipes(allRecipes);
         SentryMechanicalArm.LOGGER.info("DynamicRecipeManager: Successfully injected {} recipes", newRecipes.size());
      } catch (Exception var5) {
         SentryMechanicalArm.LOGGER.error("DynamicRecipeManager: Failed to inject recipes", var5);
      }
   }

   private static void verifyRecipes(RecipeManager recipeManager) {
      int ourRecipes = 0;

      try {
         Field byTypeField = RecipeManager.class.getDeclaredField("byType");
         byTypeField.setAccessible(true);
         Multimap<RecipeType<?>, RecipeHolder<?>> byType = (Multimap<RecipeType<?>, RecipeHolder<?>>)byTypeField.get(recipeManager);

         for (RecipeType<?> type : byType.keySet()) {
            for (RecipeHolder<?> holder : byType.get(type)) {
               if (holder.id().getPath().startsWith("ammo_")) {
                  ourRecipes++;
               }
            }
         }

         SentryMechanicalArm.LOGGER.info("DynamicRecipeManager: Found {} our recipes in RecipeManager", ourRecipes);
      } catch (Exception var8) {
         SentryMechanicalArm.LOGGER.error("DynamicRecipeManager: Failed to verify recipes", var8);
      }
   }

   private static record AssemblyStep(Ingredient ingredient, Item representativeItem) {
      private static AssemblyStep of(Item item) {
         return new AssemblyStep(Ingredient.of(item), item);
      }
   }

   private static record ResolvedConfig(AmmoRecipeConfig.AmmoCategory category, List<AssemblyStep> assemblySteps, int outputCount) {
   }

   private static record RawStep(Ingredient ingredient, Item item, int count) {
      private static RawStep of(Item item, int count) {
         return new RawStep(Ingredient.of(item), item, count);
      }
   }

   private static record ScaledResult(List<DynamicRecipeManager.RawStep> steps, int outputCount) {
   }
}

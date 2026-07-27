package euphy.upo.sentrymechanicalarm.mixin;

import com.google.common.collect.Multimap;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.tacz.guns.api.item.IAmmo;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SawBlockEntity.class})
public abstract class SawBlockEntityMixin {
   @Shadow
   private FilteringBehaviour filtering;

   @Inject(
      method = {"getRecipes"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false
   )
   private void onGetRecipes(CallbackInfoReturnable<List<RecipeHolder<? extends Recipe<?>>>> cir) {
      SawBlockEntity self = (SawBlockEntity)(Object)this;
      ItemStack filterStack = this.filtering.getFilter();
      if (!filterStack.isEmpty()) {
         ResourceLocation filterAmmoId = this.getFilterAmmoId(filterStack);
         if (filterAmmoId != null) {
            ProcessingInventory inventory = self.inventory;
            ItemStack inputStack = inventory.getStackInSlot(0);
            if (!inputStack.isEmpty()) {
               List<RecipeHolder<? extends Recipe<?>>> cuttingRecipes = this.getCuttingRecipesFromManager(self);
               List<RecipeHolder<? extends Recipe<?>>> filtered = cuttingRecipes.stream()
                  .filter(r -> r.id().getNamespace().equals("sentrymechanicalarm") && r.id().getPath().startsWith("ammo_cutting/"))
                  .filter(r -> this.firstIngredientMatches((RecipeHolder<? extends Recipe<?>>)r, inputStack))
                  .filter(r -> {
                     ItemStack output = r.value().getResultItem(self.getLevel().registryAccess());
                     if (!output.isEmpty() && output.getItem() == SentryRegistry.UNFINISHED_AMMO.get()) {
                        ResourceLocation outputAmmoId = this.getFilterAmmoId(output);
                        return outputAmmoId != null && filterAmmoId.equals(outputAmmoId);
                     } else {
                        return false;
                     }
                  })
                  .collect(Collectors.toList());
               if (!filtered.isEmpty()) {
                  cir.setReturnValue(filtered);
               }
            }
         }
      }
   }

   private List<RecipeHolder<? extends Recipe<?>>> getCuttingRecipesFromManager(SawBlockEntity self) {
      List<RecipeHolder<? extends Recipe<?>>> result = new ArrayList<>();

      try {
         RecipeManager recipeManager = self.getLevel().getServer().getRecipeManager();
         RecipeType<?> cuttingType = AllRecipeTypes.CUTTING.getType();
         Field byTypeField = RecipeManager.class.getDeclaredField("byType");
         byTypeField.setAccessible(true);
         Multimap<RecipeType<?>, RecipeHolder<?>> byType = (Multimap<RecipeType<?>, RecipeHolder<?>>)byTypeField.get(recipeManager);
         Collection<RecipeHolder<?>> cuttingHolders = byType.get(cuttingType);
         if (cuttingHolders != null) {
            result.addAll(cuttingHolders);
         }
      } catch (Exception var8) {
         SentryMechanicalArm.LOGGER.error("SawBlockEntity NBT filter: failed to query RecipeManager", var8);
      }

      return result;
   }

   private boolean firstIngredientMatches(RecipeHolder<? extends Recipe<?>> holder, ItemStack input) {
      if (holder.value() instanceof StandardProcessingRecipe<?> processingRecipe) {
         NonNullList<Ingredient> ingredients = processingRecipe.getIngredients();
         if (!ingredients.isEmpty()) {
            return ((Ingredient)ingredients.get(0)).test(input);
         }
      }

      return false;
   }

   private ResourceLocation getFilterAmmoId(ItemStack stack) {
      if (stack.getItem() == SentryRegistry.UNFINISHED_AMMO.get()) {
         CompoundTag tag = ItemNBTHelper.getTag(stack);
         if (tag != null && tag.contains("AmmoId")) {
            return ResourceLocation.parse(tag.getString("AmmoId"));
         }
      }

      IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
      return ammo != null ? ammo.getAmmoId(stack) : null;
   }
}

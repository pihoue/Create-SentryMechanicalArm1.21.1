package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.content.kinetics.press.PressingRecipe;
import euphy.upo.sentrymechanicalarm.content.UnfinishedAmmoItem;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PressingRecipe.class)
public class PressingRecipeMixin {

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void sentry$filterAmmoPressing(SingleRecipeInput inv, Level worldIn, CallbackInfoReturnable<Boolean> cir) {
        PressingRecipe recipe = (PressingRecipe) (Object) this;
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return;

        ItemStack[] recipeItems = ingredients.get(0).getItems();
        if (recipeItems.length == 0) return;
        if (!(recipeItems[0].getItem() instanceof UnfinishedAmmoItem)) return;

        ResourceLocation recipeAmmoId = UnfinishedAmmoItem.getAmmoId(recipeItems[0]);
        if (recipeAmmoId == null) return;

        ItemStack inputStack = inv.getItem(0);
        ResourceLocation inputAmmoId = UnfinishedAmmoItem.getAmmoId(inputStack);
        if (inputAmmoId == null || !recipeAmmoId.equals(inputAmmoId)) {
            cir.setReturnValue(false);
            return;
        }

        int recipeSheets = UnfinishedAmmoItem.getCopperSheets(recipeItems[0]);
        int inputSheets = UnfinishedAmmoItem.getCopperSheets(inputStack);
        if (inputSheets < recipeSheets) {
            cir.setReturnValue(false);
            return;
        }

        boolean recipeHasGunpowder = UnfinishedAmmoItem.hasGunpowder(recipeItems[0]);
        boolean inputHasGunpowder = UnfinishedAmmoItem.hasGunpowder(inputStack);
        if (recipeHasGunpowder && !inputHasGunpowder) {
            cir.setReturnValue(false);
        }
    }
}

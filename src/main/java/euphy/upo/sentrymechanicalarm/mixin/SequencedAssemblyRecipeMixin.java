package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.UnfinishedAmmoItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SequencedAssemblyRecipe.class)
public class SequencedAssemblyRecipeMixin {

    @Inject(method = "appliesTo", at = @At("RETURN"), cancellable = true)
    private void sentry$filterByAmmoId(ResourceLocation id, ItemStack input, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (!id.getNamespace().equals(SentryMechanicalArm.MODID)) return;
        if (!id.getPath().startsWith("ammo_assembly/")) return;

        if (input.has(AllDataComponents.SEQUENCED_ASSEMBLY)) return;

        SequencedAssemblyRecipe recipe = (SequencedAssemblyRecipe) (Object) this;
        Ingredient ingredient = recipe.getIngredient();
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            cir.setReturnValue(false);
            return;
        }

        ResourceLocation recipeAmmoId = UnfinishedAmmoItem.getAmmoId(items[0]);
        ResourceLocation inputAmmoId = UnfinishedAmmoItem.getAmmoId(input);

        if (recipeAmmoId == null || !recipeAmmoId.equals(inputAmmoId)) {
            cir.setReturnValue(false);
        }
    }
}

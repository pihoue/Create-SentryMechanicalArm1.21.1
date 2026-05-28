package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import com.tacz.guns.api.item.IAmmo;
import euphy.upo.sentrymechanicalarm.content.UnfinishedAmmoItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRecipeSerializers;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class AmmoTemplateRecipe extends CustomRecipe {

    public AmmoTemplateRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean hasAmmo = false;
        boolean hasCopperSheet = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (IAmmo.getIAmmoOrNull(stack) != null) {
                if (hasAmmo) return false;
                hasAmmo = true;
            } else if (stack.getItem() == AllItems.COPPER_SHEET.get()) {
                if (hasCopperSheet) return false;
                hasCopperSheet = true;
            } else {
                return false;
            }
        }
        return hasAmmo && hasCopperSheet;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack ammoStack = ItemStack.EMPTY;
        int copperCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (IAmmo.getIAmmoOrNull(stack) != null) {
                ammoStack = stack;
            } else if (stack.getItem() == AllItems.COPPER_SHEET.get()) {
                copperCount += stack.getCount();
            }
        }

        if (ammoStack.isEmpty() || copperCount == 0) return ItemStack.EMPTY;

        IAmmo ammo = IAmmo.getIAmmoOrNull(ammoStack);
        if (ammo == null) return ItemStack.EMPTY;

        ResourceLocation ammoId = ammo.getAmmoId(ammoStack);
        if (ammoId == null) return ItemStack.EMPTY;

        int resultCount = Math.min(ammoStack.getCount(), copperCount);

        ItemStack result = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get(), resultCount);
        CompoundTag tag = new CompoundTag();
        tag.putString("AmmoId", ammoId.toString());
        tag.putInt("CopperSheets", 0);
        tag.putBoolean("GunpowderAdded", false);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SentryRecipeSerializers.AMMO_TEMPLATE.get();
    }
}

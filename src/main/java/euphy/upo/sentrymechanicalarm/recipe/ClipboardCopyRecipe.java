package euphy.upo.sentrymechanicalarm.recipe;

import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import euphy.upo.sentrymechanicalarm.registry.SentryRecipeSerializers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class ClipboardCopyRecipe extends CustomRecipe {

    public ClipboardCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack sourceStack = ItemStack.EMPTY;
        ItemStack blankStack = ItemStack.EMPTY;
        int clipboardCount = 0;

        for (int i = 0; i < input.size(); ++i) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (!(stack.getItem() instanceof FireControlClipboardItem)) {
                    return false;
                }
                clipboardCount++;

                if (hasTargetData(stack)) {
                    if (!sourceStack.isEmpty()) return false;
                    sourceStack = stack;
                } else {
                    if (!blankStack.isEmpty()) return false;
                    blankStack = stack;
                }
            }
        }

        return clipboardCount == 2 && !sourceStack.isEmpty() && !blankStack.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack sourceStack = ItemStack.EMPTY;


        for (int i = 0; i < input.size(); ++i) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && hasTargetData(stack)) {
                sourceStack = stack;
                break;
            }
        }

        if (sourceStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = sourceStack.copy();

        result.setCount(2);

        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SentryRecipeSerializers.CLIPBOARD_COPY.get();
    }


    private boolean hasTargetData(ItemStack stack) {
        if (!ItemNBTHelper.hasTag(stack)) return false;
        CompoundTag tag = ItemNBTHelper.getTag(stack);
        return tag.contains("TargetList", Tag.TAG_LIST) && !tag.getList("TargetList", Tag.TAG_STRING).isEmpty();
    }
}
package euphy.upo.sentrymechanicalarm.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ItemNBTHelper {

    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            return data.copyTag();
        }
        return new CompoundTag();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean hasTag(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }
}
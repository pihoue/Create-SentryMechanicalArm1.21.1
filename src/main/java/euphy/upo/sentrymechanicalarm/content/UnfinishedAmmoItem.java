package euphy.upo.sentrymechanicalarm.content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;

public class UnfinishedAmmoItem extends Item {

    private Item cachedTacZAmmoItem = null;

    public UnfinishedAmmoItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {

        if (ItemNBTHelper.hasTag(stack) && ItemNBTHelper.getTag(stack).contains("AmmoId")) {
            String ammoIdStr = ItemNBTHelper.getTag(stack).getString("AmmoId");
            if (cachedTacZAmmoItem == null) {
                  cachedTacZAmmoItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tacz", "ammo"));
            }

            if (cachedTacZAmmoItem != null) {
                ItemStack dummyStack = new ItemStack(cachedTacZAmmoItem);
                CompoundTag tag = new CompoundTag();
                tag.putString("AmmoId", ammoIdStr);
                ItemNBTHelper.setTag(dummyStack, tag);
                Component realName = dummyStack.getHoverName();

                return Component.translatable(this.getDescriptionId())
                        .append(" (")
                        .append(realName)
                        .append(")");
            }
        }
        return super.getName(stack);
    }
}
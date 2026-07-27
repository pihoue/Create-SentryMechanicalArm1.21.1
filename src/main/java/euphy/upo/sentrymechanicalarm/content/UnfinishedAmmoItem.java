package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;

public class UnfinishedAmmoItem extends SequencedAssemblyItem {
   private Item cachedTacZAmmoItem = null;
   public static final int MAX_COPPER_SHEETS = 10;

   public UnfinishedAmmoItem(Properties properties) {
      super(properties.stacksTo(64));
   }

   public static int getCopperSheets(ItemStack stack) {
      CompoundTag tag = ItemNBTHelper.getTag(stack);
      return tag != null && tag.contains("CopperSheets") ? tag.getInt("CopperSheets") : 0;
   }

   public static void setCopperSheets(ItemStack stack, int count) {
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      tag.putInt("CopperSheets", Math.min(count, 10));
      ItemNBTHelper.setTag(stack, tag);
   }

   public static boolean hasGunpowder(ItemStack stack) {
      CompoundTag tag = ItemNBTHelper.getTag(stack);
      return tag != null && tag.getBoolean("GunpowderAdded");
   }

   public static void setGunpowderAdded(ItemStack stack) {
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      tag.putBoolean("GunpowderAdded", true);
      ItemNBTHelper.setTag(stack, tag);
   }

   public static boolean isNbtComplete(ItemStack stack) {
      return getCopperSheets(stack) >= 10 && hasGunpowder(stack);
   }

   public static ResourceLocation getAmmoId(ItemStack stack) {
      CompoundTag tag = ItemNBTHelper.getTag(stack);
      return tag != null && tag.contains("AmmoId") ? ResourceLocation.parse(tag.getString("AmmoId")) : null;
   }

   public Component getName(ItemStack stack) {
      int copper = getCopperSheets(stack);
      boolean gunpowder = hasGunpowder(stack);
      if (copper <= 0 && !gunpowder) {
         if (ItemNBTHelper.hasTag(stack) && ItemNBTHelper.getTag(stack).contains("AmmoId")) {
            String ammoIdStr = ItemNBTHelper.getTag(stack).getString("AmmoId");
            if (this.cachedTacZAmmoItem == null) {
               this.cachedTacZAmmoItem = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tacz", "ammo"));
            }

            if (this.cachedTacZAmmoItem != null) {
               ItemStack dummyStack = new ItemStack(this.cachedTacZAmmoItem);
               CompoundTag tag = new CompoundTag();
               tag.putString("AmmoId", ammoIdStr);
               ItemNBTHelper.setTag(dummyStack, tag);
               Component realName = dummyStack.getHoverName();
               return Component.translatable(this.getDescriptionId()).append(" (").append(realName).append(")");
            }
         }

         return super.getName(stack);
      } else {
         StringBuilder sb = new StringBuilder();
         sb.append(" [");
         sb.append(copper).append("/").append(10);
         if (gunpowder) {
            sb.append("+Gunpowder");
         }

         sb.append("]");
         return Component.literal(super.getName(stack).getString() + sb);
      }
   }
}

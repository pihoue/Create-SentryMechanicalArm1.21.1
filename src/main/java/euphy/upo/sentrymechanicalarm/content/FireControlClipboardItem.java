package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class FireControlClipboardItem extends Item {
   public FireControlClipboardItem(Properties properties) {
      super(properties);
   }

   private InteractionResult handleInteraction(Level level, Player player, ItemStack stack) {
      if (!level.isClientSide) {
         if (player.isShiftKeyDown()) {
            this.addNameToList(stack, player.getName().getString(), player);
         } else {
            this.openClipboardGUI(player, stack);
         }
      }

      return InteractionResult.CONSUME;
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
      ItemStack stack = player.getItemInHand(usedHand);
      InteractionResult result = this.handleInteraction(level, player, stack);
      if (result == InteractionResult.CONSUME) {
         return InteractionResultHolder.consume(stack);
      } else {
         return result == InteractionResult.SUCCESS ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
      }
   }

   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      return player == null ? InteractionResult.PASS : this.handleInteraction(context.getLevel(), player, context.getItemInHand());
   }

   private void addNameToList(ItemStack stack, String name, Player player) {
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      ListTag listTag;
      if (tag.contains("TargetList", 9)) {
         listTag = tag.getList("TargetList", 8);
      } else {
         listTag = new ListTag();
         tag.put("TargetList", listTag);
      }

      boolean alreadyExists = false;

      for (Tag t : listTag) {
         if (t.getAsString().equals(name)) {
            alreadyExists = true;
            break;
         }
      }

      if (!alreadyExists) {
         listTag.add(StringTag.valueOf(name));
         ItemNBTHelper.setTag(stack, tag);
         player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.added_target", new Object[]{name}), true);
      } else {
         player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.already_on_list", new Object[]{name}), true);
      }
   }

   private void openClipboardGUI(Player player, ItemStack stack) {
      List<String> targets = new ArrayList<>();
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      if (tag.contains("TargetList", 9)) {
         for (Tag t : tag.getList("TargetList", 8)) {
            targets.add(t.getAsString());
         }
      }

      final boolean isWhitelist = tag.getBoolean("WhitelistMode");
      final List<String> finalTargets = targets;
      ((ServerPlayer)player).openMenu(new MenuProvider() {
         public Component getDisplayName() {
            return Component.translatable("item.sentrymechanicalarm.fire_control_clipboard");
         }

         @Nullable
         public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new FireControlMenu(id, inventory, finalTargets, isWhitelist);
         }
      }, buf -> {
         buf.writeBlockPos(BlockPos.ZERO);
         buf.writeBoolean(isWhitelist);
         buf.writeVarInt(finalTargets.size());

         for (String s : finalTargets) {
            buf.writeUtf(s);
         }
      });
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
      super.appendHoverText(stack, context, tooltipComponents, isAdvanced);
      CompoundTag tag = ItemNBTHelper.getTag(stack);
      boolean isWhitelist = tag.contains("WhitelistMode") && tag.getBoolean("WhitelistMode");
      if (isWhitelist) {
         tooltipComponents.add(
            Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.mode")
               .withStyle(ChatFormatting.GRAY)
               .append(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.whitelist").withStyle(ChatFormatting.AQUA))
         );
         tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.whitelist_des_1").withStyle(ChatFormatting.DARK_GRAY));
         tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.whitelist_des_2").withStyle(ChatFormatting.DARK_GRAY));
      } else {
         tooltipComponents.add(
            Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.mode")
               .withStyle(ChatFormatting.GRAY)
               .append(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.blacklist").withStyle(ChatFormatting.RED))
         );
         tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.blacklist_des_1").withStyle(ChatFormatting.DARK_GRAY));
         tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.blacklist_des_2").withStyle(ChatFormatting.DARK_GRAY));
      }
   }
}

package euphy.upo.sentrymechanicalarm.content;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FireControlClipboardItem extends Item {

    public FireControlClipboardItem(Properties properties) {
        super(properties);
    }


    private InteractionResult handleInteraction(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                addNameToList(stack, player.getName().getString(), player);
            } else {
                openClipboardGUI(player, stack);
            }
        }
        return InteractionResult.CONSUME;
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        InteractionResult result = handleInteraction(level, player, stack);

        if (result == InteractionResult.CONSUME) {
            return InteractionResultHolder.consume(stack);
        } else if (result == InteractionResult.SUCCESS) {
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        return handleInteraction(context.getLevel(), player, context.getItemInHand());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
        if (!player.level().isClientSide) {
            addNameToList(stack, interactionTarget.getName().getString(), player);
        }
        return InteractionResult.SUCCESS;
    }

    private void addNameToList(ItemStack stack, String name, Player player) {
        CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
        ListTag listTag;
        if (tag.contains("TargetList", Tag.TAG_LIST)) {
            listTag = tag.getList("TargetList", Tag.TAG_STRING);
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
            player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.added_target", name), true);
        } else {
            player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.already_on_list", name), true);
        }
    }

    private void openClipboardGUI(Player player, ItemStack stack) {
        List<String> targets = new ArrayList<>();
        CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);

        if (tag.contains("TargetList", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("TargetList", Tag.TAG_STRING);
            for (Tag t : listTag) {
                targets.add(t.getAsString());
            }
        }

        boolean isWhitelist = tag.getBoolean("WhitelistMode");
        final List<String> finalTargets = targets;
        ((ServerPlayer) player).openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("item.sentrymechanicalarm.fire_control_clipboard");
            }
            @Nullable
            @Override
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

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, context, tooltipComponents, isAdvanced);

        CompoundTag tag = ItemNBTHelper.getTag(stack);
        boolean isWhitelist = tag.contains("WhitelistMode") && tag.getBoolean("WhitelistMode");

        if (isWhitelist) {
            tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.mode").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.whitelist").withStyle(ChatFormatting.AQUA)));
            tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.whitelist_des_1").withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.whitelist_des_2").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.mode").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.blacklist").withStyle(ChatFormatting.RED)));
            tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.blacklist_des_1").withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.translatable("item.sentrymechanicalarm.fire_control_clipboard.blacklist_des_2").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
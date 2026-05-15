package euphy.upo.sentrymechanicalarm.content;

import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import euphy.upo.sentrymechanicalarm.util.SentryTargetSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID, bus = EventBusSubscriber.Bus.GAME)
public class SentryInteractionEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof SentryArmBlock)) return;

        ItemStack stack = event.getItemStack();
        SentryArmBlockEntity sentry = (SentryArmBlockEntity) level.getBlockEntity(pos);
        if (sentry == null) return;

        if (player.isCrouching()) {
            ItemStack removed = sentry.removeLastAmmoBox();
            if (!removed.isEmpty()) {
                if (!level.isClientSide) {
                    Block.popResource(level, pos, removed);
                    level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.5f);
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        boolean isDye = stack.is(Tags.Items.DYES);
        boolean hasWater = !isDye && stack.getItem() == Items.WATER_BUCKET;

        if (isDye || hasWater) {
            DyeColor colorToApply = isDye ? DyeColor.getColor(stack) : null;
            if (sentry.applyColor(colorToApply)) {
                int newColorId = (colorToApply == null) ? 0 : (colorToApply.getId() + 1);
                level.setBlock(pos, state.setValue(SentryArmBlock.COLOR_TYPE, newColorId), 3);
                if (!level.isClientSide) {
                    level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        ItemStack armHeldStack = sentry.getHeldItem();

        if (stack.getItem() instanceof IAmmoBox iBox) {
            if (!armHeldStack.isEmpty() && armHeldStack.getItem() instanceof IGun) {
                boolean isCompatible = iBox.isAmmoBoxOfGun(armHeldStack, stack);
                boolean isEmpty = iBox.getAmmoCount(stack) == 0;
                if (!isCompatible && !isEmpty) {
                    if (!level.isClientSide) {
                        player.displayClientMessage(Component.translatable("sentry.tooltip.ammobox_3").withStyle(net.minecraft.ChatFormatting.RED), true);
                    }
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    return;
                }
                if (sentry.addAmmoBox(stack)) {
                    if (!level.isClientSide) {
                        if (!player.isCreative()) stack.shrink(1);
                        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
                        player.displayClientMessage(Component.translatable("sentry.tooltip.ammobox_2").withStyle(net.minecraft.ChatFormatting.GREEN), true);
                    }
                } else {
                    if (!level.isClientSide) {
                        player.displayClientMessage(Component.translatable("sentry.tooltip.ammobox_1").withStyle(net.minecraft.ChatFormatting.RED), true);
                    }
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getLevel().isClientSide() && event.getState().is(Blocks.TARGET)) {
            if (event.getLevel() instanceof ServerLevel level) {
                SentryTargetSavedData.get(level).addTarget(event.getPos());
                int count = SentryTargetSavedData.get(level).getTargets().size();
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.getLevel().isClientSide() && event.getState().is(Blocks.TARGET)) {
            if (event.getLevel() instanceof ServerLevel level) {
                SentryTargetSavedData.get(level).removeTarget(event.getPos());
            }
        }
    }
}
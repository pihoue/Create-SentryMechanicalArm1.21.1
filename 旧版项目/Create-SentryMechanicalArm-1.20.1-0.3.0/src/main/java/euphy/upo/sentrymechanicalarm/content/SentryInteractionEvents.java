package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.util.SentryTargetSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "sentrymechanicalarm", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SentryInteractionEvents {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
 
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (!player.isCrouching()) return; 

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SentryArmBlockEntity sentry)) return;
        ItemStack removed = sentry.removeLastAmmoBox();

        if (!removed.isEmpty()) {
 
            if (!level.isClientSide) {
                Block.popResource(level, pos, removed);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.5f);
            }
            event.setCanceled(true); 
            event.setCancellationResult(InteractionResult.SUCCESS); 
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != event.getEntity().getUsedItemHand()) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof FireControlClipboardItem)) {
            return;
        }

        Player player = event.getEntity();
        if (!(event.getTarget() instanceof LivingEntity targetEntity)) {
            return;
        }

        if (!player.level().isClientSide) {
            CompoundTag tag = stack.getOrCreateTag();

            ListTag listTag;
            if (tag.contains("TargetList", Tag.TAG_LIST)) {
                listTag = tag.getList("TargetList", Tag.TAG_STRING);
            } else {
                listTag = new ListTag();
                tag.put("TargetList", listTag);
            }

            String name = targetEntity.getName().getString();
            boolean alreadyExists = false;
            for (Tag t : listTag) {
                if (t.getAsString().equals(name)) {
                    alreadyExists = true;
                    break;
                }
            }

            if (!alreadyExists) {
                listTag.add(StringTag.valueOf(name));
                player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.added_target", name), true);
            } else {
                player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.target_exists", name), true);
            }
        }

        event.setCanceled(true);

        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
package euphy.upo.sentrymechanicalarm.content;

import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.util.SentryTargetSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;

@EventBusSubscriber(
   modid = "sentrymechanicalarm"
)
public class SentryInteractionEvents {
   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void onRightClickBlock(RightClickBlock event) {
      if (event.getHand() == InteractionHand.MAIN_HAND) {
         Player player = event.getEntity();
         Level level = event.getLevel();
         BlockPos pos = event.getPos();
         BlockState state = level.getBlockState(pos);
         if (state.getBlock() instanceof SentryArmBlock) {
            ItemStack stack = event.getItemStack();
            SentryArmBlockEntity sentry = (SentryArmBlockEntity)level.getBlockEntity(pos);
            if (sentry != null) {
               if (player.isCrouching()) {
                  ItemStack removed = sentry.removeLastAmmoBox();
                  if (!removed.isEmpty()) {
                     if (!level.isClientSide) {
                        Block.popResource(level, pos, removed);
                        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.5F);
                     }

                     event.setCanceled(true);
                     event.setCancellationResult(InteractionResult.SUCCESS);
                  }
               } else {
                  boolean isDye = stack.is(Items.DYES);
                  boolean hasWater = !isDye && stack.getItem() == net.minecraft.world.item.Items.WATER_BUCKET;
                  if (!isDye && !hasWater) {
                     ItemStack armHeldStack = sentry.getHeldItem();
                     if (stack.getItem() instanceof IAmmoBox iBox) {
                        if (!armHeldStack.isEmpty() && armHeldStack.getItem() instanceof IGun) {
                           boolean isCompatible = iBox.isAmmoBoxOfGun(armHeldStack, stack);
                           boolean isEmpty = iBox.getAmmoCount(stack) == 0;
                           if (!isCompatible && !isEmpty) {
                              if (!level.isClientSide) {
                                 player.displayClientMessage(Component.translatable("sentry.tooltip.ammobox_3").withStyle(ChatFormatting.RED), true);
                              }

                              event.setCanceled(true);
                              event.setCancellationResult(InteractionResult.SUCCESS);
                              return;
                           }

                           if (sentry.addAmmoBox(stack)) {
                              if (!level.isClientSide) {
                                 if (!player.isCreative()) {
                                    stack.shrink(1);
                                 }

                                 level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                                 player.displayClientMessage(Component.translatable("sentry.tooltip.ammobox_2").withStyle(ChatFormatting.GREEN), true);
                              }
                           } else if (!level.isClientSide) {
                              player.displayClientMessage(Component.translatable("sentry.tooltip.ammobox_1").withStyle(ChatFormatting.RED), true);
                           }

                           event.setCanceled(true);
                           event.setCancellationResult(InteractionResult.SUCCESS);
                        }
                     }
                  } else {
                     DyeColor colorToApply = isDye ? DyeColor.getColor(stack) : null;
                     if (sentry.applyColor(colorToApply)) {
                        int newColorId = colorToApply == null ? 0 : colorToApply.getId() + 1;
                        level.setBlock(pos, (BlockState)state.setValue(SentryArmBlock.COLOR_TYPE, newColorId), 3);
                        if (!level.isClientSide) {
                           level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                        }

                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onBlockPlace(EntityPlaceEvent event) {
      if (!event.getLevel().isClientSide() && event.getState().is(Blocks.TARGET) && event.getLevel() instanceof ServerLevel level) {
         SentryTargetSavedData.get(level).addTarget(event.getPos());
         int var3 = SentryTargetSavedData.get(level).getTargets().size();
      }
   }

   @SubscribeEvent
   public static void onBlockBreak(BreakEvent event) {
      if (!event.getLevel().isClientSide() && event.getState().is(Blocks.TARGET) && event.getLevel() instanceof ServerLevel level) {
         SentryTargetSavedData.get(level).removeTarget(event.getPos());
      }
   }
}

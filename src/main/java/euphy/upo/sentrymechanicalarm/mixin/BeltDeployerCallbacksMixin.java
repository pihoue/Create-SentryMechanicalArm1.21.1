package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import euphy.upo.sentrymechanicalarm.content.UnfinishedAmmoItem;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BeltDeployerCallbacks.class})
public class BeltDeployerCallbacksMixin {
   @Unique
   private static final long FILL_COOLDOWN_TICKS = 60L;

   @Inject(
      method = {"onItemReceived"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false
   )
   private static void onItemReceivedReturn(
      TransportedItemStack transported,
      TransportedItemStackHandlerBehaviour handler,
      DeployerBlockEntity deployer,
      CallbackInfoReturnable<ProcessingResult> cir
   ) {
      if (cir.getReturnValue() == ProcessingResult.PASS && shouldCustomHold(transported, deployer)) {
         cir.setReturnValue(ProcessingResult.HOLD);
      }
   }

   @Inject(
      method = {"whenItemHeld"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false
   )
   private static void whenItemHeldReturn(
      TransportedItemStack transported,
      TransportedItemStackHandlerBehaviour handler,
      DeployerBlockEntity deployer,
      CallbackInfoReturnable<ProcessingResult> cir
   ) {
      if (cir.getReturnValue() == ProcessingResult.PASS && shouldCustomHold(transported, deployer)) {
         ProcessingResult result = tryActivate(transported, handler, deployer);
         cir.setReturnValue(result);
      }
   }

   @Unique
   private static boolean shouldCustomHold(TransportedItemStack transported, DeployerBlockEntity deployer) {
      if (deployer.getPlayer() == null) {
         return false;
      } else {
         ItemStack handStack = deployer.getPlayer().getMainHandItem();
         if (handStack.isEmpty()) {
            return false;
         } else {
            ItemStack beltStack = transported.stack;
            if (beltStack.isEmpty()) {
               return false;
            } else {
               boolean isCopper = handStack.getItem() == AllItems.COPPER_SHEET.get();
               boolean isGunpowder = handStack.getItem() == Items.GUNPOWDER;
               if (beltStack.getItem() == SentryRegistry.UNFINISHED_AMMO.get()) {
                  if (beltStack.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
                     return false;
                  }

                  if (isCopper && UnfinishedAmmoItem.getCopperSheets(beltStack) >= 10) {
                     return false;
                  }

                  if (isGunpowder && UnfinishedAmmoItem.hasGunpowder(beltStack)) {
                     return false;
                  }

                  if (isCopper || isGunpowder) {
                     return true;
                  }
               }

               return IAmmo.getIAmmoOrNull(handStack) != null && beltStack.getItem() instanceof IAmmoBox;
            }
         }
      }
   }

   @Unique
   private static ProcessingResult tryActivate(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer) {
      if (deployer.getPlayer() == null) {
         return ProcessingResult.PASS;
      } else {
         ItemStack handStack = deployer.getPlayer().getMainHandItem();
         if (handStack.isEmpty()) {
            return ProcessingResult.PASS;
         } else {
            ItemStack beltStack = transported.stack;
            long gameTime = deployer.getLevel().getGameTime();
            if (handStack.getItem() == AllItems.COPPER_SHEET.get()) {
               if (beltStack.getItem() != SentryRegistry.UNFINISHED_AMMO.get()) {
                  return ProcessingResult.PASS;
               } else if (beltStack.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
                  return ProcessingResult.PASS;
               } else if (UnfinishedAmmoItem.getCopperSheets(beltStack) >= 10) {
                  return ProcessingResult.PASS;
               } else {
                  CompoundTag tag = ItemNBTHelper.getOrCreateTag(beltStack);
                  if (gameTime - tag.getLong("SMAPCooldown") < 30L) {
                     return ProcessingResult.HOLD;
                  } else {
                     tag.putLong("SMAPCooldown", gameTime);
                     int currentCopper = UnfinishedAmmoItem.getCopperSheets(beltStack);
                     UnfinishedAmmoItem.setCopperSheets(beltStack, currentCopper + 1);
                     handStack.shrink(1);
                     TransportedItemStack newTransported = transported.copy();
                     newTransported.stack = beltStack.copy();
                     handler.handleProcessingOnItem(transported, TransportedResult.convertTo(newTransported));
                     return ProcessingResult.HOLD;
                  }
               }
            } else if (handStack.getItem() == Items.GUNPOWDER) {
               if (beltStack.getItem() != SentryRegistry.UNFINISHED_AMMO.get()) {
                  return ProcessingResult.PASS;
               } else if (beltStack.has(AllDataComponents.SEQUENCED_ASSEMBLY)) {
                  return ProcessingResult.PASS;
               } else if (UnfinishedAmmoItem.hasGunpowder(beltStack)) {
                  return ProcessingResult.PASS;
               } else {
                  CompoundTag tag = ItemNBTHelper.getOrCreateTag(beltStack);
                  if (gameTime - tag.getLong("SMAPCooldown") < 120L) {
                     return ProcessingResult.HOLD;
                  } else {
                     tag.putLong("SMAPCooldown", gameTime);
                     UnfinishedAmmoItem.setGunpowderAdded(beltStack);
                     handStack.shrink(1);
                     TransportedItemStack newTransported = transported.copy();
                     newTransported.stack = beltStack.copy();
                     handler.handleProcessingOnItem(transported, TransportedResult.convertTo(newTransported));
                     return ProcessingResult.HOLD;
                  }
               }
            } else {
               IAmmo heldAmmo = IAmmo.getIAmmoOrNull(handStack);
               if (heldAmmo != null && beltStack.getItem() instanceof IAmmoBox) {
                  ItemStack newBoxStack = beltStack.copy();
                  IAmmoBox boxItem = (IAmmoBox)newBoxStack.getItem();
                  ResourceLocation boxId = boxItem.getAmmoId(newBoxStack);
                  ResourceLocation bulletId = heldAmmo.getAmmoId(handStack);
                  boolean isBoxEmpty = boxId == null || boxId.toString().equals("tacz:empty");
                  if (!isBoxEmpty && !boxId.equals(bulletId)) {
                     return ProcessingResult.PASS;
                  } else {
                     int maxCapacity = getMaxAmmoCount(boxItem, newBoxStack);
                     int currentCount = boxItem.getAmmoCount(newBoxStack);
                     if (currentCount >= maxCapacity) {
                        return ProcessingResult.PASS;
                     } else {
                        if (isBoxEmpty) {
                           boxItem.setAmmoId(newBoxStack, bulletId);
                        }

                        int space = maxCapacity - currentCount;
                        int toAdd = Math.min(space, handStack.getCount());
                        boxItem.setAmmoCount(newBoxStack, currentCount + toAdd);
                        CompoundTag tag = ItemNBTHelper.getOrCreateTag(newBoxStack);
                        if (gameTime - tag.getLong("SMAPCooldown") < 60L) {
                           return ProcessingResult.HOLD;
                        } else {
                           tag.putLong("SMAPCooldown", gameTime);
                           ItemNBTHelper.setTag(newBoxStack, tag);
                           handStack.shrink(toAdd);
                           TransportedItemStack newTransported = transported.copy();
                           newTransported.stack = newBoxStack.copy();
                           handler.handleProcessingOnItem(transported, TransportedResult.convertTo(newTransported));
                           return ProcessingResult.HOLD;
                        }
                     }
                  }
               } else {
                  return ProcessingResult.PASS;
               }
            }
         }
      }
   }

   @Unique
   private static int getMaxAmmoCount(IAmmoBox box, ItemStack stack) {
      if (box.isCreative(stack)) {
         return Integer.MAX_VALUE;
      } else {
         int level = box.getAmmoLevel(stack);

         return switch (level) {
            case 0 -> 180;
            case 1 -> 360;
            case 2 -> 540;
            default -> 180;
         };
      }
   }
}

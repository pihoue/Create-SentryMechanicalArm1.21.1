package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({DeployerHandler.class})
public class DeployerHandlerMixin {
   @Unique
   private static final long FILL_COOLDOWN = 60L;

   @Inject(
      method = {"shouldActivate"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private static void universalScan(ItemStack held, Level world, BlockPos targetPos, Direction facing, CallbackInfoReturnable<Boolean> cir) {
      if (!held.isEmpty() && IAmmo.getIAmmoOrNull(held) != null) {
         if (world.isEmptyBlock(targetPos)) {
            cir.setReturnValue(false);
         } else {
            TransportedItemStackHandlerBehaviour behaviour = (TransportedItemStackHandlerBehaviour)BlockEntityBehaviour.get(
               world, targetPos, TransportedItemStackHandlerBehaviour.TYPE
            );
            if (behaviour != null) {
               AtomicBoolean foundValidTarget = new AtomicBoolean(false);
               long posHash = targetPos.asLong();
               long gameTime = world.getGameTime();
               boolean isDepot = AllBlocks.DEPOT.has(world.getBlockState(targetPos));
               behaviour.handleCenteredProcessingOnAllItems(0.9F, transported -> {
                  if (foundValidTarget.get()) {
                     return TransportedResult.doNothing();
                  } else {
                     ItemStack stack = transported.stack;
                     if (stack.getItem() == AllItems.COPPER_SHEET.get()) {
                        foundValidTarget.set(true);
                     }

                     if (!stack.isEmpty() && stack.getItem() instanceof IAmmoBox box) {
                        CompoundTag tag = ItemNBTHelper.getTag(stack);
                        if (!isDepot && tag != null && tag.contains("DeployerCooldownPos") && tag.getLong("DeployerCooldownPos") == posHash) {
                           long unlockTime = tag.getLong("DeployerCooldownTime");
                           if (gameTime < unlockTime) {
                              return TransportedResult.doNothing();
                           }
                        }

                        if (box.getAmmoCount(stack) >= getMaxAmmoCount(box, stack)) {
                           return TransportedResult.doNothing();
                        }

                        foundValidTarget.set(true);
                     }

                     return TransportedResult.doNothing();
                  }
               });
               if (foundValidTarget.get()) {
                  cir.setReturnValue(true);
               }
            }
         }
      }
   }

   @Inject(
      method = {"activate"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private static void executeUniversalAmmoFill(
      DeployerFakePlayer player, Vec3 vec, BlockPos clickedPos, Vec3 extensionVector, @Coerce Object mode, CallbackInfo ci
   ) {
      Level level = player.level();
      if (!level.isClientSide) {
         ItemStack handStack = player.getMainHandItem();
         IAmmo ammoItem = IAmmo.getIAmmoOrNull(handStack);
         if (ammoItem != null) {
            TransportedItemStackHandlerBehaviour behaviour = (TransportedItemStackHandlerBehaviour)BlockEntityBehaviour.get(
               level, clickedPos, TransportedItemStackHandlerBehaviour.TYPE
            );
            if (behaviour != null) {
               AtomicBoolean processed = new AtomicBoolean(false);
               long posHash = clickedPos.asLong();
               long cooldownTime = level.getGameTime() + 60L;
               behaviour.handleCenteredProcessingOnAllItems(0.9F, transported -> {
                  if (processed.get()) {
                     return TransportedResult.doNothing();
                  } else {
                     ItemStack stackOnBelt = transported.stack;
                     if (stackOnBelt.getItem() == AllItems.COPPER_SHEET.get()) {
                        ResourceLocation ammoId = ammoItem.getAmmoId(handStack);
                        if (ammoId != null) {
                           int copperCount = stackOnBelt.getCount();
                           int processCount = copperCount / 3 * 3;
                           if (processCount == 0) {
                              return TransportedResult.doNothing();
                           }

                           ItemStack unfinished = new ItemStack((ItemLike)SentryRegistry.UNFINISHED_AMMO.get());
                           CompoundTag tag = new CompoundTag();
                           tag.putString("AmmoId", ammoId.toString());
                           tag.putInt("CopperSheets", 0);
                           tag.putBoolean("GunpowderAdded", false);
                           ItemNBTHelper.setTag(unfinished, tag);
                           unfinished.setCount(processCount);
                           handStack.shrink(processCount);
                           TransportedItemStack newTransported = transported.copy();
                           newTransported.stack = unfinished;
                           processed.set(true);
                           return TransportedResult.convertTo(newTransported);
                        }
                     }

                     if (!(stackOnBelt.getItem() instanceof IAmmoBox)) {
                        return TransportedResult.doNothing();
                     } else {
                        ItemStack newBoxStack = stackOnBelt.copy();
                        IAmmoBox boxItem = (IAmmoBox)newBoxStack.getItem();
                        ResourceLocation boxId = boxItem.getAmmoId(newBoxStack);
                        ResourceLocation bulletId = ammoItem.getAmmoId(handStack);
                        boolean isBoxEmpty = boxId == null || boxId.toString().equals("tacz:empty");
                        if (!isBoxEmpty && !boxId.equals(bulletId)) {
                           return TransportedResult.doNothing();
                        } else {
                           int maxCapacity = getMaxAmmoCount(boxItem, newBoxStack);
                           int currentCount = boxItem.getAmmoCount(newBoxStack);
                           if (currentCount >= maxCapacity) {
                              return TransportedResult.doNothing();
                           } else {
                              if (isBoxEmpty) {
                                 boxItem.setAmmoId(newBoxStack, bulletId);
                              }

                              int space = maxCapacity - currentCount;
                              int handCount = handStack.getCount();
                              int toAdd = Math.min(space, handCount);
                              boxItem.setAmmoCount(newBoxStack, currentCount + toAdd);
                              CompoundTag tag = ItemNBTHelper.getOrCreateTag(newBoxStack);
                              tag.putLong("DeployerCooldownPos", posHash);
                              tag.putLong("DeployerCooldownTime", cooldownTime);
                              ItemNBTHelper.setTag(newBoxStack, tag);
                              if (!player.isCreative()) {
                                 handStack.shrink(toAdd);
                              }

                              processed.set(true);
                              TransportedItemStack newTransported = transported.copy();
                              newTransported.stack = newBoxStack;
                              return TransportedResult.convertTo(newTransported);
                           }
                        }
                     }
                  }
               });
               if (processed.get()) {
                  ci.cancel();
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

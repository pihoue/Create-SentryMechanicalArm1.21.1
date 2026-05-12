package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(DeployerHandler.class)
public class DeployerHandlerMixin {

    @Unique
    private static final long FILL_COOLDOWN = 60L;

    @Inject(method = "shouldActivate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void universalScan(ItemStack held, Level world, BlockPos targetPos, Direction facing, CallbackInfoReturnable<Boolean> cir) {
        if (held.isEmpty() || IAmmo.getIAmmoOrNull(held) == null) return;

        if (world.isEmptyBlock(targetPos)) {
            cir.setReturnValue(false);
            return;
        }

        TransportedItemStackHandlerBehaviour behaviour =
                BlockEntityBehaviour.get(world, targetPos, TransportedItemStackHandlerBehaviour.TYPE);

        if (behaviour != null) {
            AtomicBoolean foundValidTarget  = new AtomicBoolean(false);

            long posHash = targetPos.asLong();
            long gameTime = world.getGameTime();
            boolean isDepot = AllBlocks.DEPOT.has(world.getBlockState(targetPos));

            behaviour.handleCenteredProcessingOnAllItems(0.9f, transported -> {
                if (foundValidTarget.get()) return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();

                ItemStack stack = transported.stack;

                if (stack.getItem().equals(com.simibubi.create.AllItems.COPPER_SHEET.get())) {
                    foundValidTarget.set(true);
                    return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
                }

                if (!stack.isEmpty() && stack.getItem() instanceof IAmmoBox box) {
                    if (!isDepot) {
                        CompoundTag tag = stack.getTag();
                        if (tag != null && tag.contains("DeployerCooldownPos") && tag.getLong("DeployerCooldownPos") == posHash) {
                            long unlockTime = tag.getLong("DeployerCooldownTime");
                            if (gameTime < unlockTime) {
                                return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
                            }
                        }
                    }

                    if (box.getAmmoCount(stack) >= getMaxAmmoCount(box, stack)) {
                        return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
                    }

                    foundValidTarget.set(true);
                }
                return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
            });

            if (foundValidTarget.get()) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "activate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void executeUniversalAmmoFill(
            DeployerFakePlayer player,
            Vec3 vec,
            BlockPos clickedPos,
            Vec3 extensionVector,
            @Coerce Object mode,
            CallbackInfo ci
    ) {
        Level level = player.level();
        if (level.isClientSide) return;

        ItemStack handStack = player.getMainHandItem();
        IAmmo ammoItem = IAmmo.getIAmmoOrNull(handStack);
        if (ammoItem == null) return;

        TransportedItemStackHandlerBehaviour behaviour =
                BlockEntityBehaviour.get(level, clickedPos, TransportedItemStackHandlerBehaviour.TYPE);
        if (behaviour == null) return;

        AtomicBoolean processed = new AtomicBoolean(false);
        long posHash = clickedPos.asLong();
        long cooldownTime = level.getGameTime() + FILL_COOLDOWN;

        behaviour.handleCenteredProcessingOnAllItems(0.9f, transported -> {
            if (processed.get()) return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();

            ItemStack stackOnBelt = transported.stack;

 
            if (stackOnBelt.getItem().equals(com.simibubi.create.AllItems.COPPER_SHEET.get())) {
                ResourceLocation ammoId = ammoItem.getAmmoId(handStack);
                if (ammoId != null) {
 
                    int copperCount = stackOnBelt.getCount();
                    int bulletCount = handStack.getCount();
                    int processCount = Math.min(copperCount, bulletCount);

                    ItemStack unfinished = new ItemStack(euphy.upo.sentrymechanicalarm.registry.SentryRegistry.UNFINISHED_AMMO.get());
                    CompoundTag tag = new CompoundTag();
                    tag.putString("AmmoId", ammoId.toString());
                    unfinished.setTag(tag);
                    unfinished.setCount(processCount);
                    handStack.shrink(processCount);
 
                    if (processCount < copperCount) {
                        ItemStack remainder = stackOnBelt.copy();
                        remainder.setCount(copperCount - processCount); 
                        Block.popResource(level, clickedPos, remainder); 
                    }
 
                    TransportedItemStack newTransported = transported.copy();
                    newTransported.stack = unfinished;

                    processed.set(true);
                    return TransportedItemStackHandlerBehaviour.TransportedResult.convertTo(newTransported);
                }
            }

            if (stackOnBelt.isEmpty() || !(stackOnBelt.getItem() instanceof IAmmoBox)) {
                return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
            }

 
            ItemStack newBoxStack = stackOnBelt.copy();
            IAmmoBox boxItem = (IAmmoBox) newBoxStack.getItem();

            ResourceLocation boxId = boxItem.getAmmoId(newBoxStack);
            ResourceLocation bulletId = ammoItem.getAmmoId(handStack);
            boolean isBoxEmpty = boxId == null || boxId.toString().equals("tacz:empty");

            if (!isBoxEmpty && !boxId.equals(bulletId)) {
                return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
            }

            int maxCapacity = getMaxAmmoCount(boxItem, newBoxStack);
            int currentCount = boxItem.getAmmoCount(newBoxStack);

            if (currentCount >= maxCapacity) {
                return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
            }

            if (isBoxEmpty) {
                boxItem.setAmmoId(newBoxStack, bulletId);
            }

            int space = maxCapacity - currentCount;
            int handCount = handStack.getCount();
            int toAdd = Math.min(space, handCount);

            boxItem.setAmmoCount(newBoxStack, currentCount + toAdd);
            CompoundTag tag = newBoxStack.getOrCreateTag();
            tag.putLong("DeployerCooldownPos", posHash);
            tag.putLong("DeployerCooldownTime", cooldownTime);

            if (!player.isCreative()) {
                handStack.shrink(toAdd);
            }
            processed.set(true);
 
            TransportedItemStack newTransported = transported.copy();
            newTransported.stack = newBoxStack;
            return TransportedItemStackHandlerBehaviour.TransportedResult.convertTo(newTransported);
        });

        if (processed.get()) {
            ci.cancel();
        }
    }

    @Unique
    private static int getMaxAmmoCount(IAmmoBox box, ItemStack stack) {
        if (box.isCreative(stack)) return Integer.MAX_VALUE;
        int level = box.getAmmoLevel(stack);
        return switch (level) {
            case 0 -> 180;
            case 1 -> 360;
            case 2 -> 540;
            default -> 180;
        };
    }
}
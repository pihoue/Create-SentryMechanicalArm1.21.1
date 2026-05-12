package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeltDeployerCallbacks.class)
public class BeltDeployerCallbacksMixin {

    @Inject(method = "onItemReceived", at = @At("HEAD"), cancellable = true, remap = false)
    private static void holdAmmoBox(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer, CallbackInfoReturnable<BeltProcessingBehaviour.ProcessingResult> cir) {
        if (shouldHold(transported, deployer, handler)) {
            cir.setReturnValue(BeltProcessingBehaviour.ProcessingResult.HOLD);
        }
    }

    @Inject(method = "whenItemHeld", at = @At("HEAD"), cancellable = true, remap = false)
    private static void keepHoldingAmmoBox(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer, CallbackInfoReturnable<BeltProcessingBehaviour.ProcessingResult> cir) {
        if (shouldHold(transported, deployer, handler)) {
            cir.setReturnValue(BeltProcessingBehaviour.ProcessingResult.HOLD);
        }
    }
    private static boolean shouldHold(TransportedItemStack transported, DeployerBlockEntity deployer, TransportedItemStackHandlerBehaviour handler) {
 
        if (deployer.getPlayer() == null) return false;
        ItemStack handStack = deployer.getPlayer().getMainHandItem();
        if (handStack.isEmpty() || IAmmo.getIAmmoOrNull(handStack) == null) return false;

 
        ItemStack boxStack = transported.stack;
        if (boxStack.isEmpty() || !(boxStack.getItem() instanceof IAmmoBox boxItem)) return false;

 
        if (boxItem.getAmmoCount(boxStack) >= getMaxAmmoCount(boxItem, boxStack)) {
            return false;
        }

        if (handler.blockEntity != null) {
            Level level = handler.blockEntity.getLevel();
            if (level != null) {
 
                BlockPos targetPos = handler.blockEntity.getBlockPos();
                long posHash = targetPos.asLong();
                long gameTime = level.getGameTime();

                CompoundTag tag = boxStack.getTag();
                if (tag != null && tag.contains("DeployerCooldownPos") && tag.getLong("DeployerCooldownPos") == posHash) {
                    long unlockTime = tag.getLong("DeployerCooldownTime");
                    if (gameTime < unlockTime) {
                        return false; 
                    }
                }
            }
        }

 
        return true;
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
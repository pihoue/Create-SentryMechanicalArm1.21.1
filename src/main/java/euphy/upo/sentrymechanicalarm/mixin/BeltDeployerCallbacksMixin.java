package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeltDeployerCallbacks.class)
public class BeltDeployerCallbacksMixin {

    @Inject(method = "onItemReceived", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onItemReceivedReturn(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer, CallbackInfoReturnable<BeltProcessingBehaviour.ProcessingResult> cir) {
        if (cir.getReturnValue() == BeltProcessingBehaviour.ProcessingResult.PASS && shouldCustomHold(transported, deployer)) {
            cir.setReturnValue(BeltProcessingBehaviour.ProcessingResult.HOLD);
        }
    }

    @Inject(method = "whenItemHeld", at = @At("RETURN"), cancellable = true, remap = false)
    private static void whenItemHeldReturn(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer, CallbackInfoReturnable<BeltProcessingBehaviour.ProcessingResult> cir) {
        if (cir.getReturnValue() == BeltProcessingBehaviour.ProcessingResult.PASS && shouldCustomHold(transported, deployer)) {
            BeltProcessingBehaviour.ProcessingResult result = tryActivate(transported, handler, deployer);
            cir.setReturnValue(result);
        }
    }

    @Unique
    private static boolean shouldCustomHold(TransportedItemStack transported, DeployerBlockEntity deployer) {
        if (deployer.getPlayer() == null) return false;
        ItemStack handStack = deployer.getPlayer().getMainHandItem();
        if (handStack.isEmpty()) return false;

        ItemStack beltStack = transported.stack;
        if (beltStack.isEmpty()) return false;

        if (beltStack.getItem() != SentryRegistry.UNFINISHED_AMMO.get()) return false;
        if (beltStack.has(AllDataComponents.SEQUENCED_ASSEMBLY)) return false;

        boolean isCopper = handStack.getItem() == AllItems.COPPER_SHEET.get();
        boolean isGunpowder = handStack.getItem() == Items.GUNPOWDER;

        if (isCopper && UnfinishedAmmoItem.getCopperSheets(beltStack) >= UnfinishedAmmoItem.MAX_COPPER_SHEETS) return false;
        if (isGunpowder && UnfinishedAmmoItem.hasGunpowder(beltStack)) return false;

        if (isCopper || isGunpowder) return true;

        if (IAmmo.getIAmmoOrNull(handStack) != null) {
            return beltStack.getItem() instanceof IAmmoBox;
        }

        return false;
    }

    @Unique
    private static BeltProcessingBehaviour.ProcessingResult tryActivate(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, DeployerBlockEntity deployer) {
        if (deployer.getPlayer() == null) return BeltProcessingBehaviour.ProcessingResult.PASS;
        ItemStack handStack = deployer.getPlayer().getMainHandItem();
        if (handStack.isEmpty()) return BeltProcessingBehaviour.ProcessingResult.PASS;

        ItemStack beltStack = transported.stack;
        long gameTime = deployer.getLevel().getGameTime();

        if (handStack.getItem() == AllItems.COPPER_SHEET.get()) {
            if (beltStack.getItem() != SentryRegistry.UNFINISHED_AMMO.get()) return BeltProcessingBehaviour.ProcessingResult.PASS;
            if (beltStack.has(AllDataComponents.SEQUENCED_ASSEMBLY)) return BeltProcessingBehaviour.ProcessingResult.PASS;
            if (UnfinishedAmmoItem.getCopperSheets(beltStack) >= UnfinishedAmmoItem.MAX_COPPER_SHEETS) return BeltProcessingBehaviour.ProcessingResult.PASS;

            CompoundTag tag = ItemNBTHelper.getOrCreateTag(beltStack);
            if (gameTime - tag.getLong("SMAPCooldown") < 30) return BeltProcessingBehaviour.ProcessingResult.HOLD;
            tag.putLong("SMAPCooldown", gameTime);

            int currentCopper = UnfinishedAmmoItem.getCopperSheets(beltStack);
            UnfinishedAmmoItem.setCopperSheets(beltStack, currentCopper + 1);
            handStack.shrink(1);

            TransportedItemStack newTransported = transported.copy();
            newTransported.stack = beltStack.copy();
            handler.handleProcessingOnItem(transported, TransportedResult.convertTo(newTransported));

            return BeltProcessingBehaviour.ProcessingResult.HOLD;
        }

        if (handStack.getItem() == Items.GUNPOWDER) {
            if (beltStack.getItem() != SentryRegistry.UNFINISHED_AMMO.get()) return BeltProcessingBehaviour.ProcessingResult.PASS;
            if (beltStack.has(AllDataComponents.SEQUENCED_ASSEMBLY)) return BeltProcessingBehaviour.ProcessingResult.PASS;
            if (UnfinishedAmmoItem.hasGunpowder(beltStack)) return BeltProcessingBehaviour.ProcessingResult.PASS;

            CompoundTag tag = ItemNBTHelper.getOrCreateTag(beltStack);
            if (gameTime - tag.getLong("SMAPCooldown") < 120) return BeltProcessingBehaviour.ProcessingResult.HOLD;
            tag.putLong("SMAPCooldown", gameTime);

            UnfinishedAmmoItem.setGunpowderAdded(beltStack);
            handStack.shrink(1);

            TransportedItemStack newTransported = transported.copy();
            newTransported.stack = beltStack.copy();
            handler.handleProcessingOnItem(transported, TransportedResult.convertTo(newTransported));

            return BeltProcessingBehaviour.ProcessingResult.HOLD;
        }

        return BeltProcessingBehaviour.ProcessingResult.PASS;
    }
}

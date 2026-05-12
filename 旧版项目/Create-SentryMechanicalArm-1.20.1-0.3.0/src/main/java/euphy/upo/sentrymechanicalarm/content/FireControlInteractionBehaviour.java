package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.apache.commons.lang3.tuple.MutablePair;

public class FireControlInteractionBehaviour extends MovingInteractionBehaviour {

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos, AbstractContraptionEntity contraptionEntity) {
        Contraption contraption = contraptionEntity.getContraption();
        MovementContext context = null;
        for (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> actor : contraption.getActors()) {
            if (actor.left.pos().equals(localPos)) {
                context = actor.right;
                break;
            }
        }
        if (context == null) return false;

        ItemStack heldItem = player.getItemInHand(activeHand);
        ItemStackHandler inventory = new ItemStackHandler(1);
        if (context.data.contains("SyncedInventory")) {
            inventory.deserializeNBT(context.data.getCompound("SyncedInventory"));
        }

        boolean success = false;
        boolean isClipboard = heldItem.getItem() instanceof FireControlClipboardItem;

        if (isClipboard && inventory.getStackInSlot(0).isEmpty()) {
            ItemStack toInsert = heldItem.copy();
            toInsert.setCount(1);
            inventory.setStackInSlot(0, toInsert);

            if (!player.isCreative()) {
                if (!player.level().isClientSide) {
                    heldItem.shrink(1);
                }
            }

            player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0f, 1.0f);
            success = true;
        }
        else if (heldItem.isEmpty() && !inventory.getStackInSlot(0).isEmpty()) {
            ItemStack extracted = inventory.getStackInSlot(0);

            if (!player.level().isClientSide) {
                ItemHandlerHelper.giveItemToPlayer(player, extracted.copy());
            }

            inventory.setStackInSlot(0, ItemStack.EMPTY);
            player.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0f, 1.0f);
            success = true;
        }

        if (success) {
            context.data.put("SyncedInventory", inventory.serializeNBT());
            if (context.world.isClientSide) {
                FireControlMovementBehaviour.FireControlData data =
                        FireControlMovementBehaviour.getOrInitData(context);

                FireControlMovementBehaviour.refreshLogicData(context, data);
            }

            if (!player.level().isClientSide) {
                FireControlMovementBehaviour.notifyConnectedSentries(context);

                context.blockEntityData.put("Inventory", inventory.serializeNBT());
            }

            return true;
        }

        return false;
    }
}
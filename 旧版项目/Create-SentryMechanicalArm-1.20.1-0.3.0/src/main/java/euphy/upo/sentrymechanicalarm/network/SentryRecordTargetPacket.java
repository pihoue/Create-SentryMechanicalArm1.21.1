package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SentryRecordTargetPacket {

    private final int entityId;

    public SentryRecordTargetPacket(int entityId) {
        this.entityId = entityId;
    }

    public SentryRecordTargetPacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readInt();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();

            if (mainHand.getItem() != Items.SPYGLASS || !(offHand.getItem() instanceof FireControlClipboardItem)) {
                return;
            }
            Entity target = player.level().getEntity(this.entityId);

            if (target != null && target.distanceToSqr(player) < 65536) {

                String name = target.getName().getString();
                addNameToListLogic(offHand, name, player);
            }
        });
        context.setPacketHandled(true);
    }

    private static void addNameToListLogic(ItemStack stack, String name, ServerPlayer player) {
        CompoundTag tag = stack.getOrCreateTag();
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
            player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.added_target", name), true);
        } else {
            player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.already_on_list", name), true);
        }
    }
}
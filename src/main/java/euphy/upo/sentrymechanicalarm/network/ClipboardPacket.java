package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record ClipboardPacket(int operation, int index) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "clipboard");
    public static final CustomPacketPayload.Type<ClipboardPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, ClipboardPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClipboardPacket::operation,
            ByteBufCodecs.VAR_INT, ClipboardPacket::index,
            ClipboardPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClipboardPacket packet, ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        if (packet.operation() == 0) {
            if (mainHand.getItem() instanceof FireControlClipboardItem ||
                offHand.getItem() instanceof FireControlClipboardItem) {
                ItemStack stack = mainHand.getItem() instanceof FireControlClipboardItem ? mainHand : offHand;
                CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
                boolean current = tag.getBoolean("WhitelistMode");
                tag.putBoolean("WhitelistMode", !current);
                ItemNBTHelper.setTag(stack, tag);
            }
        } else if (packet.operation() == 1) {
            if (mainHand.getItem() instanceof FireControlClipboardItem ||
                offHand.getItem() instanceof FireControlClipboardItem) {
                ItemStack stack = mainHand.getItem() instanceof FireControlClipboardItem ? mainHand : offHand;
                CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);

                if (tag.contains("TargetList", 9)) {
                    ListTag list = tag.getList("TargetList", 8);
                    int index = packet.index();
                    if (index >= 0 && index < list.size()) {
                        list.remove(index);
                        ItemNBTHelper.setTag(stack, tag);
                    }
                }
            }
        }
    }
}
package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketToggleClipboardMode {

    public PacketToggleClipboardMode() {}

    public PacketToggleClipboardMode(FriendlyByteBuf buffer) {}

    public void encode(FriendlyByteBuf buffer) {}

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof FireControlClipboardItem) {
                CompoundTag tag = stack.getOrCreateTag();
                boolean current = tag.getBoolean("WhitelistMode");
                tag.putBoolean("WhitelistMode", !current);
                stack.setTag(tag);
            }
        });
        context.get().setPacketHandled(true);
    }
}
package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketClearTarget {
    private final int index;

    public PacketClearTarget(int index) {
        this.index = index;
    }

    public PacketClearTarget(FriendlyByteBuf buf) {
        this.index = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.index);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof FireControlClipboardItem) {
 
                    if (stack.hasTag() && stack.getTag().contains("TargetList", 9)) { 
                        ListTag list = stack.getTag().getList("TargetList", 8); 
                        if (index >= 0 && index < list.size()) {
                            list.remove(index);
 
                        }
                    }
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}
package euphy.upo.sentrymechanicalarm.network;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import euphy.upo.sentrymechanicalarm.content.SentryMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SentryClientShootPacket {
    private final int contraptionId;
    private final BlockPos localPos;
    private final float yaw;
    private final float pitch;
    private final double distance;

    public SentryClientShootPacket(int contraptionId, BlockPos localPos, float yaw, float pitch, double distance) {
        this.contraptionId = contraptionId;
        this.localPos = localPos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.distance = distance;
    }

    public static void encode(SentryClientShootPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.contraptionId);
        buffer.writeBlockPos(msg.localPos);
        buffer.writeFloat(msg.yaw);
        buffer.writeFloat(msg.pitch);
        buffer.writeDouble(msg.distance);
    }

    public static SentryClientShootPacket decode(FriendlyByteBuf buffer) {
        return new SentryClientShootPacket(
                buffer.readInt(),
                buffer.readBlockPos(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readDouble()
        );
    }

    public static void handle(SentryClientShootPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Entity entity = sender.level().getEntity(msg.contraptionId);
            if (entity instanceof AbstractContraptionEntity ace) {
                Contraption contraption = ace.getContraption();
                if (contraption != null) {
                    for (var actor : contraption.getActors()) {
                        if (actor.getKey().pos().equals(msg.localPos)) {
                            SentryMovementBehaviour.handleClientShootPacket(
                                    actor.getValue(), ace, msg.yaw, msg.pitch, msg.distance
                            );
                            break;
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
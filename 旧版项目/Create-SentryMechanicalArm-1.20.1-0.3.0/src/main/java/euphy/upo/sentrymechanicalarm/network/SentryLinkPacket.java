package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SentryLinkPacket {
    private final BlockPos posA;
    private final BlockPos posB;

    public SentryLinkPacket(BlockPos posA, BlockPos posB) {
        this.posA = posA;
        this.posB = posB;
    }

    public SentryLinkPacket(FriendlyByteBuf buf) {
        this.posA = buf.readBlockPos();
        this.posB = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(posA);
        buf.writeBlockPos(posB);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null) return;
            Level level = player.level();

            if (posA.distSqr(posB) > 100) return;
 
            if (level.getBlockEntity(posA) instanceof SentryArmBlockEntity sentry) {
 
                if (posA.distSqr(posB) <= 36.0) {
                    sentry.setConnectedFireControl(posB);
 
                    level.playSound(null, posA, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                } else {
                    player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.distance_too_far"), true);
                }
            } else if (level.getBlockEntity(posB) instanceof SentryArmBlockEntity sentry) {
 
                if (posB.distSqr(posA) <= 36.0) {
                    sentry.setConnectedFireControl(posA);
                    level.playSound(null, posB, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                } else {
                    player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.distance_too_far"), true);
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}
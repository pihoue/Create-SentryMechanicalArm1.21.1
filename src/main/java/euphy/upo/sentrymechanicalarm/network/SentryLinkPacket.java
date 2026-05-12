package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public record SentryLinkPacket(BlockPos posA, BlockPos posB) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_link");
    public static final CustomPacketPayload.Type<SentryLinkPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SentryLinkPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SentryLinkPacket::posA,
            BlockPos.STREAM_CODEC, SentryLinkPacket::posB,
            SentryLinkPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SentryLinkPacket packet, ServerPlayer player) {
        Level level = player.level();
        if (packet.posA().distSqr(packet.posB()) > 100) return;

        if (level.getBlockEntity(packet.posA()) instanceof SentryArmBlockEntity sentry) {
            if (packet.posA().distSqr(packet.posB()) <= 36.0) {
                sentry.setConnectedFireControl(packet.posB());
                level.playSound(null, packet.posA(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f);
            } else {
                player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.distance_too_far"), true);
            }
        } else if (level.getBlockEntity(packet.posB()) instanceof SentryArmBlockEntity sentry) {
            if (packet.posB().distSqr(packet.posA()) <= 36.0) {
                sentry.setConnectedFireControl(packet.posA());
                level.playSound(null, packet.posB(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f);
            } else {
                player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.distance_too_far"), true);
            }
        }
    }
}
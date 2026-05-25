package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public record SentryMarkPosPacket(BlockPos fcPos, Vec3 worldPos, int contraptionEntityId, BlockPos localPos) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_mark_pos");
    public static final CustomPacketPayload.Type<SentryMarkPosPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SentryMarkPosPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SentryMarkPosPacket::fcPos,
            ByteBufCodecs.fromCodec(Vec3.CODEC), SentryMarkPosPacket::worldPos,
            ByteBufCodecs.VAR_INT, SentryMarkPosPacket::contraptionEntityId,
            BlockPos.STREAM_CODEC, SentryMarkPosPacket::localPos,
            SentryMarkPosPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SentryMarkPosPacket packet, ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = packet.fcPos();
        if (!level.isLoaded(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BlazeFireControlBlockEntity fc) {
            fc.setMarkedPos(packet.worldPos(), packet.contraptionEntityId(), packet.localPos());
        }
    }
}

package euphy.upo.sentrymechanicalarm.network;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import euphy.upo.sentrymechanicalarm.content.SentryMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public record SentryClientShootPacket(int contraptionId, BlockPos localPos, float yaw, float pitch, double distance) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_client_shoot");
    public static final CustomPacketPayload.Type<SentryClientShootPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SentryClientShootPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SentryClientShootPacket::contraptionId,
            BlockPos.STREAM_CODEC, SentryClientShootPacket::localPos,
            ByteBufCodecs.FLOAT, SentryClientShootPacket::yaw,
            ByteBufCodecs.FLOAT, SentryClientShootPacket::pitch,
            ByteBufCodecs.DOUBLE, SentryClientShootPacket::distance,
            SentryClientShootPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SentryClientShootPacket msg, ServerPlayer player) {
        euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("[SMB] packet received: contraptionId={}, localPos={}, yaw={}, pitch={}, dist={}",
            msg.contraptionId(), msg.localPos(), msg.yaw(), msg.pitch(), msg.distance());
        Entity entity = player.level().getEntity(msg.contraptionId());
        if (entity instanceof AbstractContraptionEntity ace) {
            Contraption contraption = ace.getContraption();
            if (contraption != null) {
                boolean found = false;
                for (var actor : contraption.getActors()) {
                    if (actor.getKey().pos().equals(msg.localPos())) {
                        found = true;
                        SentryMovementBehaviour.handleClientShootPacket(
                                actor.getValue(), ace, msg.yaw(), msg.pitch(), msg.distance()
                        );
                        break;
                    }
                }
                if (!found) {
                    euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("[SMB] packet: actor not found for localPos {}", msg.localPos());
                }
            } else {
                euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("[SMB] packet: contraption is null");
            }
        } else {
            euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.info("[SMB] packet: entity not found for id {}, type={}",
                msg.contraptionId(), entity != null ? entity.getClass().getSimpleName() : "null");
        }
    }
}
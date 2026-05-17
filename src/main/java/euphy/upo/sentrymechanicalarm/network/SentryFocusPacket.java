package euphy.upo.sentrymechanicalarm.network;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.FireControlMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.commons.lang3.tuple.MutablePair;

public record SentryFocusPacket(int contraptionEntityId, BlockPos localPos, int targetEntityId) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_focus");
    public static final CustomPacketPayload.Type<SentryFocusPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SentryFocusPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SentryFocusPacket::contraptionEntityId,
            BlockPos.STREAM_CODEC, SentryFocusPacket::localPos,
            ByteBufCodecs.VAR_INT, SentryFocusPacket::targetEntityId,
            SentryFocusPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SentryFocusPacket packet, ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = packet.localPos();

        if (packet.contraptionEntityId() != -1) {
            Entity entity = level.getEntity(packet.contraptionEntityId());
            if (!(entity instanceof AbstractContraptionEntity ace)) return;
            var contraption = ace.getContraption();
            if (contraption == null) return;

            for (MutablePair<?, MovementContext> actor : contraption.getActors()) {
                if (!actor.getValue().localPos.equals(pos)) continue;
                if (!(actor.getValue().temporaryData instanceof FireControlMovementBehaviour.FireControlData fcData))
                    continue;
                fcData.focusedEntityId = packet.targetEntityId();
                actor.getValue().data.putInt("FocusedEntityId", packet.targetEntityId());
                FireControlMovementBehaviour.notifyConnectedSentries(actor.getValue());
                return;
            }
            return;
        }

        if (!level.isLoaded(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BlazeFireControlBlockEntity fc) {
            fc.setFocusedEntity(packet.targetEntityId());
            fc.notifyConnectedSentries(false);
        }
    }
}

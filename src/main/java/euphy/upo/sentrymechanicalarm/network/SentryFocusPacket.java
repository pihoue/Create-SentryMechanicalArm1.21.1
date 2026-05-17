package euphy.upo.sentrymechanicalarm.network;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.FireControlMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

public record SentryFocusPacket(BlockPos fireControlPos, int targetEntityId) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_focus");
    public static final CustomPacketPayload.Type<SentryFocusPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SentryFocusPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SentryFocusPacket::fireControlPos,
            ByteBufCodecs.VAR_INT, SentryFocusPacket::targetEntityId,
            SentryFocusPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SentryFocusPacket packet, ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = packet.fireControlPos();
        if (!level.isLoaded(pos)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BlazeFireControlBlockEntity fc) {
            fc.setFocusedEntity(packet.targetEntityId());
            fc.notifyConnectedSentries(false);
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) return;
        AABB worldBounds = new AABB(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        for (AbstractContraptionEntity ace : serverLevel.getEntitiesOfClass(AbstractContraptionEntity.class, worldBounds)) {
            Contraption contraption = ace.getContraption();
            if (contraption == null) continue;

            for (var actor : contraption.getActors()) {
                if (!(actor.getValue().temporaryData instanceof FireControlMovementBehaviour.FireControlData fcData))
                    continue;
                fcData.focusedEntityId = packet.targetEntityId();
                actor.getValue().data.putInt("FocusedEntityId", packet.targetEntityId());
                return;
            }
        }
    }
}

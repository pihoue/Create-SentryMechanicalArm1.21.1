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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.tuple.MutablePair;

public record SentryFocusPacket(int contraptionEntityId, BlockPos localPos, int targetEntityId) implements CustomPacketPayload {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_focus");
   public static final Type<SentryFocusPacket> TYPE = new Type(ID);
   public static final StreamCodec<RegistryFriendlyByteBuf, SentryFocusPacket> CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT,
      SentryFocusPacket::contraptionEntityId,
      BlockPos.STREAM_CODEC,
      SentryFocusPacket::localPos,
      ByteBufCodecs.VAR_INT,
      SentryFocusPacket::targetEntityId,
      SentryFocusPacket::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(SentryFocusPacket packet, ServerPlayer player) {
      Level level = player.level();
      BlockPos pos = packet.localPos();
      if (packet.contraptionEntityId() != -1) {
         if (level.getEntity(packet.contraptionEntityId()) instanceof AbstractContraptionEntity ace) {
            Contraption contraption = ace.getContraption();
            if (contraption != null) {
               for (MutablePair<?, MovementContext> actor : contraption.getActors()) {
                  if (((MovementContext)actor.getValue()).localPos.equals(pos)
                     && ((MovementContext)actor.getValue()).temporaryData instanceof FireControlMovementBehaviour.FireControlData fcData) {
                     fcData.focusedEntityId = packet.targetEntityId();
                     ((MovementContext)actor.getValue()).data.putInt("FocusedEntityId", packet.targetEntityId());
                     FireControlMovementBehaviour.notifyConnectedSentries((MovementContext)actor.getValue());
                     return;
                  }
               }
            }
         }
      } else if (level.isLoaded(pos)) {
         if (level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity fc) {
            fc.setFocusedEntity(packet.targetEntityId());
            fc.notifyConnectedSentries(false);
         }
      }
   }
}

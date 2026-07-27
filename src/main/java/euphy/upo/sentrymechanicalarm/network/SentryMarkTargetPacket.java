package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public record SentryMarkTargetPacket(BlockPos fcPos, int entityId, boolean add) implements CustomPacketPayload {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_mark_target");
   public static final Type<SentryMarkTargetPacket> TYPE = new Type(ID);
   public static final StreamCodec<RegistryFriendlyByteBuf, SentryMarkTargetPacket> CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC,
      SentryMarkTargetPacket::fcPos,
      ByteBufCodecs.VAR_INT,
      SentryMarkTargetPacket::entityId,
      ByteBufCodecs.BOOL,
      SentryMarkTargetPacket::add,
      SentryMarkTargetPacket::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(SentryMarkTargetPacket packet, ServerPlayer player) {
      Level level = player.level();
      BlockPos pos = packet.fcPos();
      if (level.isLoaded(pos)) {
         if (level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity fc) {
            fc.setMarkedEntityId(packet.entityId());
         }
      }
   }
}

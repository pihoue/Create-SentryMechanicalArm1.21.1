package euphy.upo.sentrymechanicalarm.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/**
 * Synchronizes the server-authoritative target of one sentry actor to clients.
 * A target id of -1 means that the sentry currently has no target.
 */
public record SentryContraptionTargetPacket(
   int contraptionId,
   BlockPos localPos,
   int targetId,
   float baseAngle,
   float lowerArmAngle,
   float upperArmAngle,
   float headAngle
)
   implements CustomPacketPayload {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_contraption_target");
   public static final Type<SentryContraptionTargetPacket> TYPE = new Type<>(ID);
   public static final StreamCodec<RegistryFriendlyByteBuf, SentryContraptionTargetPacket> CODEC =
      new StreamCodec<RegistryFriendlyByteBuf, SentryContraptionTargetPacket>() {
         @Override
         public SentryContraptionTargetPacket decode(RegistryFriendlyByteBuf buf) {
            return new SentryContraptionTargetPacket(
               buf.readVarInt(),
               BlockPos.STREAM_CODEC.decode(buf),
               buf.readVarInt(),
               buf.readFloat(),
               buf.readFloat(),
               buf.readFloat(),
               buf.readFloat()
            );
         }

         @Override
         public void encode(RegistryFriendlyByteBuf buf, SentryContraptionTargetPacket packet) {
            buf.writeVarInt(packet.contraptionId());
            BlockPos.STREAM_CODEC.encode(buf, packet.localPos());
            buf.writeVarInt(packet.targetId());
            buf.writeFloat(packet.baseAngle());
            buf.writeFloat(packet.lowerArmAngle());
            buf.writeFloat(packet.upperArmAngle());
            buf.writeFloat(packet.headAngle());
         }
      };

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}

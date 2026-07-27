package euphy.upo.sentrymechanicalarm.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SentryShootPacket(BlockPos pos, int slotIndex, CompoundTag itemTag, Vec3 realStart, Vec3 realEnd, SentryShootPacket.ActionType actionType)
   implements CustomPacketPayload {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_shoot");
   public static final Type<SentryShootPacket> TYPE = new Type(ID);
   public static final StreamCodec<FriendlyByteBuf, SentryShootPacket> CODEC = new StreamCodec<FriendlyByteBuf, SentryShootPacket>() {
      public SentryShootPacket decode(FriendlyByteBuf buf) {
         BlockPos pos = (BlockPos)BlockPos.STREAM_CODEC.decode(buf);
         int slotIndex = buf.readVarInt();
         CompoundTag itemTag = buf.readNbt();
         Vec3 realStart = (Vec3)ModCodecs.VEC3_STREAM_CODEC.decode(buf);
         Vec3 realEnd = (Vec3)ModCodecs.VEC3_STREAM_CODEC.decode(buf);
         int actionOrdinal = buf.readVarInt();
         SentryShootPacket.ActionType actionType = SentryShootPacket.ActionType.values()[actionOrdinal];
         return new SentryShootPacket(pos, slotIndex, itemTag, realStart, realEnd, actionType);
      }

      public void encode(FriendlyByteBuf buf, SentryShootPacket packet) {
         BlockPos.STREAM_CODEC.encode(buf, packet.pos);
         buf.writeVarInt(packet.slotIndex);
         buf.writeNbt(packet.itemTag);
         ModCodecs.VEC3_STREAM_CODEC.encode(buf, packet.realStart);
         ModCodecs.VEC3_STREAM_CODEC.encode(buf, packet.realEnd);
         buf.writeVarInt(packet.actionType().ordinal());
      }
   };

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static enum ActionType {
      SHOOT,
      CHARGE,
      BOLT,
      RELOAD_TACTICAL,
      RELOAD_EMPTY;
   }
}

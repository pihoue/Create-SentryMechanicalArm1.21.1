package euphy.upo.sentrymechanicalarm.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public record SentryContraptionShootPacket(int contraptionId, BlockPos localPos, Vec3 realStart, Vec3 realEnd, ItemStack gunStack) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_contraption_shoot");
    public static final CustomPacketPayload.Type<SentryContraptionShootPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SentryContraptionShootPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SentryContraptionShootPacket::contraptionId,
            BlockPos.STREAM_CODEC, SentryContraptionShootPacket::localPos,
            ModCodecs.VEC3_STREAM_CODEC_REGISTRY, SentryContraptionShootPacket::realStart,
            ModCodecs.VEC3_STREAM_CODEC_REGISTRY, SentryContraptionShootPacket::realEnd,
            ItemStack.OPTIONAL_STREAM_CODEC, SentryContraptionShootPacket::gunStack,
            SentryContraptionShootPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public record SentryInteractPacket(BlockPos pos, InteractionHand hand, boolean isAttack) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_interact");
    public static final CustomPacketPayload.Type<SentryInteractPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<ByteBuf, SentryInteractPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SentryInteractPacket::pos,
            ByteBufCodecs.STRING_UTF8.map(
                    s -> InteractionHand.valueOf(s),
                    InteractionHand::name
            ), SentryInteractPacket::hand,
            ByteBufCodecs.BOOL, SentryInteractPacket::isAttack,
            SentryInteractPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SentryInteractPacket msg, ServerPlayer player) {
        Level level = player.level();
        BlockEntity be = level.getBlockEntity(msg.pos);
        if (be instanceof SentryArmBlockEntity sentry) {
            // interact method not implemented - interaction handled by fire control block
        }
    }
}
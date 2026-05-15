package euphy.upo.sentrymechanicalarm.network;

import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
            ItemStack held = player.getMainHandItem();
            ItemStack armHeld = sentry.getHeldItem();

            if (armHeld.isEmpty() && held.getItem() instanceof IGun) {
                ItemStack gunCopy = held.copy();
                gunCopy.setCount(1);
                sentry.setHeldItem(gunCopy);
                if (!player.isCreative()) held.shrink(1);
                level.playSound(null, msg.pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.5f);
                sentry.setChanged();
                sentry.sendData();
            }
        }
    }
}
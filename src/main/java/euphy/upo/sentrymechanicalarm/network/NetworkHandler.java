package euphy.upo.sentrymechanicalarm.network;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class NetworkHandler {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                SentryShootPacket.TYPE,
                SentryShootPacket.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                    dispatchClientHandler("handleSentryShoot", SentryShootPacket.class, payload)
                )
        );

        registrar.playToClient(
                SentryContraptionShootPacket.TYPE,
                SentryContraptionShootPacket.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                    dispatchClientHandler("handleSentryContraptionShoot", SentryContraptionShootPacket.class, payload)
                )
        );

        registrar.playToServer(
                SentryInteractPacket.TYPE,
                SentryInteractPacket.CODEC,
                (payload, context) -> SentryInteractPacket.handle(payload, (ServerPlayer) context.player())
        );

        registrar.playToServer(
                SentryLinkPacket.TYPE,
                SentryLinkPacket.CODEC,
                (payload, context) -> SentryLinkPacket.handle(payload, (ServerPlayer) context.player())
        );

        registrar.playToServer(
                SentryRecordTargetPacket.TYPE,
                SentryRecordTargetPacket.CODEC,
                (payload, context) -> SentryRecordTargetPacket.handle(payload, (ServerPlayer) context.player())
        );

        registrar.playToServer(
                SentryClientShootPacket.TYPE,
                SentryClientShootPacket.CODEC,
                (payload, context) -> SentryClientShootPacket.handle(payload, (ServerPlayer) context.player())
        );

        registrar.playToServer(
                ClipboardPacket.TYPE,
                ClipboardPacket.CODEC,
                (payload, context) -> ClipboardPacket.handle(payload, (ServerPlayer) context.player())
        );

        registrar.playToServer(
                SentryFocusPacket.TYPE,
                SentryFocusPacket.CODEC,
                (payload, context) -> SentryFocusPacket.handle(payload, (ServerPlayer) context.player())
        );

        registrar.playToServer(
                SentryMarkTargetPacket.TYPE,
                SentryMarkTargetPacket.CODEC,
                (payload, context) -> SentryMarkTargetPacket.handle(payload, (ServerPlayer) context.player())
        );

        registrar.playToServer(
                SentryMarkPosPacket.TYPE,
                SentryMarkPosPacket.CODEC,
                (payload, context) -> SentryMarkPosPacket.handle(payload, (ServerPlayer) context.player())
        );

    }

    @SuppressWarnings("unchecked")
    private static <T> void dispatchClientHandler(String methodName, Class<T> packetClass, Object payload) {
        try {
            Class<?> handlerClass = Class.forName("euphy.upo.sentrymechanicalarm.network.ClientPacketHandler");
            handlerClass.getMethod(methodName, packetClass).invoke(null, packetClass.cast(payload));
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.error("Failed to dispatch client packet handler {}", methodName, e);
        }
    }

    public static void sendToNearby(Object packet, Level level, BlockPos pos) {
        if (level.isClientSide) return;
        if (!(packet instanceof net.minecraft.network.protocol.common.custom.CustomPacketPayload payload)) return;
        for (ServerPlayer player : ((ServerLevel) level).getServer().getPlayerList().getPlayers()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}

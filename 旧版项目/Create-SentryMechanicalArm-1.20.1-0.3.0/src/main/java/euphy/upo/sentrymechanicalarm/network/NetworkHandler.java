package euphy.upo.sentrymechanicalarm.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("sentrymechanicalarm", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SentryInteractPacket.class, SentryInteractPacket::encode, SentryInteractPacket::decode, SentryInteractPacket::handle);
        CHANNEL.registerMessage(id++, SentryShootPacket.class, SentryShootPacket::encode, SentryShootPacket::decode, SentryShootPacket::handle);
        CHANNEL.registerMessage(id++, PacketClearTarget.class, PacketClearTarget::encode, PacketClearTarget::new, PacketClearTarget::handle);
        CHANNEL.registerMessage(id++, SentryLinkPacket.class, SentryLinkPacket::encode, SentryLinkPacket::new, SentryLinkPacket::handle);
        CHANNEL.registerMessage(id++, SentryContraptionShootPacket.class, SentryContraptionShootPacket::encode, SentryContraptionShootPacket::decode, SentryContraptionShootPacket::handle);
        CHANNEL.registerMessage(id++, PacketToggleClipboardMode.class, PacketToggleClipboardMode::encode, PacketToggleClipboardMode::new, PacketToggleClipboardMode::handle);
        CHANNEL.registerMessage(id++, SentryRecordTargetPacket.class, SentryRecordTargetPacket::toBytes, SentryRecordTargetPacket::new, SentryRecordTargetPacket::handle);
        CHANNEL.registerMessage(id++, SentryClientShootPacket.class, SentryClientShootPacket::encode, SentryClientShootPacket::decode, SentryClientShootPacket::handle);
    }

    public static void sendToNearby(Object message, Level level, BlockPos pos) {
        if (level.isClientSide) return;

 
        LevelChunk chunk = level.getChunkAt(pos);

 
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), message);
    }
}
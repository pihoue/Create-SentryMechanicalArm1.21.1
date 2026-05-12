package euphy.upo.sentrymechanicalarm.network;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.util.ArmSoundHelper;
import euphy.upo.sentrymechanicalarm.util.SentryTrailManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;

import java.util.Optional;

public record SentryShootPacket(BlockPos pos, int slotIndex, CompoundTag itemTag, Vec3 realStart, Vec3 realEnd, ActionType actionType) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_shoot");
    public static final CustomPacketPayload.Type<SentryShootPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SentryShootPacket> CODEC = new StreamCodec<>() {
        @Override
        public SentryShootPacket decode(FriendlyByteBuf buf) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
            int slotIndex = buf.readVarInt();
            CompoundTag itemTag = buf.readNbt();
            Vec3 realStart = ModCodecs.VEC3_STREAM_CODEC.decode(buf);
            Vec3 realEnd = ModCodecs.VEC3_STREAM_CODEC.decode(buf);
            int actionOrdinal = buf.readVarInt();
            ActionType actionType = ActionType.values()[actionOrdinal];
            return new SentryShootPacket(pos, slotIndex, itemTag, realStart, realEnd, actionType);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SentryShootPacket packet) {
            BlockPos.STREAM_CODEC.encode(buf, packet.pos);
            buf.writeVarInt(packet.slotIndex);
            buf.writeNbt(packet.itemTag);
            ModCodecs.VEC3_STREAM_CODEC.encode(buf, packet.realStart);
            ModCodecs.VEC3_STREAM_CODEC.encode(buf, packet.realEnd);
            buf.writeVarInt(packet.actionType().ordinal());
        }
    };

    public enum ActionType {
        SHOOT, CHARGE, BOLT, RELOAD_TACTICAL, RELOAD_EMPTY
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SentryShootPacket msg, ServerPlayer player) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> handleClient(msg));
    }

    private static void handleClient(SentryShootPacket msg) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;

        BlockEntity be = mc.level.getBlockEntity(msg.pos);
        if (!(be instanceof SentryArmBlockEntity sentry)) return;

        sentry.updateAmmoFromPacket(msg.slotIndex, msg.itemTag);
        ItemStack gunStack = sentry.getHeldItem();

        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun iGun)) return;

        Level level = mc.level;
        Vec3 center = sentry.getBlockPos().getCenter();

        ArmorStand dummyEntity = new ArmorStand(level, center.x, center.y, center.z);
        dummyEntity.setInvisible(true);
        dummyEntity.setPos(center.x, center.y, center.z);
        dummyEntity.xo = center.x;
        dummyEntity.yo = center.y;
        dummyEntity.zo = center.z;
        dummyEntity.xOld = center.x;
        dummyEntity.yOld = center.y;
        dummyEntity.zOld = center.z;

        Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(gunStack);
        if (displayOpt.isEmpty()) return;
        GunDisplayInstance display = displayOpt.get();

        switch (msg.actionType) {
            case CHARGE -> ArmSoundHelper.playAnimationSound(gunStack, level, center, "charge", "warmup", "build", "charge_start");
            case BOLT -> SoundPlayManager.playBoltSound(dummyEntity, display);
            case RELOAD_EMPTY -> SoundPlayManager.playReloadSound(dummyEntity, display, true);
            case RELOAD_TACTICAL -> SoundPlayManager.playReloadSound(dummyEntity, display, false);
            case SHOOT -> {
                sentry.triggerShootEffects();
                TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack)).ifPresent(index ->
                    ArmSoundHelper.playFireEffects(null, level, center, new Vec3(0, 0, 0), 0, gunStack, index.getGunData())
                );

                Vec3 direction = msg.realEnd().subtract(msg.realStart()).normalize();
                double totalDistance = msg.realStart().distanceTo(msg.realEnd());
                double offsetDistance = 0.8;
                Vec3 adjustedStart = totalDistance > offsetDistance
                        ? msg.realStart().add(direction.scale(offsetDistance))
                        : msg.realStart();
                double adjustedDist = totalDistance > offsetDistance
                        ? totalDistance - offsetDistance
                        : totalDistance;

                SentryTrailManager.addTracer(adjustedStart, direction, 8.0, 2.0, adjustedDist);
            }
        }
    }
}
package euphy.upo.sentrymechanicalarm.network;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.util.ArmSoundHelper;
import euphy.upo.sentrymechanicalarm.util.SentryTrailManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

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

    public static void handle(SentryContraptionShootPacket msg, ServerPlayer player) {
        Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> handleClient(msg));
    }

    private static void handleClient(SentryContraptionShootPacket msg) {
        Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(msg.contraptionId());
        if (entity instanceof AbstractContraptionEntity ace) {
            Contraption contraption = ace.getContraption();
            if (contraption != null) {
                for (var actor : contraption.getActors()) {
                    if (actor.getKey().pos().equals(msg.localPos())) {
                        MovementContext context = actor.getValue();
                        if (context.temporaryData instanceof SentryArmBlockEntity sentry) {
                            sentry.setLastShootTime(System.currentTimeMillis());
                            sentry.triggerShootEffects();
                        }
                        break;
                    }
                }
            }
        }

        Vec3 direction = msg.realEnd().subtract(msg.realStart()).normalize();
        double totalDistance = msg.realStart().distanceTo(msg.realEnd());
        double offsetDistance = 0.6;
        Vec3 adjustedStart = totalDistance > offsetDistance
                ? msg.realStart().add(direction.scale(offsetDistance))
                : msg.realStart();
        double adjustedDist = totalDistance > offsetDistance
                ? totalDistance - offsetDistance
                : totalDistance;

        SentryTrailManager.addTracer(adjustedStart, direction, 8.0, 2.0, adjustedDist);

        Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(msg.gunStack());
        if (displayOpt.isPresent()) {
            GunDisplayInstance display = displayOpt.get();
            boolean isSilenced = ArmSoundHelper.isSilenced(msg.gunStack());
            String soundKey = isSilenced ? SoundManager.SILENCE_SOUND : SoundManager.SHOOT_SOUND;
            ResourceLocation soundId = display.getSounds(soundKey);
            if (soundId == null && isSilenced) {
                soundId = display.getSounds(SoundManager.SHOOT_SOUND);
            }

            if (soundId != null && msg.gunStack().getItem() instanceof IGun iGun) {
                ResourceLocation finalSoundId = soundId;
                TimelessAPI.getCommonGunIndex(iGun.getGunId(msg.gunStack())).ifPresent(index -> {
                    GunData gunData = index.getGunData();
                    if (gunData.getFireSound() != null) {
                        float finalVol = 4.0f * gunData.getFireSound().getFireMultiplier();
                        float pitch = 1.0f + (level.random.nextFloat() - 0.5f) * 0.1f;
                        int distance = 32;

                        Entity dummyEntity = new Snowball(level, msg.realStart().x, msg.realStart().y, msg.realStart().z);
                        dummyEntity.setPos(msg.realStart().x, msg.realStart().y, msg.realStart().z);

                        SoundPlayManager.playClientSound(dummyEntity, finalSoundId, finalVol, pitch, distance);
                    }
                });
            }
        }
    }
}
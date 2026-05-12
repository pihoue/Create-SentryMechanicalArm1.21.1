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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public class SentryContraptionShootPacket {
    private final int contraptionId;
    private final BlockPos localPos;
    private final Vec3 realStart;
    private final Vec3 realEnd;
    private final ItemStack gunStack;

    public SentryContraptionShootPacket(int contraptionId, BlockPos localPos, Vec3 realStart, Vec3 realEnd, ItemStack gunStack) {
        this.contraptionId = contraptionId;
        this.localPos = localPos;
        this.realStart = realStart;
        this.realEnd = realEnd;
        this.gunStack = gunStack;
    }

    public static void encode(SentryContraptionShootPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.contraptionId);
        buffer.writeBlockPos(msg.localPos);
        buffer.writeDouble(msg.realStart.x);
        buffer.writeDouble(msg.realStart.y);
        buffer.writeDouble(msg.realStart.z);
        buffer.writeDouble(msg.realEnd.x);
        buffer.writeDouble(msg.realEnd.y);
        buffer.writeDouble(msg.realEnd.z);
        buffer.writeItem(msg.gunStack);
    }

    public static SentryContraptionShootPacket decode(FriendlyByteBuf buffer) {
        return new SentryContraptionShootPacket(
                buffer.readInt(),
                buffer.readBlockPos(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readItem()
        );
    }

    public static void handle(SentryContraptionShootPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePacket(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handlePacket(SentryContraptionShootPacket msg) {
            Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            Level level = mc.level;
            if (level == null) return;

            Entity entity = level.getEntity(msg.contraptionId);
            if (entity instanceof AbstractContraptionEntity ace) {
                Contraption contraption = ace.getContraption();
                if (contraption != null) {
                    for (var actor : contraption.getActors()) {
                        if (actor.getKey().pos().equals(msg.localPos)) {
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

            Vec3 direction = msg.realEnd.subtract(msg.realStart).normalize();
            double totalDistance = msg.realStart.distanceTo(msg.realEnd);
            double offsetDistance = 0.6;
            Vec3 adjustedStart = totalDistance > offsetDistance
                    ? msg.realStart.add(direction.scale(offsetDistance))
                    : msg.realStart;
            double adjustedDist = totalDistance > offsetDistance
                    ? totalDistance - offsetDistance
                    : totalDistance;

            SentryTrailManager.addTracer(adjustedStart, direction, 8.0, 2.0, adjustedDist);

            Optional<com.tacz.guns.client.resource.GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(msg.gunStack);
            if (displayOpt.isPresent()) {
                GunDisplayInstance display = displayOpt.get();
                boolean isSilenced = ArmSoundHelper.isSilenced(msg.gunStack);
                String soundKey = isSilenced ? SoundManager.SILENCE_SOUND : SoundManager.SHOOT_SOUND;
                ResourceLocation soundId = display.getSounds(soundKey);
                if (soundId == null && isSilenced) {
                    soundId = display.getSounds(SoundManager.SHOOT_SOUND);
                }

                if (soundId != null) {
                    float volume = 4.0f;
                    if (msg.gunStack.getItem() instanceof IGun iGun) {
                        ResourceLocation finalSoundId = soundId;
                        TimelessAPI.getCommonGunIndex(iGun.getGunId(msg.gunStack)).ifPresent(index -> {
                            GunData gunData = index.getGunData();
                            if (gunData.getFireSound() != null) {
                                float finalVol = 4.0f * gunData.getFireSound().getFireMultiplier();
                                float pitch = 1.0f + (level.random.nextFloat() - 0.5f) * 0.1f;
                                int distance = 32;

                                Entity dummyEntity = new Snowball(level, msg.realStart.x, msg.realStart.y, msg.realStart.z);
                                dummyEntity.setPos(msg.realStart.x, msg.realStart.y, msg.realStart.z);

                                SoundPlayManager.playClientSound(dummyEntity, finalSoundId, finalVol, pitch, distance);
                            }
                        });
                    }
                }
            }
        }

    }
}
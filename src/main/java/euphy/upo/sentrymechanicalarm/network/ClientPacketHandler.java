package euphy.upo.sentrymechanicalarm.network;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import java.util.Optional;

public class ClientPacketHandler {

    public static void handleSentryShoot(SentryShootPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockEntity be = mc.level.getBlockEntity(msg.pos());
        if (!(be instanceof SentryArmBlockEntity sentry)) return;

        sentry.updateAmmoFromPacket(msg.slotIndex(), msg.itemTag());
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

        switch (msg.actionType()) {
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

    public static void handleSentryContraptionShoot(SentryContraptionShootPacket msg) {
        Minecraft mc = Minecraft.getInstance();
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
                        float finalVol = 3.0f * gunData.getFireSound().getFireMultiplier();
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

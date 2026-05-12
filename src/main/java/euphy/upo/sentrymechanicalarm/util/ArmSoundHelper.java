package euphy.upo.sentrymechanicalarm.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class ArmSoundHelper {

    private static java.lang.reflect.Field CONTROLLER_PROTOTYPES_FIELD;

    public static void playAnimationSound(ItemStack stack, Level level, Vec3 pos, String... animationNames) {
        Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(stack);
        if (displayOpt.isEmpty()) {
            return;
        }
        GunDisplayInstance display = displayOpt.get();

        try {
            var stateMachine = display.getAnimationStateMachine();
            if (stateMachine == null) {
                return;
            }

            com.tacz.guns.api.client.animation.AnimationController controller = stateMachine.getAnimationController();
            if (controller == null) {
                return;
            }

            if (CONTROLLER_PROTOTYPES_FIELD == null) {
                CONTROLLER_PROTOTYPES_FIELD = com.tacz.guns.api.client.animation.AnimationController.class.getDeclaredField("prototypes");
                CONTROLLER_PROTOTYPES_FIELD.setAccessible(true);
            }

            @SuppressWarnings("unchecked")
            Map<String, com.tacz.guns.api.client.animation.ObjectAnimation> prototypes =
                    (Map<String, com.tacz.guns.api.client.animation.ObjectAnimation>) CONTROLLER_PROTOTYPES_FIELD.get(controller);

            if (prototypes == null) {
                return;
            }

            for (String animName : animationNames) {
                if (prototypes.containsKey(animName)) {
                    com.tacz.guns.api.client.animation.ObjectAnimation anim = prototypes.get(animName);

                    var soundChannel = anim.getSoundChannel();
                    if (soundChannel != null) {
                        net.minecraft.world.entity.decoration.ArmorStand dummyEntity =
                                new net.minecraft.world.entity.decoration.ArmorStand(level, pos.x, pos.y, pos.z);

                        dummyEntity.setPos(pos.x, pos.y, pos.z);
                        dummyEntity.xo = pos.x;
                        dummyEntity.yo = pos.y;
                        dummyEntity.zo = pos.z;
                        dummyEntity.xOld = pos.x;
                        dummyEntity.yOld = pos.y;
                        dummyEntity.zOld = pos.z;

                        double maxDuration = anim.getMaxEndTimeS();
                        // soundChannel.playSound(-0.01, maxDuration, dummyEntity, 64, 4.0f, 1.0f);
                        break;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static boolean isSilenced(ItemStack stack) {
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) {
            return false;
        }
        ResourceLocation muzzleId = iGun.getAttachmentId(stack, AttachmentType.MUZZLE);

        return TimelessAPI.getCommonAttachmentIndex(muzzleId).map(index -> {
            if (index.getData() != null && index.getData().getModifier() != null) {
                return index.getData().getModifier().containsKey("silence");
            }
            return false;
        }).orElse(false);
    }

    public static void playFireEffects(SentryArmBlockEntity sentry, Level level, Vec3 pos, Vec3 direction, double maxDistance, ItemStack stack, GunData gunData) {

        Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(stack);
        if (displayOpt.isPresent()) {
            GunDisplayInstance display = displayOpt.get();

            boolean isSilenced = isSilenced(stack);
            String soundKey = isSilenced(stack) ? SoundManager.SILENCE_SOUND : SoundManager.SHOOT_SOUND;
            ResourceLocation soundId = display.getSounds(soundKey);

            if (soundId == null && soundKey.equals(SoundManager.SILENCE_SOUND)) {
                soundId = display.getSounds(SoundManager.SHOOT_SOUND);
            }

            if (soundId != null) {
                float volume = 3.0f;
                if(isSilenced) volume =0.25f;

                if (gunData.getFireSound() != null) {
                    volume *= gunData.getFireSound().getFireMultiplier();
                }
                float pitch = 1.0f + (level.random.nextFloat() - 0.5f) * 0.1f;
                int distance = 32;

                Entity dummyEntity = new Snowball(level, pos.x, pos.y, pos.z);
                dummyEntity.setPos(pos.x, pos.y, pos.z);
                SoundPlayManager.playClientSound(dummyEntity, soundId, volume, pitch, distance);
            }
        }

        BulletData bulletData = gunData.getBulletData();
        if (bulletData != null && bulletData.hasTracerAmmo()) {
            int rpm = gunData.getRoundsPerMinute();
            float rps = rpm / 60.0f;
            if (rps <= 0) rps = 1;
            float chance = 30.0f / rps;

            if (level.random.nextFloat() < chance) {
                Vec3 startPos = pos.add(0, 1.8, 0).add(direction.scale(0.5));

            }
        }
    }
}
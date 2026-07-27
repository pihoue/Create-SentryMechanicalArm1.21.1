package euphy.upo.sentrymechanicalarm.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.init.ModSounds;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ArmSoundHelper {
   private static volatile ArmorStand cachedDummyEntity;
   private static volatile Level cachedLevel;

   private static ArmorStand getOrCreateDummyEntity(Level level, Vec3 pos) {
      ArmorStand entity = cachedDummyEntity;
      if (entity == null || entity.isRemoved() || cachedLevel != level) {
         synchronized (ArmSoundHelper.class) {
            entity = cachedDummyEntity;
            if (entity == null || entity.isRemoved() || cachedLevel != level) {
               entity = new ArmorStand(level, pos.x, pos.y, pos.z);
               entity.setInvisible(true);
               cachedDummyEntity = entity;
               cachedLevel = level;
               return entity;
            }
         }
      }

      entity.setPos(pos.x, pos.y, pos.z);
      return entity;
   }

   public static boolean isSilenced(ItemStack stack) {
      IGun iGun = IGun.getIGunOrNull(stack);
      if (iGun == null) {
         return false;
      } else {
         ResourceLocation muzzleId = iGun.getAttachmentId(stack, AttachmentType.MUZZLE);
         return TimelessAPI.getCommonAttachmentIndex(muzzleId)
            .map(index -> index.getData())
            .map(data -> data.getModifier())
            .map(modifier -> modifier.containsKey("silence"))
            .orElse(false);
      }
   }

   public static void playFireEffects(Level level, Vec3 pos, ItemStack stack, GunData gunData) {
      Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(stack);
      if (!displayOpt.isEmpty()) {
         GunDisplayInstance display = displayOpt.get();
         ArmorStand dummyEntity = getOrCreateDummyEntity(level, pos);
         boolean silenced = isSilenced(stack);
         ResourceLocation soundId = silenced ? display.getSounds(SoundManager.SILENCE_SOUND) : display.getSounds(SoundManager.SHOOT_SOUND);
         if (soundId != null) {
            float volume = silenced ? 0.6F : 0.8F;
            float pitch = 0.9F + level.random.nextFloat() * 0.125F;
            int distance = (int)(
               (float)(silenced ? (Integer)GunConfig.DEFAULT_GUN_SILENCE_SOUND_DISTANCE.get() : (Integer)GunConfig.DEFAULT_GUN_FIRE_SOUND_DISTANCE.get())
                     .intValue()
                  * (silenced ? gunData.getFireSound().getSilenceMultiplier() : gunData.getFireSound().getFireMultiplier())
            );
            playDirectSound(dummyEntity, soundId, volume, pitch, distance);
         }
      }
   }

   public static void playChargeSound(Level level, Vec3 pos, ItemStack stack, GunDisplayInstance display) {
      ArmorStand dummyEntity = getOrCreateDummyEntity(level, pos);
      ResourceLocation soundId = display.getSounds("charge");
      if (soundId == null) {
         soundId = display.getSounds("warmup");
      }

      if (soundId == null) {
         soundId = display.getSounds("build");
      }

      if (soundId == null) {
         soundId = display.getSounds(SoundManager.BOLT_SOUND);
      }

      if (soundId != null) {
         playDirectSound(dummyEntity, soundId, 1.0F, 1.0F, (Integer)GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
      }
   }

   public static void playBoltSound(Level level, Vec3 pos, GunDisplayInstance display) {
      ArmorStand dummyEntity = getOrCreateDummyEntity(level, pos);
      ResourceLocation soundId = display.getSounds(SoundManager.BOLT_SOUND);
      if (soundId != null) {
         playDirectSound(dummyEntity, soundId, 1.0F, 1.0F, (Integer)GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
      }
   }

   public static void playReloadSound(Level level, Vec3 pos, GunDisplayInstance display, boolean noAmmo) {
      ArmorStand dummyEntity = getOrCreateDummyEntity(level, pos);
      ResourceLocation soundId = noAmmo ? display.getSounds(SoundManager.RELOAD_EMPTY_SOUND) : display.getSounds(SoundManager.RELOAD_TACTICAL_SOUND);
      if (soundId != null) {
         playDirectSound(dummyEntity, soundId, 1.0F, 1.0F, (Integer)GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
      }
   }

   private static void playDirectSound(Entity entity, ResourceLocation soundId, float volume, float pitch, int distance) {
      Minecraft mc = Minecraft.getInstance();
      boolean relative = entity instanceof LocalPlayer;
      GunSoundInstance instance = new GunSoundInstance(
         (SoundEvent)ModSounds.GUN.get(), SoundSource.PLAYERS, volume, pitch, entity, distance, soundId, false, relative
      );
      mc.getSoundManager().play(instance);
   }
}

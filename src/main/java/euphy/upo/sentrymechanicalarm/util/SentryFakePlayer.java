package euphy.upo.sentrymechanicalarm.util;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

public class SentryFakePlayer {
   private static final WeakHashMap<SentryArmBlockEntity, FakePlayer> FAKE_PLAYERS = new WeakHashMap<>();
   private static final WeakHashMap<FakePlayer, SentryArmBlockEntity> REVERSE_MAP = new WeakHashMap<>();
   private static final Map<String, SentryFakePlayer.RobustFakePlayer> CONTRAPTION_FAKE_PLAYERS = new HashMap<>();
   private static final Map<FakePlayer, Boolean> FIRED_TRACKER = Collections.synchronizedMap(new WeakHashMap<>());
   private static final AtomicInteger COUNTER = new AtomicInteger(0);

   public static void markFired(FakePlayer fp) {
      FIRED_TRACKER.put(fp, true);
   }

   public static boolean checkAndClearFired(FakePlayer fp) {
      return FIRED_TRACKER.remove(fp) != null;
   }

   public static void setTempCreative(FakePlayer fp, boolean active) {
      if (fp instanceof SentryFakePlayer.RobustFakePlayer robust) {
         robust.setFakeCreative(active);
      } else if (active) {
         fp.setGameMode(GameType.CREATIVE);
      } else {
         fp.setGameMode(GameType.SURVIVAL);
      }
   }

   public static FakePlayer get(SentryArmBlockEntity arm) {
      if (arm.getLevel() instanceof ServerLevel serverLevel) {
         FakePlayer var3 = FAKE_PLAYERS.computeIfAbsent(arm, k -> {
            String name = "Sentry_" + arm.getBlockPos().getX() + "_" + arm.getBlockPos().getY() + "_" + arm.getBlockPos().getZ();
            return createRobustFakePlayer(serverLevel, name);
         });
         REVERSE_MAP.put(var3, arm);
         return var3;
      } else {
         return null;
      }
   }

   public static SentryArmBlockEntity getArmFromPlayer(FakePlayer fp) {
      return REVERSE_MAP.get(fp);
   }

   public static FakePlayer getForContraption(ServerLevel level, UUID contraptionUUID, BlockPos localPos) {
      String key = contraptionUUID.toString() + "_" + localPos.asLong();
      SentryFakePlayer.RobustFakePlayer existing = CONTRAPTION_FAKE_PLAYERS.get(key);
      if (existing != null) {
         if (existing.level() != level) {
            existing.setLevelPublic(level);
         }

         return existing;
      } else {
         String name = "SentryC_" + Math.abs(key.hashCode());
         SentryFakePlayer.RobustFakePlayer newPlayer = createRobustFakePlayer(level, name);
         CONTRAPTION_FAKE_PLAYERS.put(key, newPlayer);
         return newPlayer;
      }
   }

   public static void removeForContraption(UUID contraptionUUID, BlockPos localPos) {
      String key = contraptionUUID.toString() + "_" + localPos.asLong();
      FakePlayer removed = CONTRAPTION_FAKE_PLAYERS.remove(key);
      if (removed != null) {
         removed.discard();
      }
   }

   private static SentryFakePlayer.RobustFakePlayer createRobustFakePlayer(ServerLevel level, String name) {
      UUID uuid = new UUID((long)COUNTER.incrementAndGet(), UUID.nameUUIDFromBytes(name.getBytes()).getLeastSignificantBits());
      GameProfile profile = new GameProfile(uuid, name);
      SentryFakePlayer.RobustFakePlayer fp = new SentryFakePlayer.RobustFakePlayer(level, profile);
      fp.setGameMode(GameType.SURVIVAL);
      fp.setNoGravity(true);
      fp.setInvisible(true);
      fp.setInvulnerable(true);
      if (fp.connection == null) {
         try {
            Connection fakeConnection = new Connection(PacketFlow.CLIENTBOUND);
            fp.connection = new ServerGamePacketListenerImpl(level.getServer(), fakeConnection, fp, CommonListenerCookie.createInitial(profile, false)) {
               public void send(Packet<?> packet) {
               }
            };
         } catch (Exception var6) {
            SentryMechanicalArm.LOGGER.error("Failed to mock connection for {}", name, var6);
         }
      }

      return fp;
   }

   public static Vec3 getMuzzlePosition(Vec3 basePos, float yaw, float pitch, double armLength) {
      double yawRad = Math.toRadians((double)yaw);
      double pitchRad = Math.toRadians((double)pitch);
      return basePos.add(-Math.sin(yawRad) * Math.cos(pitchRad) * armLength, Math.sin(pitchRad) * armLength, Math.cos(yawRad) * Math.cos(pitchRad) * armLength);
   }

   public static boolean hasEntityBullet(ItemStack gunStack) {
      IGun iGun = IGun.getIGunOrNull(gunStack);
      if (iGun == null) {
         return false;
      } else {
         ResourceLocation gunId = iGun.getGunId(gunStack);
         if (gunId == null) {
            return false;
         } else {
            String path = gunId.getPath().toLowerCase();
            return !path.contains("rifle")
               && !path.contains("pistol")
               && !path.contains("smg")
               && !path.contains("shotgun")
               && !path.contains("carbine")
               && !path.contains("dmr")
               && !path.contains("lmg")
               && !path.contains("machine_gun")
               && !path.contains("marksman")
               && !path.contains("sniper")
               && !path.contains("revolver")
               && !path.contains("handgun")
               && !path.contains("mini_gun")
               && !path.contains("minigun")
               && !path.contains("blaster");
         }
      }
   }

   public static double getGunArmLength(ItemStack gunStack) {
      IGun iGun = IGun.getIGunOrNull(gunStack);
      if (iGun == null) {
         return 2.8;
      } else {
         ResourceLocation gunId = iGun.getGunId(gunStack);
         if (gunId == null) {
            return 2.8;
         } else {
            String path = gunId.getPath().toLowerCase();
            if (path.contains("sniper") || path.contains("rpg") || path.contains("rocket") || path.contains("launcher")) {
               return 3.4;
            } else if (path.contains("rifle")
               || path.contains("carbine")
               || path.contains("shotgun")
               || path.contains("dmr")
               || path.contains("lmg")
               || path.contains("machine_gun")
               || path.contains("marksman")) {
               return 3.0;
            } else if (path.contains("smg") || path.contains("pistol") || path.contains("handgun") || path.contains("revolver") || path.contains("shotgun")) {
               return 2.6;
            } else {
               return !path.contains("grenade") && !path.contains("throwable") && !path.contains("melee") ? 2.8 : 2.2;
            }
         }
      }
   }

   public static Vec3 getMuzzlePosition(SentryArmBlockEntity arm, float yaw, float pitch, double armLength) {
      boolean isCeiling = arm.getBlockState().hasProperty(SentryArmBlock.CEILING) && (Boolean)arm.getBlockState().getValue(SentryArmBlock.CEILING);
      double yBase = isCeiling ? -2.8 : 1.0;
      Vec3 basePos = new Vec3((double)arm.getBlockPos().getX() + 0.5, (double)arm.getBlockPos().getY() + yBase, (double)arm.getBlockPos().getZ() + 0.5);
      return getMuzzlePosition(basePos, yaw, pitch, armLength);
   }

   public static Vec3 getContraptionLocalMuzzle(Vec3 localPosCenter, VirtualSentryArmBlockEntity virtualBE, ItemStack gunStack) {
      boolean isCeiling = virtualBE.getBlockState().hasProperty(SentryArmBlock.CEILING) && (Boolean)virtualBE.getBlockState().getValue(SentryArmBlock.CEILING);
      double yBase = isCeiling ? -2.0 : 2.0;
      Vec3 basePos = new Vec3(localPosCenter.x, localPosCenter.y + yBase, localPosCenter.z);
      float yaw;
      float pitch;
      if (isCeiling) {
         yaw = virtualBE.baseAngle.getValue();
         pitch = virtualBE.headAngle.getValue();
      } else {
         yaw = 180.0F - virtualBE.baseAngle.getValue();
         pitch = -virtualBE.headAngle.getValue();
      }

      double armLen = getGunArmLength(gunStack);
      return getMuzzlePosition(basePos, yaw, pitch, armLen);
   }

   public static void remove(SentryArmBlockEntity arm) {
      FakePlayer fp = FAKE_PLAYERS.remove(arm);
      if (fp != null) {
         REVERSE_MAP.remove(fp);
         FIRED_TRACKER.remove(fp);
         if (fp.level() instanceof ServerLevel serverLevel) {
            fp.discard();
         }
      }
   }

   public static void sync(FakePlayer fp, SentryArmBlockEntity arm, float yaw, float pitch, ItemStack gunStack) {
      boolean isVirtual = arm instanceof VirtualSentryArmBlockEntity;
      if (!isVirtual) {
         Vec3 worldPos;
         if (arm.isInSableSubLevel()) {
            Vec3 muzzleWorld = arm.getProjectedMuzzlePos();
            worldPos = new Vec3(muzzleWorld.x, muzzleWorld.y - 1.62, muzzleWorld.z);
         } else if (AeronauticsHelper.isAeronauticsLoaded()) {
            worldPos = arm.getProjectedMuzzlePos();
         } else {
            BlockState state = arm.getBlockState();
            boolean isCeiling = false;
            if (state.hasProperty(SentryArmBlock.CEILING)) {
               isCeiling = (Boolean)state.getValue(SentryArmBlock.CEILING);
            }

            double x = (double)arm.getBlockPos().getX() + 0.5;
            double yOffset = isCeiling ? -2.8 : 1.0;
            double y = (double)arm.getBlockPos().getY() + yOffset;
            double z = (double)arm.getBlockPos().getZ() + 0.5;
            worldPos = new Vec3(x, y, z);
         }

         fp.setPos(worldPos.x, worldPos.y, worldPos.z);
         fp.xo = worldPos.x;
         fp.yo = worldPos.y;
         fp.zo = worldPos.z;
         fp.xOld = worldPos.x;
         fp.yOld = worldPos.y;
         fp.zOld = worldPos.z;
      }

      fp.setYRot(yaw);
      fp.setXRot(pitch);
      fp.yRotO = yaw;
      fp.xRotO = pitch;
      fp.yHeadRot = yaw;
      fp.yBodyRot = yaw;
      fp.setHealth(fp.getMaxHealth());
      fp.deathTime = 0;
      fp.removeAllEffects();
      fp.clearFire();
      IGunOperator operator = IGunOperator.fromLivingEntity(fp);
      operator.aim(true);
      if (operator.getDataHolder().currentGunItem == null) {
         operator.initialData();
      }

      ItemStack currentFakeItem = fp.getMainHandItem();
      boolean needsSwitch = true;
      IGun iGunTarget = IGun.getIGunOrNull(gunStack);
      if (iGunTarget != null && !currentFakeItem.isEmpty()) {
         IGun iGunCurrent = IGun.getIGunOrNull(currentFakeItem);
         if (iGunCurrent != null) {
            ResourceLocation idTarget = iGunTarget.getGunId(gunStack);
            ResourceLocation idCurrent = iGunCurrent.getGunId(currentFakeItem);
            if (idTarget.equals(idCurrent)) {
               needsSwitch = false;
            }
         }
      } else if (iGunTarget == null && currentFakeItem.isEmpty()) {
         needsSwitch = false;
      }

      if (needsSwitch) {
         fp.getInventory().clearContent();
         ItemStack newStack = gunStack.copy();
         fp.setItemSlot(EquipmentSlot.MAINHAND, newStack);
         operator.draw(() -> newStack);
         operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10000L;
      } else {
         if (ItemNBTHelper.hasTag(gunStack)) {
            if (!Objects.equals(ItemNBTHelper.getTag(currentFakeItem), ItemNBTHelper.getTag(gunStack))) {
               ItemNBTHelper.setTag(currentFakeItem, ItemNBTHelper.getTag(gunStack));
            }
         } else if (ItemNBTHelper.hasTag(currentFakeItem)) {
            currentFakeItem.remove(DataComponents.CUSTOM_DATA);
         }

         if (currentFakeItem.getCount() != gunStack.getCount()) {
            currentFakeItem.setCount(gunStack.getCount());
         }
      }

      if (iGunTarget != null) {
         for (int i = 0; i < arm.attachedAmmoBoxes.size(); i++) {
            ItemStack box = (ItemStack)arm.attachedAmmoBoxes.get(i);
            fp.getInventory().setItem(9 + i, box.copy());
         }
      }
   }

   public static void setContraptionRoot(FakePlayer var0, Entity var1) {
      ((SentryFakePlayer.RobustFakePlayer)var0).contraptionRoot = var1;
   }

   public static Entity getContraptionRoot(Entity entity) {
      return entity instanceof SentryFakePlayer.RobustFakePlayer robust ? robust.contraptionRoot : null;
   }

   private static class RobustFakePlayer extends FakePlayer {
      private boolean fakeCreativeMode = false;
      private Entity contraptionRoot;

      public RobustFakePlayer(ServerLevel level, GameProfile name) {
         super(level, name);
      }

      public void setLevelPublic(ServerLevel level) {
         super.setLevel(level);
      }

      public void setFakeCreative(boolean active) {
         this.fakeCreativeMode = active;
      }

      public boolean isCreative() {
         return this.fakeCreativeMode || super.isCreative();
      }

      public boolean isInvulnerableTo(DamageSource source) {
         return true;
      }

      public Entity getRootVehicle() {
         return this.contraptionRoot != null ? this.contraptionRoot : super.getRootVehicle();
      }
   }
}

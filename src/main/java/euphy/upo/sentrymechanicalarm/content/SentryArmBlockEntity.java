package euphy.upo.sentrymechanicalarm.content;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.entity.ReloadState.StateType;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.network.SentryShootPacket;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.IArmAmmoStorage;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import euphy.upo.sentrymechanicalarm.util.SentryAimUtil;
import euphy.upo.sentrymechanicalarm.util.SentryTargetSavedData;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Vector3f;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.slf4j.Logger;

public class SentryArmBlockEntity extends KineticBlockEntity implements IArmAmmoStorage {
   private static final double AIM_TOLERANCE_DEGREES = 12.0;
   public static final Logger LOGGER = LogUtils.getLogger();
   private static final String[] EXPLOSIVE_KEYWORDS = new String[]{"rpg", "rocket", "grenade", "missile", "explosive", "rpg7"};
   private boolean currentTargetIsMarked = false;
   private Vec3 cachedMarkedPos = null;
   private int cachedMarkedContraptionId = -1;
   private BlockPos cachedMarkedLocalPos = null;
   private boolean isSableMarked = false;
   private Vec3 sableMarkedLocalPos = null;
   private int markedPosShotCounter = 0;
   private boolean isCurrentTargetMarkedPos = false;
   private int markedPosUpdateTimer = 0;
   private Vec3 cachedTrackedMarkedPos = null;
   private BlockPos connectedFireControlPos = null;
   private BlockPos projectedFireControlPos = null;
   private float lowerArmRecoilOffset = 0.0F;
   private BlockPos cachedTargetBlock = null;
   private LivingEntity cachedTarget;
   private int idleScanTimer = 0;
   public float idleTargetYaw = 0.0F;
   public float idleTargetPitch = 0.0F;
   int syncedTargetId = -1;
   private int angleSyncTimer = 0;
   private int targetRescanTimer = 0;
   private boolean shouldEjectShell = false;
   private float shootDelayAccumulator = 0.0F;
   public LerpedFloat baseAngle;
   public LerpedFloat lowerArmAngle;
   public LerpedFloat upperArmAngle;
   public LerpedFloat headAngle;
   private ItemStack heldItem = ItemStack.EMPTY;
   public final NonNullList<ItemStack> attachedAmmoBoxes = NonNullList.withSize(2, ItemStack.EMPTY);
   private int lineOfSightTicker = 0;
   private long lastShootTime = 0L;
   private int scanCooldown = 0;
   public ScrollValueBehaviour rangeScroll;
   public Optional<DyeColor> color = Optional.empty();
   private String lastScriptDataStr = "";
   private float lastAimingProgress = -1.0F;
   private int triggerHoldTime = 0;
   private int currentTimeoutThreshold = 100;
   private boolean isTestingRelease = false;
   private int releaseWatchTimer = 0;
   private boolean wasCharging = false;
   private SentryArmBlockEntity.SentryStatus currentStatus = SentryArmBlockEntity.SentryStatus.IDLE;
   public long lastShellEjectTime = Long.MIN_VALUE;

   public SentryArmBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)SentryRegistry.SENTRY_ARM_BE.get(), pos, state);
      this.baseAngle = LerpedFloat.angular().startWithValue(0.0);
      this.lowerArmAngle = LerpedFloat.angular().startWithValue(135.0);
      this.upperArmAngle = LerpedFloat.angular().startWithValue(45.0);
      this.headAngle = LerpedFloat.angular().startWithValue(0.0);
   }

   public void onLoad() {
      super.onLoad();
      if (this.level instanceof ServerLevel serverLevel) {
         ItemStack held = this.getHeldItem();
         boolean isGun = !held.isEmpty() && held.getItem() instanceof IGun;
         if (isGun) {
            SentryFakePlayer.get(this);
         }
      }
   }

   public void onChunkUnloaded() {
      if (this.level instanceof ServerLevel) {
         SentryFakePlayer.remove(this);
      }

      super.onChunkUnloaded();
   }

   public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
      super.addBehaviours(behaviours);
      this.rangeScroll = new ScrollValueBehaviour(Component.translatable("sentry.scroll_value.range"), this, new SentryArmBlockEntity.SentryValueBoxTransform());
      this.rangeScroll.between(0, 0);
      this.rangeScroll.withCallback(newValue -> this.scanCooldown = 0);
      behaviours.add(this.rangeScroll);
      this.updateRangeScrollBounds();
   }

   public boolean shouldEjectShell() {
      return this.shouldEjectShell;
   }

   public SentryArmBlockEntity.SentryStatus getSentryStatus() {
      return this.currentStatus;
   }

   public void setShellEjected() {
      this.shouldEjectShell = false;
   }

   public ItemStack getHeldItem() {
      return this.heldItem == null ? ItemStack.EMPTY : this.heldItem;
   }

   public void setHeldItem(ItemStack stack) {
      this.heldItem = stack;
      this.setChanged();
      this.sendData();
      this.updateRangeScrollBounds();
   }

   public long getLastShootTime() {
      return this.lastShootTime;
   }

   public boolean hasAnyAmmo() {
      if (this.heldItem.isEmpty()) {
         return false;
      } else if (!(this.heldItem.getItem() instanceof IGun iGun)) {
         return false;
      } else {
         ResourceLocation gunId = iGun.getGunId(this.heldItem);
         Optional<CommonGunIndex> gunIndexOpt = TimelessAPI.getCommonGunIndex(gunId);
         if (gunIndexOpt.isEmpty()) {
            return false;
         } else {
            GunData gunData = gunIndexOpt.get().getGunData();
            int currentAmmo = iGun.getCurrentAmmoCount(this.heldItem);
            if (iGun.useInventoryAmmo(this.heldItem)) {
               currentAmmo = 0;
            }

            if (currentAmmo > 0) {
               return true;
            } else {
               ResourceLocation requiredAmmoId = gunData.getAmmoId();

               for (ItemStack box : this.attachedAmmoBoxes) {
                  if (!box.isEmpty() && box.getItem() instanceof IAmmoBox iBox) {
                     if (iBox.isAllTypeCreative(box) || iBox.isCreative(box)) {
                        return true;
                     }

                     ResourceLocation boxAmmoId = iBox.getAmmoId(box);
                     if (requiredAmmoId != null && requiredAmmoId.equals(boxAmmoId) && iBox.getAmmoCount(box) > 0) {
                        return true;
                     }
                  }
               }

               return false;
            }
         }
      }
   }

   public boolean addAmmoBox(ItemStack stack) {
      for (int i = 0; i < this.attachedAmmoBoxes.size(); i++) {
         if (((ItemStack)this.attachedAmmoBoxes.get(i)).isEmpty()) {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            this.attachedAmmoBoxes.set(i, copy);
            this.setChanged();
            this.sendData();
            return true;
         }
      }

      return false;
   }

   public ItemStack removeLastAmmoBox() {
      for (int i = this.attachedAmmoBoxes.size() - 1; i >= 0; i--) {
         if (!((ItemStack)this.attachedAmmoBoxes.get(i)).isEmpty()) {
            ItemStack stack = (ItemStack)this.attachedAmmoBoxes.get(i);
            this.attachedAmmoBoxes.set(i, ItemStack.EMPTY);
            this.setChanged();
            this.sendData();
            return stack;
         }
      }

      return ItemStack.EMPTY;
   }

   @Override
   public ItemStack getAmmoBox() {
      for (ItemStack stack : this.attachedAmmoBoxes) {
         if (!stack.isEmpty()) {
            return stack;
         }
      }

      return ItemStack.EMPTY;
   }

   @Override
   public void setAmmoBox(ItemStack stack) {
      this.attachedAmmoBoxes.clear();
      this.attachedAmmoBoxes.set(0, stack);
      this.attachedAmmoBoxes.set(1, ItemStack.EMPTY);
      this.setChanged();
      this.sendData();
   }

   public void tick() {
      super.tick();
      if (this.level != null) {
         this.baseAngle.tickChaser();
         this.lowerArmAngle.tickChaser();
         this.upperArmAngle.tickChaser();
         this.headAngle.tickChaser();
         ItemStack currentHeld = this.getHeldItem();
         boolean isPowered = Math.abs(this.getSpeed()) > 0.0F;
         boolean isGun = !currentHeld.isEmpty() && currentHeld.getItem() instanceof IGun;
         FakePlayer fakePlayer = null;
         if (!this.level.isClientSide && isGun) {
            fakePlayer = SentryFakePlayer.get(this);
            if (fakePlayer != null) {
               SentryFakePlayer.sync(fakePlayer, this, this.idleTargetYaw, this.idleTargetPitch, currentHeld);
            }
         }

         if (!isPowered) {
            this.sentryDeactivated();
         } else if (isGun) {
            if (this.level.isClientSide) {
               this.updateClientTarget();
            }

            this.sentryLogic();
         } else {
            this.sentryDeactivated();
         }

         if (!this.level.isClientSide && isGun && fakePlayer != null) {
            try {
               fakePlayer.tick();
               ItemStack handItem = fakePlayer.getMainHandItem();
               if (!handItem.isEmpty()) {
                  handItem.inventoryTick(this.level, fakePlayer, 0, true);
                  if (handItem.getItem() instanceof AbstractGunItem gunItem) {
                     IGunOperator operator = IGunOperator.fromLivingEntity(fakePlayer);
                     gunItem.tickHeat(operator.getDataHolder(), handItem, fakePlayer);
                  }
               }

               for (int i = 0; i < this.attachedAmmoBoxes.size(); i++) {
                  ItemStack playerStack = fakePlayer.getInventory().getItem(9 + i);
                  ItemStack currentBox = (ItemStack)this.attachedAmmoBoxes.get(i);
                  if (playerStack.isEmpty() && !currentBox.isEmpty()) {
                     fakePlayer.getInventory().setItem(9 + i, currentBox.copy());
                  } else if ((!playerStack.isEmpty() || currentBox.isEmpty()) && !ItemStack.matches(playerStack, currentBox)) {
                     this.attachedAmmoBoxes.set(i, playerStack.copy());
                     this.setChanged();
                     this.sendData();
                  }
               }
            } catch (Exception var9) {
            }

            ItemStack fakeHeld = fakePlayer.getMainHandItem();
            if (fakeHeld.getItem() == currentHeld.getItem()
               && ItemNBTHelper.hasTag(fakeHeld)
               && !Objects.equals(ItemNBTHelper.getTag(currentHeld), ItemNBTHelper.getTag(fakeHeld))) {
               ItemNBTHelper.setTag(currentHeld, ItemNBTHelper.getTag(fakeHeld).copy());
               this.setChanged();
               this.sendData();
            }
         }

         if (!this.level.isClientSide && !this.attachedAmmoBoxes.isEmpty() && (currentHeld.isEmpty() || !(currentHeld.getItem() instanceof IGun))) {
            this.popAmmoBox();
         }

         if (!this.level.isClientSide && this.syncedTargetId != -1 && ++this.angleSyncTimer >= 5) {
            this.angleSyncTimer = 0;
            this.sendData();
         }
      }
   }

   private void sentryDeactivated() {
      float speed = 0.05F;
      this.lowerArmAngle.chase(135.0, (double)speed, Chaser.EXP);
      this.upperArmAngle.chase(45.0, (double)speed, Chaser.EXP);
      this.headAngle.chase(0.0, (double)speed, Chaser.EXP);
      this.baseAngle.chase((double)this.baseAngle.getValue(), 0.0, Chaser.EXP);
   }

   private double getSentryRange() {
      if (this.rangeScroll != null && !this.heldItem.isEmpty()) {
         double rawValue = (double)this.rangeScroll.getValue();
         return Math.min(rawValue, 256.0);
      } else {
         return 16.0;
      }
   }

   public void setLastShootTime(long time) {
      this.lastShootTime = time;
   }

   private void sentryLogic() {
      if (this.currentStatus == SentryArmBlockEntity.SentryStatus.NO_AMMO && !this.level.isClientSide) {
         if (!this.hasAnyAmmo()) {
            this.sentryDeactivated();
            return;
         }

         this.setStatus(SentryArmBlockEntity.SentryStatus.IDLE);
      }

      if (!this.level.isClientSide) {
         FakePlayer fp = SentryFakePlayer.get(this);
         if (fp != null) {
            SentryFakePlayer.sync(fp, this, this.baseAngle.getValue(), this.headAngle.getValue(), this.heldItem);
         }
      }

      Vec3 currentTickBestPos = null;
      if (!this.level.isClientSide) {
         double maxRange = this.getSentryRange();
         boolean invalid = false;
         Vec3 rangeCheckCenter = this.isInSableSubLevel() ? this.getProjectedWorldPos() : this.worldPosition.getCenter();
         this.refreshMarkedPosFromFireControl();
         if (this.cachedTarget != null) {
            if (++this.targetRescanTimer >= 300) {
               this.targetRescanTimer = 0;
               invalid = true;
            } else if (this.cachedTarget.isAlive()
               && !this.cachedTarget.isRemoved()
               && !(this.cachedTarget.distanceToSqr(rangeCheckCenter) > maxRange * maxRange)) {
               if (this.lineOfSightTicker++ >= 10) {
                  this.lineOfSightTicker = 0;
                  currentTickBestPos = this.getBestTargetPos(this.cachedTarget);
                  if (currentTickBestPos == null) {
                     invalid = true;
                  }
               }
            } else {
               invalid = true;
            }
         } else if (this.cachedTargetBlock != null) {
            if (!(this.cachedTargetBlock.distToCenterSqr(rangeCheckCenter) > maxRange * maxRange)
               && this.level.getBlockState(this.cachedTargetBlock).is(Blocks.TARGET)) {
               if (this.lineOfSightTicker++ >= 20) {
                  this.lineOfSightTicker = 0;
                  if (!this.isBlockVisible(this.getActualMuzzlePos(), this.cachedTargetBlock)) {
                     invalid = true;
                  }
               }
            } else {
               invalid = true;
               if (!this.level.getBlockState(this.cachedTargetBlock).is(Blocks.TARGET)) {
                  SentryTargetSavedData.get(this.level).removeTarget(this.cachedTargetBlock);
               }

               this.cachedTargetBlock = null;
               this.syncTargetBlock();
            }
         } else if (this.cachedMarkedPos != null && this.hasExplosiveAmmo()) {
            Vec3 trackedPos = this.updateMarkedPos();
            if (trackedPos != null && trackedPos.distanceToSqr(rangeCheckCenter) <= maxRange * maxRange) {
               currentTickBestPos = trackedPos;
               this.isCurrentTargetMarkedPos = true;
            } else {
               invalid = true;
            }
         } else if (this.scanCooldown-- <= 0) {
            this.scanCooldown = 4;
            this.scanForTarget();
         }

         if (invalid) {
            this.cachedTarget = null;
            if (this.cachedTargetBlock != null) {
               this.cachedTargetBlock = null;
               this.syncTargetBlock();
            }

            this.cachedMarkedPos = null;
            this.cachedMarkedContraptionId = -1;
            this.cachedMarkedLocalPos = null;
            this.cachedTrackedMarkedPos = null;
            this.setTargetId(-1);
            this.scanCooldown = 0;
         }
      }

      // Entity targeting is server-authoritative. While an entity target is
      // active, the client only advances the LerpedFloats toward angles received
      // through block-entity synchronization.
      if (this.level.isClientSide && this.syncedTargetId != -1) {
         return;
      }

      if (currentTickBestPos == null) {
         if (this.cachedTarget != null && this.cachedTarget.isAlive()) {
            currentTickBestPos = this.getBestTargetPos(this.cachedTarget);
         } else if (this.cachedTargetBlock != null) {
            currentTickBestPos = Vec3.atCenterOf(this.cachedTargetBlock);
         } else if (this.cachedMarkedPos != null && this.hasExplosiveAmmo()) {
            currentTickBestPos = this.updateMarkedPos();
         }
      }

      if (currentTickBestPos != null) {
         boolean useLocalSpace = this.isInSableSubLevel();
         Vec2 targetAngles;
         if (useLocalSpace) {
            Vec3 subTarget = this.worldToSubLevel(currentTickBestPos);
            Vec3 subMuzzle = this.getLocalMuzzlePos();
            targetAngles = this.calculateAngleBetween(subMuzzle, subTarget);
         } else {
            targetAngles = this.calculateTruthAngle(currentTickBestPos);
         }

         if (!this.level.isClientSide || this.syncedTargetId == -1) {
            this.aimAtAngle(targetAngles.x, targetAngles.y);
         }

         if (!this.level.isClientSide) {
            float currentYaw;
            float currentPitch;
            if (this.isCeiling()) {
               currentYaw = this.baseAngle.getValue();
               currentPitch = this.headAngle.getValue();
            } else {
               currentYaw = 180.0F - this.baseAngle.getValue();
               currentPitch = -this.headAngle.getValue();
            }

            Vec3 alignmentOrigin = useLocalSpace ? this.getLocalMuzzlePos() : this.getActualMuzzlePos();
            Vec3 alignmentTarget = useLocalSpace ? this.worldToSubLevel(currentTickBestPos) : currentTickBestPos;
            SentryAimUtil.BarrelPose barrelPose = SentryAimUtil.barrelPose(
               alignmentOrigin, Vec3.directionFromRotation(currentPitch, currentYaw)
            );
            float currentUpperArm = this.upperArmAngle.getValue();
            boolean isDeployed = currentUpperArm > 80.0F;
            if (SentryAimUtil.isAligned(barrelPose, alignmentTarget, AIM_TOLERANCE_DEGREES) && isDeployed) {
               Vec2 worldAngles = this.calculateTruthAngle(currentTickBestPos);
               this.fireGun(worldAngles.x, worldAngles.y);
            }
         }
      } else if (!this.level.isClientSide || this.syncedTargetId == -1) {
         this.resetAimer();
      }
   }

   private void aimAtAngle(float targetYaw, float targetPitch) {
      if (this.isCeiling()) {
         targetPitch = -targetPitch;
         targetYaw = -targetYaw + 180.0F;
      }

      float currentYaw = this.baseAngle.getValue();
      float desiredYaw = -targetYaw + 180.0F;
      float yawDiff = desiredYaw - currentYaw;

      while (yawDiff < -180.0F) {
         yawDiff += 360.0F;
      }

      while (yawDiff > 180.0F) {
         yawDiff -= 360.0F;
      }

      float absYawDiff = Math.abs(yawDiff);
      if (absYawDiff < 0.5F) {
         this.baseAngle.chase((double)(currentYaw + yawDiff), 1.0, Chaser.EXP);
      } else {
         float yawSpeedBase;
         if (absYawDiff < 10.0F) {
            yawSpeedBase = 0.44F;
         } else if (absYawDiff < 45.0F) {
            yawSpeedBase = 0.22F;
         } else {
            yawSpeedBase = 0.19F;
         }

         this.baseAngle.chase((double)(currentYaw + yawDiff), (double)this.getAnimationSpeed(yawSpeedBase), Chaser.EXP);
      }

      float currentPitch = this.headAngle.getValue();
      float desiredPitch = Mth.clamp(-targetPitch, -90.0F, 90.0F);
      float pitchDiff = desiredPitch - currentPitch;
      float absPitchDiff = Math.abs(pitchDiff);
      if (absPitchDiff < 0.5F) {
         this.headAngle.chase((double)desiredPitch, 1.0, Chaser.EXP);
      } else {
         float pitchSpeedBase;
         if (absPitchDiff < 5.0F) {
            pitchSpeedBase = 0.44F;
         } else {
            pitchSpeedBase = 0.19F;
         }

         this.headAngle.chase((double)desiredPitch, (double)this.getAnimationSpeed(pitchSpeedBase), Chaser.EXP);
      }

      this.upperArmAngle.chase(90.0, (double)this.getAnimationSpeed(0.4F), Chaser.EXP);
      this.lowerArmRecoilOffset = Mth.lerp(0.7F, this.lowerArmRecoilOffset, 0.0F);
      if (Math.abs(this.lowerArmRecoilOffset) < 0.01F) {
         this.lowerArmRecoilOffset = 0.0F;
      }

      float targetLowerArm = 135.0F + this.lowerArmRecoilOffset;
      this.lowerArmAngle.chase((double)targetLowerArm, 0.6F, Chaser.EXP);
   }

   public Vec3 getActualMuzzlePos() {
      if (this.isInSableSubLevel()) {
         return this.getProjectedMuzzlePos();
      } else {
         boolean isCeiling = this.isCeiling();
         float yaw = isCeiling ? this.baseAngle.getValue() : 180.0F - this.baseAngle.getValue();
         float pitch = isCeiling ? this.headAngle.getValue() : -this.headAngle.getValue();
         double armLen = SentryFakePlayer.getGunArmLength(this.heldItem);
         if (SentryFakePlayer.hasEntityBullet(this.heldItem)) {
            Vec3 muzzleBase = SentryFakePlayer.getMuzzlePosition(this, yaw, pitch, 0.0);
            return muzzleBase.add(0.0, 1.62, 0.0);
         } else {
            return SentryFakePlayer.getMuzzlePosition(this, yaw, pitch, armLen);
         }
      }
   }

   public boolean isInSableSubLevel() {
      return AeronauticsHelper.isInSableSubLevel(this.level, this.worldPosition);
   }

   public Vec3 getProjectedWorldPos() {
      return this.isInSableSubLevel() ? AeronauticsHelper.sableSubLevelToWorld(this.level, this.worldPosition.getCenter()) : this.worldPosition.getCenter();
   }

   public Vec3 worldToSubLevel(Vec3 worldPos) {
      return this.isInSableSubLevel() ? AeronauticsHelper.sableWorldToSubLevel(this.level, worldPos, this.worldPosition) : worldPos;
   }

   public Vec3 getProjectedMuzzlePos() {
      Vec3 local = this.worldPosition.getCenter().add(0.0, 1.5, 0.0);
      if (this.isCeiling()) {
         local = local.add(0.0, -4.0, 0.0);
      }

      if (this.isInSableSubLevel()) {
         local = local.add(0.0, 0.42, 0.0);
         return AeronauticsHelper.sableSubLevelToWorld(this.level, local);
      } else {
         return AeronauticsHelper.isAeronauticsLoaded() ? AeronauticsHelper.projectOutOfSubLevel(this.level, local, this.worldPosition.getCenter()) : local;
      }
   }

   private void resetAimer() {
      this.sentryIdleScanning();
   }

   public void updateFromFireControl() {
      this.cachedTarget = null;
      this.cachedTargetBlock = null;
      this.setTargetId(-1);
      this.scanCooldown = 0;
   }

   private void scanForTarget() {
      double range = this.getSentryRange();
      if (!(range < 1.0)) {
         if (range > 256.0) {
            range = 256.0;
         }

         boolean isStrictControlMode = false;
         boolean isWhitelistMode = false;
         List<String> activeWhitelist = null;
         BlockEntity be = null;
         if (this.connectedFireControlPos != null && this.level.isLoaded(this.connectedFireControlPos)) {
            be = this.level.getBlockEntity(this.connectedFireControlPos);
            if (be instanceof BlazeFireControlBlockEntity fc) {
               if (this.connectedFireControlPos.distSqr(this.worldPosition) > 36.0) {
                  this.disconnectFireControl();
               } else if (!fc.inventory.getStackInSlot(0).isEmpty()) {
                  isStrictControlMode = true;
                  isWhitelistMode = fc.isWhitelist();
                  activeWhitelist = fc.getTargetList();
               }
            } else {
               this.disconnectFireControl();
            }
         }

         Vec3 localCenter = this.worldPosition.getCenter();
         Vec3 center;
         if (this.isInSableSubLevel()) {
            center = this.getProjectedWorldPos();
         } else if (AeronauticsHelper.isAeronauticsLoaded()) {
            center = AeronauticsHelper.projectOutOfSubLevel(this.level, localCenter, localCenter);
         } else {
            center = localCenter;
         }

         if (be instanceof BlazeFireControlBlockEntity fc2) {
            int focusId = fc2.getFocusedEntityId();
            if (focusId != -1
               && this.level.getEntity(focusId) instanceof LivingEntity living
               && living.isAlive()
               && !living.isSpectator()
               && living.distanceToSqr(center) <= range * range) {
               Vec3 focusPos = this.getBestTargetPos(living);
               if (focusPos != null) {
                  this.cachedTarget = living;
                  this.currentTargetIsMarked = false;
                  this.setTargetId(living.getId());
                  this.cachedTargetBlock = null;
                  this.targetRescanTimer = 0;
                  return;
               }
            }

            for (int markedId : fc2.getMarkedEntityIds()) {
               Entity markedEntity = this.level.getEntity(markedId);
               if (markedEntity instanceof LivingEntity) {
                  LivingEntity livingx = (LivingEntity)markedEntity;
                  if (livingx.isAlive() && !livingx.isSpectator() && livingx.distanceToSqr(center) <= range * range) {
                     Vec3 markedPos = this.getBestTargetPos(livingx);
                     if (markedPos != null) {
                        this.cachedTarget = livingx;
                        this.currentTargetIsMarked = true;
                        this.setTargetId(livingx.getId());
                        this.cachedTargetBlock = null;
                        this.targetRescanTimer = 0;
                        return;
                     }
                  }
               }
            }
         }

         boolean finalStrict = isStrictControlMode;
         boolean finalWhitelistMode = isWhitelistMode;
         List<String> finalList = activeWhitelist;
         double maxRangeSq = range * range;
         AABB area;
         if (this.isInSableSubLevel()) {
            area = new AABB(center, center).inflate(range);
         } else {
            area = new AABB(this.worldPosition).inflate(range);
         }

         List<LivingEntity> potentialTargets = this.level.getEntitiesOfClass(LivingEntity.class, area, e -> {
            if (e.distanceToSqr(center) > maxRangeSq) {
               return false;
            } else if (!e.isAlive() || e.isSpectator()) {
               return false;
            } else if (!finalStrict) {
               return e instanceof Enemy;
            } else if (finalList != null && !finalList.isEmpty()) {
               String name = e.getName().getString();
               boolean inList = false;

               for (String targetName : finalList) {
                  if (targetName.equals(name)) {
                     inList = true;
                     break;
                  }
               }

               return finalWhitelistMode ? !inList : inList;
            } else {
               return finalWhitelistMode && e instanceof Enemy;
            }
         });
         Vec3 compareCenter = this.isInSableSubLevel() ? center : this.worldPosition.getCenter();
         LivingEntity newTarget = potentialTargets.stream()
            .filter(target -> this.getBestTargetPos(target) != null)
            .min(Comparator.comparingDouble(e -> e.distanceToSqr(compareCenter)))
            .orElse(null);
         if (newTarget != null) {
            if (this.cachedTarget != newTarget) {
               this.cachedTarget = newTarget;
               this.setTargetId(newTarget.getId());
            }

            this.targetRescanTimer = 0;
            this.cachedTargetBlock = null;
         } else {
            if (this.cachedTarget != null) {
               this.cachedTarget = null;
               this.setTargetId(-1);
            }

            if (!this.level.isClientSide) {
               Set<BlockPos> targets = SentryTargetSavedData.get(this.level).getTargets();
               BlockPos bestBlock = null;
               double minDstSqr = range * range;
               Vec3 muzzle = this.getActualMuzzlePos();

               for (BlockPos pos : targets) {
                  double dstSqr = center.distanceToSqr((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5);
                  if (!(dstSqr > minDstSqr) && this.isBlockVisible(muzzle, pos)) {
                     minDstSqr = dstSqr;
                     bestBlock = pos;
                  }
               }

               if (!Objects.equals(this.cachedTargetBlock, bestBlock)) {
                  this.cachedTargetBlock = bestBlock;
                  this.syncTargetBlock();
               }
            }
         }
      }
   }

   private void setTargetId(int id) {
      if (this.syncedTargetId != id) {
         this.syncedTargetId = id;
         this.setChanged();
         this.sendData();
      }
   }

   private Vec3 getBestTargetPos(LivingEntity target) {
      Vec3 armPos = this.getActualMuzzlePos();
      Vec3 headPos = SentryAimUtil.headAimPoint(target);
      if (this.isPointVisible(armPos, headPos)) {
         return headPos;
      }

      Vec3 centerPos = target.getBoundingBox().getCenter();
      return centerPos.distanceToSqr(headPos) > 1.0E-8 && this.isPointVisible(armPos, centerPos)
         ? centerPos
         : null;
   }

   private Vec3 updateMarkedPos() {
      if (++this.markedPosUpdateTimer < 5) {
         if (this.cachedTrackedMarkedPos != null) {
            return this.cachedTrackedMarkedPos;
         }

         this.markedPosUpdateTimer = 5;
      }

      this.markedPosUpdateTimer = 0;
      if (this.cachedMarkedContraptionId != -1) {
         if (this.level.getEntity(this.cachedMarkedContraptionId) instanceof AbstractContraptionEntity ace && ace.isAlive()) {
            Vec3 localCenter = Vec3.atCenterOf(this.cachedMarkedLocalPos);
            Vec3 global = ace.toGlobalVector(localCenter, 1.0F);
            this.cachedMarkedPos = global;
            this.cachedTrackedMarkedPos = global;
            return global;
         }

         this.cachedTrackedMarkedPos = null;
         return null;
      } else if (this.isSableMarked && this.sableMarkedLocalPos != null) {
         Vec3 projected = AeronauticsHelper.sableSubLevelToWorld(this.level, this.sableMarkedLocalPos);
         this.cachedMarkedPos = projected;
         this.cachedTrackedMarkedPos = projected;
         return projected;
      } else {
         this.cachedTrackedMarkedPos = this.cachedMarkedPos;
         return this.cachedMarkedPos;
      }
   }

   private void refreshMarkedPosFromFireControl() {
      if (this.connectedFireControlPos != null
         && this.level.isLoaded(this.connectedFireControlPos)
         && this.level.getBlockEntity(this.connectedFireControlPos) instanceof BlazeFireControlBlockEntity fc) {
         Vec3 wp = fc.getMarkedWorldPos();
         if (wp != null) {
            if (!wp.equals(this.cachedMarkedPos)) {
               this.markedPosShotCounter = 0;
            }

            this.cachedMarkedPos = wp;
            this.cachedMarkedContraptionId = fc.getMarkedContraptionEntityId();
            this.cachedMarkedLocalPos = fc.getMarkedLocalPos();
            this.isSableMarked = fc.isSableMarked();
            this.sableMarkedLocalPos = fc.getSableMarkedLocalPos();
         } else {
            this.cachedMarkedPos = null;
            this.cachedMarkedContraptionId = -1;
            this.cachedMarkedLocalPos = null;
            this.isSableMarked = false;
            this.sableMarkedLocalPos = null;
            this.cachedTrackedMarkedPos = null;
            this.markedPosShotCounter = 0;
         }
      }
   }

   private boolean hasExplosiveAmmo() {
      if (!this.heldItem.isEmpty() && this.heldItem.getItem() instanceof IGun iGun) {
         ResourceLocation gunId = iGun.getGunId(this.heldItem);
         Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gunId);
         if (index.isPresent()) {
            ResourceLocation ammoId = index.get().getGunData().getAmmoId();
            if (ammoId != null) {
               String path = ammoId.getPath().toLowerCase();

               for (String kw : EXPLOSIVE_KEYWORDS) {
                  if (path.contains(kw)) {
                     return true;
                  }
               }
            }

            String gunPath = gunId.getPath().toLowerCase();

            for (String kwx : EXPLOSIVE_KEYWORDS) {
               if (gunPath.contains(kwx)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isPointVisible(Vec3 start, Vec3 end) {
      if (this.isInSableSubLevel()) {
         Vec3 subStart = this.worldToSubLevel(start);
         Vec3 subEnd = this.worldToSubLevel(end);
         if (subStart.distanceToSqr(subEnd) > 1000000.0) {
            return false;
         } else {
            BlockHitResult result = this.level.clip(new ClipContext(subStart, subEnd, Block.COLLIDER, Fluid.NONE, CollisionContext.empty()));
            return result.getType() == Type.MISS;
         }
      } else {
         if (AeronauticsHelper.isAeronauticsLoaded()) {
            Vec3 worldDir = end.subtract(start);
            Vec3 localDir = AeronauticsHelper.toLocalVector(this.level, this.worldPosition.getCenter(), worldDir);
            end = start.add(localDir);
         }

         BlockHitResult result = this.level.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, CollisionContext.empty()));
         return result.getType() == Type.MISS;
      }
   }

   private float getAnimationSpeed(float baseChaserSpeed) {
      float currentRpm = Math.abs(this.getSpeed());
      float multiplier = Mth.map(currentRpm, 1.0F, 256.0F, 0.01F, 1.0F);
      multiplier = Mth.clamp(multiplier, 0.01F, 1.0F);
      return baseChaserSpeed * multiplier;
   }

   public void disconnectFireControl() {
      this.connectedFireControlPos = null;
      this.projectedFireControlPos = null;
      this.cachedTarget = null;
      this.setTargetId(-1);
      this.setChanged();
      this.syncTargetBlock();
   }

   private void fireGun(float targetYaw, float targetPitch) {
      if (!this.heldItem.isEmpty() && !this.level.isClientSide) {
         FakePlayer fakePlayer = SentryFakePlayer.get(this);
         if (fakePlayer != null) {
            SentryFakePlayer.sync(fakePlayer, this, targetYaw, targetPitch, this.heldItem);
            IGunOperator operator = IGunOperator.fromLivingEntity(fakePlayer);
            ItemStack fakeHeldItem = fakePlayer.getMainHandItem();
            IGun iGunFake = IGun.getIGunOrNull(fakeHeldItem);
            if (iGunFake != null) {
               long gameTick = this.level.getGameTime();
               ShooterDataHolder dataHolder = operator.getDataHolder();
               if (this.shootDelayAccumulator > 0.0F) {
                  this.shootDelayAccumulator--;
                  this.triggerHoldTime = 0;
                  if (fakeHeldItem.getItem() == this.heldItem.getItem() && ItemNBTHelper.hasTag(fakeHeldItem)) {
                     ItemNBTHelper.setTag(this.heldItem, ItemNBTHelper.getTag(fakeHeldItem).copy());
                  }
               } else {
                  ResourceLocation gunIdRes = iGunFake.getGunId(fakeHeldItem);
                  Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gunIdRes);
                  FireMode mode = iGunFake.getFireMode(fakeHeldItem);
                  if (gunIndex.isPresent()) {
                     this.applySentryAccuracyModifier(operator, iGunFake, fakeHeldItem, gunIndex.get().getGunData());
                  }

                  this.triggerHoldTime++;
                  boolean hasEntityBullet = SentryFakePlayer.hasEntityBullet(this.heldItem);
                  Vec3 originalPos = fakePlayer.position();
                  if (!hasEntityBullet) {
                     double armLen = SentryFakePlayer.getGunArmLength(this.heldItem);
                     Vec3 muzzlePos = SentryFakePlayer.getMuzzlePosition(this, targetYaw, targetPitch, armLen);
                     double fpY = muzzlePos.y - (double)fakePlayer.getEyeHeight();
                     fakePlayer.setPos(muzzlePos.x, fpY, muzzlePos.z);
                     fakePlayer.xo = muzzlePos.x;
                     fakePlayer.yo = fpY;
                     fakePlayer.zo = muzzlePos.z;
                     fakePlayer.xOld = muzzlePos.x;
                     fakePlayer.yOld = fpY;
                     fakePlayer.zOld = muzzlePos.z;
                  }

                  ShootResult result = ShootResult.UNKNOWN_FAIL;

                  try {
                     result = operator.shoot(() -> targetPitch, () -> targetYaw);
                  } catch (Exception var23) {
                     LOGGER.error("Error executing operator.shoot", var23);
                  }

                  if (!hasEntityBullet) {
                     fakePlayer.setPos(originalPos.x, originalPos.y, originalPos.z);
                     fakePlayer.xo = originalPos.x;
                     fakePlayer.yo = originalPos.y;
                     fakePlayer.zo = originalPos.z;
                     fakePlayer.xOld = originalPos.x;
                     fakePlayer.yOld = originalPos.y;
                     fakePlayer.zOld = originalPos.z;
                  }

                  boolean actuallyFired = SentryFakePlayer.checkAndClearFired(fakePlayer);
                  if (actuallyFired && this.isCurrentTargetMarkedPos) {
                     this.markedPosShotCounter++;
                     if (this.markedPosShotCounter >= 1) {
                        this.cachedMarkedPos = null;
                        this.cachedMarkedContraptionId = -1;
                        this.cachedMarkedLocalPos = null;
                        this.cachedTrackedMarkedPos = null;
                        this.markedPosShotCounter = 0;
                        this.isCurrentTargetMarkedPos = false;
                        if (this.connectedFireControlPos != null
                           && this.level.isLoaded(this.connectedFireControlPos)
                           && this.level.getBlockEntity(this.connectedFireControlPos) instanceof BlazeFireControlBlockEntity fc) {
                           fc.clearMarkedPos();
                        }

                        return;
                     }
                  }

                  SentryArmBlockEntity.FireContext ctx = new SentryArmBlockEntity.FireContext(
                     fakePlayer, this.heldItem, fakeHeldItem, iGunFake, operator, dataHolder, gunIndex, actuallyFired, mode
                  );
                  String currentScriptDataStr = this.getLuaDataSnapshot(ctx.dataHolder.scriptData);
                  float currentAimingProgress = ctx.operator.getSynAimingProgress();
                  boolean isScriptChanging = !currentScriptDataStr.equals(this.lastScriptDataStr);
                  boolean isProgressChanging = Math.abs(currentAimingProgress - this.lastAimingProgress) > 0.001F;
                  this.lastScriptDataStr = currentScriptDataStr;
                  this.lastAimingProgress = currentAimingProgress;
                  boolean isGunActive = ctx.actuallyFired || isScriptChanging || isProgressChanging;
                  if (isGunActive && !this.wasCharging) {
                     this.sendActionPacket(SentryShootPacket.ActionType.CHARGE);
                  }

                  this.wasCharging = isGunActive;
                  switch (result) {
                     case NEED_BOLT:
                        this.handleNeedBolt(ctx);
                        break;
                     case NO_AMMO:
                        this.handleNoAmmo(ctx);
                        break;
                     case OVERHEATED:
                        this.handleOverheated(ctx);
                        break;
                     case IS_BOLTING:
                        this.handleIsBolting(ctx);
                        break;
                     case IS_RELOADING:
                     case IS_DRAWING:
                        this.handleWaitState();
                        break;
                     case SUCCESS:
                        this.handleSuccess(ctx);
                  }

                  if (ctx.actuallyFired) {
                     this.wasCharging = false;
                  }

                  if (fakeHeldItem.getItem() == this.heldItem.getItem() && ItemNBTHelper.hasTag(fakeHeldItem)) {
                     ItemNBTHelper.setTag(this.heldItem, ItemNBTHelper.getTag(fakeHeldItem).copy());
                  }
               }
            }
         }
      }
   }

   private void handleWaitState() {
      this.shootDelayAccumulator = 2.0F;
      this.setStatus(SentryArmBlockEntity.SentryStatus.RELOADING);
   }

   private void handleNeedBolt(SentryArmBlockEntity.FireContext ctx) {
      ctx.operator.bolt();
      this.setStatus(SentryArmBlockEntity.SentryStatus.BOLTING);
      this.sendActionPacket(SentryShootPacket.ActionType.BOLT);
      float boltTime = 0.5F;
      if (ctx.gunIndex.isPresent()) {
         boltTime = ctx.gunIndex.get().getGunData().getBoltActionTime();
      }

      this.shootDelayAccumulator = Math.max(10.0F, boltTime * 20.0F) + 5.0F;
   }

   private void handleNoAmmo(SentryArmBlockEntity.FireContext ctx) {
      boolean reloaded = this.performInstantReload(ctx.player, ctx.iGunFake, ctx.fakeHeldItem);
      if (reloaded) {
         this.shootDelayAccumulator = 2.0F;
         this.setStatus(SentryArmBlockEntity.SentryStatus.RELOADING);
      } else {
         this.shootDelayAccumulator = 40.0F;
         this.setStatus(SentryArmBlockEntity.SentryStatus.NO_AMMO);
      }
   }

   private void handleIsBolting(SentryArmBlockEntity.FireContext ctx) {
      long boltTimestamp = ctx.dataHolder.boltTimestamp;
      float boltTime = 0.5F;
      if (ctx.gunIndex.isPresent()) {
         boltTime = ctx.gunIndex.get().getGunData().getBoltActionTime();
      }

      long boltDurationMs = (long)(boltTime * 1000.0F);
      long elapsed = System.currentTimeMillis() - boltTimestamp;
      if (elapsed > boltDurationMs + 500L) {
         if (!ctx.iGunFake.hasBulletInBarrel(ctx.fakeHeldItem) && ctx.iGunFake.getCurrentAmmoCount(ctx.fakeHeldItem) > 0) {
            ctx.iGunFake.reduceCurrentAmmoCount(ctx.fakeHeldItem);
            ctx.iGunFake.setBulletInBarrel(ctx.fakeHeldItem, true);
         }

         ctx.dataHolder.isBolting = false;
         ctx.dataHolder.boltTimestamp = -1L;
         this.shootDelayAccumulator = 0.0F;
      } else {
         this.shootDelayAccumulator = 2.0F;
      }
   }

   private void handleOverheated(SentryArmBlockEntity.FireContext ctx) {
      long heatTimestamp = ctx.dataHolder.heatTimestamp;
      long currentTimestamp = System.currentTimeMillis();
      long idleTime = currentTimestamp - heatTimestamp;
      long coolingDelay = 2000L;
      if (ctx.gunIndex.isPresent() && ctx.gunIndex.get().getGunData().getHeatData() != null) {
         coolingDelay = ctx.gunIndex.get().getGunData().getHeatData().getCoolingDelay();
      }

      long gracePeriod = coolingDelay + 2000L;
      if (idleTime < gracePeriod) {
         this.setStatus(SentryArmBlockEntity.SentryStatus.COOLING);
         this.shootDelayAccumulator = 20.0F;
      } else {
         float currentHeat = ctx.iGunFake.getHeatAmount(ctx.fakeHeldItem);
         float maxHeat = 0.0F;
         if (ctx.gunIndex.isPresent() && ctx.gunIndex.get().getGunData().getHeatData() != null) {
            maxHeat = ctx.gunIndex.get().getGunData().getHeatData().getHeatMax();
         }

         boolean isCoolingDown = maxHeat > 0.0F && currentHeat < maxHeat - 0.01F;
         if (isCoolingDown) {
            this.setStatus(SentryArmBlockEntity.SentryStatus.COOLING);
            this.shootDelayAccumulator = 20.0F;
         } else {
            boolean consumed = false;
            if (ctx.gunIndex.isPresent()) {
               ResourceLocation requiredAmmoId = ctx.gunIndex.get().getGunData().getAmmoId();
               consumed = this.tryConsumeGenericAmmo(ctx.player, requiredAmmoId);
            }

            if (consumed) {
               ctx.iGunFake.setHeatAmount(ctx.fakeHeldItem, 0.0F);
               ctx.iGunFake.setOverheatLocked(ctx.fakeHeldItem, false);
               ctx.dataHolder.reloadStateType = StateType.NOT_RELOADING;
               ctx.dataHolder.reloadTimestamp = -1L;
               this.shootDelayAccumulator = 10.0F;
            } else {
               this.shootDelayAccumulator = 40.0F;
            }

            this.setStatus(SentryArmBlockEntity.SentryStatus.COOLING);
         }
      }
   }

   private void handleSuccess(SentryArmBlockEntity.FireContext ctx) {
      this.lastShootTime = System.currentTimeMillis();
      Bolt boltType = Bolt.OPEN_BOLT;
      if (ctx.gunIndex.isPresent()) {
         boltType = ctx.gunIndex.get().getGunData().getBolt();
      }

      boolean isManualAction = boltType == Bolt.MANUAL_ACTION;
      this.setStatus(SentryArmBlockEntity.SentryStatus.IDLE);
      if (isManualAction) {
         this.handleManualActionStrategy(ctx);
      } else if (ctx.fireMode != FireMode.SEMI && ctx.fireMode != FireMode.BURST) {
         this.handleAdaptiveAutoStrategy(ctx);
         if (ctx.actuallyFired) {
            if (ctx.gunIndex.isPresent()) {
               float rpm = (float)ctx.gunIndex.get().getGunData().getRoundsPerMinute(FireMode.AUTO);
               if (rpm > 0.0F) {
                  this.shootDelayAccumulator = 1200.0F / rpm;
               } else {
                  this.shootDelayAccumulator = 2.0F;
               }
            } else {
               this.shootDelayAccumulator = 2.0F;
            }
         }
      } else {
         this.handleSemiAutoStrategy(ctx);
      }

      if (ctx.actuallyFired) {
         this.sendShootPacket(ctx.player);
      }
   }

   private void handleManualActionStrategy(SentryArmBlockEntity.FireContext ctx) {
      if (ctx.actuallyFired) {
         this.setStatus(SentryArmBlockEntity.SentryStatus.SHOOTING);
         this.shootDelayAccumulator = 4.0F;
         this.triggerHoldTime = 0;
      } else {
         this.setStatus(SentryArmBlockEntity.SentryStatus.SHOOTING);
         this.triggerHoldTime++;
         if (this.triggerHoldTime > 20) {
            this.shootDelayAccumulator = 4.0F;
            this.triggerHoldTime = 0;
         } else {
            this.shootDelayAccumulator = 0.0F;
         }
      }
   }

   private void handleSemiAutoStrategy(SentryArmBlockEntity.FireContext ctx) {
      if (ctx.actuallyFired) {
         this.setStatus(SentryArmBlockEntity.SentryStatus.SHOOTING);
         this.shootDelayAccumulator = 8.0F;
      } else {
         this.setStatus(SentryArmBlockEntity.SentryStatus.IDLE);
         if (this.triggerHoldTime > 60) {
            this.shootDelayAccumulator = 10.0F;
            this.triggerHoldTime = 0;
         } else {
            this.shootDelayAccumulator = 0.0F;
         }
      }
   }

   private void handleAdaptiveAutoStrategy(SentryArmBlockEntity.FireContext ctx) {
      if (this.isTestingRelease) {
         if (ctx.actuallyFired) {
            this.isTestingRelease = false;
         } else {
            this.releaseWatchTimer--;
            if (this.releaseWatchTimer <= 0) {
               this.currentTimeoutThreshold = Math.min(this.currentTimeoutThreshold * 2, 1200);
               this.isTestingRelease = false;
            }
         }
      }

      String currentScriptDataStr = this.getLuaDataSnapshot(ctx.dataHolder.scriptData);
      float currentAimingProgress = ctx.operator.getSynAimingProgress();
      boolean isScriptChanging = !currentScriptDataStr.equals(this.lastScriptDataStr);
      boolean isProgressChanging = Math.abs(currentAimingProgress - this.lastAimingProgress) > 0.001F;
      this.lastScriptDataStr = currentScriptDataStr;
      this.lastAimingProgress = currentAimingProgress;
      boolean isGunActive = ctx.actuallyFired || isScriptChanging || isProgressChanging;
      this.wasCharging = isGunActive;
      if (ctx.actuallyFired) {
         this.setStatus(SentryArmBlockEntity.SentryStatus.SHOOTING);
         this.triggerHoldTime = 0;
         this.shootDelayAccumulator = 0.0F;
         this.wasCharging = false;
      } else if (isGunActive) {
         this.setStatus(SentryArmBlockEntity.SentryStatus.CHARGING);
         this.triggerHoldTime++;
         if (this.triggerHoldTime > this.currentTimeoutThreshold) {
            this.shootDelayAccumulator = 10.0F;
            this.triggerHoldTime = 0;
            this.isTestingRelease = true;
            this.releaseWatchTimer = 20;
         } else {
            this.shootDelayAccumulator = 0.0F;
         }
      } else {
         this.setStatus(SentryArmBlockEntity.SentryStatus.IDLE);
         this.triggerHoldTime++;
         if (this.triggerHoldTime > 60) {
            this.shootDelayAccumulator = 10.0F;
            this.triggerHoldTime = 0;
         } else {
            this.shootDelayAccumulator = 0.0F;
         }
      }
   }

   private void sendShootPacket(FakePlayer fakePlayer) {
      Vec3 lookVec = fakePlayer.getViewVector(1.0F);
      Vec3 realStart = fakePlayer.getEyePosition();
      Vec3 traceEnd = realStart.add(lookVec.scale(100.0));
      BlockHitResult hitResult = this.level.clip(new ClipContext(realStart, traceEnd, Block.COLLIDER, Fluid.NONE, fakePlayer));
      NetworkHandler.sendToNearby(
         new SentryShootPacket(
            this.worldPosition, -1, ItemNBTHelper.getOrCreateTag(this.heldItem), realStart, hitResult.getLocation(), SentryShootPacket.ActionType.SHOOT
         ),
         this.level,
         this.worldPosition
      );
   }

   private boolean tryConsumeGenericAmmo(FakePlayer fakePlayer, ResourceLocation ammoId) {
      if (ammoId == null) {
         return false;
      } else {
         Inventory inventory = fakePlayer.getInventory();

         for (int i = 9; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof IAmmoBox iBox) {
               if (iBox.isAllTypeCreative(stack)) {
                  return true;
               }

               if (Objects.equals(iBox.getAmmoId(stack), ammoId)) {
                  if (iBox.isCreative(stack)) {
                     return true;
                  }

                  if (iBox.getAmmoCount(stack) > 0) {
                     iBox.setAmmoCount(stack, iBox.getAmmoCount(stack) - 1);
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private void applySentryAccuracyModifier(IGunOperator operator, IGun iGun, ItemStack gunStack, GunData gunData) {
      AttachmentCacheProperty cache = operator.getCacheProperty();
      if (cache != null) {
         double distToTarget = 0.0;
         Vec3 accuracyCenter = this.isInSableSubLevel() ? this.getProjectedWorldPos() : this.worldPosition.getCenter();
         if (this.cachedTarget != null) {
            distToTarget = Math.sqrt(this.cachedTarget.distanceToSqr(accuracyCenter));
         } else if (this.cachedTargetBlock != null) {
            distToTarget = Math.sqrt(this.cachedTargetBlock.distToCenterSqr(accuracyCenter));
         }

         float effectiveRange = this.calculateEffectiveRange(gunData);
         ResourceLocation gunId = iGun.getGunId(gunStack);
         boolean isSniper = false;
         Optional<CommonGunIndex> gunIndexOpt = TimelessAPI.getCommonGunIndex(gunId);
         if (gunIndexOpt.isPresent()) {
            String type = gunIndexOpt.get().getType();
            if ("sniper".equalsIgnoreCase(type)) {
               isSniper = true;
            }
         }

         float targetSpread;
         if (isSniper) {
            targetSpread = 0.0F;
         } else if (distToTarget <= (double)effectiveRange) {
            targetSpread = 0.1F;
         } else {
            double excessDistance = distToTarget - (double)effectiveRange;
            targetSpread = (float)(excessDistance * 0.02);
            targetSpread = Math.min(targetSpread, 5.0F);
         }

         Map<InaccuracyType, Float> cachedMap = (Map<InaccuracyType, Float>)cache.getCache(GunProperties.INACCURACY);
         Map<InaccuracyType, Float> mutableInaccuracyMap;
         if (cachedMap != null) {
            mutableInaccuracyMap = new HashMap<>(cachedMap);
         } else {
            mutableInaccuracyMap = new EnumMap<>(InaccuracyType.class);
         }

         mutableInaccuracyMap.put(InaccuracyType.AIM, targetSpread);
         mutableInaccuracyMap.put(InaccuracyType.STAND, targetSpread);
         cache.setCache(GunProperties.INACCURACY, mutableInaccuracyMap);
         operator.getDataHolder().isAiming = true;
         operator.getDataHolder().aimingProgress = 1.0F;
      }
   }

   private boolean performInstantReload(FakePlayer fakePlayer, IGun iGun, ItemStack gunStack) {
      ResourceLocation gunId = iGun.getGunId(gunStack);
      Optional<CommonGunIndex> gunIndexOpt = TimelessAPI.getCommonGunIndex(gunId);
      if (gunIndexOpt.isEmpty()) {
         return false;
      } else {
         GunData gunData = gunIndexOpt.get().getGunData();
         ResourceLocation neededAmmoId = gunData.getAmmoId();
         int maxAmmo = gunData.getAmmoAmount();
         int currentAmmo = iGun.getCurrentAmmoCount(gunStack);
         int neededAmount = maxAmmo - currentAmmo;
         if (neededAmount <= 0) {
            return true;
         } else {
            int totalReloaded = 0;
            Inventory inventory = fakePlayer.getInventory();

            for (int i = 9; i < inventory.getContainerSize(); i++) {
               ItemStack stack = inventory.getItem(i);
               if (!stack.isEmpty() && stack.getItem() instanceof IAmmoBox iBox) {
                  if (iBox.isAllTypeCreative(stack) || iBox.isCreative(stack) && Objects.equals(iBox.getAmmoId(stack), neededAmmoId)) {
                     totalReloaded = neededAmount;
                     break;
                  }

                  if (Objects.equals(iBox.getAmmoId(stack), neededAmmoId)) {
                     int boxCount = iBox.getAmmoCount(stack);
                     int toTake = Math.min(boxCount, neededAmount - totalReloaded);
                     iBox.setAmmoCount(stack, boxCount - toTake);
                     totalReloaded += toTake;
                     if (totalReloaded >= neededAmount) {
                        break;
                     }
                  }
               }
            }

            if (totalReloaded > 0) {
               iGun.setCurrentAmmoCount(gunStack, currentAmmo + totalReloaded);
               if (!iGun.hasBulletInBarrel(gunStack) && iGun.getCurrentAmmoCount(gunStack) > 0) {
                  iGun.reduceCurrentAmmoCount(gunStack);
                  iGun.setBulletInBarrel(gunStack, true);
               }

               IGunOperator operator = IGunOperator.fromLivingEntity(fakePlayer);
               ShooterDataHolder holder = operator.getDataHolder();
               holder.reloadStateType = StateType.NOT_RELOADING;
               holder.reloadTimestamp = -1L;
               holder.isBolting = false;
               holder.boltTimestamp = -1L;
               return true;
            } else {
               return false;
            }
         }
      }
   }

   private float calculateEffectiveRange(GunData gunData) {
      BulletData bulletData = gunData.getBulletData();
      if (bulletData == null) {
         return 32.0F;
      } else {
         float effectiveRange = -1.0F;
         ExtraDamage extraDamage = bulletData.getExtraDamage();
         if (extraDamage != null) {
            LinkedList<DistanceDamagePair> damageAdjust = extraDamage.getDamageAdjust();
            if (damageAdjust != null && !damageAdjust.isEmpty()) {
               effectiveRange = damageAdjust.get(0).getDistance();
            }
         }

         if (effectiveRange <= 0.0F) {
            float speed = bulletData.getSpeed();
            effectiveRange = (speed > 0.0F ? speed : 10.0F) * 12.0F;
         }

         return Math.max(effectiveRange, 8.0F);
      }
   }

   private void popAmmoBox() {
      for (int i = 0; i < this.attachedAmmoBoxes.size(); i++) {
         ItemStack stack = (ItemStack)this.attachedAmmoBoxes.get(i);
         if (!stack.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(this.level, this.worldPosition, stack);
            this.attachedAmmoBoxes.set(i, ItemStack.EMPTY);
         }
      }

      this.setChanged();
      this.sendData();
   }

   public ClientboundBlockEntityDataPacket getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, Provider registries) {
      CompoundTag tag = pkt.getTag();
      if (tag != null) {
         this.read(tag, registries, true);
      }
   }

   public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
      boolean superResult = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
      if (!this.heldItem.isEmpty() && this.heldItem.getItem() instanceof IGun) {
         this.addSentryGunTooltip(tooltip, this.heldItem);
         return true;
      } else {
         return superResult;
      }
   }

   private void addSentryGunTooltip(List<Component> tooltip, ItemStack heldItem) {
      if (heldItem.getItem() instanceof IGun iGun) {
         MutableComponent var19 = Component.literal("    ");
         tooltip.add(var19.copy().append(Component.translatable("sentry.tooltip.firepower").withStyle(ChatFormatting.GRAY)));
         ResourceLocation gunId = iGun.getGunId(heldItem);
         Optional<CommonGunIndex> gunIndexOpt = TimelessAPI.getCommonGunIndex(gunId);
         if (!gunIndexOpt.isEmpty()) {
            GunData gunData = gunIndexOpt.get().getGunData();
            int currentAmmo = iGun.getCurrentAmmoCount(heldItem);
            if (iGun.useInventoryAmmo(heldItem)) {
               currentAmmo = 0;
            }

            int totalAmmo = currentAmmo;
            boolean isInfinite = false;
            ResourceLocation requiredAmmoId = gunData.getAmmoId();

            for (ItemStack box : this.attachedAmmoBoxes) {
               if (!box.isEmpty() && box.getItem() instanceof IAmmoBox iBox) {
                  if (iBox.isAllTypeCreative(box)) {
                     isInfinite = true;
                     break;
                  }

                  ResourceLocation boxAmmoId = iBox.getAmmoId(box);
                  if (requiredAmmoId != null && requiredAmmoId.equals(boxAmmoId)) {
                     if (iBox.isCreative(box)) {
                        isInfinite = true;
                        break;
                     }

                     totalAmmo += iBox.getAmmoCount(box);
                  }
               }
            }

            boolean isAbnormalAmmo = totalAmmo > 100000;
            if (isInfinite) {
               tooltip.add(
                  var19.copy()
                     .append(
                        Component.translatable("sentry.tooltip.ammo")
                           .withStyle(ChatFormatting.GOLD)
                           .append(Component.literal(" ∞").withStyle(ChatFormatting.AQUA))
                     )
               );
            } else if (isAbnormalAmmo) {
               tooltip.add(
                  var19.copy()
                     .append(
                        Component.translatable("sentry.tooltip.ammo")
                           .withStyle(ChatFormatting.GOLD)
                           .append(Component.literal(" /").withStyle(ChatFormatting.GRAY))
                     )
               );
            } else {
               tooltip.add(
                  var19.copy()
                     .append(
                        Component.translatable("sentry.tooltip.ammo")
                           .withStyle(ChatFormatting.GOLD)
                           .append(Component.literal(" " + totalAmmo).withStyle(ChatFormatting.AQUA))
                     )
               );
            }

            tooltip.add(
               var19.copy()
                  .append(
                     Component.translatable("sentry.tooltip.gun")
                        .withStyle(ChatFormatting.GOLD)
                        .append(heldItem.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                  )
            );
            if (gunData.hasHeatData()) {
               float currentHeat = iGun.getHeatAmount(heldItem);
               float maxHeat = gunData.getHeatData().getHeatMax();
               int totalBars = 10;
               int filledBars = (int)(currentHeat / maxHeat * (float)totalBars);
               filledBars = Math.max(0, Math.min(filledBars, totalBars));
               StringBuilder barBuilder = new StringBuilder("[");

               for (int i = 0; i < totalBars; i++) {
                  barBuilder.append(i < filledBars ? "▌" : " ");
               }

               barBuilder.append("]");
               ChatFormatting color = ChatFormatting.GREEN;
               if ((double)((float)filledBars / (float)totalBars) > 0.75) {
                  color = ChatFormatting.RED;
               } else if ((double)((float)filledBars / (float)totalBars) > 0.4) {
                  color = ChatFormatting.YELLOW;
               }

               tooltip.add(
                  var19.copy()
                     .append(
                        Component.translatable("sentry.tooltip.heat")
                           .withStyle(ChatFormatting.GOLD)
                           .append(Component.literal(barBuilder.toString()).withStyle(color))
                           .append(Component.literal(String.format(" %.0f/%.0f", currentHeat, maxHeat)).withStyle(ChatFormatting.GRAY))
                     )
               );
            }

            double range = this.getSentryRange();
            String rangeStr = String.format("%.1f", range);
            tooltip.add(
               var19.copy()
                  .append(
                     Component.translatable("sentry.tooltip.range")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(rangeStr).withStyle(ChatFormatting.GREEN))
                  )
            );
            MutableComponent statusComponent = Component.translatable("sentry.status.unknown");
            ChatFormatting statusColor = ChatFormatting.GRAY;
            switch (this.currentStatus) {
               case IDLE:
                  statusComponent = Component.translatable("sentry.status.idle");
                  statusColor = ChatFormatting.GREEN;
                  break;
               case SHOOTING:
                  statusComponent = Component.translatable("sentry.status.shooting");
                  statusColor = ChatFormatting.RED;
                  break;
               case CHARGING:
                  statusComponent = Component.translatable("sentry.status.charging");
                  statusColor = ChatFormatting.GOLD;
                  break;
               case BOLTING:
                  statusComponent = Component.translatable("sentry.status.bolting");
                  statusColor = ChatFormatting.YELLOW;
                  break;
               case RELOADING:
                  statusComponent = Component.translatable("sentry.status.reloading");
                  statusColor = ChatFormatting.YELLOW;
                  break;
               case COOLING:
                  statusComponent = Component.translatable("sentry.status.cooling");
                  statusColor = ChatFormatting.AQUA;
                  break;
               case NO_AMMO:
                  statusComponent = Component.translatable("sentry.status.no_ammo");
                  statusColor = ChatFormatting.DARK_RED;
            }

            tooltip.add(
               var19.copy()
                  .append(Component.translatable("sentry.tooltip.status").withStyle(ChatFormatting.GRAY).append(statusComponent.withStyle(statusColor)))
            );
         }
      }
   }

   public void triggerShootEffects() {
      this.lastShootTime = System.currentTimeMillis();
      this.shouldEjectShell = true;
      this.lowerArmRecoilOffset += 8.0F;
      if (this.lowerArmRecoilOffset > 18.0F) {
         this.lowerArmRecoilOffset = 18.0F;
      }

      float currentHead = this.headAngle.getValue();
      float targetHead = this.headAngle.getChaseTarget();
      if (currentHead > targetHead - 2.0F) {
         this.headAngle.setValue((double)(currentHead - 0.5F));
      }
   }

   public void updateAmmoFromPacket(int slotIndex, CompoundTag newTag) {
      if (slotIndex == -1) {
         if (!this.heldItem.isEmpty()) {
            ItemNBTHelper.setTag(this.heldItem, newTag);
         }
      } else if (slotIndex >= 0 && slotIndex < this.attachedAmmoBoxes.size()) {
         ItemStack box = (ItemStack)this.attachedAmmoBoxes.get(slotIndex);
         if (!box.isEmpty()) {
            ItemNBTHelper.setTag(box, newTag);
         }
      }
   }

   private boolean isBlockVisible(Vec3 start, BlockPos targetPos) {
      Vec3 end = Vec3.atCenterOf(targetPos);
      if (this.isInSableSubLevel()) {
         Vec3 subStart = this.worldToSubLevel(start);
         Vec3 subEnd = this.worldToSubLevel(end);
         if (subStart.distanceToSqr(subEnd) > 1000000.0) {
            return false;
         } else {
            BlockHitResult result = this.level.clip(new ClipContext(subStart, subEnd, Block.COLLIDER, Fluid.NONE, CollisionContext.empty()));
            return result.getType() == Type.MISS ? true : result.getType() == Type.BLOCK && result.getBlockPos().equals(targetPos);
         }
      } else {
         BlockHitResult result = this.level.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, SentryFakePlayer.get(this)));
         if (result.getType() == Type.MISS) {
            return true;
         } else {
            if (result.getType() == Type.BLOCK) {
               BlockPos hitPos = result.getBlockPos();
               if (hitPos.equals(targetPos)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private void updateClientTarget() {
      if (this.syncedTargetId == -1) {
         this.cachedTarget = null;
      } else if (this.cachedTarget != null && this.cachedTarget.getId() == this.syncedTargetId) {
         if (!this.cachedTarget.isAlive() || this.cachedTarget.isRemoved()) {
            this.cachedTarget = null;
         }
      } else {
         if (this.level.getEntity(this.syncedTargetId) instanceof LivingEntity living) {
            this.cachedTarget = living;
         } else {
            this.cachedTarget = null;
         }
      }
   }

   private void updateRangeScrollBounds() {
      if (this.rangeScroll != null) {
         if (!(this.level instanceof VirtualRenderWorld)) {
            ItemStack stack = this.getHeldItem();
            if (stack == null) {
               stack = ItemStack.EMPTY;
            }

            int min = 1;
            int max = 2;
            int smartDefault = 1;
            if (!stack.isEmpty() && stack.getItem() instanceof IGun iGun) {
               Optional<CommonGunIndex> indexOpt = TimelessAPI.getCommonGunIndex(iGun.getGunId(stack));
               if (indexOpt.isPresent()) {
                  float effRange = this.calculateEffectiveRange(indexOpt.get().getGunData());
                  int calculatedMax = Math.round(effRange * 2.0F);
                  max = Math.min(calculatedMax, 256);
                  if (max < 4) {
                     max = 4;
                  }

                  smartDefault = Math.min(Math.round(effRange * 1.5F), 256);
               }

               this.rangeScroll.between(min, max);
               int currentValue = this.rangeScroll.getValue();
               if (currentValue == 0) {
                  this.rangeScroll.setValue(smartDefault);
               } else if (currentValue < min) {
                  this.rangeScroll.setValue(min);
               } else if (currentValue > max) {
                  this.rangeScroll.setValue(max);
               }
            } else {
               this.rangeScroll.between(0, 0);
               this.rangeScroll.setValue(0);
            }
         }
      }
   }

   private void sendActionPacket(SentryShootPacket.ActionType type) {
      FakePlayer fakePlayer = SentryFakePlayer.get(this);
      if (fakePlayer != null) {
         Vec3 pos = fakePlayer.getEyePosition();
         NetworkHandler.sendToNearby(
            new SentryShootPacket(this.worldPosition, -1, ItemNBTHelper.getOrCreateTag(this.heldItem), pos, pos, type), this.level, this.worldPosition
         );
      }
   }

   private void sentryIdleScanning() {
      if (!this.level.isClientSide && this.idleScanTimer-- <= 0) {
         this.idleTargetYaw = this.level.random.nextFloat() * 1800.0F;
         this.idleTargetPitch = this.level.random.nextFloat() * 30.0F - 15.0F;
         this.idleScanTimer = 80 + this.level.random.nextInt(60);
         this.sendData();
      }

      float animSpeed = this.getAnimationSpeed(0.1F);
      float currentBase = this.baseAngle.getValue();
      float diffYaw = this.idleTargetYaw - currentBase;

      while (diffYaw < -180.0F) {
         diffYaw += 360.0F;
      }

      while (diffYaw > 180.0F) {
         diffYaw -= 360.0F;
      }

      this.baseAngle.chase((double)(currentBase + diffYaw), (double)animSpeed, Chaser.EXP);
      this.headAngle.chase((double)this.idleTargetPitch, (double)animSpeed, Chaser.EXP);
      this.lowerArmAngle.chase(135.0, (double)animSpeed, Chaser.EXP);
      this.upperArmAngle.chase(90.0, (double)animSpeed, Chaser.EXP);
   }

   private Vec2 calculateAngleBetween(Vec3 from, Vec3 to) {
      double diffX = to.x - from.x;
      double diffY = to.y - from.y;
      double diffZ = to.z - from.z;
      float yaw = (float)(Mth.atan2(diffZ, diffX) * (180.0 / Math.PI)) - 90.0F;
      double distHorizontal = Math.sqrt(diffX * diffX + diffZ * diffZ);
      float pitch = (float)(-(Mth.atan2(diffY, distHorizontal) * (180.0 / Math.PI)));
      return new Vec2(yaw, pitch);
   }

   private Vec3 getLocalMuzzlePos() {
      Vec3 pos = Vec3.atBottomCenterOf(this.worldPosition).add(0.0, 1.5, 0.0);
      if (this.isCeiling()) {
         pos = pos.add(0.0, -4.0, 0.0);
      }

      return pos;
   }

   private Vec2 calculateTruthAngle(Vec3 targetPos) {
      Vec3 muzzlePos = this.getActualMuzzlePos();
      return this.calculateAngleBetween(muzzlePos, targetPos);
   }

   private void spawnDebugLine(Vec3 start, Vec3 end, Vector3f color) {
      if (this.level instanceof ServerLevel serverLevel) {
         double distance = start.distanceTo(end);
         Vec3 direction = end.subtract(start).normalize();

         for (double d = 0.0; d < distance; d += 0.25) {
            Vec3 pos = start.add(direction.scale(d));
            serverLevel.sendParticles(new DustParticleOptions(color, 0.5F), pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
         }
      }
   }

   public boolean applyColor(DyeColor colorIn) {
      if (colorIn == null) {
         if (this.color.isEmpty()) {
            return false;
         }

         this.color = Optional.empty();
      } else {
         if (this.color.isPresent() && this.color.get() == colorIn) {
            return false;
         }

         this.color = Optional.of(colorIn);
      }

      this.setChanged();
      this.sendData();
      return true;
   }

   private void setStatus(SentryArmBlockEntity.SentryStatus newStatus) {
      if (this.currentStatus != newStatus) {
         this.currentStatus = newStatus;
         this.setChanged();
         if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
         }
      }
   }

   public CompoundTag getUpdateTag(Provider registries) {
      CompoundTag tag = new CompoundTag();
      this.saveAdditional(tag, registries);
      return tag;
   }

   private void syncTargetBlock() {
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   public void setConnectedFireControl(BlockPos pos) {
      this.connectedFireControlPos = pos;
      if (this.level != null && !this.level.isClientSide && AeronauticsHelper.isInSableSubLevel(this.level, pos)) {
         Vec3 projected = AeronauticsHelper.sableSubLevelToWorld(this.level, Vec3.atCenterOf(pos));
         this.projectedFireControlPos = BlockPos.containing(projected);
      } else {
         this.projectedFireControlPos = pos;
      }

      this.setChanged();
      this.syncTargetBlock();
      if (this.level != null && !this.level.isClientSide && this.level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity fc) {
         fc.notifyConnectedSentries(false);
      }
   }

   public BlockPos getConnectedFireControl() {
      return this.connectedFireControlPos;
   }

   public BlockPos getProjectedFireControlPos() {
      return this.projectedFireControlPos != null ? this.projectedFireControlPos : this.connectedFireControlPos;
   }

   public boolean isCeiling() {
      if (this.level == null) {
         return false;
      } else {
         BlockState state = this.getBlockState();
         return state.hasProperty(SentryArmBlock.CEILING) && (Boolean)state.getValue(SentryArmBlock.CEILING);
      }
   }

   private String getLuaDataSnapshot(LuaValue luaValue) {
      if (luaValue == null || luaValue.isnil()) {
         return "nil";
      } else if (!luaValue.istable()) {
         return luaValue.toString();
      } else {
         StringBuilder sb = new StringBuilder();
         sb.append("{");
         LuaValue k = LuaValue.NIL;

         while (true) {
            Varargs n = luaValue.next(k);
            if ((k = n.arg1()).isnil()) {
               sb.append("}");
               return sb.toString();
            }

            LuaValue v = n.arg(2);
            sb.append(k.toString()).append(":").append(v.toString()).append(",");
         }
      }
   }

   protected void read(CompoundTag compound, Provider registries, boolean clientPacket) {
      super.read(compound, registries, clientPacket);
      this.heldItem = ItemStack.EMPTY;
      if (registries != null) {
         if (compound.contains("SentryHeldItem")) {
            this.heldItem = ItemStack.parseOptional(registries, compound.getCompound("SentryHeldItem"));
         }

         for (int i = 0; i < this.attachedAmmoBoxes.size(); i++) {
            this.attachedAmmoBoxes.set(i, ItemStack.EMPTY);
         }

         if (compound.contains("SentryAmmoBoxes")) {
            ContainerHelper.loadAllItems(compound.getCompound("SentryAmmoBoxes"), this.attachedAmmoBoxes, registries);
         }
      }

      if (compound.contains("TargetId")) {
         this.syncedTargetId = compound.getInt("TargetId");
      }

      if (compound.contains("Angles")) {
         CompoundTag angles = compound.getCompound("Angles");
         if (!clientPacket) {
            this.baseAngle.setValue((double)angles.getFloat("Base"));
            this.lowerArmAngle.setValue((double)angles.getFloat("Lower"));
            this.upperArmAngle.setValue((double)angles.getFloat("Upper"));
            this.headAngle.setValue((double)angles.getFloat("Head"));
         } else {
            boolean isCombatMode = this.syncedTargetId != -1;
            if (isCombatMode) {
               float speed = 0.25F;
               this.baseAngle.chase((double)angles.getFloat("Base"), (double)speed, Chaser.EXP);
               this.lowerArmAngle.chase((double)angles.getFloat("Lower"), (double)speed, Chaser.EXP);
               this.upperArmAngle.chase((double)angles.getFloat("Upper"), (double)speed, Chaser.EXP);
               this.headAngle.chase((double)angles.getFloat("Head"), (double)speed, Chaser.EXP);
            } else {
               float serverBase = angles.getFloat("Base");
               if (Math.abs(this.baseAngle.getValue() - serverBase) > 10.0F) {
                  this.baseAngle.setValue((double)serverBase);
               }
            }
         }
      }

      if (compound.contains("TargetBlock")) {
         this.cachedTargetBlock = (BlockPos)NbtUtils.readBlockPos(compound, "TargetBlock").orElse(null);
      } else {
         this.cachedTargetBlock = null;
      }

      if (compound.contains("FireControlPos")) {
         this.connectedFireControlPos = (BlockPos)NbtUtils.readBlockPos(compound, "FireControlPos").orElse(null);
         if (this.connectedFireControlPos != null && compound.contains("ProjFCX")) {
            this.projectedFireControlPos = new BlockPos(compound.getInt("ProjFCX"), compound.getInt("ProjFCY"), compound.getInt("ProjFCZ"));
         } else {
            this.projectedFireControlPos = this.connectedFireControlPos;
         }
      } else {
         this.connectedFireControlPos = null;
      }

      this.idleTargetYaw = compound.getFloat("IdleTargetYaw");
      this.idleTargetPitch = compound.getFloat("IdleTargetPitch");
      this.idleScanTimer = compound.getInt("IdleScanTimer");
      if (compound.contains("Dye")) {
         this.color = Optional.of((DyeColor)NBTHelper.readEnum(compound, "Dye", DyeColor.class));
      } else {
         this.color = Optional.empty();
      }

      if (compound.contains("SentryStatus")) {
         int statusIdx = compound.getInt("SentryStatus");
         if (statusIdx >= 0 && statusIdx < SentryArmBlockEntity.SentryStatus.values().length) {
            this.currentStatus = SentryArmBlockEntity.SentryStatus.values()[statusIdx];
         }
      }

      this.updateRangeScrollBounds();
   }

   protected void write(CompoundTag compound, Provider registries, boolean clientPacket) {
      super.write(compound, registries, clientPacket);
      if (registries != null) {
         if (!this.heldItem.isEmpty()) {
            compound.put("SentryHeldItem", this.heldItem.save(registries, new CompoundTag()));
         }

         CompoundTag ammoTag = new CompoundTag();
         ContainerHelper.saveAllItems(ammoTag, this.attachedAmmoBoxes, registries);
         compound.put("SentryAmmoBoxes", ammoTag);
      }

      compound.putInt("TargetId", this.syncedTargetId);
      CompoundTag angles = new CompoundTag();
      angles.putFloat("Base", this.baseAngle.getValue());
      angles.putFloat("Lower", this.lowerArmAngle.getValue());
      angles.putFloat("Upper", this.upperArmAngle.getValue());
      angles.putFloat("Head", this.headAngle.getValue());
      compound.put("Angles", angles);
      compound.putFloat("IdleTargetYaw", this.idleTargetYaw);
      compound.putFloat("IdleTargetPitch", this.idleTargetPitch);
      compound.putInt("IdleScanTimer", this.idleScanTimer);
      if (this.connectedFireControlPos != null) {
         compound.put("FireControlPos", NbtUtils.writeBlockPos(this.connectedFireControlPos));
      }

      if (this.projectedFireControlPos != null) {
         compound.putInt("ProjFCX", this.projectedFireControlPos.getX());
         compound.putInt("ProjFCY", this.projectedFireControlPos.getY());
         compound.putInt("ProjFCZ", this.projectedFireControlPos.getZ());
      }

      if (this.cachedTargetBlock != null) {
         compound.put("TargetBlock", NbtUtils.writeBlockPos(this.cachedTargetBlock));
      }

      this.color.ifPresent(dyeColor -> NBTHelper.writeEnum(compound, "Dye", dyeColor));
      compound.putInt("SentryStatus", this.currentStatus.ordinal());
   }

   public IItemHandler getItemHandler() {
      return new IItemHandler() {
         public int getSlots() {
            return SentryArmBlockEntity.this.attachedAmmoBoxes.size();
         }

         public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < this.getSlots() ? (ItemStack)SentryArmBlockEntity.this.attachedAmmoBoxes.get(slot) : ItemStack.EMPTY;
         }

         public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.getItem() instanceof IAmmoBox iBox) {
               if (!((ItemStack)SentryArmBlockEntity.this.attachedAmmoBoxes.get(slot)).isEmpty()) {
                  return stack;
               } else {
                  ItemStack gun = SentryArmBlockEntity.this.getHeldItem();
                  if (gun.isEmpty() || !(gun.getItem() instanceof IGun)) {
                     return stack;
                  } else if (!iBox.isAmmoBoxOfGun(gun, stack)) {
                     return stack;
                  } else {
                     if (!simulate) {
                        ItemStack copy = stack.copy();
                        copy.setCount(1);
                        SentryArmBlockEntity.this.attachedAmmoBoxes.set(slot, copy);
                        SentryArmBlockEntity.this.setChanged();
                        SentryArmBlockEntity.this.sendData();
                     }

                     ItemStack remainder = stack.copy();
                     remainder.shrink(1);
                     return remainder;
                  }
               }
            } else {
               return stack;
            }
         }

         public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack inSlot = this.getStackInSlot(slot);
            if (inSlot.isEmpty()) {
               return ItemStack.EMPTY;
            } else {
               if (inSlot.getItem() instanceof IAmmoBox iBox && (iBox.isCreative(inSlot) || iBox.getAmmoCount(inSlot) > 0)) {
                  return ItemStack.EMPTY;
               }

               int extractCount = Math.min(inSlot.getCount(), amount);
               if (extractCount <= 0) {
                  return ItemStack.EMPTY;
               } else {
                  ItemStack extracted = inSlot.copy();
                  extracted.setCount(extractCount);
                  if (!simulate) {
                     inSlot.shrink(extractCount);
                     if (inSlot.isEmpty()) {
                        SentryArmBlockEntity.this.attachedAmmoBoxes.set(slot, ItemStack.EMPTY);
                     }

                     SentryArmBlockEntity.this.setChanged();
                     SentryArmBlockEntity.this.sendData();
                  }

                  return extracted;
               }
            }
         }

         public int getSlotLimit(int slot) {
            return 1;
         }

         public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof IAmmoBox;
         }

         public void setStackInSlot(int slot, ItemStack stack) {
            if (slot >= 0 && slot < SentryArmBlockEntity.this.attachedAmmoBoxes.size()) {
               SentryArmBlockEntity.this.attachedAmmoBoxes.set(slot, stack);
               SentryArmBlockEntity.this.setChanged();
               SentryArmBlockEntity.this.sendData();
            }
         }
      };
   }

   private static record FireContext(
      FakePlayer player,
      ItemStack heldItem,
      ItemStack fakeHeldItem,
      IGun iGunFake,
      IGunOperator operator,
      ShooterDataHolder dataHolder,
      Optional<CommonGunIndex> gunIndex,
      boolean actuallyFired,
      FireMode fireMode
   ) {
   }

   public static enum SentryStatus {
      IDLE,
      SHOOTING,
      CHARGING,
      BOLTING,
      RELOADING,
      COOLING,
      NO_AMMO,
      BROKEN;
   }

   private class SentryValueBoxTransform extends Sided {
      protected boolean isSideActive(BlockState state, Direction direction) {
         return !direction.getAxis().isVertical();
      }

      public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
         boolean isCeiling = (Boolean)state.getValue(SentryArmBlock.CEILING);
         int yPos = isCeiling ? 13 : 3;
         Vec3 location = VecHelper.voxelSpace(8.0, (double)yPos, 15.5);
         return VecHelper.rotateCentered(location, (double)AngleHelper.horizontalAngle(this.getSide()), Axis.Y);
      }

      protected Vec3 getSouthLocation() {
         return Vec3.ZERO;
      }

      public float getScale() {
         return 0.5F;
      }
   }
}

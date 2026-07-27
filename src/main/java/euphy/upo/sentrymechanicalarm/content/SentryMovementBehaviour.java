package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.network.SentryContraptionShootPacket;
import euphy.upo.sentrymechanicalarm.network.SentryContraptionTargetPacket;
import euphy.upo.sentrymechanicalarm.util.SentryAimUtil;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import euphy.upo.sentrymechanicalarm.util.TargetPool;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Vector3f;

public class SentryMovementBehaviour implements MovementBehaviour {
   private static final double AIM_TOLERANCE_DEGREES = 12.0;
   private static final int TARGET_RESCAN_INTERVAL_TICKS = 20;
   private static final int TARGET_SYNC_INTERVAL_TICKS = 3;

   public boolean isActive(MovementContext context) {
      return true;
   }

   public void startMoving(MovementContext context) {
      VirtualSentryArmBlockEntity virtualBE = new VirtualSentryArmBlockEntity(context.localPos, context.state);
      virtualBE.setVirtualLevel(context.world);
      if (context.blockEntityData != null) {
         virtualBE.read(context.blockEntityData, context.world != null ? context.world.registryAccess() : null, false);
      }

      context.temporaryData = virtualBE;
      float rpm = 16.0F;
      if (context.blockEntityData != null && context.blockEntityData.contains("Speed")) {
         rpm = Math.abs(context.blockEntityData.getFloat("Speed"));
      }

      if (rpm <= 0.0F && context.motion != null) {
         rpm = Math.max((float)(context.motion.length() * 512.0), 16.0F);
      }

      virtualBE.setContraptionSpeed(rpm);
      if (!context.world.isClientSide) {
         if (!context.data.contains("ShootDelay")) {
            context.data.putFloat("ShootDelay", 0.0F);
         }

         context.data.putInt("_AeroScanCD", 0);
         context.data.remove("_LastSyncedTargetId");
         context.data.putInt("_TargetSyncTicks", TARGET_SYNC_INTERVAL_TICKS);
      }

      context.data.putInt("_TargetId", -1);
   }

   public void tick(MovementContext context) {
      if (context.temporaryData == null && context.blockEntityData != null) {
         VirtualSentryArmBlockEntity virtualBE = new VirtualSentryArmBlockEntity(context.localPos, context.state);
         virtualBE.setVirtualLevel(context.world);
         if (context.blockEntityData != null) {
            virtualBE.read(context.blockEntityData, context.world != null ? context.world.registryAccess() : null, false);
         }

         context.temporaryData = virtualBE;
         float rpm = context.blockEntityData != null ? Math.abs(context.blockEntityData.getFloat("Speed")) : 0.0F;
         if (rpm <= 0.0F && context.motion != null) {
            rpm = Math.max((float)(context.motion.length() * 512.0), 16.0F);
         }

         virtualBE.setContraptionSpeed(rpm);
      }

      if (context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBEx) {
         int var15 = context.data.getInt("_AmmoSync");
         context.data.putInt("_AmmoSync", ++var15);
         if (var15 % 20 == 0 && context.blockEntityData != null && context.blockEntityData.contains("SentryAmmoBoxes")) {
            NonNullList<ItemStack> tempList = NonNullList.withSize(virtualBEx.attachedAmmoBoxes.size(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(
               context.blockEntityData.getCompound("SentryAmmoBoxes"), tempList, context.world != null ? context.world.registryAccess() : null
            );
            boolean hasAny = false;

            for (ItemStack s : tempList) {
               if (!s.isEmpty()) {
                  hasAny = true;
                  break;
               }
            }

            if (hasAny) {
               for (int i = 0; i < virtualBEx.attachedAmmoBoxes.size(); i++) {
                  virtualBEx.attachedAmmoBoxes.set(i, (ItemStack)tempList.get(i));
               }
            }
         }

         ItemStack heldItem = virtualBEx.getHeldItem();
         boolean hasGun = !heldItem.isEmpty() && heldItem.getItem() instanceof IGun;
         if (!hasGun) {
            virtualBEx.baseAngle.chase(0.0, 0.05F, Chaser.EXP);
            virtualBEx.headAngle.chase(0.0, 0.05F, Chaser.EXP);
            virtualBEx.lowerArmAngle.chase(135.0, 0.05F, Chaser.EXP);
            virtualBEx.upperArmAngle.chase(45.0, 0.05F, Chaser.EXP);
            virtualBEx.baseAngle.tickChaser();
            virtualBEx.headAngle.tickChaser();
            virtualBEx.lowerArmAngle.tickChaser();
            virtualBEx.upperArmAngle.tickChaser();
         } else {
            AbstractContraptionEntity contraptionEntity = context.contraption != null ? context.contraption.entity : null;
            if (contraptionEntity != null) {
               SentryMechanicalArm.LOGGER
                  .debug(
                     "[SentryTick] contraptionClass={} worldClass={} isClient={} hasGun={} pos=({},{},{})",
                     new Object[]{
                        contraptionEntity.getClass().getSimpleName(),
                        context.world != null ? context.world.getClass().getSimpleName() : "null",
                        context.world != null && context.world.isClientSide,
                        hasGun,
                        String.format("%.1f", contraptionEntity.position().x),
                        String.format("%.1f", contraptionEntity.position().y),
                        String.format("%.1f", contraptionEntity.position().z)
                     }
                  );
               Vec3 localPosCenter = VecHelper.getCenterOf(context.localPos);
               Vec3 turretGlobalPos = contraptionEntity.toGlobalVector(localPosCenter, 1.0F);
               virtualBEx.setVirtualPos(BlockPos.containing(turretGlobalPos));
               virtualBEx.setVirtualLevel(context.world);
               boolean useSpeed = context.blockEntityData != null
                  && context.blockEntityData.contains("Speed")
                  && Math.abs(context.blockEntityData.getFloat("Speed")) > 0.0F;
               if (useSpeed) {
                  float rpm = Math.abs(context.blockEntityData.getFloat("Speed"));
                  virtualBEx.setContraptionSpeed(rpm);
               } else {
                  double motionSpeed = context.motion.length();
                  float rpm = (float)(motionSpeed * 512.0);
                  virtualBEx.setContraptionSpeed(Math.max(rpm, 16.0F));
               }

               if (context.world != null && context.world.isClientSide) {
                  this.tickClientLogic(context, virtualBEx);
               } else if (context.world != null) {
                  this.tickServerLogic(context);
                  this.tickServerTargeting(context, virtualBEx, contraptionEntity);
               }
            } else if (context.world != null && context.world.isClientSide) {
               this.tickIdleScan(context, virtualBEx);
            }

            virtualBEx.baseAngle.tickChaser();
            virtualBEx.headAngle.tickChaser();
            virtualBEx.lowerArmAngle.tickChaser();
            virtualBEx.upperArmAngle.tickChaser();
         }
      }
   }

   private void tickClientLogic(MovementContext context, VirtualSentryArmBlockEntity virtualBE) {
      if (context.contraption == null || context.contraption.entity == null || context.world == null) {
         this.tickIdleScan(context, virtualBE);
         return;
      }

      int targetId = context.data.getInt("_TargetId");
      if (targetId == -1) {
         this.tickIdleScan(context, virtualBE);
      }
   }

   private void aimAtWorldPosition(
      MovementContext context,
      VirtualSentryArmBlockEntity virtualBE,
      AbstractContraptionEntity contraptionEntity,
      Vec3 accurateMuzzlePos,
      Vec3 aimPos
   ) {
      Vec3 worldAimVec = aimPos.subtract(accurateMuzzlePos);
      Vec3 targetVecLocal = contraptionEntity.reverseRotation(worldAimVec, 0.0F);
      double yawRad = Mth.atan2(targetVecLocal.x, targetVecLocal.z);
      float targetYaw = (float)Math.toDegrees(yawRad) + 180.0F;
      double horizontalDist = Math.sqrt(targetVecLocal.x * targetVecLocal.x + targetVecLocal.z * targetVecLocal.z);
      double pitchRad = Mth.atan2(targetVecLocal.y, horizontalDist);
      float targetPitch = (float)Math.toDegrees(pitchRad);
      this.aimAtAngle(context, virtualBE, targetYaw, targetPitch);
   }

   private void tickServerTargeting(MovementContext context, VirtualSentryArmBlockEntity virtualBE, AbstractContraptionEntity contraptionEntity) {
      Vec3 localPosCenter = VecHelper.getCenterOf(context.localPos);
      Vec3 globalPos = contraptionEntity.toGlobalVector(localPosCenter, 0.0F);
      ItemStack gunForMuzzle = virtualBE.getHeldItem();
      Vec3 localMuzzlePos = SentryFakePlayer.getContraptionLocalMuzzle(localPosCenter, virtualBE, gunForMuzzle);
      Vec3 accurateMuzzlePos = contraptionEntity.toGlobalVector(localMuzzlePos, 0.0F);
      if (AeronauticsHelper.isAeronauticsLoaded() && context.world != null) {
         Vec3 correctedGlobal = AeronauticsHelper.localToSimulatedWorld(context.world, localPosCenter, globalPos);
         Vec3 correctedMuzzle = AeronauticsHelper.localToSimulatedWorld(context.world, localMuzzlePos, accurateMuzzlePos);
         if (correctedGlobal.distanceToSqr(globalPos) > 0.01 || correctedMuzzle.distanceToSqr(accurateMuzzlePos) > 0.01) {
            SentryMechanicalArm.LOGGER
               .debug(
                  "[AeroScan] tickServerTargeting coords corrected: global=({},{},{})->({},{},{}) muzzle=({},{},{})->({},{},{})",
                  new Object[]{
                     String.format("%.1f", globalPos.x),
                     String.format("%.1f", globalPos.y),
                     String.format("%.1f", globalPos.z),
                     String.format("%.1f", correctedGlobal.x),
                     String.format("%.1f", correctedGlobal.y),
                     String.format("%.1f", correctedGlobal.z),
                     String.format("%.1f", accurateMuzzlePos.x),
                     String.format("%.1f", accurateMuzzlePos.y),
                     String.format("%.1f", accurateMuzzlePos.z),
                     String.format("%.1f", correctedMuzzle.x),
                     String.format("%.1f", correctedMuzzle.y),
                     String.format("%.1f", correctedMuzzle.z)
                  }
               );
         }

         globalPos = correctedGlobal;
         accurateMuzzlePos = correctedMuzzle;
      }

      int aeroCheckTick = context.data.getInt("_AeroCheckTick");
      context.data.putInt("_AeroCheckTick", ++aeroCheckTick);
      if (aeroCheckTick % 20 == 0 && AeronauticsHelper.isAeronauticsLoaded()) {
         AeronauticsHelper.isPositionOnShip(context.world, globalPos);
         AeronauticsHelper.isPositionOnShip(context.world, accurateMuzzlePos);
      }

      float serverDelay = context.data.getFloat("ShootDelay");
      int targetId = context.data.getInt("_TargetId");
      boolean hasValidTarget = false;
      if (targetId != -1) {
         Entity target = context.world.getEntity(targetId);
         SentryMechanicalArm.LOGGER.debug("[SentryTarget] serverTargeting targetId={} found={}", targetId, target != null);
         if (target instanceof LivingEntity living && living.isAlive()) {
            double range = this.calculateContraptionRange(context, virtualBE);
            double distSq = living.distanceToSqr(globalPos);
            boolean inRange = distSq <= range * range;
            SentryMechanicalArm.LOGGER
               .debug(
                  "[SentryTarget] server validation range={} distSq={} inRange={}",
                  new Object[]{String.format("%.1f", range), String.format("%.1f", distSq), inRange}
               );
            if (inRange) {
               Vec3 hitPos = this.getBestTargetPos(context.world, living, accurateMuzzlePos, contraptionEntity);
               SentryMechanicalArm.LOGGER.debug("[SentryTarget] server getBestTargetPos result={}", hitPos != null);
               if (hitPos != null) {
                   hasValidTarget = true;
                   this.aimAtWorldPosition(context, virtualBE, contraptionEntity, accurateMuzzlePos, hitPos);
                   Vec3 localForward = this.getCurrentLocalForward(context, virtualBE);
                   Vec3 localForwardPoint = localMuzzlePos.add(localForward);
                   Vec3 worldForwardPoint = contraptionEntity.toGlobalVector(localForwardPoint, 0.0F);
                   if (AeronauticsHelper.isAeronauticsLoaded() && context.world != null) {
                      worldForwardPoint = AeronauticsHelper.localToSimulatedWorld(context.world, localForwardPoint, worldForwardPoint);
                   }

                   SentryAimUtil.BarrelPose barrelPose = SentryAimUtil.barrelPose(
                      accurateMuzzlePos, worldForwardPoint.subtract(accurateMuzzlePos)
                   );
                   boolean isDeployed = virtualBE.upperArmAngle.getValue() > 80.0F;
                   boolean isAligned = isDeployed
                      && SentryAimUtil.isAligned(barrelPose, hitPos, AIM_TOLERANCE_DEGREES);
                   double dist = Math.sqrt(hitPos.distanceToSqr(accurateMuzzlePos));
                   if (isAligned && serverDelay <= 0.0F) {
                      ItemStack gunStack = virtualBE.getHeldItem();
                      if (!gunStack.isEmpty()) {
                         SentryAimUtil.BarrelPose shotPose = SentryAimUtil.barrelPose(
                            accurateMuzzlePos, hitPos.subtract(accurateMuzzlePos)
                         );
                         boolean fired = this.fireGunInContraption(
                            context, virtualBE, gunStack, accurateMuzzlePos, shotPose.yaw(), shotPose.pitch(), dist
                         );
                         if (fired) {
                           float rpm = 600.0F;
                           if (gunStack.getItem() instanceof IGun iGun) {
                              Optional<CommonGunIndex> idx = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack));
                              if (idx.isPresent()) {
                                 rpm = (float)idx.get().getGunData().getRoundsPerMinute(FireMode.AUTO);
                              }
                           }

                           if (rpm <= 0.0F) {
                              rpm = 1.0F;
                           }

                           context.data.putFloat("ShootDelay", 1200.0F / rpm);
                        }
                     }
                  }
               }
            }
         }
      }

      if (!hasValidTarget) {
         TargetPool.releaseByOwner(contraptionEntity.getUUID() + "|" + context.localPos);
         if (targetId != -1) {
            // A target that was valid on the previous pass just died, left
            // range, or lost line of sight. Do not inherit the idle rescan
            // delay; immediately look for a replacement.
            context.data.putInt("_TargetId", -1);
            context.data.putInt("_AeroScanCD", 0);
         }

         int scanCd = context.data.getInt("_AeroScanCD");
         if (scanCd <= 0) {
            context.data.putInt("_AeroScanCD", TARGET_RESCAN_INTERVAL_TICKS);
            SentryMovementBehaviour.TargetResult result = this.scanForTarget(
               context, virtualBE, contraptionEntity, globalPos, accurateMuzzlePos, contraptionEntity
            );
            if (result != null) {
               context.data.putInt("_TargetId", result.entity().getId());
               this.aimAtWorldPosition(context, virtualBE, contraptionEntity, accurateMuzzlePos, result.aimPos());
            } else {
               context.data.putInt("_TargetId", -1);
            }
         }
      }

      this.syncTargetToClients(context, contraptionEntity, virtualBE);
   }

   private void tickServerLogic(MovementContext context) {
      float delay = context.data.getFloat("ShootDelay");
      if (delay > 0.0F) {
         context.data.putFloat("ShootDelay", delay - 1.0F);
      }

      int scanCooldown = context.data.getInt("_AeroScanCD");
      if (scanCooldown > 0) {
         context.data.putInt("_AeroScanCD", scanCooldown - 1);
      }

      int refillTick = context.data.getInt("_RefillTick");
      context.data.putInt("_RefillTick", ++refillTick);
      if (refillTick % 20 == 0
         && context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE
         && !virtualBE.getHeldItem().isEmpty()
         && context.contraption != null
         && context.contraption.getStorage() != null) {
         ResourceLocation ammoId = null;
         if (virtualBE.getHeldItem().getItem() instanceof IGun iGun) {
            Optional<CommonGunIndex> idx = TimelessAPI.getCommonGunIndex(iGun.getGunId(virtualBE.getHeldItem()));
            if (idx.isPresent()) {
               ammoId = idx.get().getGunData().getAmmoId();
            }
         }

         if (ammoId != null) {
            this.refillAmmoBoxesFromContraption(context, virtualBE, ammoId);
         }
      }
   }

   private void syncTargetToClients(
      MovementContext context,
      AbstractContraptionEntity contraptionEntity,
      VirtualSentryArmBlockEntity virtualBE
   ) {
      int targetId = context.data.getInt("_TargetId");
      int syncTicks = context.data.getInt("_TargetSyncTicks") + 1;
      boolean targetChanged = !context.data.contains("_LastSyncedTargetId")
         || context.data.getInt("_LastSyncedTargetId") != targetId;
      if (!targetChanged && syncTicks < TARGET_SYNC_INTERVAL_TICKS) {
         context.data.putInt("_TargetSyncTicks", syncTicks);
         return;
      }

      context.data.putInt("_LastSyncedTargetId", targetId);
      context.data.putInt("_TargetSyncTicks", 0);
      NetworkHandler.sendToNearby(
         new SentryContraptionTargetPacket(
            contraptionEntity.getId(),
            context.localPos,
            targetId,
            virtualBE.baseAngle.getValue(),
            virtualBE.lowerArmAngle.getValue(),
            virtualBE.upperArmAngle.getValue(),
            virtualBE.headAngle.getValue()
         ),
         context.world,
         BlockPos.containing(contraptionEntity.position())
      );
   }

   private Vec3 getCurrentLocalForward(MovementContext context, VirtualSentryArmBlockEntity virtualBE) {
      float currentYaw = virtualBE.baseAngle.getValue();
      float currentPitch = virtualBE.headAngle.getValue();
      if (this.isCeiling(context)) {
         return Vec3.directionFromRotation(currentPitch, currentYaw);
      }

      return Vec3.directionFromRotation(-currentPitch, -currentYaw - 180.0F);
   }

   private boolean fireGunInContraption(
      MovementContext context,
      VirtualSentryArmBlockEntity virtualBE,
      ItemStack gunStack,
      Vec3 barrelGlobalPos,
      float globalYaw,
      float globalPitch,
      double distToTarget
   ) {
      if (context.world instanceof ServerLevel serverLevel) {
         IGun iGun = (IGun)gunStack.getItem();
         ResourceLocation gunId = iGun.getGunId(gunStack);
         int currentInternalAmmo = iGun.getCurrentAmmoCount(gunStack);
         boolean hasInternal = currentInternalAmmo > 0;
         AtomicReference<ResourceLocation> ammoIdRef = new AtomicReference<>(null);
         TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> ammoIdRef.set(index.getGunData().getAmmoId()));
         ResourceLocation requiredAmmoId = ammoIdRef.get();
         if (requiredAmmoId != null) {
            this.refillAmmoBoxesFromContraption(context, virtualBE, requiredAmmoId);
         }

         boolean hasExternal = false;
         if (requiredAmmoId != null) {
            hasExternal = this.consumeAmmoFromContraption(context.contraption, gunStack, true);
            if (!hasExternal) {
               for (ItemStack box : virtualBE.attachedAmmoBoxes) {
                  if (!box.isEmpty()
                     && box.getItem() instanceof IAmmoBox iBox
                     && (
                        iBox.isAllTypeCreative(box)
                           || Objects.equals(iBox.getAmmoId(box), requiredAmmoId) && (iBox.isCreative(box) || iBox.getAmmoCount(box) > 0)
                     )) {
                     hasExternal = true;
                     break;
                  }
               }
            }
         }

         if (!hasInternal && !hasExternal) {
            SentryMechanicalArm.LOGGER
               .info(
                  "[SentryDebug] FIRE_FAIL reason=noAmmo internal={} external={} dist={}",
                  new Object[]{hasInternal, hasExternal, String.format("%.2f", distToTarget)}
               );
            return false;
         } else {
            FakePlayer var40 = SentryFakePlayer.getForContraption(serverLevel, context.contraption.entity.getUUID(), context.localPos);
            SentryFakePlayer.setContraptionRoot(var40, context.contraption.entity);
            var40.setDeltaMovement(Vec3.ZERO);
            // TaCZ creates the real server-side bullet from the shooter's eye position.
            // Contraption sentries use a virtual block entity, so sync() deliberately does
            // not have enough world-space information to position the fake player. Always
            // place it here, regardless of the gun id or projectile type.
            double feetY = barrelGlobalPos.y - (double)var40.getEyeHeight();
            var40.setPos(barrelGlobalPos.x, feetY, barrelGlobalPos.z);
            var40.xo = barrelGlobalPos.x;
            var40.yo = feetY;
            var40.zo = barrelGlobalPos.z;
            var40.xOld = barrelGlobalPos.x;
            var40.yOld = feetY;
            var40.zOld = barrelGlobalPos.z;

            SentryFakePlayer.sync(var40, virtualBE, globalYaw, globalPitch, gunStack);
            IGunOperator operator = IGunOperator.fromLivingEntity(var40);
            operator.getDataHolder().isAiming = true;
            operator.getDataHolder().aimingProgress = 1.0F;
            TimelessAPI.getCommonGunIndex(gunId)
               .ifPresent(
                  index -> {
                     GunData gunData = index.getGunData();
                     float effectiveRange = this.calculateEffectiveRange(gunData);
                     float targetSpread = 0.0F;
                     if (distToTarget > (double)effectiveRange) {
                        double excessDistance = distToTarget - (double)effectiveRange;
                        targetSpread = (float)(excessDistance * 0.02);
                        targetSpread = Math.min(targetSpread, 5.0F);
                     }

                     AttachmentCacheProperty cache = operator.getCacheProperty();
                     if (cache != null) {
                        String inaccuracyId = GunProperties.INACCURACY.name();
                        Map<InaccuracyType, Float> cachedMap = (Map<InaccuracyType, Float>)cache.getCache(inaccuracyId);
                        Map<InaccuracyType, Float> mutableInaccuracyMap = (Map<InaccuracyType, Float>)(cachedMap != null
                           ? new HashMap<>(cachedMap)
                           : new EnumMap<>(InaccuracyType.class));
                        mutableInaccuracyMap.put(InaccuracyType.AIM, targetSpread);
                        mutableInaccuracyMap.put(InaccuracyType.STAND, targetSpread);
                        mutableInaccuracyMap.put(InaccuracyType.MOVE, targetSpread);
                        cache.setCache(GunProperties.INACCURACY, mutableInaccuracyMap);
                     }
                  }
               );
            ShootResult result = ShootResult.UNKNOWN_FAIL;
            boolean consumedInternal = false;
            boolean consumedExternal = false;
            if (hasInternal) {
               if (!iGun.hasBulletInBarrel(gunStack)) {
                  iGun.reduceCurrentAmmoCount(gunStack);
                  iGun.setBulletInBarrel(gunStack, true);
                  currentInternalAmmo--;
               }

               try {
                  var40.setGameMode(GameType.SURVIVAL);
                  result = operator.shoot(() -> globalPitch, () -> globalYaw);
               } catch (Exception var38) {
                  SentryMechanicalArm.LOGGER.warn("[SentryDebug] internalShoot failed", var38);
               }

               if (result == ShootResult.NEED_BOLT) {
                  try {
                     operator.bolt();
                     result = operator.shoot(() -> globalPitch, () -> globalYaw);
                  } catch (Exception var37) {
                     SentryMechanicalArm.LOGGER.warn("[SentryDebug] boltRetry failed", var37);
                  }
               }

               if (result == ShootResult.IS_BOLTING) {
                  try {
                     operator.bolt();
                     result = operator.shoot(() -> globalPitch, () -> globalYaw);
                  } catch (Exception var36) {
                     SentryMechanicalArm.LOGGER.warn("[SentryDebug] isBoltingRetry failed", var36);
                  }
               }

               if (result == ShootResult.SUCCESS) {
                  consumedInternal = true;
               } else if (result != ShootResult.NO_AMMO) {
                  SentryMechanicalArm.LOGGER.info("[SentryDebug] FIRE_FAIL reason=internalShootResult result={}", result);
                  return false;
               }
            }

            if (result != ShootResult.SUCCESS && hasExternal) {
               ItemStack fpGun = var40.getMainHandItem();
               IGun iGunFp = IGun.getIGunOrNull(fpGun);
               if (iGunFp != null) {
                  int fpAmmo = iGunFp.getCurrentAmmoCount(fpGun);
                  int maxAmmo = iGunFp.getMaxDummyAmmoAmount(fpGun);
                  if (maxAmmo <= 0) {
                     maxAmmo = 30;
                  }

                  int needed = maxAmmo - fpAmmo;
                  int totalFromBox = 0;
                  if (needed > 0) {
                     for (ItemStack boxx : virtualBE.attachedAmmoBoxes) {
                        if (needed <= 0) {
                           break;
                        }

                        if (!boxx.isEmpty() && boxx.getItem() instanceof IAmmoBox iBox) {
                           if (iBox.isCreative(boxx) || iBox.isAllTypeCreative(boxx)) {
                              fpAmmo = maxAmmo;
                              needed = 0;
                              break;
                           }

                           if (requiredAmmoId.equals(iBox.getAmmoId(boxx))) {
                              int boxCount = iBox.getAmmoCount(boxx);
                              int take = Math.min(needed, boxCount);
                              totalFromBox += take;
                              fpAmmo += take;
                              needed -= take;
                           }
                        }
                     }

                     if (needed > 0 && context.contraption != null && context.contraption.getStorage() != null) {
                        IItemHandler inv = context.contraption.getStorage().getAllItems();
                        if (inv != null) {
                           for (int i = 0; i < inv.getSlots() && needed > 0; i++) {
                              ItemStack slot = inv.getStackInSlot(i);
                              if (!slot.isEmpty()) {
                                 Item var66 = slot.getItem();
                                 if (var66 instanceof IAmmoBox) {
                                    IAmmoBox iBox = (IAmmoBox)var66;
                                    if (requiredAmmoId.equals(iBox.getAmmoId(slot))) {
                                       int boxCount = iBox.getAmmoCount(slot);
                                       int take = Math.min(needed, boxCount);
                                       totalFromBox += take;
                                       fpAmmo += take;
                                       needed -= take;
                                    }
                                 } else if (slot.getItem() instanceof IAmmo && requiredAmmoId.equals(((IAmmo)slot.getItem()).getAmmoId(slot))) {
                                    int taken = Math.min(needed, slot.getCount());
                                    ItemStack extractedAmmo = inv.extractItem(i, taken, false);
                                    if (!extractedAmmo.isEmpty()) {
                                       fpAmmo += extractedAmmo.getCount();
                                       needed -= extractedAmmo.getCount();
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }

                  if (fpAmmo > 0) {
                     iGunFp.setCurrentAmmoCount(fpGun, fpAmmo);
                     if (!iGunFp.hasBulletInBarrel(fpGun) && iGunFp.getCurrentAmmoCount(fpGun) > 0) {
                        iGunFp.reduceCurrentAmmoCount(fpGun);
                        iGunFp.setBulletInBarrel(fpGun, true);
                     }

                     try {
                        var40.setGameMode(GameType.SURVIVAL);
                        result = operator.shoot(() -> globalPitch, () -> globalYaw);
                     } catch (Exception var35) {
                        SentryMechanicalArm.LOGGER.warn("[SentryDebug] externalShoot failed", var35);
                     }

                     if (result == ShootResult.NEED_BOLT) {
                        operator.bolt();

                        try {
                           result = operator.shoot(() -> globalPitch, () -> globalYaw);
                        } catch (Exception var34) {
                           SentryMechanicalArm.LOGGER.warn("[SentryDebug] externalBoltRetry failed", var34);
                        }
                     }

                     if (result == ShootResult.SUCCESS) {
                        consumedExternal = true;
                        if (totalFromBox > 0) {
                           for (ItemStack boxx : virtualBE.attachedAmmoBoxes) {
                              if (totalFromBox <= 0) {
                                 break;
                              }

                              if (!boxx.isEmpty() && boxx.getItem() instanceof IAmmoBox iBox && requiredAmmoId.equals(iBox.getAmmoId(boxx))) {
                                 int boxCount = iBox.getAmmoCount(boxx);
                                 int consume = Math.min(totalFromBox, boxCount);
                                 iBox.setAmmoCount(boxx, boxCount - consume);
                                 totalFromBox -= consume;
                              }
                           }
                        }

                        int newAmmo = iGunFp.getCurrentAmmoCount(fpGun);
                        iGun.setCurrentAmmoCount(gunStack, newAmmo);
                     }
                  }
               }
            }

            if (result != ShootResult.SUCCESS) {
               SentryMechanicalArm.LOGGER
                  .info("[SentryDebug] FIRE_FAIL reason=fallThrough hasInternal={} hasExternal={} result={}", new Object[]{hasInternal, hasExternal, result});
               return false;
            } else {
               if (hasExternal && !consumedExternal) {
                  if (!this.consumeAmmoFromContraption(context.contraption, gunStack, false)) {
                     for (ItemStack boxx : virtualBE.attachedAmmoBoxes) {
                        if (!boxx.isEmpty() && boxx.getItem() instanceof IAmmoBox iBox && !iBox.isCreative(boxx) && iBox.getAmmoCount(boxx) > 0) {
                           iBox.setAmmoCount(boxx, iBox.getAmmoCount(boxx) - 1);
                           break;
                        }
                     }
                  }

                  iGun.setCurrentAmmoCount(gunStack, currentInternalAmmo);
               } else {
                  iGun.setCurrentAmmoCount(gunStack, currentInternalAmmo - 1);
               }

               this.refillAmmoBoxesFromContraption(context, virtualBE, requiredAmmoId);
               virtualBE.setLastShootTime(System.currentTimeMillis());
               Vec3 eyePos = var40.getEyePosition(1.0F);
               Vec3 lookVec = Vec3.directionFromRotation(globalPitch, globalYaw);
               Vec3 traceEnd = eyePos.add(lookVec.scale(100.0));
               BlockHitResult hitResult = serverLevel.clip(new ClipContext(eyePos, traceEnd, Block.COLLIDER, Fluid.NONE, var40));
               Vec3 visualStart = barrelGlobalPos.add(lookVec.scale(0.3));
               SentryMechanicalArm.LOGGER
                  .info(
                     "[ContraptionFireGun] sending packet contraptionId={} localPos={} start=({},{},{}) end=({},{},{})",
                     new Object[]{
                        context.contraption.entity.getId(),
                        context.localPos,
                        String.format("%.1f", visualStart.x),
                        String.format("%.1f", visualStart.y),
                        String.format("%.1f", visualStart.z),
                        String.format("%.1f", hitResult.getLocation().x),
                        String.format("%.1f", hitResult.getLocation().y),
                        String.format("%.1f", hitResult.getLocation().z)
                     }
                  );
               NetworkHandler.sendToNearby(
                  new SentryContraptionShootPacket(context.contraption.entity.getId(), context.localPos, visualStart, hitResult.getLocation(), gunStack),
                  serverLevel,
                  BlockPos.containing(barrelGlobalPos)
               );
               return true;
            }
         }
      } else {
         SentryMechanicalArm.LOGGER.info("[SentryDebug] FIRE_FAIL reason=notServerLevel");
         return false;
      }
   }

   private void refillAmmoBoxesFromContraption(MovementContext context, VirtualSentryArmBlockEntity virtualBE, ResourceLocation requiredAmmoId) {
      if (requiredAmmoId != null && context.contraption != null && context.contraption.getStorage() != null) {
         IItemHandler inventory = context.contraption.getStorage().getAllItems();
         if (inventory != null) {
            for (int slot = 0; slot < virtualBE.attachedAmmoBoxes.size(); slot++) {
               ItemStack box = (ItemStack)virtualBE.attachedAmmoBoxes.get(slot);
               if (!box.isEmpty()) {
                  Item boxId = box.getItem();
                  if (boxId instanceof IAmmoBox) {
                     IAmmoBox iBox = (IAmmoBox)boxId;
                     if (!iBox.isCreative(box) && !iBox.isAllTypeCreative(box)) {
                        ResourceLocation boxIdx = iBox.getAmmoId(box);
                        if (requiredAmmoId.equals(boxIdx)) {
                           int currentAmmo = iBox.getAmmoCount(box);
                           if (currentAmmo <= 256) {
                              int needed = 512 - currentAmmo;
                              if (needed > 0) {
                                 for (int i = 0; i < inventory.getSlots() && needed > 0; i++) {
                                    ItemStack slotStack = inventory.getStackInSlot(i);
                                    if (!slotStack.isEmpty()) {
                                       if (slotStack.getItem() instanceof IAmmoBox storageBox) {
                                          if (requiredAmmoId.equals(storageBox.getAmmoId(slotStack))) {
                                             if (storageBox.isCreative(slotStack) || storageBox.isAllTypeCreative(slotStack)) {
                                                iBox.setAmmoCount(box, 512);
                                                break;
                                             }

                                             int storageCount = storageBox.getAmmoCount(slotStack);
                                             int take = Math.min(needed, storageCount);
                                             if (take > 0) {
                                                storageBox.setAmmoCount(slotStack, storageCount - take);
                                                currentAmmo += take;
                                                needed -= take;
                                             }
                                          }
                                       } else {
                                          boolean ammoIdMatch = false;
                                          if (slotStack.getItem() instanceof IAmmo ammoItem) {
                                             if (requiredAmmoId.equals(ammoItem.getAmmoId(slotStack))) {
                                                ammoIdMatch = true;
                                             }
                                          } else {
                                             ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(slotStack.getItem());
                                             if (itemId.equals(requiredAmmoId)) {
                                                ammoIdMatch = true;
                                             }
                                          }

                                          if (ammoIdMatch) {
                                             int taken = Math.min(needed, slotStack.getCount());
                                             ItemStack extracted = inventory.extractItem(i, taken, false);
                                             if (!extracted.isEmpty()) {
                                                currentAmmo += extracted.getCount();
                                                needed -= extracted.getCount();
                                             }
                                          }
                                       }
                                    }
                                 }

                                 iBox.setAmmoCount(box, currentAmmo);
                                 virtualBE.attachedAmmoBoxes.set(slot, box);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void spawnDebugLine(Level level, Vec3 start, Vec3 end, Vector3f color) {
      double distance = start.distanceTo(end);
      Vec3 direction = end.subtract(start).normalize();

      for (double d = 0.0; d < distance; d += 0.5) {
         Vec3 pos = start.add(direction.scale(d));
         if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new DustParticleOptions(color, 0.5F), pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
         } else {
            level.addParticle(new DustParticleOptions(color, 0.5F), pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
         }
      }
   }

   private void spawnDebugAABB(Level level, AABB aabb, Vector3f color) {
      if (level instanceof ServerLevel serverLevel) {
         double step = 1.0;

         for (double x = aabb.minX; x <= aabb.maxX; x += step) {
            for (double y = aabb.minY; y <= aabb.maxY; y += step) {
               for (double z = aabb.minZ; z <= aabb.maxZ; z += step) {
                  boolean onEdge = x == aabb.minX
                     || x >= aabb.maxX - 0.01
                     || y == aabb.minY
                     || y >= aabb.maxY - 0.01
                     || z == aabb.minZ
                     || z >= aabb.maxZ - 0.01;
                  if (onEdge) {
                     serverLevel.sendParticles(new DustParticleOptions(color, 0.5F), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                  }
               }
            }
         }
      }
   }

   private SentryMovementBehaviour.TargetResult scanForTarget(
      MovementContext context,
      VirtualSentryArmBlockEntity virtualBE,
      AbstractContraptionEntity contraptionEntity,
      Vec3 globalPos,
      Vec3 muzzlePos,
      Entity shooter
   ) {
      if (AeronauticsHelper.isAeronauticsLoaded() && context.world != null) {
         Vec3 localPosCenter = VecHelper.getCenterOf(context.localPos);
         ItemStack gunForMuzzle = virtualBE.getHeldItem();
         Vec3 localMuzzlePos = SentryFakePlayer.getContraptionLocalMuzzle(localPosCenter, virtualBE, gunForMuzzle);
         Vec3 correctedGlobal = AeronauticsHelper.localToSimulatedWorld(context.world, localPosCenter, globalPos);
         Vec3 correctedMuzzle = AeronauticsHelper.localToSimulatedWorld(context.world, localMuzzlePos, muzzlePos);
         if (correctedGlobal.distanceToSqr(globalPos) > 0.01) {
            SentryMechanicalArm.LOGGER
               .info(
                  "[AeroScan] scanForTarget coords corrected: global ({},{},{})->({},{},{})",
                  new Object[]{
                     String.format("%.1f", globalPos.x),
                     String.format("%.1f", globalPos.y),
                     String.format("%.1f", globalPos.z),
                     String.format("%.1f", correctedGlobal.x),
                     String.format("%.1f", correctedGlobal.y),
                     String.format("%.1f", correctedGlobal.z)
                  }
               );
         }

         globalPos = correctedGlobal;
         muzzlePos = correctedMuzzle;
      }

      double range = this.calculateContraptionRange(context, virtualBE);
      FireControlMovementBehaviour.FireControlData fcData = FireControlMovementBehaviour.findFireControl(context.contraption);
      if (fcData != null && !fcData.displayItem.isEmpty()) {
         boolean var41 = true;
      } else {
         boolean var10000 = false;
      }

      Class<? extends LivingEntity> targetClass = LivingEntity.class;
      Level world = context.world;
      if (world == null && contraptionEntity != null) {
         world = contraptionEntity.level();
      }

      if (world == null) {
         SentryMechanicalArm.LOGGER.info("[SentryScan] world is null aborting");
         return null;
      } else {
         if (contraptionEntity != null) {
            world = contraptionEntity.level();
         }

         AABB searchBox = new AABB(globalPos, globalPos).inflate(range);
         if (AeronauticsHelper.isAeronauticsLoaded() && world instanceof ServerLevel serverLevel) {
            this.spawnDebugAABB(serverLevel, searchBox, new Vector3f(0.0F, 1.0F, 0.0F));
         }

         List<? extends LivingEntity> entities = world.getEntitiesOfClass(targetClass, searchBox);
         SentryMechanicalArm.LOGGER
            .info(
               "[SentryScan] worldClass={} entityCount={} globalPos=({},{},{}) range={}",
               new Object[]{
                  world.getClass().getSimpleName(),
                  entities.size(),
                  String.format("%.1f", globalPos.x),
                  String.format("%.1f", globalPos.y),
                  String.format("%.1f", globalPos.z),
                  range
               }
            );
         LivingEntity bestEntity = null;
         Vec3 bestPos = null;
         double minDstSq = range * range;
         String ownerKey = contraptionEntity.getUUID() + "|" + context.localPos;
         if (fcData != null
            && fcData.focusedEntityId != -1
            && world.getEntity(fcData.focusedEntityId) instanceof LivingEntity living
            && living.isAlive()
            && !living.isSpectator()
            && !living.is(contraptionEntity)
            && living != shooter
            && living.distanceToSqr(globalPos) <= range * range) {
            Vec3 hitPos = this.getBestTargetPos(context.world, living, muzzlePos, shooter);
            if (hitPos != null) {
               TargetPool.tryAcquire(living.getId(), ownerKey);
               return new SentryMovementBehaviour.TargetResult(living, hitPos);
            }
         }

         int debugFilterDead = 0;
         int debugFilterSpectator = 0;
         int debugFilterIsContraption = 0;
         int debugFilterShooter = 0;
         int debugFilterValidTarget = 0;
         int debugFilterClaimed = 0;
         int debugFilterRange = 0;
         int debugFilterLOS = 0;
         int debugTotalInRange = 0;

         for (LivingEntity enemy : entities) {
            if (!enemy.isAlive()) {
               debugFilterDead++;
            } else if (enemy.isSpectator()) {
               debugFilterSpectator++;
            } else if (enemy.is(contraptionEntity)) {
               debugFilterIsContraption++;
            } else if (enemy == shooter) {
               debugFilterShooter++;
            } else if (!this.isValidTarget(enemy, fcData)) {
               debugFilterValidTarget++;
               SentryMechanicalArm.LOGGER
                  .info(
                     "[SentryScan] FILTERED isValidTarget=false entity={} class={} id={} pos=({},{},{})",
                     new Object[]{
                        enemy.getName().getString(),
                        enemy.getClass().getSimpleName(),
                        enemy.getId(),
                        String.format("%.1f", enemy.position().x),
                        String.format("%.1f", enemy.position().y),
                        String.format("%.1f", enemy.position().z)
                     }
                  );
            } else if (TargetPool.isClaimedByOther(enemy.getId(), ownerKey)) {
               debugFilterClaimed++;
            } else {
               double distSq = enemy.distanceToSqr(globalPos);
               if (distSq > minDstSq) {
                  debugFilterRange++;
                  SentryMechanicalArm.LOGGER
                     .info(
                        "[SentryScan] FILTERED outOfRange entity={} distSq={} maxDistSq={} globalPos=({},{},{}) targetPos=({},{},{})",
                        new Object[]{
                           enemy.getName().getString(),
                           String.format("%.1f", distSq),
                           String.format("%.1f", minDstSq),
                           String.format("%.1f", globalPos.x),
                           String.format("%.1f", globalPos.y),
                           String.format("%.1f", globalPos.z),
                           String.format("%.1f", enemy.position().x),
                           String.format("%.1f", enemy.position().y),
                           String.format("%.1f", enemy.position().z)
                        }
                     );
               } else {
                  debugTotalInRange++;
                  Vec3 hitPos = this.getBestTargetPos(context.world, enemy, muzzlePos, shooter);
                  if (hitPos != null) {
                     minDstSq = distSq;
                     bestEntity = enemy;
                     bestPos = hitPos;
                  } else {
                     debugFilterLOS++;
                     SentryMechanicalArm.LOGGER
                        .info(
                           "[SentryScan] FILTERED noLOS entity={} muzzle=({},{},{}) target=({},{},{})",
                           new Object[]{
                              enemy.getName().getString(),
                              String.format("%.1f", muzzlePos.x),
                              String.format("%.1f", muzzlePos.y),
                              String.format("%.1f", muzzlePos.z),
                              String.format("%.1f", enemy.position().x),
                              String.format("%.1f", enemy.position().y),
                              String.format("%.1f", enemy.position().z)
                           }
                        );
                  }
               }
            }
         }

         if (entities.size() > 0) {
            SentryMechanicalArm.LOGGER
               .info(
                  "[SentryScan] FILTER_SUMMARY totalEntities={} dead={} spectator={} isContraption={} isShooter={} invalidTarget={} claimed={} outOfRange={} inRange={} noLOS={} bestResult={}",
                  new Object[]{
                     entities.size(),
                     debugFilterDead,
                     debugFilterSpectator,
                     debugFilterIsContraption,
                     debugFilterShooter,
                     debugFilterValidTarget,
                     debugFilterClaimed,
                     debugFilterRange,
                     debugTotalInRange,
                     debugFilterLOS,
                     bestEntity != null ? bestEntity.getName().getString() : "null"
                  }
               );
         }

         if (bestEntity != null && bestPos != null) {
            TargetPool.tryAcquire(bestEntity.getId(), ownerKey);
            return new SentryMovementBehaviour.TargetResult(bestEntity, bestPos);
         } else {
            return null;
         }
      }
   }

   private boolean isValidTarget(LivingEntity entity, FireControlMovementBehaviour.FireControlData fcData) {
      if (fcData == null) {
         return entity instanceof Enemy;
      } else if (fcData.displayItem.isEmpty()) {
         return entity instanceof Enemy;
      } else {
         String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
         String name = entity.getName().getString();
         boolean inList = false;
         if (fcData.targetList != null) {
            inList = fcData.targetList.contains(entityId) || fcData.targetList.contains(name);
         }

         return fcData.isWhitelist ? !inList : inList;
      }
   }

   private void tickIdleScan(MovementContext context, VirtualSentryArmBlockEntity virtualBE) {
      int timer = context.data.getInt("IdleScanTimer");
      if (timer <= 0) {
         float randomYaw = (context.world.random.nextFloat() - 0.5F) * 240.0F;
         float randomPitch = context.world.random.nextFloat() * 30.0F - 15.0F;
         context.data.putFloat("IdleTargetYaw", randomYaw);
         context.data.putFloat("IdleTargetPitch", randomPitch);
         context.data.putInt("IdleScanTimer", 80 + context.world.random.nextInt(60));
      } else {
         context.data.putInt("IdleScanTimer", timer - 1);
      }

      float targetYaw = context.data.getFloat("IdleTargetYaw") + 180.0F;
      float targetPitch = context.data.getFloat("IdleTargetPitch");
      this.aimAtAngle(context, virtualBE, targetYaw, targetPitch);
   }

   private void aimAtAngle(MovementContext context, VirtualSentryArmBlockEntity virtualBE, float targetYaw, float targetPitch) {
      if (this.isCeiling(context)) {
         targetYaw = -targetYaw + 180.0F;
         targetPitch = -targetPitch;
      }

      float currentYaw = virtualBE.baseAngle.getValue();
      float diffYaw = targetYaw - currentYaw;

      while (diffYaw < -180.0F) {
         diffYaw += 360.0F;
      }

      while (diffYaw > 180.0F) {
         diffYaw -= 360.0F;
      }

      float absYawDiff = Math.abs(diffYaw);
      if (absYawDiff < 3.5F) {
         virtualBE.baseAngle.chase((double)(currentYaw + diffYaw), 1.0, Chaser.EXP);
      } else {
         float yawSpeedBase;
         if (absYawDiff < 10.0F) {
            yawSpeedBase = 0.44F;
         } else if (absYawDiff < 45.0F) {
            yawSpeedBase = 0.22F;
         } else {
            yawSpeedBase = 0.19F;
         }

         virtualBE.baseAngle.chase((double)(currentYaw + diffYaw), (double)this.getAnimationSpeed(context, yawSpeedBase), Chaser.EXP);
      }

      float desiredPitch = Mth.clamp(targetPitch, -90.0F, 90.0F);
      float pitchDiff = desiredPitch - virtualBE.headAngle.getValue();
      float absPitchDiff = Math.abs(pitchDiff);
      if (absPitchDiff < 3.5F) {
         virtualBE.headAngle.chase((double)desiredPitch, 1.0, Chaser.EXP);
      } else {
         float pitchSpeedBase = absPitchDiff < 5.0F ? 0.44F : 0.19F;
         virtualBE.headAngle.chase((double)desiredPitch, (double)this.getAnimationSpeed(context, pitchSpeedBase), Chaser.EXP);
      }

      virtualBE.upperArmAngle.chase(90.0, (double)this.getAnimationSpeed(context, 0.4F), Chaser.EXP);
      virtualBE.lowerArmAngle.chase(135.0, 0.6F, Chaser.EXP);
   }

   private float getAnimationSpeed(MovementContext context, float baseChaserSpeed) {
      VirtualSentryArmBlockEntity virtualBE = context.temporaryData instanceof VirtualSentryArmBlockEntity v ? v : null;
      float rpm = virtualBE != null ? virtualBE.getContraptionSpeed() : 0.0F;
      if (rpm <= 0.0F) {
         double movementSpeed = context.motion.length();
         rpm = (float)(movementSpeed * 512.0);
      }

      rpm = Mth.clamp(rpm, 1.0F, 64.0F);
      float multiplier = Mth.map(rpm, 1.0F, 64.0F, 0.3F, 1.0F);
      return baseChaserSpeed * multiplier;
   }

   private Vec3 getBestTargetPos(Level level, LivingEntity target, Vec3 muzzlePos, Entity shooter) {
      Vec3 headPos = SentryAimUtil.headAimPoint(target);
      if (this.isPointVisible(level, muzzlePos, headPos, shooter)) {
         return headPos;
      }

      Vec3 centerPos = target.getBoundingBox().getCenter();
      return centerPos.distanceToSqr(headPos) > 1.0E-8
            && this.isPointVisible(level, muzzlePos, centerPos, shooter)
         ? centerPos
         : null;
   }

   private boolean isPointVisible(Level level, Vec3 start, Vec3 end, Entity shooter) {
      BlockHitResult result = level.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, shooter));
      if (result.getType() == Type.MISS) {
         return true;
      } else {
         if (shooter instanceof AbstractContraptionEntity contraption && contraption.getContraption() != null) {
            BlockPos hitPos = result.getBlockPos();
            if (contraption.getContraption().getBlocks().containsKey(hitPos)) {
               return true;
            }

            if (contraption.getBoundingBox().contains(Vec3.atCenterOf(hitPos))) {
               return true;
            }

            SentryMechanicalArm.LOGGER
               .info(
                  "[SentryLOS] BLOCKED by block={} at=({},{},{}) contraptionBB={} isInBB={} distFromStart={}",
                  new Object[]{
                     level.getBlockState(hitPos).getBlock().builtInRegistryHolder().key().location(),
                     hitPos.getX(),
                     hitPos.getY(),
                     hitPos.getZ(),
                     contraption.getBoundingBox(),
                     contraption.getBoundingBox().contains(Vec3.atCenterOf(hitPos)),
                     String.format("%.1f", start.distanceTo(result.getLocation()))
                  }
               );
            return false;
         }

         BlockPos hitPosx;
         boolean var10000;
         label36: {
            hitPosx = result.getBlockPos();
            if (shooter instanceof AbstractContraptionEntity ace && ace.getContraption() != null) {
               var10000 = true;
               break label36;
            }

            var10000 = false;
         }

         boolean isContraption = var10000;
         boolean inBB = isContraption && ((AbstractContraptionEntity)shooter).getBoundingBox().contains(Vec3.atCenterOf(hitPosx));
         SentryMechanicalArm.LOGGER
            .info(
               "[SentryLOS] BLOCKED by block={} at=({},{},{}) shooterType={} hasContraption={} isInBB={}",
               new Object[]{
                  level.getBlockState(hitPosx).getBlock().builtInRegistryHolder().key().location(),
                  hitPosx.getX(),
                  hitPosx.getY(),
                  hitPosx.getZ(),
                  shooter.getClass().getSimpleName(),
                  isContraption,
                  inBB
               }
            );
         return false;
      }
   }

   private double calculateContraptionRange(MovementContext context, VirtualSentryArmBlockEntity virtualBE) {
      double base = this.calculateMaxSentryRange(virtualBE.getHeldItem());
      if (context.blockEntityData != null && context.blockEntityData.contains("ScrollValue")) {
         CompoundTag scrollTag = context.blockEntityData.getCompound("ScrollValue");
         if (scrollTag.contains("Value")) {
            int savedRange = scrollTag.getInt("Value");
            if (savedRange > 1 && (double)savedRange > base) {
               base = (double)savedRange;
            }
         }
      }

      return base;
   }

   private double calculateMaxSentryRange(ItemStack gunStack) {
      if (!gunStack.isEmpty() && gunStack.getItem() instanceof IGun iGun) {
         ResourceLocation var5 = iGun.getGunId(gunStack);
         AtomicReference rangeRef = new AtomicReference(null);
         TimelessAPI.getCommonGunIndex(var5).ifPresent(index -> {
            float effectiveRange = this.calculateEffectiveRange(index.getGunData());
            rangeRef.set(Math.min((double)effectiveRange * 1.5, 128.0));
         });
         return rangeRef.get() != null ? (Double)rangeRef.get() : 16.0;
      } else {
         return 16.0;
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

         return effectiveRange;
      }
   }

   private boolean consumeAmmoFromContraption(Contraption contraption, ItemStack gunStack, boolean simulate) {
      if (contraption.getStorage() != null && contraption.getStorage().getAllItems() != null) {
         IItemHandler inventory = contraption.getStorage().getAllItems();
         IGun iGun = (IGun)gunStack.getItem();
         ResourceLocation gunId = iGun.getGunId(gunStack);
         AtomicReference<ResourceLocation> ammoIdRef = new AtomicReference<>(null);
         TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> ammoIdRef.set(index.getGunData().getAmmoId()));
         ResourceLocation requiredAmmoId = ammoIdRef.get();

         for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
               if (stack.getItem() instanceof IAmmoBox iAmmoBox
                  && iAmmoBox.isAmmoBoxOfGun(gunStack, stack)
                  && (iAmmoBox.isCreative(stack) || iAmmoBox.getAmmoCount(stack) > 0)) {
                  if (!simulate && !iAmmoBox.isCreative(stack)) {
                     iAmmoBox.setAmmoCount(stack, iAmmoBox.getAmmoCount(stack) - 1);
                  }

                  return true;
               }

               if (requiredAmmoId != null) {
                  boolean isLooseMatch = false;
                  if (stack.getItem() instanceof IAmmo iAmmoItem) {
                     if (iAmmoItem.getAmmoId(stack).equals(requiredAmmoId)) {
                        isLooseMatch = true;
                     }
                  } else {
                     ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                     if (itemId.equals(requiredAmmoId)) {
                        isLooseMatch = true;
                     }
                  }

                  if (isLooseMatch) {
                     if (simulate) {
                        if (!stack.isEmpty()) {
                           return true;
                        }
                     } else {
                        ItemStack extracted = inventory.extractItem(i, 1, false);
                        if (!extracted.isEmpty()) {
                           return true;
                        }
                     }
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isCeiling(MovementContext context) {
      return context.state == null ? false : context.state.hasProperty(SentryArmBlock.CEILING) && (Boolean)context.state.getValue(SentryArmBlock.CEILING);
   }

   @OnlyIn(Dist.CLIENT)
   public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices, MultiBufferSource buffer) {
      SentryArmRenderer.renderInContraption(context, renderWorld, matrices, buffer);
   }

   public boolean disableBlockEntityRendering() {
      return true;
   }

   public void stopMoving(MovementContext context) {
      if (context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
         virtualBE.write(context.blockEntityData, context.world != null ? context.world.registryAccess() : null, false);
      }

      if (context.contraption != null && context.contraption.entity != null) {
         TargetPool.releaseByOwner(context.contraption.entity.getUUID() + "|" + context.localPos);
      }
   }

   public void writeExtraData(MovementContext context) {
      if (context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
         virtualBE.write(context.blockEntityData, context.world != null ? context.world.registryAccess() : null, false);
      }
   }

   private static record TargetResult(LivingEntity entity, Vec3 aimPos) {
   }
}

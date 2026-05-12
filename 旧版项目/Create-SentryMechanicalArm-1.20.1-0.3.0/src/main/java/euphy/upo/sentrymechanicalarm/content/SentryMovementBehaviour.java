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
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.network.SentryClientShootPacket;
import euphy.upo.sentrymechanicalarm.network.SentryContraptionShootPacket;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.*;

public class SentryMovementBehaviour implements MovementBehaviour {

    private record TargetResult(LivingEntity entity, Vec3 aimPos) {}

    @Override
    public boolean isActive(MovementContext context) {
        return true;
    }

    @Override
    public void startMoving(MovementContext context) {
        VirtualSentryArmBlockEntity virtualBE = new VirtualSentryArmBlockEntity(
                null,
                context.localPos,
                context.state
        );

        virtualBE.setVirtualLevel(context.world);
        virtualBE.read(context.blockEntityData, false);
        context.temporaryData = virtualBE;

        if (!context.world.isClientSide) {
            if (!context.data.contains("ShootDelay")) context.data.putFloat("ShootDelay", 0f);
        }
    }

    @Override
    public void tick(MovementContext context) {

        if (context.temporaryData == null && context.blockEntityData != null) {
            VirtualSentryArmBlockEntity virtualBE = new VirtualSentryArmBlockEntity(
                    null,
                    context.localPos,
                    context.state
            );
            virtualBE.setVirtualLevel(context.world);
            virtualBE.read(context.blockEntityData, false);
            context.temporaryData = virtualBE;
        }

        if (!(context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE)) return;

        ItemStack heldItem = virtualBE.getHeldItem();
        boolean hasGun = !heldItem.isEmpty() && heldItem.getItem() instanceof IGun;
        double speed = context.motion.length();
        boolean isPowered = speed > 0.01;

        if (!hasGun || !isPowered) {
            virtualBE.baseAngle.chase(0, 0.05f, LerpedFloat.Chaser.EXP);
            virtualBE.headAngle.chase(0, 0.05f, LerpedFloat.Chaser.EXP);
            virtualBE.lowerArmAngle.chase(135f, 0.05f, LerpedFloat.Chaser.EXP);
            virtualBE.upperArmAngle.chase(45f, 0.05f, LerpedFloat.Chaser.EXP);
            virtualBE.baseAngle.tickChaser();
            virtualBE.headAngle.tickChaser();
            virtualBE.lowerArmAngle.tickChaser();
            virtualBE.upperArmAngle.tickChaser();
            return;
        }

        AbstractContraptionEntity contraptionEntity = context.contraption.entity;
        if (contraptionEntity == null) return;

        Vec3 localPosCenter = VecHelper.getCenterOf(context.localPos);
        Vec3 turretGlobalPos = contraptionEntity.toGlobalVector(localPosCenter, 1.0f);
        virtualBE.setVirtualPos(BlockPos.containing(turretGlobalPos));
        virtualBE.setVirtualLevel(context.world);

        if (context.world.isClientSide) {
            tickClientLogic(context, virtualBE);
        } else {
            tickServerLogic(context);
        }

        virtualBE.baseAngle.tickChaser();
        virtualBE.headAngle.tickChaser();
        virtualBE.lowerArmAngle.tickChaser();
        virtualBE.upperArmAngle.tickChaser();

    }

    private void tickClientLogic(MovementContext context, VirtualSentryArmBlockEntity virtualBE) {
        float clientDelay = context.data.contains("ClientShootDelay") ? context.data.getFloat("ClientShootDelay") : 0;
        if (clientDelay > 0) clientDelay--;
        context.data.putFloat("ClientShootDelay", clientDelay);

        AbstractContraptionEntity contraptionEntity = context.contraption.entity;
        Vec3 localPosCenter = VecHelper.getCenterOf(context.localPos);

        double yOffset = isCeiling(context) ? -2.0 : 2.0;
        Vec3 localMuzzlePos = localPosCenter.add(0, yOffset, 0);


        Vec3 accurateMuzzlePos = contraptionEntity.toGlobalVector(localMuzzlePos, 0.0F);
        Vec3 globalPos = contraptionEntity.toGlobalVector(localPosCenter, 0.0F);

        float currentLocalYaw = virtualBE.baseAngle.getValue();
        float currentLocalPitch = virtualBE.headAngle.getValue();

        Vec3 localViewVec;
        if (isCeiling(context)) {

            localViewVec = Vec3.directionFromRotation(currentLocalPitch, currentLocalYaw);
        } else {
            localViewVec = Vec3.directionFromRotation(-currentLocalPitch, -currentLocalYaw - 180);
        }

        TargetResult result = scanForTarget(context, virtualBE, contraptionEntity, globalPos, accurateMuzzlePos, null);

        if (result != null) {

            //Vec3 targetPos = result.aimPos();
            //Vec3 actualVec = contraptionEntity.toGlobalVector(localMuzzlePos.add(localViewVec), 0.0F).subtract(accurateMuzzlePos).normalize();
            //spawnDebugLine(context.world, accurateMuzzlePos, accurateMuzzlePos.add(actualVec.scale(10)), new org.joml.Vector3f(0f, 1f, 0f));
            //spawnDebugLine(context.world, accurateMuzzlePos, targetPos, new org.joml.Vector3f(0f, 0f, 1f));

            Vec3 targetGlobal = result.aimPos();
            Vec3 worldAimVec = targetGlobal.subtract(accurateMuzzlePos);

            Vec3 targetVecLocal = contraptionEntity.reverseRotation(worldAimVec, 0.0F);

            double yawRad = Mth.atan2(targetVecLocal.x, targetVecLocal.z);
            float targetYaw = (float) Math.toDegrees(yawRad) + 180;

            double horizontalDist = Math.sqrt(targetVecLocal.x * targetVecLocal.x + targetVecLocal.z * targetVecLocal.z);
            double pitchRad = Mth.atan2(targetVecLocal.y, horizontalDist);
            float targetPitch = (float) Math.toDegrees(pitchRad);

            aimAtAngle(context, virtualBE, targetYaw, targetPitch);

            Vec3 targetDir = targetVecLocal.normalize();

            double dot = localViewVec.dot(targetDir);
            dot = Mth.clamp(dot, -1.0, 1.0);

            double totalDeviation = Math.toDegrees(Math.acos(dot));

            boolean isDeployed = virtualBE.upperArmAngle.getValue() > 45f;


            if (totalDeviation < 6.0 && isDeployed && clientDelay <= 0) {
                double dist = Math.sqrt(targetGlobal.distanceToSqr(accurateMuzzlePos));

                double gYawRad = Mth.atan2(worldAimVec.z, worldAimVec.x) - (Math.PI / 2.0);
                float packetYaw = (float) Math.toDegrees(gYawRad);

                double gPitchRad = Mth.atan2(worldAimVec.y, horizontalDist);
                float packetPitch = (float) -Math.toDegrees(gPitchRad);

                NetworkHandler.CHANNEL.sendToServer(new SentryClientShootPacket(
                        contraptionEntity.getId(), context.localPos, packetYaw, packetPitch, dist
                ));

                context.data.putFloat("ClientShootDelay", 2.0f);
            }
        } else {
            tickIdleScan(context, virtualBE);
        }
    }

    private void tickServerLogic(MovementContext context) {
        float delay = context.data.getFloat("ShootDelay");
        if (delay > 0) {
            context.data.putFloat("ShootDelay", delay - 1);
        }
    }

    public static void handleClientShootPacket(MovementContext context, AbstractContraptionEntity ace, float yaw, float pitch, double distance) {
        if (context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
            float serverDelay = context.data.getFloat("ShootDelay");
            if (serverDelay > 0) return;

            Vec3 motionOffset = context.motion;
            Vec3 localPosCenter = VecHelper.getCenterOf(context.localPos);
            boolean isCeiling = context.state.hasProperty(SentryArmBlock.CEILING) && context.state.getValue(SentryArmBlock.CEILING);

            double yOffset = isCeiling ? -2.0 : 2.0;
            Vec3 localMuzzlePos = localPosCenter.add(0, yOffset, 0);

            Vec3 accurateMuzzlePos = ace.toGlobalVector(localMuzzlePos, 1.0f).subtract(motionOffset).subtract(motionOffset).subtract(motionOffset).subtract(motionOffset).subtract(motionOffset);

            ItemStack gunStack = virtualBE.getHeldItem();
            if (gunStack.isEmpty()) return;

            SentryMovementBehaviour behavior = new SentryMovementBehaviour();
            boolean fired = behavior.fireGunInContraption(context, virtualBE, gunStack, accurateMuzzlePos, yaw, pitch, distance);

            if (fired) {
                float rpm = 600f;
                if (gunStack.getItem() instanceof IGun iGun) {
                    var idx = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack));
                    if (idx.isPresent()) rpm = idx.get().getGunData().getRoundsPerMinute(FireMode.AUTO);
                }
                if (rpm <= 0) rpm = 1;
                float cooldown = 1200f / rpm;

                context.data.putFloat("ShootDelay", cooldown);
            }
        }
    }

    private boolean fireGunInContraption(MovementContext context, VirtualSentryArmBlockEntity virtualBE,
                                         ItemStack gunStack, Vec3 barrelGlobalPos, float globalYaw, float globalPitch, double distToTarget) {
        if (!(context.world instanceof ServerLevel serverLevel)) return false;

        IGun iGun = (IGun) gunStack.getItem();
        ResourceLocation gunId = iGun.getGunId(gunStack);

        int currentInternalAmmo = iGun.getCurrentAmmoCount(gunStack);
        boolean hasInternal = currentInternalAmmo > 0;

        java.util.concurrent.atomic.AtomicReference<ResourceLocation> ammoIdRef = new java.util.concurrent.atomic.AtomicReference<>(null);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> ammoIdRef.set(index.getGunData().getAmmoId()));
        ResourceLocation requiredAmmoId = ammoIdRef.get();

        boolean hasExternal = false;
        if (requiredAmmoId != null) {
            hasExternal = consumeAmmoFromContraption(context.contraption, gunStack, true);
        }

        if (!hasInternal && !hasExternal) {
            return false;
        }

        FakePlayer fp = SentryFakePlayer.getForContraption(serverLevel, context.contraption.entity.getUUID(), context.localPos);
        if (fp == null) return false;

        double feetY = barrelGlobalPos.y - 1.62;
        fp.setPos(barrelGlobalPos.x, feetY, barrelGlobalPos.z);
        fp.xo = barrelGlobalPos.x; fp.yo = feetY; fp.zo = barrelGlobalPos.z;
        fp.xOld = barrelGlobalPos.x; fp.yOld = feetY; fp.zOld = barrelGlobalPos.z;

        SentryFakePlayer.sync(fp, virtualBE, globalYaw, globalPitch, gunStack);

        IGunOperator operator = IGunOperator.fromLivingEntity(fp);
        operator.getDataHolder().isAiming = true;
        operator.getDataHolder().aimingProgress = 1.0f;

        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
            GunData gunData = index.getGunData();
            float effectiveRange = calculateEffectiveRange(gunData);
            float targetSpread = 0.0f;
            if (distToTarget > effectiveRange) {
                double excessDistance = distToTarget - effectiveRange;
                targetSpread = (float) (excessDistance * 0.02);
                targetSpread = Math.min(targetSpread, 5.0f);
            }
            AttachmentCacheProperty cache = operator.getCacheProperty();
            if (cache != null) {
                String inaccuracyId = GunProperties.INACCURACY.name();
                @SuppressWarnings("unchecked")
                Map<InaccuracyType, Float> cachedMap = (Map<InaccuracyType, Float>) cache.getCache(inaccuracyId);
                Map<InaccuracyType, Float> mutableInaccuracyMap = (cachedMap != null) ? new HashMap<>(cachedMap) : new EnumMap<>(InaccuracyType.class);
                mutableInaccuracyMap.put(InaccuracyType.AIM, targetSpread);
                mutableInaccuracyMap.put(InaccuracyType.STAND, targetSpread);
                mutableInaccuracyMap.put(InaccuracyType.MOVE, targetSpread);
                cache.setCache(GunProperties.INACCURACY, mutableInaccuracyMap);
            }
        });

        ShootResult result = ShootResult.UNKNOWN_FAIL;
        boolean consumedInternal = false;
        boolean consumedExternal = false;

        if (hasInternal) {
            try {
                fp.setGameMode(GameType.SURVIVAL);
                result = operator.shoot(() -> globalPitch, () -> globalYaw);
            } catch (Exception e) {}

            if (result == ShootResult.SUCCESS) {
                consumedInternal = true;
            } else if (result == ShootResult.NO_AMMO) {
            } else {
                return false;
            }
        }

        if (result != ShootResult.SUCCESS && hasExternal) {
            SentryFakePlayer.setTempCreative(fp, true);

            try {
                result = operator.shoot(() -> globalPitch, () -> globalYaw);
            } catch (Exception ignored) {
            } finally {
                SentryFakePlayer.setTempCreative(fp, false);
            }

            if (result == ShootResult.SUCCESS) {
                consumedExternal = true;
            }
        }

        if (result == ShootResult.SUCCESS) {

            if (hasExternal) {
                consumeAmmoFromContraption(context.contraption, gunStack, false);
                if (consumedInternal) {
                    iGun.setCurrentAmmoCount(gunStack, currentInternalAmmo);
                }
            } else {
                if (consumedInternal) {
                    iGun.setCurrentAmmoCount(gunStack, currentInternalAmmo - 1);
                }
            }

            virtualBE.setLastShootTime(System.currentTimeMillis());

            Vec3 eyePos = fp.getEyePosition(1.0f);
            Vec3 lookVec = fp.getViewVector(1.0F);
            Vec3 traceEnd = eyePos.add(lookVec.scale(100));
            net.minecraft.world.phys.BlockHitResult hitResult = serverLevel.clip(new net.minecraft.world.level.ClipContext(
                    eyePos, traceEnd,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    fp
            ));

            Vec3 visualStart = barrelGlobalPos.add(lookVec.scale(0.3));
            NetworkHandler.sendToNearby(
                    new SentryContraptionShootPacket(context.contraption.entity.getId(), context.localPos, visualStart, hitResult.getLocation(), gunStack),
                    serverLevel, BlockPos.containing(barrelGlobalPos)
            );

            return true;
        }

        return false;
    }

    private void spawnDebugLine(Level level, Vec3 start, Vec3 end, Vector3f color) {
        double distance = start.distanceTo(end);
        Vec3 direction = end.subtract(start).normalize();

        for (double d = 0; d < distance; d += 0.5) {
            Vec3 pos = start.add(direction.scale(d));
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(new DustParticleOptions(color, 0.5f),
                        pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            } else {
                level.addParticle(new DustParticleOptions(color, 0.5f),
                        pos.x, pos.y, pos.z, 0, 0, 0);
            }
        }
    }

    private TargetResult scanForTarget(MovementContext context, VirtualSentryArmBlockEntity virtualBE,
                                       AbstractContraptionEntity contraptionEntity,
                                       Vec3 globalPos, Vec3 muzzlePos, Entity shooter) {
        double range = calculateMaxSentryRange(virtualBE.getHeldItem());
        FireControlMovementBehaviour.FireControlData fcData = FireControlMovementBehaviour.findFireControl(context.contraption);

        boolean fireControlActive = fcData != null && !fcData.displayItem.isEmpty();

        Class<? extends LivingEntity> targetClass = fireControlActive ? LivingEntity.class : Monster.class;

        AABB searchBox = new AABB(globalPos, globalPos).inflate(range);
        List<? extends LivingEntity> entities = context.world.getEntitiesOfClass(targetClass, searchBox);

        LivingEntity bestEntity = null;
        Vec3 bestPos = null;
        double minDstSq = range * range;

        for (LivingEntity enemy : entities) {
            if (!enemy.isAlive()) continue;
            if (enemy.isSpectator()) continue;
            if (enemy.is(contraptionEntity)) continue;
            if (enemy == shooter) continue;
            //if (enemy instanceof Player p && p.isCreative()) continue;

            if (!isValidTarget(enemy, fcData)) continue;

            double distSq = enemy.distanceToSqr(globalPos);
            if (distSq > minDstSq) continue;

            Vec3 hitPos = getBestTargetPos(context.world, enemy, muzzlePos, shooter);
            if (hitPos != null) {
                minDstSq = distSq;
                bestEntity = enemy;
                bestPos = hitPos;
            }
        }

        if (bestEntity != null && bestPos != null) {
            return new TargetResult(bestEntity, bestPos);
        }
        return null;
    }

    private boolean isValidTarget(LivingEntity entity, FireControlMovementBehaviour.FireControlData fcData) {
        if (fcData == null) return entity instanceof Monster;

        if (fcData.displayItem.isEmpty()) return entity instanceof Monster;

        String entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
        String name = entity.getName().getString();

        boolean inList = false;
        if (fcData.targetList != null) {
            inList = fcData.targetList.contains(entityId) || fcData.targetList.contains(name);
        }

        if (fcData.isWhitelist) {
            return !inList;
        } else {

            return inList;
        }
    }

    private void tickIdleScan(MovementContext context, VirtualSentryArmBlockEntity virtualBE) {
        int timer = context.data.getInt("IdleScanTimer");
        if (timer <= 0) {
            float randomYaw = (context.world.random.nextFloat() - 0.5f) * 240f;
            float randomPitch = (context.world.random.nextFloat() * 30f) - 15f;
            context.data.putFloat("IdleTargetYaw", randomYaw);
            context.data.putFloat("IdleTargetPitch", randomPitch);
            context.data.putInt("IdleScanTimer", 80 + context.world.random.nextInt(60));
        } else {
            context.data.putInt("IdleScanTimer", timer - 1);
        }
        float targetYaw = context.data.getFloat("IdleTargetYaw") + 180;
        float targetPitch = context.data.getFloat("IdleTargetPitch");
        aimAtAngle(context, virtualBE, targetYaw, targetPitch);
    }

    private void aimAtAngle(MovementContext context, VirtualSentryArmBlockEntity virtualBE, float targetYaw, float targetPitch) {
        if (isCeiling(context)) {
            targetYaw = -targetYaw + 180;
            targetPitch = -targetPitch;
        }

        float currentYaw = virtualBE.baseAngle.getValue();
        float diffYaw = targetYaw - currentYaw;
        while (diffYaw < -180) diffYaw += 360;
        while (diffYaw > 180) diffYaw -= 360;

        float absYawDiff = Math.abs(diffYaw);
        float yawSpeedBase;
        if (absYawDiff < 3.5f) {
            virtualBE.baseAngle.chase(currentYaw + diffYaw, 1.0f, LerpedFloat.Chaser.EXP);
        } else {
            if (absYawDiff < 10.0f) yawSpeedBase = 0.8f;
            else if (absYawDiff < 45.0f) yawSpeedBase = 0.4f;
            else yawSpeedBase = 0.35f;
            virtualBE.baseAngle.chase(currentYaw + diffYaw, getAnimationSpeed(context, yawSpeedBase), LerpedFloat.Chaser.EXP);
        }

        float desiredPitch = Mth.clamp(targetPitch, -90, 90);
        float pitchDiff = desiredPitch - virtualBE.headAngle.getValue();
        float absPitchDiff = Math.abs(pitchDiff);

        if (absPitchDiff < 3.5f) {
            virtualBE.headAngle.chase(desiredPitch, 1.0f, LerpedFloat.Chaser.EXP);
        } else {
            float pitchSpeedBase = (absPitchDiff < 5.0f) ? 0.8f : 0.35f;
            virtualBE.headAngle.chase(desiredPitch, getAnimationSpeed(context, pitchSpeedBase), LerpedFloat.Chaser.EXP);
        }

        virtualBE.upperArmAngle.chase(90f, getAnimationSpeed(context, 0.4f), LerpedFloat.Chaser.EXP);
        virtualBE.lowerArmAngle.chase(135f, 0.6f, LerpedFloat.Chaser.EXP);
    }

    private float getAnimationSpeed(MovementContext context, float baseChaserSpeed) {
        double movementSpeed = context.motion.length();
        float simulatedRpm = (float) (movementSpeed * 512.0);
        simulatedRpm = Mth.clamp(simulatedRpm, 0f, 256f);
        float multiplier = Mth.map(simulatedRpm, 1.0f, 256.0f, 0.01f, 1.0f);
        return baseChaserSpeed * multiplier;
    }

    private Vec3 getBestTargetPos(Level level, LivingEntity target, Vec3 muzzlePos, Entity shooter) {
        float height = target.getBbHeight();
        Vec3 basePos = target.position();
        Vec3 headPos = basePos.add(0, height * 0.90, 0);
        if (isPointVisible(level, muzzlePos, headPos, shooter)) return headPos;
        Vec3 centerPos = basePos.add(0, height * 0.6, 0);
        if (isPointVisible(level, muzzlePos, centerPos, shooter)) return centerPos;
        Vec3 legPos = basePos.add(0, height * 0.25, 0);
        if (isPointVisible(level, muzzlePos, legPos, shooter)) return legPos;
        Vec3 feetPos = basePos.add(0, height * 0.1, 0);
        if (isPointVisible(level, muzzlePos, feetPos, shooter)) return feetPos;
        return null;
    }

    private boolean isPointVisible(Level level, Vec3 start, Vec3 end, Entity shooter) {
        net.minecraft.world.phys.BlockHitResult result = level.clip(new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                shooter
        ));
        return result.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private double calculateMaxSentryRange(ItemStack gunStack) {
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun iGun)) return 16.0;
        ResourceLocation gunId = iGun.getGunId(gunStack);
        java.util.concurrent.atomic.AtomicReference<Double> rangeRef = new java.util.concurrent.atomic.AtomicReference<>(null);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
            float effectiveRange = calculateEffectiveRange(index.getGunData());
            rangeRef.set(Math.min(effectiveRange * 1.5, 128.0));
        });
        return rangeRef.get() != null ? rangeRef.get() : 16.0;
    }

    private float calculateEffectiveRange(GunData gunData) {
        BulletData bulletData = gunData.getBulletData();
        if (bulletData == null) return 32.0f;
        float effectiveRange = -1.0f;
        ExtraDamage extraDamage = bulletData.getExtraDamage();
        if (extraDamage != null) {
            LinkedList<ExtraDamage.DistanceDamagePair> damageAdjust = extraDamage.getDamageAdjust();
            if (damageAdjust != null && !damageAdjust.isEmpty()) {
                effectiveRange = damageAdjust.get(0).getDistance();
            }
        }
        if (effectiveRange <= 0) {
            float speed = bulletData.getSpeed();
            effectiveRange = (speed > 0 ? speed : 10.0f) * 12.0f;
        }
        return effectiveRange;
    }

    private boolean consumeAmmoFromContraption(Contraption contraption, ItemStack gunStack, boolean simulate) {
        if (contraption.getStorage() == null || contraption.getStorage().getAllItems() == null) return false;
        net.minecraftforge.items.IItemHandler inventory = contraption.getStorage().getAllItems();
        IGun iGun = (IGun) gunStack.getItem();
        ResourceLocation gunId = iGun.getGunId(gunStack);
        java.util.concurrent.atomic.AtomicReference<ResourceLocation> ammoIdRef = new java.util.concurrent.atomic.AtomicReference<>(null);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> ammoIdRef.set(index.getGunData().getAmmoId()));
        ResourceLocation requiredAmmoId = ammoIdRef.get();

        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof com.tacz.guns.api.item.IAmmoBox iAmmoBox) {
                if (iAmmoBox.isAmmoBoxOfGun(gunStack, stack)) {
                    if (iAmmoBox.isCreative(stack) || iAmmoBox.getAmmoCount(stack) > 0) {
                        if (!simulate && !iAmmoBox.isCreative(stack)) {
                            iAmmoBox.setAmmoCount(stack, iAmmoBox.getAmmoCount(stack) - 1);
                        }
                        return true;
                    }
                }
            }
            if (requiredAmmoId != null) {
                boolean isLooseMatch = false;
                if (stack.getItem() instanceof com.tacz.guns.api.item.IAmmo iAmmoItem) {
                    if (iAmmoItem.getAmmoId(stack).equals(requiredAmmoId)) isLooseMatch = true;
                } else {
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (itemId != null && itemId.equals(requiredAmmoId)) isLooseMatch = true;
                }
                if (isLooseMatch) {
                    ItemStack extracted = inventory.extractItem(i, 1, simulate);
                    if (!extracted.isEmpty()) return true;
                }
            }
        }
        return false;
    }

    private boolean isCeiling(MovementContext context) {
        if (context.state == null) return false;
        return context.state.hasProperty(SentryArmBlock.CEILING) && context.state.getValue(SentryArmBlock.CEILING);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                    ContraptionMatrices matrices, MultiBufferSource buffer) {
        SentryArmRenderer.renderInContraption(context, renderWorld, matrices, buffer);
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
            virtualBE.write(context.blockEntityData, false);
        }
    }

    @Override
    public void writeExtraData(MovementContext context) {
        if (context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
            virtualBE.write(context.blockEntityData, false);
        }
    }
}
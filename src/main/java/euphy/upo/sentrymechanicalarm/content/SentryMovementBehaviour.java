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
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.network.SentryClientShootPacket;
import euphy.upo.sentrymechanicalarm.network.SentryContraptionShootPacket;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.registries.BuiltInRegistries;
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
                context.localPos,
                context.state
        );

        virtualBE.setVirtualLevel(context.world);
        if (context.blockEntityData != null) {
            virtualBE.read(context.blockEntityData, context.world != null ? context.world.registryAccess() : null, false);
        }
        context.temporaryData = virtualBE;

        float rpm = 16f;
        if (context.blockEntityData != null && context.blockEntityData.contains("Speed")) {
            rpm = Math.abs(context.blockEntityData.getFloat("Speed"));
        }
        virtualBE.setContraptionSpeed(rpm);

        if (!context.world.isClientSide) {
            if (!context.data.contains("ShootDelay")) context.data.putFloat("ShootDelay", 0f);
        }
    }

    @Override
    public void tick(MovementContext context) {

        if (context.temporaryData == null && context.blockEntityData != null) {
            VirtualSentryArmBlockEntity virtualBE = new VirtualSentryArmBlockEntity(
                    context.localPos,
                    context.state
            );
            virtualBE.setVirtualLevel(context.world);
            if (context.blockEntityData != null) {
                virtualBE.read(context.blockEntityData, context.world != null ? context.world.registryAccess() : null, false);
            }
            context.temporaryData = virtualBE;
            float rpm = context.blockEntityData != null ? context.blockEntityData.getFloat("Speed") : 0f;
            virtualBE.setContraptionSpeed(Math.abs(rpm));
        }

        if (!(context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE)) {
            SentryMechanicalArm.LOGGER.debug("[SMB] tick: temporaryData is not VirtualSentryArmBlockEntity");
            return;
        }

        ItemStack heldItem = virtualBE.getHeldItem();
        boolean hasGun = !heldItem.isEmpty() && heldItem.getItem() instanceof IGun;

        if (!hasGun) {
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

        AbstractContraptionEntity contraptionEntity = context.contraption != null ? context.contraption.entity : null;

        if (contraptionEntity != null) {
            Vec3 localPosCenter = VecHelper.getCenterOf(context.localPos);
            Vec3 turretGlobalPos = contraptionEntity.toGlobalVector(localPosCenter, 1.0f);
            virtualBE.setVirtualPos(BlockPos.containing(turretGlobalPos));
            virtualBE.setVirtualLevel(context.world);

            int tick = context.data.getInt("_DebugTick");
            tick++;
            context.data.putInt("_DebugTick", tick);
            if (tick % 20 == 0) {
            SentryMechanicalArm.LOGGER.info("[SMB] tick: localPos={}, globalPos={}, entity={}, world={}",
                context.localPos, turretGlobalPos,
                contraptionEntity.getClass().getSimpleName(),
                context.world != null ? context.world.isClientSide : "null");
            }

            if (context.blockEntityData != null && context.blockEntityData.contains("Speed")) {
                float rpm = context.blockEntityData.getFloat("Speed");
                virtualBE.setContraptionSpeed(Math.abs(rpm));
            } else {
                double motionSpeed = context.motion.length();
                float rpm = (float) (motionSpeed * 512.0);
                virtualBE.setContraptionSpeed(Math.max(rpm, 16f));
            }

            if (context.world != null && context.world.isClientSide) {
                tickClientLogic(context, virtualBE);
            } else if (context.world != null) {
                tickServerLogic(context);
            }
        } else {
            if (context.world != null && context.world.isClientSide) {
                tickIdleScan(context, virtualBE);
            }
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

        AbstractContraptionEntity contraptionEntity = context.contraption != null ? context.contraption.entity : null;
        if (contraptionEntity == null) { tickIdleScan(context, virtualBE); return; }

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

        TargetResult result = scanForTarget(context, virtualBE, contraptionEntity, globalPos, accurateMuzzlePos, contraptionEntity);

        int tick = context.data.getInt("_DebugTick");
        if (tick % 40 == 0) {
            SentryMechanicalArm.LOGGER.info("[SMB] scan: muzzle={}, globalPos={}, result={}",
                accurateMuzzlePos, globalPos, result != null ? result.entity().getType().getDescriptionId() : "null");
        }

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

            if (tick % 10 == 0) {
                double armUpper = virtualBE.upperArmAngle.getValue();
                SentryMechanicalArm.LOGGER.info("[SMB] aim: tYaw={}, tPitch={}, cYaw={}, cPitch={}, dev={}, deployed={}, upperArm={}",
                    Math.round(targetYaw), Math.round(targetPitch),
                    Math.round(currentLocalYaw), Math.round(currentLocalPitch),
                    Math.round(totalDeviation * 100.0) / 100.0, isDeployed,
                    Math.round(armUpper * 10.0) / 10.0);
            }

            boolean readyToShoot = totalDeviation < 6.0 && isDeployed && clientDelay <= 0;

            if (tick % 5 == 0 && result != null) {
                SentryMechanicalArm.LOGGER.info("[SMB] shootCheck: dev<6={}, deployed={}, delay<={}={}, ready={}",
                    totalDeviation < 6.0, isDeployed, clientDelay, clientDelay <= 0, readyToShoot);
            }

            if (readyToShoot) {
                double dist = Math.sqrt(targetGlobal.distanceToSqr(accurateMuzzlePos));

                double gYawRad = Mth.atan2(worldAimVec.z, worldAimVec.x) - (Math.PI / 2.0);
                float packetYaw = (float) Math.toDegrees(gYawRad);

                double gPitchRad = Mth.atan2(worldAimVec.y, horizontalDist);
                float packetPitch = (float) -Math.toDegrees(gPitchRad);

                PacketDistributor.sendToServer(new SentryClientShootPacket(
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
        int refillTick = context.data.getInt("_RefillTick");
        refillTick++;
        context.data.putInt("_RefillTick", refillTick);
        if (refillTick % 20 == 0 && context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
            if (!virtualBE.getHeldItem().isEmpty() && context.contraption != null && context.contraption.getStorage() != null) {
                ResourceLocation ammoId = null;
                if (virtualBE.getHeldItem().getItem() instanceof IGun iGun) {
                    var idx = TimelessAPI.getCommonGunIndex(iGun.getGunId(virtualBE.getHeldItem()));
                    if (idx.isPresent()) ammoId = idx.get().getGunData().getAmmoId();
                }
                if (ammoId != null) refillAmmoBoxesFromContraption(context, virtualBE, ammoId);
            }
        }
    }

    public static void handleClientShootPacket(MovementContext context, AbstractContraptionEntity ace, float yaw, float pitch, double distance) {
        if (context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
            float serverDelay = context.data.getFloat("ShootDelay");
            SentryMechanicalArm.LOGGER.info("[SMB] handleShoot: delay={}, hasGun={}, localPos={}, yaw={}, pitch={}",
                serverDelay, !virtualBE.getHeldItem().isEmpty(), context.localPos, yaw, pitch);
            if (serverDelay > 0) return;

            Vec3 localPosCenter = VecHelper.getCenterOf(context.localPos);
            boolean isCeiling = context.state.hasProperty(SentryArmBlock.CEILING) && context.state.getValue(SentryArmBlock.CEILING);

            double yOffset = isCeiling ? -2.0 : 2.0;
            Vec3 localMuzzlePos = localPosCenter.add(0, yOffset, 0);
            Vec3 accurateMuzzlePos = ace.toGlobalVector(localMuzzlePos, 0.0f);

            ItemStack gunStack = virtualBE.getHeldItem();
            if (gunStack.isEmpty()) return;

            SentryMovementBehaviour behavior = new SentryMovementBehaviour();
            boolean fired = behavior.fireGunInContraption(context, virtualBE, gunStack, accurateMuzzlePos, yaw, pitch, distance);
            int ammoCount = -1;
            if (gunStack.getItem() instanceof IGun) {
                try { ammoCount = ((IGun) gunStack.getItem()).getCurrentAmmoCount(gunStack); } catch (Exception ignored) {}
            }
            SentryMechanicalArm.LOGGER.info("[SMB] fireResult: fired={}, ammo={}, barrelPos={}", fired, ammoCount, accurateMuzzlePos);

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
        if (!(context.world instanceof ServerLevel serverLevel)) {
            SentryMechanicalArm.LOGGER.debug("[SMB] fireGun: world not ServerLevel, world={}",
                context.world != null ? context.world.getClass().getSimpleName() : "null");
            return false;
        }

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
            if (!hasExternal) {
                for (ItemStack box : virtualBE.attachedAmmoBoxes) {
                    if (!box.isEmpty() && box.getItem() instanceof com.tacz.guns.api.item.IAmmoBox iBox) {
                        if (iBox.isAllTypeCreative(box) || (java.util.Objects.equals(iBox.getAmmoId(box), requiredAmmoId)
                            && (iBox.isCreative(box) || iBox.getAmmoCount(box) > 0))) {
                            hasExternal = true;
                            break;
                        }
                    }
                }
            }
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
                if (!consumeAmmoFromContraption(context.contraption, gunStack, false)) {
                    for (ItemStack box : virtualBE.attachedAmmoBoxes) {
                        if (!box.isEmpty() && box.getItem() instanceof com.tacz.guns.api.item.IAmmoBox iBox) {
                            if (!iBox.isCreative(box) && iBox.getAmmoCount(box) > 0) {
                                iBox.setAmmoCount(box, iBox.getAmmoCount(box) - 1);
                                break;
                            }
                        }
                    }
                }
                if (consumedInternal) {
                    iGun.setCurrentAmmoCount(gunStack, currentInternalAmmo);
                }
            } else {
                if (consumedInternal) {
                    iGun.setCurrentAmmoCount(gunStack, currentInternalAmmo - 1);
                }
            }

            refillAmmoBoxesFromContraption(context, virtualBE, requiredAmmoId);

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

    private void refillAmmoBoxesFromContraption(MovementContext context, VirtualSentryArmBlockEntity virtualBE, ResourceLocation requiredAmmoId) {
        if (requiredAmmoId == null || context.contraption == null || context.contraption.getStorage() == null) return;
        net.neoforged.neoforge.items.IItemHandler inventory = context.contraption.getStorage().getAllItems();
        if (inventory == null) return;

        for (int slot = 0; slot < virtualBE.attachedAmmoBoxes.size(); slot++) {
            ItemStack box = virtualBE.attachedAmmoBoxes.get(slot);
            boolean needsReplace = false;
            if (box.isEmpty()) {
                needsReplace = true;
            } else if (box.getItem() instanceof com.tacz.guns.api.item.IAmmoBox iBox) {
                if (iBox.isCreative(box) || iBox.isAllTypeCreative(box)) continue;
                ResourceLocation boxId = iBox.getAmmoId(box);
                if (boxId == null || !boxId.equals(requiredAmmoId) || iBox.getAmmoCount(box) <= 0) {
                    needsReplace = true;
                }
            }

            if (needsReplace) {
                for (int i = 0; i < inventory.getSlots(); i++) {
                    ItemStack stack = inventory.getStackInSlot(i);
                    if (stack.isEmpty() || !(stack.getItem() instanceof com.tacz.guns.api.item.IAmmoBox iBox)) continue;
                    if (iBox.isCreative(stack) || iBox.isAllTypeCreative(stack)) {
                        virtualBE.attachedAmmoBoxes.set(slot, stack.copy());
                        break;
                    }
                    ResourceLocation stackId = iBox.getAmmoId(stack);
                    if (requiredAmmoId.equals(stackId) && iBox.getAmmoCount(stack) > 0) {
                        ItemStack fullBox = inventory.extractItem(i, 1, false);
                        if (!fullBox.isEmpty()) {
                            ItemStack oldBox = virtualBE.attachedAmmoBoxes.get(slot);
                            virtualBE.attachedAmmoBoxes.set(slot, fullBox);
                            if (!oldBox.isEmpty() && oldBox.getItem() instanceof com.tacz.guns.api.item.IAmmoBox) {
                                inventory.insertItem(i, oldBox, false);
                            }
                        }
                        break;
                    }
                }
            }
        }
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

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
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
        VirtualSentryArmBlockEntity virtualBE = context.temporaryData instanceof VirtualSentryArmBlockEntity v ? v : null;
        float rpm = virtualBE != null ? virtualBE.getContraptionSpeed() : 0f;

        if (rpm <= 0) {
            double movementSpeed = context.motion.length();
            rpm = (float) (movementSpeed * 512.0);
        }

        rpm = Mth.clamp(rpm, 1f, 256f);
        float multiplier = Mth.map(rpm, 1.0f, 256.0f, 0.1f, 1.0f);
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
        if (shooter == null) return true;
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
        net.neoforged.neoforge.items.IItemHandler inventory = contraption.getStorage().getAllItems();
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
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
            virtualBE.write(context.blockEntityData, context.world != null ? context.world.registryAccess() : null, false);
        }
    }

    @Override
    public void writeExtraData(MovementContext context) {
        if (context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
            virtualBE.write(context.blockEntityData, context.world != null ? context.world.registryAccess() : null, false);
        }
    }
}
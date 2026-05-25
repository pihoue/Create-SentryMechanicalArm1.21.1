package euphy.upo.sentrymechanicalarm.content;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.network.SentryShootPacket;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.IArmAmmoStorage;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import euphy.upo.sentrymechanicalarm.util.SentryTargetSavedData;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.*;

public class SentryArmBlockEntity extends KineticBlockEntity implements IArmAmmoStorage {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] EXPLOSIVE_KEYWORDS = {"rpg", "rocket", "grenade", "missile", "explosive", "rpg7"};
    private boolean currentTargetIsMarked = false;
    private Vec3 cachedMarkedPos = null;
    private int cachedMarkedContraptionId = -1;
    private BlockPos cachedMarkedLocalPos = null;
    private int markedPosShotCounter = 0;
    private boolean isCurrentTargetMarkedPos = false;
    private int markedPosUpdateTimer = 0;
    private Vec3 cachedTrackedMarkedPos = null;
    private BlockPos connectedFireControlPos = null;
    private float lowerArmRecoilOffset = 0f;
    private BlockPos cachedTargetBlock = null;
    private LivingEntity cachedTarget;
    private int idleScanTimer = 0;
    public float idleTargetYaw = 0;
    public float idleTargetPitch = 0;
    int syncedTargetId = -1;
    private int angleSyncTimer = 0;
    private int targetRescanTimer = 0;
    private boolean shouldEjectShell = false;
    private float shootDelayAccumulator = 0f;
    public LerpedFloat baseAngle;
    public LerpedFloat lowerArmAngle;
    public LerpedFloat upperArmAngle;
    public LerpedFloat headAngle;
    private ItemStack heldItem = ItemStack.EMPTY;
    public final NonNullList<ItemStack> attachedAmmoBoxes = NonNullList.withSize(2, ItemStack.EMPTY);
    private int lineOfSightTicker = 0;
    private long lastShootTime = 0;
    private int scanCooldown = 0;
    public ScrollValueBehaviour rangeScroll;
    public Optional<DyeColor> color = Optional.empty();
    private String lastScriptDataStr = "";
    private float lastAimingProgress = -1.0f;
    private int triggerHoldTime = 0;
    private int currentTimeoutThreshold = 100;
    private boolean isTestingRelease = false;
    private int releaseWatchTimer = 0;
    private boolean wasCharging = false;
    private SentryStatus currentStatus = SentryStatus.IDLE;

    public enum SentryStatus {
        IDLE,
        SHOOTING,
        CHARGING,
        BOLTING,
        RELOADING,
        COOLING,
        NO_AMMO,
        BROKEN
    }

    private record FireContext(
            FakePlayer player,
            ItemStack heldItem,
            ItemStack fakeHeldItem,
            IGun iGunFake,
            IGunOperator operator,
            ShooterDataHolder dataHolder,
            Optional<CommonGunIndex> gunIndex,
            boolean actuallyFired,
            FireMode fireMode
    ) {}

    public SentryArmBlockEntity(BlockPos pos, BlockState state) {
        super(SentryRegistry.SENTRY_ARM_BE.get(), pos, state);
        baseAngle = LerpedFloat.angular().startWithValue(0);
        lowerArmAngle = LerpedFloat.angular().startWithValue(135);
        upperArmAngle = LerpedFloat.angular().startWithValue(45);
        headAngle = LerpedFloat.angular().startWithValue(0);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        rangeScroll = new ScrollValueBehaviour(
                Component.translatable("sentry.scroll_value.range"),
                this,
                new SentryValueBoxTransform()
        );
        rangeScroll.between(0, 0);
        rangeScroll.withCallback(newValue -> this.scanCooldown = 0);
        behaviours.add(rangeScroll);
        updateRangeScrollBounds();
    }

    public boolean shouldEjectShell() { return shouldEjectShell; }
    public SentryStatus getSentryStatus() { return currentStatus; }
    public void setShellEjected() { this.shouldEjectShell = false; }
    public ItemStack getHeldItem() {
        return heldItem == null ? ItemStack.EMPTY : heldItem;

    }

    public void setHeldItem(ItemStack stack) {
        this.heldItem = stack;
        this.setChanged();
        this.sendData();
        updateRangeScrollBounds();
    }

    public long getLastShootTime() {
        return lastShootTime;
    }

    public boolean hasAnyAmmo() {
        if (heldItem.isEmpty()) return false;
        if (!(heldItem.getItem() instanceof IGun iGun)) return false;

        ResourceLocation gunId = iGun.getGunId(heldItem);
        Optional<CommonGunIndex> gunIndexOpt = TimelessAPI.getCommonGunIndex(gunId);
        if (gunIndexOpt.isEmpty()) return false;
        GunData gunData = gunIndexOpt.get().getGunData();

        int currentAmmo = iGun.getCurrentAmmoCount(heldItem);
        if (iGun.useInventoryAmmo(heldItem)) {
            currentAmmo = 0;
        }
        if (currentAmmo > 0) return true;

        ResourceLocation requiredAmmoId = gunData.getAmmoId();
        for (ItemStack box : attachedAmmoBoxes) {
            if (box.isEmpty() || !(box.getItem() instanceof IAmmoBox iBox)) continue;
            if (iBox.isAllTypeCreative(box) || iBox.isCreative(box)) return true;
            ResourceLocation boxAmmoId = iBox.getAmmoId(box);
            if (requiredAmmoId != null && requiredAmmoId.equals(boxAmmoId) && iBox.getAmmoCount(box) > 0)
                return true;
        }
        return false;
    }

    public boolean addAmmoBox(ItemStack stack) {
        for (int i = 0; i < attachedAmmoBoxes.size(); i++) {
            if (attachedAmmoBoxes.get(i).isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                attachedAmmoBoxes.set(i, copy);
                this.setChanged();
                this.sendData();
                return true;
            }
        }
        return false;
    }

    public ItemStack removeLastAmmoBox() {

        for (int i = attachedAmmoBoxes.size() - 1; i >= 0; i--) {
            if (!attachedAmmoBoxes.get(i).isEmpty()) {

                ItemStack stack = attachedAmmoBoxes.get(i);
                attachedAmmoBoxes.set(i, ItemStack.EMPTY);
                this.setChanged();
                this.sendData();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAmmoBox() {
        for(ItemStack stack : attachedAmmoBoxes) {
            if(!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setAmmoBox(ItemStack stack) {

        attachedAmmoBoxes.clear();
        attachedAmmoBoxes.set(0, stack);
        attachedAmmoBoxes.set(1, ItemStack.EMPTY);
        this.setChanged();
        this.sendData();
    }

    @Override
    public void tick() {
        super.tick();

        baseAngle.tickChaser();
        lowerArmAngle.tickChaser();
        upperArmAngle.tickChaser();
        headAngle.tickChaser();

        ItemStack currentHeld = getHeldItem();
        boolean isPowered = Math.abs(this.getSpeed()) > 0;

        boolean isGun = !currentHeld.isEmpty() && currentHeld.getItem() instanceof com.tacz.guns.api.item.IGun;
        net.neoforged.neoforge.common.util.FakePlayer fakePlayer = null;

        if (!level.isClientSide && isGun) {
            fakePlayer = euphy.upo.sentrymechanicalarm.util.SentryFakePlayer.get(this);
            if (fakePlayer != null) {
                euphy.upo.sentrymechanicalarm.util.SentryFakePlayer.sync(fakePlayer, this, this.idleTargetYaw, this.idleTargetPitch, currentHeld);
            }
        }

        if (!isPowered) {
            sentryDeactivated();
        }
        else if (isGun) {
            if (this.level.isClientSide) {
                updateClientTarget();
            }
            sentryLogic();
        }
        else {
            sentryDeactivated();
        }

        if (!level.isClientSide && isGun && fakePlayer != null) {
            try {
                fakePlayer.tick();

                ItemStack handItem = fakePlayer.getMainHandItem();
                if (!handItem.isEmpty()) {
                    handItem.inventoryTick(this.level, fakePlayer, 0, true);

                    if (handItem.getItem() instanceof com.tacz.guns.api.item.gun.AbstractGunItem gunItem) {
                        com.tacz.guns.api.entity.IGunOperator operator = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(fakePlayer);
                        if (operator != null) {
                            gunItem.tickHeat(operator.getDataHolder(), handItem, fakePlayer);
                        }
                    }
                }

                for (int i = 0; i < attachedAmmoBoxes.size(); i++) {
                    ItemStack playerStack = fakePlayer.getInventory().getItem(9 + i);
                    ItemStack currentBox = attachedAmmoBoxes.get(i);

                    if (playerStack.isEmpty() && !currentBox.isEmpty()) {
                        fakePlayer.getInventory().setItem(9 + i, currentBox.copy());
                    } else if (!playerStack.isEmpty() || currentBox.isEmpty()) {
                        if (!ItemStack.matches(playerStack, currentBox)) {
                            attachedAmmoBoxes.set(i, playerStack.copy());
                            this.setChanged();
                            this.sendData();
                        }
                    }
                }

            } catch (Exception ignored) {
            }

            ItemStack fakeHeld = fakePlayer.getMainHandItem();
            if (fakeHeld.getItem() == currentHeld.getItem() && ItemNBTHelper.hasTag(fakeHeld)) {
                if (!java.util.Objects.equals(ItemNBTHelper.getTag(currentHeld), ItemNBTHelper.getTag(fakeHeld))) {
                    ItemNBTHelper.setTag(currentHeld, ItemNBTHelper.getTag(fakeHeld).copy());
                    this.setChanged();
                    this.sendData();
                }
            }

        }

        if (!level.isClientSide && !attachedAmmoBoxes.isEmpty()) {
            if (currentHeld.isEmpty() || !(currentHeld.getItem() instanceof IGun)) {
                popAmmoBox();
            }
        }

        if (!level.isClientSide && this.syncedTargetId != -1) {
            if (++angleSyncTimer >= 5) {
                angleSyncTimer = 0;
                sendData();
            }
        }
    }

    private void sentryDeactivated() {
        float speed = 0.05f;
        lowerArmAngle.chase(135, speed, LerpedFloat.Chaser.EXP);
        upperArmAngle.chase(45, speed, LerpedFloat.Chaser.EXP);
        headAngle.chase(0, speed, LerpedFloat.Chaser.EXP);
        baseAngle.chase(baseAngle.getValue(), 0, LerpedFloat.Chaser.EXP);
    }

    private double getSentryRange() {
        if (rangeScroll == null || heldItem.isEmpty()) {
            return 16.0;
        }
        double rawValue = rangeScroll.getValue();

        return Math.min(rawValue, 256.0);
    }

    public void setLastShootTime(long time) {
        this.lastShootTime = time;
    }

    private void sentryLogic() {
        if (currentStatus == SentryStatus.NO_AMMO && !this.level.isClientSide) {
            if (hasAnyAmmo()) {
                setStatus(SentryStatus.IDLE);
            } else {
                sentryDeactivated();
                return;
            }
        }

        if (!this.level.isClientSide) {
            FakePlayer fp = SentryFakePlayer.get(this);
            if (fp != null) {
                SentryFakePlayer.sync(fp, this, baseAngle.getValue(), headAngle.getValue(), this.heldItem);
            }
        }

        Vec3 currentTickBestPos = null;

        if (!this.level.isClientSide) {
            double maxRange = this.getSentryRange();
            boolean invalid = false;
            Vec3 rangeCheckCenter = isInSableSubLevel() ? getProjectedWorldPos() : this.worldPosition.getCenter();
            refreshMarkedPosFromFireControl();

            if (cachedTarget != null) {
                if (++targetRescanTimer >= 300) {
                    targetRescanTimer = 0;
                    invalid = true;
                } else if (!cachedTarget.isAlive() || cachedTarget.isRemoved() ||
                        cachedTarget.distanceToSqr(rangeCheckCenter) > maxRange * maxRange) {
                    invalid = true;
                } else if (lineOfSightTicker++ >= 10) {
                    lineOfSightTicker = 0;
                    currentTickBestPos = getBestTargetPos(cachedTarget);
                    if (currentTickBestPos == null) {
                        invalid = true;
                    }
                }
            }
            else if (cachedTargetBlock != null) {
                if (cachedTargetBlock.distToCenterSqr(rangeCheckCenter) > maxRange * maxRange ||
                        !this.level.getBlockState(cachedTargetBlock).is(Blocks.TARGET)) {
                    invalid = true;

                    if (!this.level.getBlockState(cachedTargetBlock).is(Blocks.TARGET)) {
                        SentryTargetSavedData.get(this.level).removeTarget(cachedTargetBlock);
                    }
                    this.cachedTargetBlock = null;
                    syncTargetBlock();

                } else {
                    if (lineOfSightTicker++ >= 20) {
                        lineOfSightTicker = 0;
                        if (!isBlockVisible(getActualMuzzlePos(), cachedTargetBlock)) {
                            invalid = true;
                        }
                    }
                }
            } else if (cachedMarkedPos != null && hasExplosiveAmmo()) {
                Vec3 trackedPos = updateMarkedPos();
                if (trackedPos != null && trackedPos.distanceToSqr(rangeCheckCenter) <= maxRange * maxRange) {
                    currentTickBestPos = trackedPos;
                    isCurrentTargetMarkedPos = true;
                } else {
                    invalid = true;
                }
            } else {
                if (scanCooldown-- <= 0) {
                    scanCooldown = 20;
                    scanForTarget();
                }
            }

            if (invalid) {
                this.cachedTarget = null;
                if (this.cachedTargetBlock != null) {
                    this.cachedTargetBlock = null;
                    syncTargetBlock();
                }
                this.cachedMarkedPos = null;
                this.cachedMarkedContraptionId = -1;
                this.cachedMarkedLocalPos = null;
                this.cachedTrackedMarkedPos = null;
                setTargetId(-1);
                scanCooldown = 0;
            }
        }

        if (currentTickBestPos == null) {
            if (cachedTarget != null && cachedTarget.isAlive()) {
                currentTickBestPos = getBestTargetPos(cachedTarget);
            } else if (cachedTargetBlock != null) {
                currentTickBestPos = Vec3.atCenterOf(cachedTargetBlock);
            } else if (cachedMarkedPos != null && hasExplosiveAmmo()) {
                currentTickBestPos = updateMarkedPos();
            }
        }

        if (currentTickBestPos != null) {
            boolean useLocalSpace = isInSableSubLevel();

            if (!this.level.isClientSide || this.syncedTargetId == -1) {
                Vec2 aimAngles;
                if (useLocalSpace) {
                    Vec3 subTarget = worldToSubLevel(currentTickBestPos);
                    Vec3 subMuzzle = getLocalMuzzlePos();
                    aimAngles = calculateAngleBetween(subMuzzle, subTarget);
                } else {
                    aimAngles = calculateTruthAngle(currentTickBestPos);
                }
                aimAtAngle(aimAngles.x, aimAngles.y);
            }

            if (!this.level.isClientSide) {
                float targetYaw, targetPitch;
                float currentYaw, currentPitch;

                if (useLocalSpace) {
                    Vec3 subTarget = worldToSubLevel(currentTickBestPos);
                    Vec3 subMuzzle = getLocalMuzzlePos();
                    Vec2 subAngles = calculateAngleBetween(subMuzzle, subTarget);
                    targetYaw = subAngles.x;
                    targetPitch = subAngles.y;
                } else {
                    Vec2 worldAngles = calculateTruthAngle(currentTickBestPos);
                    targetYaw = worldAngles.x;
                    targetPitch = worldAngles.y;
                }

                if (isCeiling()) {
                    currentYaw = baseAngle.getValue();
                    currentPitch = headAngle.getValue();
                } else {
                    currentYaw = 180 - baseAngle.getValue();
                    currentPitch = -headAngle.getValue();
                }

                double absDiff = Math.abs(targetYaw - currentYaw) % 360;
                double deviation = Math.min(absDiff, 360 - absDiff);
                float currentUpperArm = upperArmAngle.getValue();
                boolean isDeployed = currentUpperArm > 80f;

                if (deviation < 1.0 && isDeployed) {
                    if (useLocalSpace) {
                        Vec2 worldAngles = calculateTruthAngle(currentTickBestPos);
                        fireGun(worldAngles.x, worldAngles.y);
                    } else {
                        fireGun(currentYaw, currentPitch);
                    }
                }
            }
        } else {
            if (!this.level.isClientSide || this.syncedTargetId == -1) {
                resetAimer();
            }
        }
    }

    private void aimAtAngle(float targetYaw, float targetPitch) {
        if (isCeiling()) {
            targetPitch = -targetPitch ;
            targetYaw = -targetYaw + 180;
        }

        float currentYaw = baseAngle.getValue();
        float desiredYaw = -targetYaw + 180;
        float yawDiff = desiredYaw - currentYaw;
        while (yawDiff < -180) yawDiff += 360;
        while (yawDiff > 180) yawDiff -= 360;

        float absYawDiff = Math.abs(yawDiff);
        float yawSpeedBase;
        if (absYawDiff < 0.5f) {
            baseAngle.chase(currentYaw + yawDiff, 1.0f, LerpedFloat.Chaser.EXP);
        }
        else {
            if (absYawDiff < 10.0f) {
                yawSpeedBase = 0.44f;
            } else if (absYawDiff < 45.0f) {
                yawSpeedBase = 0.22f;
            } else {
                yawSpeedBase = 0.19f;
            }
            baseAngle.chase(currentYaw + yawDiff, getAnimationSpeed(yawSpeedBase), LerpedFloat.Chaser.EXP);
        }

        float currentPitch = headAngle.getValue();
        float desiredPitch = net.minecraft.util.Mth.clamp(-targetPitch, -90, 90);
        float pitchDiff = desiredPitch - currentPitch;
        float absPitchDiff = Math.abs(pitchDiff);

        if (absPitchDiff < 0.5f) {

            headAngle.chase(desiredPitch, 1.0f, LerpedFloat.Chaser.EXP);
        } else {

            float pitchSpeedBase;
            if (absPitchDiff < 5.0f) {
                pitchSpeedBase = 0.44f;
            } else {
                pitchSpeedBase = 0.19f;
            }
            headAngle.chase(desiredPitch, getAnimationSpeed(pitchSpeedBase), LerpedFloat.Chaser.EXP);
        }
        upperArmAngle.chase(90f, getAnimationSpeed(0.4f), LerpedFloat.Chaser.EXP);
        this.lowerArmRecoilOffset = net.minecraft.util.Mth.lerp(0.7f, this.lowerArmRecoilOffset, 0f);
        if (Math.abs(this.lowerArmRecoilOffset) < 0.01f) this.lowerArmRecoilOffset = 0f;
        float targetLowerArm = 135f + this.lowerArmRecoilOffset;
        lowerArmAngle.chase(targetLowerArm, 0.6f, LerpedFloat.Chaser.EXP);
    }

    public Vec3 getActualMuzzlePos() {
        if (isInSableSubLevel()) {
            return getProjectedMuzzlePos();
        }
        FakePlayer fp = SentryFakePlayer.get(this);
        if (fp != null) {
            return fp.getEyePosition();
        }
        Vec3 basePos = this.worldPosition.getCenter().add(0, 1.5, 0);
        if (isCeiling()) {
            basePos = basePos.add(0, -4.0, 0);
        }
        return basePos;
    }

    public boolean isInSableSubLevel() {
        return AeronauticsHelper.isInSableSubLevel(this.level, this.worldPosition);
    }

    public Vec3 getProjectedWorldPos() {
        if (isInSableSubLevel()) {
            return AeronauticsHelper.sableSubLevelToWorld(this.level, this.worldPosition.getCenter());
        }
        return this.worldPosition.getCenter();
    }

    public Vec3 worldToSubLevel(Vec3 worldPos) {
        if (isInSableSubLevel()) {
            return AeronauticsHelper.sableWorldToSubLevel(this.level, worldPos, this.worldPosition);
        }
        return worldPos;
    }

    public Vec3 getProjectedMuzzlePos() {
        Vec3 local = this.worldPosition.getCenter().add(0, 1.5, 0);
        if (isCeiling()) {
            local = local.add(0, -4.0, 0);
        }
        if (isInSableSubLevel()) {
            local = local.add(0, 0.42, 0);
            return AeronauticsHelper.sableSubLevelToWorld(this.level, local);
        }
        if (AeronauticsHelper.isAeronauticsLoaded()) {
            return AeronauticsHelper.projectOutOfSubLevel(this.level, local, this.worldPosition.getCenter());
        }
        return local;
    }

    private void resetAimer() {
        sentryIdleScanning();
    }

    public void updateFromFireControl() {
        this.cachedTarget = null;
        this.cachedTargetBlock = null;
        this.setTargetId(-1);
        this.scanCooldown = 0;
    }

    private void scanForTarget() {

        double range = this.getSentryRange();
        if (range < 1.0) return;
        if (range > 256.0) range = 256.0;

        boolean isStrictControlMode = false;
        boolean isWhitelistMode = false;
        List<String> activeWhitelist = null;

        BlockEntity be = null;
        if (this.connectedFireControlPos != null) {
            if (level.isLoaded(this.connectedFireControlPos)) {
                be = level.getBlockEntity(this.connectedFireControlPos);


                if (be instanceof BlazeFireControlBlockEntity fc) {

                    if (this.connectedFireControlPos.distSqr(this.worldPosition) > 36.0) {
                        this.disconnectFireControl();
                    } else {
                        if (!fc.inventory.getStackInSlot(0).isEmpty()) {
                            isStrictControlMode = true;
                            isWhitelistMode = fc.isWhitelist();
                            activeWhitelist = fc.getTargetList();
                        }
                    }
                } else {
                    this.disconnectFireControl();
                }
            }
        }

        final Vec3 localCenter = this.worldPosition.getCenter();
    final Vec3 center;
    if (isInSableSubLevel()) {
        center = getProjectedWorldPos();
    } else if (AeronauticsHelper.isAeronauticsLoaded()) {
        center = AeronauticsHelper.projectOutOfSubLevel(this.level, localCenter, localCenter);
    } else {
        center = localCenter;
    }
        if (be instanceof BlazeFireControlBlockEntity fc2) {
            int focusId = fc2.getFocusedEntityId();
            if (focusId != -1) {
                Entity focusEntity = level.getEntity(focusId);
                if (focusEntity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
                        && living.distanceToSqr(center) <= range * range) {
                    Vec3 focusPos = getBestTargetPos(living);
                    if (focusPos != null) {
                        this.cachedTarget = living;
                        this.currentTargetIsMarked = false;
                        setTargetId(living.getId());
                        this.cachedTargetBlock = null;
                        targetRescanTimer = 0;
                        return;
                    }
                }
            }

            for (int markedId : fc2.getMarkedEntityIds()) {
                Entity markedEntity = level.getEntity(markedId);
                if (markedEntity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
                        && living.distanceToSqr(center) <= range * range) {
                    Vec3 markedPos = getBestTargetPos(living);
                    if (markedPos != null) {
                        this.cachedTarget = living;
                        this.currentTargetIsMarked = true;
                        setTargetId(living.getId());
                        this.cachedTargetBlock = null;
                        targetRescanTimer = 0;
                        return;
                    }
                }
            }
        }

        final boolean finalStrict = isStrictControlMode;
        final boolean finalWhitelistMode = isWhitelistMode;
        final List<String> finalList = activeWhitelist;

        final double maxRangeSq = range * range;
        AABB area;
        if (isInSableSubLevel()) {
            area = new AABB(center, center).inflate(range);
        } else {
            area = new AABB(this.worldPosition).inflate(range);
        }
        List<LivingEntity> potentialTargets = this.level.getEntitiesOfClass(LivingEntity.class, area, e -> {
            if (e.distanceToSqr(center) > maxRangeSq) return false;
            if (!e.isAlive() || e.isSpectator()) return false;
            if (finalStrict) {
                if (finalList == null || finalList.isEmpty()) {
                    return finalWhitelistMode && (e instanceof Enemy);
                }
                String name = e.getName().getString();
                boolean inList = false;
                for (String targetName : finalList) {
                    if (targetName.equals(name)) {
                        inList = true;
                        break;
                    }
                }
                if (finalWhitelistMode) {
                    return !inList;
                } else {
                    return inList;
                }
            } else {
                return (e instanceof Enemy);
            }
        });

        final Vec3 compareCenter = isInSableSubLevel() ? center : this.worldPosition.getCenter();
        LivingEntity newTarget = potentialTargets.stream()
                .filter(target -> getBestTargetPos(target) != null)
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(compareCenter)))
                .orElse(null);

        if (newTarget != null) {
            if (this.cachedTarget != newTarget) {
                this.cachedTarget = newTarget;
                setTargetId(newTarget.getId());
            }
            targetRescanTimer = 0;
            this.cachedTargetBlock = null;
            return;
        } else {
            if (this.cachedTarget != null) {
                this.cachedTarget = null;
                setTargetId(-1);
            }
        }

        if (!this.level.isClientSide) {
            Set<BlockPos> targets = SentryTargetSavedData.get(this.level).getTargets();
            BlockPos bestBlock = null;
            double minDstSqr = range * range;
            Vec3 muzzle = this.getActualMuzzlePos();
            for (BlockPos pos : targets) {
                double dstSqr = center.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (dstSqr > minDstSqr) continue;
                if (isBlockVisible(muzzle, pos)) {
                    minDstSqr = dstSqr;
                    bestBlock = pos;
                }
            }
            if (!java.util.Objects.equals(this.cachedTargetBlock, bestBlock)) {
                this.cachedTargetBlock = bestBlock;
                syncTargetBlock();
            }
        }
    }

    private void setTargetId(int id) {
        if (this.syncedTargetId == id) return;

        this.syncedTargetId = id;
        this.setChanged();
        this.sendData();
    }

    private Vec3 getBestTargetPos(LivingEntity target) {

        Vec3 armPos = this.getActualMuzzlePos();
        float height = target.getBbHeight();
        Vec3 basePos = target.position();

        Vec3 headPos = basePos.add(0, height * 0.90, 0);
        if (isPointVisible(armPos, headPos)) return headPos;

        Vec3 centerPos = basePos.add(0, height * 0.6, 0);
        if (isPointVisible(armPos, centerPos)) return centerPos;

        Vec3 legPos = basePos.add(0, height * 0.25, 0);
        if (isPointVisible(armPos, legPos)) return legPos;

        Vec3 feetPos = basePos.add(0, height * 0.1, 0);
        if (isPointVisible(armPos, feetPos)) return feetPos;

        return null;
    }

    private Vec3 updateMarkedPos() {
        if (++markedPosUpdateTimer < 5) {
            if (cachedTrackedMarkedPos != null) return cachedTrackedMarkedPos;
            markedPosUpdateTimer = 5;
        }
        markedPosUpdateTimer = 0;
        if (cachedMarkedContraptionId != -1) {
            Entity entity = level.getEntity(cachedMarkedContraptionId);
            if (entity instanceof AbstractContraptionEntity ace && ace.isAlive()) {
                Vec3 localCenter = Vec3.atCenterOf(cachedMarkedLocalPos);
                Vec3 global = ace.toGlobalVector(localCenter, 1.0F);
                cachedMarkedPos = global;
                cachedTrackedMarkedPos = global;
                return global;
            }
            cachedTrackedMarkedPos = null;
            return null;
        }
        if (isInSableSubLevel()) {
            Vec3 projected = AeronauticsHelper.sableSubLevelToWorld(this.level, cachedMarkedPos);
            cachedMarkedPos = projected;
            cachedTrackedMarkedPos = projected;
            return projected;
        }
        cachedTrackedMarkedPos = cachedMarkedPos;
        return cachedMarkedPos;
    }

    private void refreshMarkedPosFromFireControl() {
        if (this.connectedFireControlPos != null && level.isLoaded(this.connectedFireControlPos)
                && level.getBlockEntity(this.connectedFireControlPos) instanceof BlazeFireControlBlockEntity fc) {
            Vec3 wp = fc.getMarkedWorldPos();
            if (wp != null) {
                if (!wp.equals(this.cachedMarkedPos)) {
                    markedPosShotCounter = 0;
                }
                this.cachedMarkedPos = wp;
                this.cachedMarkedContraptionId = fc.getMarkedContraptionEntityId();
                this.cachedMarkedLocalPos = fc.getMarkedLocalPos();
            } else {
                this.cachedMarkedPos = null;
                this.cachedMarkedContraptionId = -1;
                this.cachedMarkedLocalPos = null;
                this.cachedTrackedMarkedPos = null;
                markedPosShotCounter = 0;
            }
        }
    }

    private boolean hasExplosiveAmmo() {
        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof IGun iGun)) return false;
        ResourceLocation gunId = iGun.getGunId(heldItem);
        Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gunId);
        if (index.isPresent()) {
            ResourceLocation ammoId = index.get().getGunData().getAmmoId();
            if (ammoId != null) {
                String path = ammoId.getPath().toLowerCase();
                for (String kw : EXPLOSIVE_KEYWORDS) {
                    if (path.contains(kw)) return true;
                }
            }
            String gunPath = gunId.getPath().toLowerCase();
            for (String kw : EXPLOSIVE_KEYWORDS) {
                if (gunPath.contains(kw)) return true;
            }
        }
        return false;
    }

    private boolean isPointVisible(Vec3 start, Vec3 end) {
        if (isInSableSubLevel()) {
            Vec3 subStart = worldToSubLevel(start);
            Vec3 subEnd = worldToSubLevel(end);
            if (subStart.distanceToSqr(subEnd) > 1000000.0) return false;
            BlockHitResult result = this.level.clip(new ClipContext(
                    subStart, subEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()
            ));
            return result.getType() == HitResult.Type.MISS;
        }
        if (AeronauticsHelper.isAeronauticsLoaded()) {
            Vec3 worldDir = end.subtract(start);
            Vec3 localDir = AeronauticsHelper.toLocalVector(this.level, this.worldPosition.getCenter(), worldDir);
            end = start.add(localDir);
        }
        BlockHitResult result = this.level.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()
        ));

        return result.getType() == HitResult.Type.MISS;
    }

    private float getAnimationSpeed(float baseChaserSpeed) {
        float currentRpm = Math.abs(this.getSpeed());
        float multiplier = Mth.map(currentRpm, 1.0f, 256.0f, 0.01f, 1.0f);

        multiplier = Mth.clamp(multiplier, 0.01f, 1.0f);

        return baseChaserSpeed * multiplier;
    }

    public void disconnectFireControl() {
        this.connectedFireControlPos = null;
        this.cachedTarget = null;
        this.setTargetId(-1);


        this.setChanged();
        this.syncTargetBlock();
    }



    private void fireGun(float targetYaw, float targetPitch) {
        if (heldItem.isEmpty() || this.level.isClientSide) return;

        FakePlayer fakePlayer = SentryFakePlayer.get(this);
        if (fakePlayer == null) return;

        SentryFakePlayer.sync(fakePlayer, this, targetYaw, targetPitch, heldItem);
        IGunOperator operator = IGunOperator.fromLivingEntity(fakePlayer);
        ItemStack fakeHeldItem = fakePlayer.getMainHandItem();
        IGun iGunFake = IGun.getIGunOrNull(fakeHeldItem);
        if (iGunFake == null) return;

        long gameTick = this.level.getGameTime();
        ShooterDataHolder dataHolder = operator.getDataHolder();

        if (this.shootDelayAccumulator > 0) {
            this.shootDelayAccumulator -= 1.0f;
            this.triggerHoldTime = 0;
            if (fakeHeldItem.getItem() == heldItem.getItem() && ItemNBTHelper.hasTag(fakeHeldItem)) {
                ItemNBTHelper.setTag(heldItem, ItemNBTHelper.getTag(fakeHeldItem).copy());
            }
            return;
        }

        ResourceLocation gunIdRes = iGunFake.getGunId(fakeHeldItem);
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gunIdRes);
        FireMode mode = iGunFake.getFireMode(fakeHeldItem);
        if (gunIndex.isPresent()) {
            applySentryAccuracyModifier(operator, iGunFake, fakeHeldItem, gunIndex.get().getGunData());
        }
        this.triggerHoldTime++;
        ShootResult result = ShootResult.UNKNOWN_FAIL;
        try {
            result = operator.shoot(() -> targetPitch, () -> targetYaw);
        } catch (Exception e) {
            LOGGER.error("Error executing operator.shoot", e);
        }

        boolean actuallyFired = SentryFakePlayer.checkAndClearFired(fakePlayer);

        if (actuallyFired && isCurrentTargetMarkedPos) {
            markedPosShotCounter++;
            if (markedPosShotCounter >= 1) {
                cachedMarkedPos = null;
                cachedMarkedContraptionId = -1;
                cachedMarkedLocalPos = null;
                cachedTrackedMarkedPos = null;
                markedPosShotCounter = 0;
                isCurrentTargetMarkedPos = false;
                if (connectedFireControlPos != null && level.isLoaded(connectedFireControlPos)
                        && level.getBlockEntity(connectedFireControlPos) instanceof BlazeFireControlBlockEntity fc) {
                    fc.clearMarkedPos();
                }
                return;
            }
        }

        FireContext ctx = new FireContext(
                fakePlayer, heldItem, fakeHeldItem, iGunFake, operator, dataHolder, gunIndex, actuallyFired, mode
        );

        switch (result) {
            case NEED_BOLT -> handleNeedBolt(ctx);
            case NO_AMMO -> handleNoAmmo(ctx);
            case OVERHEATED -> handleOverheated(ctx);
            case IS_BOLTING -> handleIsBolting(ctx);
            case IS_RELOADING, IS_DRAWING -> handleWaitState();
            case SUCCESS -> handleSuccess(ctx);
            default -> {}
        }

        if (fakeHeldItem.getItem() == heldItem.getItem() && ItemNBTHelper.hasTag(fakeHeldItem)) {
            ItemNBTHelper.setTag(heldItem, ItemNBTHelper.getTag(fakeHeldItem).copy());
        }
    }


    private void handleWaitState() {
        this.shootDelayAccumulator = 2.0f;
        setStatus(SentryStatus.RELOADING);
    }

    private void handleNeedBolt(FireContext ctx) {
        ctx.operator.bolt();
        setStatus(SentryStatus.BOLTING);
        sendActionPacket(SentryShootPacket.ActionType.BOLT);
        float boltTime = 0.5f;
        if (ctx.gunIndex.isPresent()) boltTime = ctx.gunIndex.get().getGunData().getBoltActionTime();
        this.shootDelayAccumulator = Math.max(10, boltTime * 20) + 5;
    }

    private void handleNoAmmo(FireContext ctx) {
        boolean reloaded = performInstantReload(ctx.player, ctx.iGunFake, ctx.fakeHeldItem);
        if (reloaded) {
            this.shootDelayAccumulator = 2.0f;
            setStatus(SentryStatus.RELOADING);
        } else {
            this.shootDelayAccumulator = 40.0f;
            setStatus(SentryStatus.NO_AMMO);
        }
    }

    private void handleIsBolting(FireContext ctx) {
        long boltTimestamp = ctx.dataHolder.boltTimestamp;
        float boltTime = 0.5f;
        if (ctx.gunIndex.isPresent()) {
            boltTime = ctx.gunIndex.get().getGunData().getBoltActionTime();
        }

        long boltDurationMs = (long) (boltTime * 1000);
        long elapsed = System.currentTimeMillis() - boltTimestamp;

        if (elapsed > boltDurationMs + 500) {
            if (!ctx.iGunFake.hasBulletInBarrel(ctx.fakeHeldItem)) {
                if (ctx.iGunFake.getCurrentAmmoCount(ctx.fakeHeldItem) > 0) {
                    ctx.iGunFake.reduceCurrentAmmoCount(ctx.fakeHeldItem);
                    ctx.iGunFake.setBulletInBarrel(ctx.fakeHeldItem, true);
                }
            }
            ctx.dataHolder.isBolting = false;
            ctx.dataHolder.boltTimestamp = -1L;
            this.shootDelayAccumulator = 0f;
        } else {
            this.shootDelayAccumulator = 2.0f;
        }
    }

    private void handleOverheated(FireContext ctx) {
        long heatTimestamp = ctx.dataHolder.heatTimestamp;
        long currentTimestamp = System.currentTimeMillis();
        long idleTime = currentTimestamp - heatTimestamp;

        long coolingDelay = 2000L;
        if (ctx.gunIndex.isPresent() && ctx.gunIndex.get().getGunData().getHeatData() != null) {
            coolingDelay = ctx.gunIndex.get().getGunData().getHeatData().getCoolingDelay();
        }
        long gracePeriod = coolingDelay + 2000L;

        if (idleTime < gracePeriod) {
            setStatus(SentryStatus.COOLING);
            this.shootDelayAccumulator = 20.0f;
        } else {
            float currentHeat = ctx.iGunFake.getHeatAmount(ctx.fakeHeldItem);
            float maxHeat = 0.0f;
            if (ctx.gunIndex.isPresent() && ctx.gunIndex.get().getGunData().getHeatData() != null) {
                maxHeat = ctx.gunIndex.get().getGunData().getHeatData().getHeatMax();
            }

            boolean isCoolingDown = (maxHeat > 0) && (currentHeat < (maxHeat - 0.01f));

            if (isCoolingDown) {
                setStatus(SentryStatus.COOLING);
                this.shootDelayAccumulator = 20.0f;
            } else {
                boolean consumed = false;
                if (ctx.gunIndex.isPresent()) {
                    net.minecraft.resources.ResourceLocation requiredAmmoId = ctx.gunIndex.get().getGunData().getAmmoId();
                    consumed = tryConsumeGenericAmmo(ctx.player, requiredAmmoId);
                }

                if (consumed) {
                    ctx.iGunFake.setHeatAmount(ctx.fakeHeldItem, 0.0f);
                    ctx.iGunFake.setOverheatLocked(ctx.fakeHeldItem, false);
                    ctx.dataHolder.reloadStateType = com.tacz.guns.api.entity.ReloadState.StateType.NOT_RELOADING;
                    ctx.dataHolder.reloadTimestamp = -1L;
                    this.shootDelayAccumulator = 10.0f;
                } else {
                    this.shootDelayAccumulator = 40.0f;
                }
                setStatus(SentryStatus.COOLING);
            }
        }
    }

    private void handleSuccess(FireContext ctx) {
        this.lastShootTime = System.currentTimeMillis();

        com.tacz.guns.resource.pojo.data.gun.Bolt boltType = com.tacz.guns.resource.pojo.data.gun.Bolt.OPEN_BOLT;
        if (ctx.gunIndex.isPresent()) {
            boltType = ctx.gunIndex.get().getGunData().getBolt();
        }
        boolean isManualAction = (boltType == com.tacz.guns.resource.pojo.data.gun.Bolt.MANUAL_ACTION);
        setStatus(SentryStatus.IDLE);
        if (isManualAction) {
            handleManualActionStrategy(ctx);
        } else if (ctx.fireMode == FireMode.SEMI || ctx.fireMode == FireMode.BURST) {
            handleSemiAutoStrategy(ctx);
        } else {
            handleAdaptiveAutoStrategy(ctx);
            if (ctx.actuallyFired) {
                if (ctx.gunIndex.isPresent()) {
                    float rpm = ctx.gunIndex.get().getGunData().getRoundsPerMinute(FireMode.AUTO);
                    if (rpm > 0) {
                        this.shootDelayAccumulator = 1200f / rpm;
                    } else {
                        this.shootDelayAccumulator = 2.0f;
                    }
                } else {
                    this.shootDelayAccumulator = 2.0f;
                }
            }
        }

        if (ctx.actuallyFired) {
            sendShootPacket(ctx.player);
        }
    }

    private void handleManualActionStrategy(FireContext ctx) {
        if (ctx.actuallyFired) {
            setStatus(SentryStatus.SHOOTING);
            this.shootDelayAccumulator = 4.0f;
            this.triggerHoldTime = 0;
        } else {
            setStatus(SentryStatus.SHOOTING);
            this.triggerHoldTime++;
            if (this.triggerHoldTime > 20) {
                this.shootDelayAccumulator = 4.0f;
                this.triggerHoldTime = 0;
            } else {
                this.shootDelayAccumulator = 0f;
            }
        }
    }

    private void handleSemiAutoStrategy(FireContext ctx) {
        if (ctx.actuallyFired) {
            setStatus(SentryStatus.SHOOTING);
            this.shootDelayAccumulator = 8.0f;
        } else {
            setStatus(SentryStatus.IDLE);
            if (this.triggerHoldTime > 60) {
                this.shootDelayAccumulator = 10.0f;
                this.triggerHoldTime = 0;
            } else {
                this.shootDelayAccumulator = 0f;
            }
        }
    }

    private void handleAdaptiveAutoStrategy(FireContext ctx) {

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

        String currentScriptDataStr = getLuaDataSnapshot(ctx.dataHolder.scriptData);
        float currentAimingProgress = ctx.operator.getSynAimingProgress();
        boolean isScriptChanging = !currentScriptDataStr.equals(this.lastScriptDataStr);
        boolean isProgressChanging = Math.abs(currentAimingProgress - this.lastAimingProgress) > 0.001f;
        this.lastScriptDataStr = currentScriptDataStr;
        this.lastAimingProgress = currentAimingProgress;

        boolean isGunActive = ctx.actuallyFired || isScriptChanging || isProgressChanging;

        if (isGunActive && !this.wasCharging) {
            sendActionPacket(euphy.upo.sentrymechanicalarm.network.SentryShootPacket.ActionType.CHARGE);
        }
        this.wasCharging = isGunActive;

        if (ctx.actuallyFired) {
            setStatus(SentryStatus.SHOOTING);
            this.triggerHoldTime = 0;
            this.shootDelayAccumulator = 0f;
            this.wasCharging = false;
        } else if (isGunActive) {
            setStatus(SentryStatus.CHARGING);
            this.triggerHoldTime++;
            if (this.triggerHoldTime > this.currentTimeoutThreshold) {
                this.shootDelayAccumulator = 10.0f;
                this.triggerHoldTime = 0;
                this.isTestingRelease = true;
                this.releaseWatchTimer = 20;
            } else {
                this.shootDelayAccumulator = 0f;
            }
        } else {
            setStatus(SentryStatus.IDLE);
            this.triggerHoldTime++;
            if (this.triggerHoldTime > 60) {
                this.shootDelayAccumulator = 10.0f;
                this.triggerHoldTime = 0;
            } else {
                this.shootDelayAccumulator = 0f;
            }
        }
    }

    private void sendShootPacket(FakePlayer fakePlayer) {
        Vec3 realStart = fakePlayer.getEyePosition();
        Vec3 lookVec = fakePlayer.getViewVector(1.0F);
        Vec3 traceEnd = realStart.add(lookVec.scale(100));
        BlockHitResult hitResult = this.level.clip(new ClipContext(
                realStart, traceEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                fakePlayer));

        NetworkHandler.sendToNearby(
                new SentryShootPacket(
                        this.worldPosition, -1, ItemNBTHelper.getOrCreateTag(heldItem), realStart, hitResult.getLocation(), SentryShootPacket.ActionType.SHOOT),
                this.level, this.worldPosition);
    }

    private boolean tryConsumeGenericAmmo(net.neoforged.neoforge.common.util.FakePlayer fakePlayer, net.minecraft.resources.ResourceLocation ammoId) {
        if (ammoId == null) return false;
        net.minecraft.world.entity.player.Inventory inventory = fakePlayer.getInventory();

        for (int i = 9; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof com.tacz.guns.api.item.IAmmoBox iBox) {
                if (iBox.isAllTypeCreative(stack)) {
                    return true;
                }

                if (java.util.Objects.equals(iBox.getAmmoId(stack), ammoId)) {
                    if (iBox.isCreative(stack)) return true;

                    if (iBox.getAmmoCount(stack) > 0) {
                        iBox.setAmmoCount(stack, iBox.getAmmoCount(stack) - 1);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void applySentryAccuracyModifier(IGunOperator operator, IGun iGun, ItemStack gunStack, GunData gunData) {
        AttachmentCacheProperty cache = operator.getCacheProperty();
        if (cache == null) return;

        double distToTarget = 0.0;
        Vec3 accuracyCenter = isInSableSubLevel() ? getProjectedWorldPos() : this.worldPosition.getCenter();
        if (this.cachedTarget != null) {
            distToTarget = Math.sqrt(this.cachedTarget.distanceToSqr(accuracyCenter));
        } else if (this.cachedTargetBlock != null) {
            distToTarget = Math.sqrt(this.cachedTargetBlock.distToCenterSqr(accuracyCenter));
        }

        float effectiveRange = calculateEffectiveRange(gunData);
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
            targetSpread = 0.0f;
        } else if (distToTarget <= effectiveRange) {
            targetSpread = 0.1f;
        } else {
            double excessDistance = distToTarget - effectiveRange;
            targetSpread = (float) (excessDistance * 0.02);
            targetSpread = Math.min(targetSpread, 5.0f);
        }

        Map<InaccuracyType, Float> cachedMap = cache.getCache(GunProperties.INACCURACY);
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
        operator.getDataHolder().aimingProgress = 1.0f;
    }

    private boolean performInstantReload(net.neoforged.neoforge.common.util.FakePlayer fakePlayer, com.tacz.guns.api.item.IGun iGun, ItemStack gunStack) {
        ResourceLocation gunId = iGun.getGunId(gunStack);
        Optional<CommonGunIndex> gunIndexOpt = TimelessAPI.getCommonGunIndex(gunId);
        if (gunIndexOpt.isEmpty()) return false;

        GunData gunData = gunIndexOpt.get().getGunData();
        ResourceLocation neededAmmoId = gunData.getAmmoId();
        int maxAmmo = gunData.getAmmoAmount();
        int currentAmmo = iGun.getCurrentAmmoCount(gunStack);
        int neededAmount = maxAmmo - currentAmmo;

        if (neededAmount <= 0) return true;

        int totalReloaded = 0;
       Inventory inventory = fakePlayer.getInventory();

        for (int i = 9; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof IAmmoBox iBox) {
                if (iBox.isAllTypeCreative(stack) || (iBox.isCreative(stack) && Objects.equals(iBox.getAmmoId(stack), neededAmmoId))) {
                    totalReloaded = neededAmount;
                    break;
                }
                if (Objects.equals(iBox.getAmmoId(stack), neededAmmoId)) {
                    int boxCount = iBox.getAmmoCount(stack);
                    int toTake = Math.min(boxCount, neededAmount - totalReloaded);

                    iBox.setAmmoCount(stack, boxCount - toTake);
                    totalReloaded += toTake;

                    if (totalReloaded >= neededAmount) break;
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
            if (operator != null) {
                ShooterDataHolder holder = operator.getDataHolder();
                holder.reloadStateType = ReloadState.StateType.NOT_RELOADING;
                holder.reloadTimestamp = -1L;
                holder.isBolting = false;
                holder.boltTimestamp = -1L;
            }

            return true;
        }

        return false;
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

        return Math.max(effectiveRange, 8.0f);
    }

    private void popAmmoBox() {
        for (int i = 0; i < attachedAmmoBoxes.size(); i++) {
            ItemStack stack = attachedAmmoBoxes.get(i);
            if (!stack.isEmpty()) {
                Block.popResource(this.level, this.worldPosition, stack);
                attachedAmmoBoxes.set(i, ItemStack.EMPTY);
            }
        }
        this.setChanged();
        this.sendData();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {

        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {

        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.read(tag, registries, true);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean superResult = super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof IGun)) {
            return superResult;
        }
        addSentryGunTooltip(tooltip, heldItem);
        return true;
    }

    private void addSentryGunTooltip(java.util.List<Component> tooltip, ItemStack heldItem) {
        if (!(heldItem.getItem() instanceof com.tacz.guns.api.item.IGun iGun)) return;

        Component indent = Component.literal("    ");
        tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.firepower").withStyle(ChatFormatting.GRAY)));

        ResourceLocation gunId = iGun.getGunId(heldItem);
        Optional<CommonGunIndex> gunIndexOpt = TimelessAPI.getCommonGunIndex(gunId);

        if (gunIndexOpt.isEmpty()) return;
        GunData gunData = gunIndexOpt.get().getGunData();

        int currentAmmo = iGun.getCurrentAmmoCount(heldItem);
        if (iGun.useInventoryAmmo(heldItem)) {
            currentAmmo = 0;
        }
        int totalAmmo = currentAmmo;
        boolean isInfinite = false;
        ResourceLocation requiredAmmoId = gunData.getAmmoId();

        for (ItemStack box : attachedAmmoBoxes) {
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
            tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.ammo")
                    .withStyle(net.minecraft.ChatFormatting.GOLD)
                    .append(Component.literal(" ∞")
                            .withStyle(ChatFormatting.AQUA))));
        } else if (isAbnormalAmmo) {
            tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.ammo")
                    .withStyle(net.minecraft.ChatFormatting.GOLD)
                    .append(Component.literal(" /")
                            .withStyle(ChatFormatting.GRAY))));
        } else {
            tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.ammo")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" " + totalAmmo)
                            .withStyle(ChatFormatting.AQUA))));
        }

        tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.gun")
                .withStyle(ChatFormatting.GOLD)
                .append(heldItem.getHoverName().copy()
                        .withStyle(ChatFormatting.WHITE))));

        if (gunData.hasHeatData()) {
            float currentHeat = iGun.getHeatAmount(heldItem);
            float maxHeat = gunData.getHeatData().getHeatMax();

            int totalBars = 10;
            int filledBars = (int) ((currentHeat / maxHeat) * totalBars);
            filledBars = Math.max(0, Math.min(filledBars, totalBars));

            StringBuilder barBuilder = new StringBuilder("[");
            for (int i = 0; i < totalBars; i++) {
                barBuilder.append(i < filledBars ? "▌" : " ");
            }
            barBuilder.append("]");

            ChatFormatting color = ChatFormatting.GREEN;
            if ((float)filledBars / totalBars > 0.75) color = ChatFormatting.RED;
            else if ((float)filledBars / totalBars > 0.4) color = ChatFormatting.YELLOW;

            tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.heat")
                    .withStyle(net.minecraft.ChatFormatting.GOLD)
                    .append(Component.literal(barBuilder.toString())
                            .withStyle(color))
                    .append(Component.literal(String.format(" %.0f/%.0f", currentHeat, maxHeat))
                            .withStyle(net.minecraft.ChatFormatting.GRAY))));
        }

        double range = this.getSentryRange();
        String rangeStr = String.format("%.1f", range);
        tooltip.add(indent.copy().append(net.minecraft.network.chat.Component.translatable("sentry.tooltip.range")
                .withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(net.minecraft.network.chat.Component.literal(rangeStr)
                        .withStyle(net.minecraft.ChatFormatting.GREEN))));

        MutableComponent statusComponent = Component.translatable("sentry.status.unknown");
        ChatFormatting statusColor = ChatFormatting.GRAY;
        switch (this.currentStatus) {
            case IDLE -> {
                statusComponent = Component.translatable("sentry.status.idle");
                statusColor = ChatFormatting.GREEN;
            }
            case SHOOTING -> {
                statusComponent = Component.translatable("sentry.status.shooting");
                statusColor = ChatFormatting.RED;
            }
            case CHARGING -> {
                statusComponent = Component.translatable("sentry.status.charging");
                statusColor = ChatFormatting.GOLD;
            }
            case BOLTING -> {
                statusComponent = Component.translatable("sentry.status.bolting");
                statusColor = ChatFormatting.YELLOW;
            }
            case RELOADING -> {
                statusComponent = Component.translatable("sentry.status.reloading");
                statusColor = ChatFormatting.YELLOW;
            }
            case COOLING -> {
                statusComponent = Component.translatable("sentry.status.cooling");
                statusColor = ChatFormatting.AQUA;
            }
            case NO_AMMO -> {
                statusComponent = Component.translatable("sentry.status.no_ammo");
                statusColor = ChatFormatting.DARK_RED;
            }
        }

        tooltip.add(indent.copy().append(Component.translatable("sentry.tooltip.status")
                .withStyle(ChatFormatting.GRAY)
                .append(statusComponent.withStyle(statusColor))));
    }

    public void triggerShootEffects() {
        this.lastShootTime = System.currentTimeMillis();
        this.shouldEjectShell = true;
        this.lowerArmRecoilOffset += 8.0f;
        if (this.lowerArmRecoilOffset > 18.0f) {
            this.lowerArmRecoilOffset = 18.0f;
        }
        float currentHead = headAngle.getValue();
        float targetHead = headAngle.getChaseTarget();

        if (currentHead > targetHead - 2.0f) {
            headAngle.setValue(currentHead - 0.5f);
        }

    }

    public void updateAmmoFromPacket(int slotIndex, CompoundTag newTag) {
        if (slotIndex == -1) {

            if (!heldItem.isEmpty()) {
                ItemNBTHelper.setTag(heldItem, newTag);
            }
        } else if (slotIndex >= 0 && slotIndex < attachedAmmoBoxes.size()) {

            ItemStack box = attachedAmmoBoxes.get(slotIndex);
            if (!box.isEmpty()) {
                ItemNBTHelper.setTag(box, newTag);
            }
        }
    }

    private boolean isBlockVisible(Vec3 start, BlockPos targetPos) {
        Vec3 end = Vec3.atCenterOf(targetPos);

        if (isInSableSubLevel()) {
            Vec3 subStart = worldToSubLevel(start);
            Vec3 subEnd = worldToSubLevel(end);
            if (subStart.distanceToSqr(subEnd) > 1000000.0) return false;
            BlockHitResult result = this.level.clip(new net.minecraft.world.level.ClipContext(
                    subStart, subEnd,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    CollisionContext.empty()
            ));
            if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return true;
            if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                if (result.getBlockPos().equals(targetPos)) return true;
            }
            return false;
        }

        BlockHitResult result = this.level.clip(new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                SentryFakePlayer.get(this)
        ));


        if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return true;
        }

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos hitPos = result.getBlockPos();

            if (hitPos.equals(targetPos)) {
                return true;
            }
        }

        return false;
    }

    private void updateClientTarget() {

        if (syncedTargetId == -1) {
            this.cachedTarget = null;
            return;
        }

        if (this.cachedTarget != null && this.cachedTarget.getId() == syncedTargetId) {
            if (!this.cachedTarget.isAlive() || this.cachedTarget.isRemoved()) {
                this.cachedTarget = null;
            }
            return;
        }

        net.minecraft.world.entity.Entity entity = this.level.getEntity(syncedTargetId);
        if (entity instanceof LivingEntity living) {
            this.cachedTarget = living;
        } else {

            this.cachedTarget = null;
        }
    }

    private void updateRangeScrollBounds() {
        if (this.rangeScroll == null) return;

        if (this.level instanceof VirtualRenderWorld) {
            return;
        }

        ItemStack stack = getHeldItem();
        if (stack == null) stack = ItemStack.EMPTY;
        int min = 1;
        int max = 2;
        int smartDefault = 1;
        if (!stack.isEmpty() && stack.getItem() instanceof IGun iGun) {
            Optional<CommonGunIndex> indexOpt = TimelessAPI.getCommonGunIndex(iGun.getGunId(stack));
            if (indexOpt.isPresent()) {
                float effRange = calculateEffectiveRange(indexOpt.get().getGunData());
                int calculatedMax = Math.round(effRange * 2);
                max = Math.min(calculatedMax, 256);
                if (max < 4) max = 4;
                smartDefault = Math.min(Math.round(effRange * 1.5f), 256);
            }
        }else {
            rangeScroll.between(0, 0);
            rangeScroll.setValue(0);
            return;
        }

        rangeScroll.between(min, max);
        int currentValue = rangeScroll.getValue();

        if (currentValue == 0) {
            rangeScroll.setValue(smartDefault);
        }

        else if (currentValue < min) {
            rangeScroll.setValue(min);
        }
        else if (currentValue > max) {
            rangeScroll.setValue(max);
        }
    }

    private void sendActionPacket(SentryShootPacket.ActionType type) {
        FakePlayer fakePlayer = SentryFakePlayer.get(this);
        if (fakePlayer == null) return;
        Vec3 pos = fakePlayer.getEyePosition();
        NetworkHandler.sendToNearby(
                new SentryShootPacket(
                        this.worldPosition, -1, ItemNBTHelper.getOrCreateTag(heldItem), pos, pos, type),
                this.level, this.worldPosition);
    }

    private void sentryIdleScanning() {
        if (!this.level.isClientSide) {
            if (idleScanTimer-- <= 0) {
                this.idleTargetYaw = this.level.random.nextFloat() * 1800f;
                this.idleTargetPitch = (this.level.random.nextFloat() * 30f) - 15f;
                this.idleScanTimer = 80 + this.level.random.nextInt(60);
                this.sendData();
            }
        }

        float animSpeed = getAnimationSpeed(0.1f);

        float currentBase = baseAngle.getValue();
        float diffYaw = idleTargetYaw - currentBase;
        while (diffYaw < -180) diffYaw += 360;
        while (diffYaw > 180) diffYaw -= 360;

        baseAngle.chase(currentBase + diffYaw, animSpeed, LerpedFloat.Chaser.EXP);
        headAngle.chase(idleTargetPitch, animSpeed, LerpedFloat.Chaser.EXP);
        lowerArmAngle.chase(135f, animSpeed, LerpedFloat.Chaser.EXP);
        upperArmAngle.chase(90f, animSpeed, LerpedFloat.Chaser.EXP);
    }
    private Vec2 calculateAngleBetween(Vec3 from, Vec3 to) {
        double diffX = to.x - from.x;
        double diffY = to.y - from.y;
        double diffZ = to.z - from.z;
        float yaw = (float) (Mth.atan2(diffZ, diffX) * (180D / Math.PI)) - 90.0F;
        double distHorizontal = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float pitch = (float) -(Mth.atan2(diffY, distHorizontal) * (180D / Math.PI));
        return new Vec2(yaw, pitch);
    }

    private Vec3 getLocalMuzzlePos() {
        Vec3 pos = Vec3.atBottomCenterOf(this.worldPosition).add(0, 1.5, 0);
        if (isCeiling()) {
            pos = pos.add(0, -4.0, 0);
        }
        return pos;
    }

    private Vec2 calculateTruthAngle(Vec3 targetPos) {
        Vec3 muzzlePos = getActualMuzzlePos();
        return calculateAngleBetween(muzzlePos, targetPos);
    }

    private void spawnDebugLine(Vec3 start, Vec3 end, org.joml.Vector3f color) {
        if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            double distance = start.distanceTo(end);
            Vec3 direction = end.subtract(start).normalize();
            for (double d = 0; d < distance; d += 0.25) {
                Vec3 pos = start.add(direction.scale(d));
                serverLevel.sendParticles(new net.minecraft.core.particles.DustParticleOptions(color, 0.5f),
                        pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }
        }
    }

    public boolean applyColor(DyeColor colorIn) {
        if (colorIn == null) {
            if (this.color.isEmpty()) return false;
            this.color = Optional.empty();
        } else {
            if (this.color.isPresent() && this.color.get() == colorIn) return false;
            this.color = Optional.of(colorIn);
        }

        setChanged();
        sendData();
        return true;
    }

    private void setStatus(SentryStatus newStatus) {
        if (this.currentStatus != newStatus) {
            this.currentStatus = newStatus;

            this.setChanged();

            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
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
        this.setChanged();
        this.syncTargetBlock();
    }

    public BlockPos getConnectedFireControl() {
        return connectedFireControlPos;
    }

    public boolean isCeiling() {
        if (this.level == null) return false;
        BlockState state = this.getBlockState();
        return state.hasProperty(SentryArmBlock.CEILING) && state.getValue(SentryArmBlock.CEILING);
    }


    private String getLuaDataSnapshot(org.luaj.vm2.LuaValue luaValue) {
        if (luaValue == null || luaValue.isnil()) {
            return "nil";
        }

        if (luaValue.istable()) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");

            org.luaj.vm2.LuaValue k = org.luaj.vm2.LuaValue.NIL;
            while (true) {
                org.luaj.vm2.Varargs n = luaValue.next(k);
                if ((k = n.arg1()).isnil()) break;
                org.luaj.vm2.LuaValue v = n.arg(2);

                sb.append(k.toString()).append(":").append(v.toString()).append(",");
            }

            sb.append("}");
            return sb.toString();
        }

        return luaValue.toString();
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        heldItem = ItemStack.EMPTY;
        if (registries != null) {
            if (compound.contains("SentryHeldItem")) {
                heldItem = ItemStack.parseOptional(registries, compound.getCompound("SentryHeldItem"));
            }
            for (int i = 0; i < attachedAmmoBoxes.size(); i++) {
                attachedAmmoBoxes.set(i, ItemStack.EMPTY);
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

                baseAngle.setValue(angles.getFloat("Base"));
                lowerArmAngle.setValue(angles.getFloat("Lower"));
                upperArmAngle.setValue(angles.getFloat("Upper"));
                headAngle.setValue(angles.getFloat("Head"));
            } else {

                boolean isCombatMode = (this.syncedTargetId != -1);

                if (isCombatMode) {
                    float speed = 0.25f;
                    baseAngle.chase(angles.getFloat("Base"), speed, LerpedFloat.Chaser.EXP);
                    lowerArmAngle.chase(angles.getFloat("Lower"), speed, LerpedFloat.Chaser.EXP);
                    upperArmAngle.chase(angles.getFloat("Upper"), speed, LerpedFloat.Chaser.EXP);
                    headAngle.chase(angles.getFloat("Head"), speed, LerpedFloat.Chaser.EXP);
                } else {
                    float serverBase = angles.getFloat("Base");
                    if (Math.abs(baseAngle.getValue() - serverBase) > 10f) {
                        baseAngle.setValue(serverBase);
                    }
                }
            }
        }

        if (compound.contains("TargetBlock")) {
            this.cachedTargetBlock = NbtUtils.readBlockPos(compound, "TargetBlock").orElse(null);
        } else {
            this.cachedTargetBlock = null;
        }
        if (compound.contains("FireControlPos")) {
            this.connectedFireControlPos = NbtUtils.readBlockPos(compound, "FireControlPos").orElse(null);
        } else {
            this.connectedFireControlPos = null;
        }

        this.idleTargetYaw = compound.getFloat("IdleTargetYaw");
        this.idleTargetPitch = compound.getFloat("IdleTargetPitch");
        this.idleScanTimer = compound.getInt("IdleScanTimer");

        if (compound.contains("Dye")) {
            color = Optional.of(NBTHelper.readEnum(compound, "Dye", DyeColor.class));
        } else {
            color = Optional.empty();
        }
        if (compound.contains("SentryStatus")) {
            int statusIdx = compound.getInt("SentryStatus");
            if (statusIdx >= 0 && statusIdx < SentryStatus.values().length) {
                this.currentStatus = SentryStatus.values()[statusIdx];
            }
        }
        updateRangeScrollBounds();
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        if (registries != null) {
            if (!heldItem.isEmpty()) {
                compound.put("SentryHeldItem", heldItem.save(registries, new CompoundTag()));
            }
            CompoundTag ammoTag = new CompoundTag();
            ContainerHelper.saveAllItems(ammoTag, this.attachedAmmoBoxes, registries);
            compound.put("SentryAmmoBoxes", ammoTag);
        }
        compound.putInt("TargetId", this.syncedTargetId);
        CompoundTag angles = new CompoundTag();
        angles.putFloat("Base", baseAngle.getValue());
        angles.putFloat("Lower", lowerArmAngle.getValue());
        angles.putFloat("Upper", upperArmAngle.getValue());
        angles.putFloat("Head", headAngle.getValue());
        compound.put("Angles", angles);
        compound.putFloat("IdleTargetYaw", idleTargetYaw);
        compound.putFloat("IdleTargetPitch", idleTargetPitch);
        compound.putInt("IdleScanTimer", idleScanTimer);
        if (this.connectedFireControlPos != null) {
            compound.put("FireControlPos", NbtUtils.writeBlockPos(this.connectedFireControlPos));
        }
        if (this.cachedTargetBlock != null) {
            compound.put("TargetBlock", NbtUtils.writeBlockPos(this.cachedTargetBlock));
        }
        color.ifPresent(dyeColor -> NBTHelper.writeEnum(compound, "Dye", dyeColor));
        compound.putInt("SentryStatus", this.currentStatus.ordinal());
    }

    public IItemHandler getItemHandler() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return attachedAmmoBoxes.size();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                if (slot < 0 || slot >= getSlots()) return ItemStack.EMPTY;
                return attachedAmmoBoxes.get(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!(stack.getItem() instanceof IAmmoBox iBox)) {
                    return stack;
                }
                if (!attachedAmmoBoxes.get(slot).isEmpty()) {
                    return stack;
                }
                ItemStack gun = getHeldItem();
                if (gun.isEmpty() || !(gun.getItem() instanceof IGun)) {
                    return stack;
                }
                if (!iBox.isAmmoBoxOfGun(gun, stack)) {
                    return stack;
                }
                if (!simulate) {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    attachedAmmoBoxes.set(slot, copy);
                    setChanged();
                    sendData();
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(1);
                return remainder;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack inSlot = getStackInSlot(slot);
                if (inSlot.isEmpty()) return ItemStack.EMPTY;
                if (inSlot.getItem() instanceof IAmmoBox iBox) {
                    if (iBox.isCreative(inSlot) || iBox.getAmmoCount(inSlot) > 0) {
                        return ItemStack.EMPTY;
                    }
                }
                int extractCount = Math.min(inSlot.getCount(), amount);
                if (extractCount <= 0) return ItemStack.EMPTY;
                ItemStack extracted = inSlot.copy();
                extracted.setCount(extractCount);
                if (!simulate) {
                    inSlot.shrink(extractCount);
                    if (inSlot.isEmpty()) {
                        attachedAmmoBoxes.set(slot, ItemStack.EMPTY);
                    }
                    setChanged();
                    sendData();
                }
                return extracted;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem() instanceof IAmmoBox;
            }

            public void setStackInSlot(int slot, ItemStack stack) {
                if (slot >= 0 && slot < attachedAmmoBoxes.size()) {
                    attachedAmmoBoxes.set(slot, stack);
                    setChanged();
                    sendData();
                }
            }
        };
    }

    private class SentryValueBoxTransform extends ValueBoxTransform.Sided {

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return !direction.getAxis().isVertical();
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            boolean isCeiling = state.getValue(SentryArmBlock.CEILING);
            int yPos = isCeiling ? 16 - 3 : 3;
            Vec3 location = VecHelper.voxelSpace(8, yPos, 15.5);
            location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(getSide()), Direction.Axis.Y);

            return location;
        }
        @Override
        protected Vec3 getSouthLocation() {
            return Vec3.ZERO;
        }

        @Override
        public float getScale() {
            return 0.5f;
        }
    }

}
package euphy.upo.sentrymechanicalarm.util;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class SentryFakePlayer {

    private static final WeakHashMap<SentryArmBlockEntity, FakePlayer> FAKE_PLAYERS = new WeakHashMap<>();
    private static final WeakHashMap<FakePlayer, SentryArmBlockEntity> REVERSE_MAP = new WeakHashMap<>();
    private static final Map<String, RobustFakePlayer> CONTRAPTION_FAKE_PLAYERS = new HashMap<>();

    private static final java.util.Map<FakePlayer, Boolean> FIRED_TRACKER = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    public static void markFired(FakePlayer fp) {
        FIRED_TRACKER.put(fp, true);
    }

    public static boolean checkAndClearFired(FakePlayer fp) {
        return FIRED_TRACKER.remove(fp) != null;
    }

    private static class RobustFakePlayer extends FakePlayer {
        private boolean fakeCreativeMode = false;

        public RobustFakePlayer(ServerLevel level, GameProfile name) {
            super(level, name);
        }

        public void setLevelPublic(ServerLevel level) {
            super.setLevel(level);
        }

        public void setFakeCreative(boolean active) {
            this.fakeCreativeMode = active;
        }

        @Override
        public boolean isCreative() {
            return this.fakeCreativeMode || super.isCreative();
        }

        // justlevelingfork Compat
    }

    public static void setTempCreative(FakePlayer fp, boolean active) {
        if (fp instanceof RobustFakePlayer robust) {
            robust.setFakeCreative(active);
        } else {
            if (active) fp.setGameMode(GameType.CREATIVE);
            else fp.setGameMode(GameType.SURVIVAL);
        }
    }

    public static FakePlayer get(SentryArmBlockEntity arm) {
        if (!(arm.getLevel() instanceof ServerLevel serverLevel)) return null;
        FakePlayer fp = FAKE_PLAYERS.computeIfAbsent(arm, k -> {
            String name = "Sentry_" + arm.getBlockPos().getX() + "_" + arm.getBlockPos().getY() + "_" + arm.getBlockPos().getZ();
            return createRobustFakePlayer(serverLevel, name);
        });
        REVERSE_MAP.put(fp, arm);
        return fp;
    }

    public static SentryArmBlockEntity getArmFromPlayer(FakePlayer fp) {
        return REVERSE_MAP.get(fp);
    }

    public static FakePlayer getForContraption(ServerLevel level, UUID contraptionUUID, BlockPos localPos) {
        String key = contraptionUUID.toString() + "_" + localPos.asLong();

        RobustFakePlayer existing = CONTRAPTION_FAKE_PLAYERS.get(key);

        if (existing != null) {
            if (existing.level() != level) {
                existing.setLevelPublic(level);
            }
            return existing;
        }

        String name = "SentryC_" + Math.abs(key.hashCode());
        RobustFakePlayer newPlayer = createRobustFakePlayer(level, name);
        CONTRAPTION_FAKE_PLAYERS.put(key, newPlayer);
        return newPlayer;
    }

    public static void removeForContraption(UUID contraptionUUID, BlockPos localPos) {
        String key = contraptionUUID.toString() + "_" + localPos.asLong();
        FakePlayer removed = CONTRAPTION_FAKE_PLAYERS.remove(key);
        if (removed != null) {
            removed.discard();
        }
    }

    private static RobustFakePlayer createRobustFakePlayer(ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name);

        RobustFakePlayer fp = new RobustFakePlayer(level, profile);
        fp.setGameMode(GameType.SURVIVAL);
        fp.setNoGravity(true);
        fp.setInvisible(true);
        fp.setInvulnerable(true);

        if (fp.connection == null) {
            try {
                net.minecraft.network.Connection fakeConnection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
                fp.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), fakeConnection, fp, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
                    @Override
                    public void send(net.minecraft.network.protocol.Packet<?> packet) {
                    }
                };
            } catch (Exception e) {
                euphy.upo.sentrymechanicalarm.SentryMechanicalArm.LOGGER.error("Failed to mock connection for {}", name, e);
            }
        }

        return fp;
    }

    public static void sync(FakePlayer fp, SentryArmBlockEntity arm, float yaw, float pitch, ItemStack gunStack) {
        boolean isVirtual = arm instanceof VirtualSentryArmBlockEntity;

        if (!isVirtual) {
            Vec3 worldPos;
            if (arm.isInSableSubLevel()) {
                Vec3 localFeet = Vec3.atBottomCenterOf(arm.getBlockPos()).add(0, 0.8, 0);
                worldPos = AeronauticsHelper.sableSubLevelToWorld(arm.getLevel(), localFeet);
            } else if (AeronauticsHelper.isAeronauticsLoaded()) {
                worldPos = arm.getProjectedMuzzlePos();
            } else {
                net.minecraft.world.level.block.state.BlockState state = arm.getBlockState();
                boolean isCeiling = false;
                if (state.hasProperty(SentryArmBlock.CEILING)) {
                    isCeiling = state.getValue(SentryArmBlock.CEILING);
                }
                double x = arm.getBlockPos().getX() + 0.5;
                double yOffset = isCeiling ? -2.8 : 1.0;
                double y = arm.getBlockPos().getY() + yOffset;
                double z = arm.getBlockPos().getZ() + 0.5;
                worldPos = new Vec3(x, y, z);
            }
            fp.setPos(worldPos.x, worldPos.y, worldPos.z);
            fp.xo = worldPos.x; fp.yo = worldPos.y; fp.zo = worldPos.z;
            fp.xOld = worldPos.x; fp.yOld = worldPos.y; fp.zOld = worldPos.z;
        }

        fp.setYRot(yaw);
        fp.setXRot(pitch);
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
            operator.getDataHolder().drawTimestamp = System.currentTimeMillis() - 10000;

        } else {
            if (ItemNBTHelper.hasTag(gunStack)) {
                if (!java.util.Objects.equals(ItemNBTHelper.getTag(currentFakeItem), ItemNBTHelper.getTag(gunStack))) {
                    ItemNBTHelper.setTag(currentFakeItem, ItemNBTHelper.getTag(gunStack));
                }
            } else {
                if (ItemNBTHelper.hasTag(currentFakeItem)) currentFakeItem.remove(DataComponents.CUSTOM_DATA);
            }
            if (currentFakeItem.getCount() != gunStack.getCount()) {
                currentFakeItem.setCount(gunStack.getCount());
            }
        }

        if (iGunTarget != null) {
            for (int i = 0; i < arm.attachedAmmoBoxes.size(); i++) {
                ItemStack box = arm.attachedAmmoBoxes.get(i);
                fp.getInventory().setItem(9 + i, box.copy());
            }
        }
    }
}
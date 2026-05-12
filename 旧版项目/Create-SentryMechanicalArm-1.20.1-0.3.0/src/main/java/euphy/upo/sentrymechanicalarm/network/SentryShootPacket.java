package euphy.upo.sentrymechanicalarm.network;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.util.ArmSoundHelper;
import euphy.upo.sentrymechanicalarm.util.SentryTrailManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public class SentryShootPacket {
    public enum ActionType {
        SHOOT,
        CHARGE,
        BOLT,
        RELOAD_TACTICAL,
        RELOAD_EMPTY
    }

    private final BlockPos pos;
    private final int slotIndex;
    private final CompoundTag itemTag;
    private final Vec3 realStart;
    private final Vec3 realEnd;
    private final ActionType actionType;

    public SentryShootPacket(BlockPos pos, int slotIndex, CompoundTag itemTag, Vec3 realStart, Vec3 realEnd) {
        this(pos, slotIndex, itemTag, realStart, realEnd, ActionType.SHOOT);
    }

    public SentryShootPacket(BlockPos pos, int slotIndex, CompoundTag itemTag, Vec3 realStart, Vec3 realEnd, ActionType actionType) {
        this.pos = pos;
        this.slotIndex = slotIndex;
        this.itemTag = itemTag;
        this.realStart = realStart;
        this.realEnd = realEnd;
        this.actionType = actionType;
    }

    public static void encode(SentryShootPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.pos);
        buffer.writeInt(msg.slotIndex);
        buffer.writeNbt(msg.itemTag);
        buffer.writeDouble(msg.realStart.x);
        buffer.writeDouble(msg.realStart.y);
        buffer.writeDouble(msg.realStart.z);
        buffer.writeDouble(msg.realEnd.x);
        buffer.writeDouble(msg.realEnd.y);
        buffer.writeDouble(msg.realEnd.z);
        buffer.writeEnum(msg.actionType);
    }

    public static SentryShootPacket decode(FriendlyByteBuf buffer) {
        return new SentryShootPacket(
                buffer.readBlockPos(),
                buffer.readInt(),
                buffer.readNbt(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readEnum(ActionType.class)
        );
    }

    public static void handle(SentryShootPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePacket(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handlePacket(SentryShootPacket msg) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;

            BlockEntity be = mc.level.getBlockEntity(msg.pos);
            if (be instanceof SentryArmBlockEntity sentry) {
                sentry.updateAmmoFromPacket(msg.slotIndex, msg.itemTag);
                ItemStack gunStack = sentry.getHeldItem();

                if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun iGun)) {
                    return;
                }

                Level level = mc.level;
                Vec3 center = sentry.getBlockPos().getCenter();

                net.minecraft.world.entity.LivingEntity dummyEntity = new net.minecraft.world.entity.decoration.ArmorStand(level, center.x, center.y, center.z);
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

                switch (msg.actionType) {
                    case CHARGE -> {
                        ArmSoundHelper.playAnimationSound(gunStack, level, center, "charge", "warmup", "build", "charge_start");
                    }

                    case BOLT -> {
                        SoundPlayManager.playBoltSound(dummyEntity, display);
                    }

                    case RELOAD_EMPTY -> {
                        SoundPlayManager.playReloadSound(dummyEntity, display, true);
                    }

                    case RELOAD_TACTICAL -> {
                        SoundPlayManager.playReloadSound(dummyEntity, display, false);
                    }

                    case SHOOT -> {
                        sentry.triggerShootEffects();

                        TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack)).ifPresent(index -> {
                            euphy.upo.sentrymechanicalarm.util.ArmSoundHelper.playFireEffects(
                                    null, level, center, new Vec3(0,0,0), 0, gunStack, index.getGunData()
                            );
                        });

                        Vec3 direction = msg.realEnd.subtract(msg.realStart).normalize();
                        double totalDistance = msg.realStart.distanceTo(msg.realEnd);
                        double offsetDistance = 0.8;
                        Vec3 adjustedStart = totalDistance > offsetDistance
                                ? msg.realStart.add(direction.scale(offsetDistance))
                                : msg.realStart;
                        double adjustedDist = totalDistance > offsetDistance
                                ? totalDistance - offsetDistance
                                : totalDistance;

                        SentryTrailManager.addTracer(adjustedStart, direction, 8.0, 2.0, adjustedDist);
                    }
                }
            }
        }
    }
}
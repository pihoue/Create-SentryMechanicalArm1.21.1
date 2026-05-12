package euphy.upo.sentrymechanicalarm.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class SentryTargetSavedData extends SavedData {
    private static final String DATA_NAME = "tacz_sentry_target_blocks";

    private final Set<BlockPos> targetBlocks = new HashSet<>();


    public static SentryTargetSavedData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getDataStorage().computeIfAbsent(
                    SentryTargetSavedData::load,
                    SentryTargetSavedData::new,
                    DATA_NAME
            );
        }
        throw new RuntimeException("Client side cannot access SavedData");
    }

    public void addTarget(BlockPos pos) {
        if (targetBlocks.add(pos)) {
            setDirty();
        }
    }

    public void removeTarget(BlockPos pos) {
        if (targetBlocks.remove(pos)) {
            setDirty();
        }
    }

    public Set<BlockPos> getTargets() {
        return targetBlocks;
    }

    public static SentryTargetSavedData load(CompoundTag nbt) {
        SentryTargetSavedData data = new SentryTargetSavedData();
        ListTag list = nbt.getList("Targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.targetBlocks.add(NbtUtils.readBlockPos(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (BlockPos pos : targetBlocks) {
            list.add(NbtUtils.writeBlockPos(pos));
        }
        nbt.put("Targets", list);
        return nbt;
    }
}
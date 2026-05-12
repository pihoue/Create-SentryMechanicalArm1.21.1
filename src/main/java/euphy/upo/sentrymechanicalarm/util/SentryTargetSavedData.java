package euphy.upo.sentrymechanicalarm.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;

import java.util.HashSet;
import java.util.Set;

public class SentryTargetSavedData extends SavedData {
    private static final String DATA_NAME = "tacz_sentry_target_blocks";

    private final Set<BlockPos> targetBlocks = new HashSet<>();


    public static SentryTargetSavedData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getDataStorage().computeIfAbsent(
                    new net.minecraft.world.level.saveddata.SavedData.Factory<>(SentryTargetSavedData::new, SentryTargetSavedData::load),
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

    public static SentryTargetSavedData load(CompoundTag nbt, HolderLookup.Provider provider) {
        SentryTargetSavedData data = new SentryTargetSavedData();
        ListTag list = nbt.getList("Targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.targetBlocks.add(BlockPos.of(list.getCompound(i).getLong("Pos")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (BlockPos pos : targetBlocks) {
            CompoundTag compound = new CompoundTag();
            compound.putLong("Pos", pos.asLong());
            list.add(compound);
        }
        nbt.put("Targets", list);
        return nbt;
    }
}
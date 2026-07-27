package euphy.upo.sentrymechanicalarm.util;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public class SentryTargetSavedData extends SavedData {
   private static final String DATA_NAME = "tacz_sentry_target_blocks";
   private final Set<BlockPos> targetBlocks = new HashSet<>();

   public static SentryTargetSavedData get(Level level) {
      if (level instanceof ServerLevel serverLevel) {
         return (SentryTargetSavedData)serverLevel.getDataStorage()
            .computeIfAbsent(new Factory<>(SentryTargetSavedData::new, SentryTargetSavedData::load), "tacz_sentry_target_blocks");
      } else {
         throw new RuntimeException("Client side cannot access SavedData");
      }
   }

   public void addTarget(BlockPos pos) {
      if (this.targetBlocks.add(pos)) {
         this.setDirty();
      }
   }

   public void removeTarget(BlockPos pos) {
      if (this.targetBlocks.remove(pos)) {
         this.setDirty();
      }
   }

   public Set<BlockPos> getTargets() {
      return this.targetBlocks;
   }

   public static SentryTargetSavedData load(CompoundTag nbt, Provider provider) {
      SentryTargetSavedData data = new SentryTargetSavedData();
      ListTag list = nbt.getList("Targets", 10);

      for (int i = 0; i < list.size(); i++) {
         data.targetBlocks.add(BlockPos.of(list.getCompound(i).getLong("Pos")));
      }

      return data;
   }

   public CompoundTag save(CompoundTag nbt, Provider provider) {
      ListTag list = new ListTag();

      for (BlockPos pos : this.targetBlocks) {
         CompoundTag compound = new CompoundTag();
         compound.putLong("Pos", pos.asLong());
         list.add(compound);
      }

      nbt.put("Targets", list);
      return nbt;
   }
}

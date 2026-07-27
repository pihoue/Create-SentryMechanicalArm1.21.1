package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

public class BlazeFireControlBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
   private static final String[] EMOTICONS = new String[]{"(OwO)", "(>_<)", "^_^", "(='X'=)", "(*^▽^*)", "(¬_¬ )", "(ToT)", "(o_o)"};
   public String currentEmoticon = "";
   public int emoticonTimer = 0;
   public final int MAX_EMOTICON_TIME = 60;
   public int msgColor = 16777215;
   public float msgOffsetX = 0.0F;
   public float msgOffsetZ = 0.0F;
   public final LerpedFloat headAngle = LerpedFloat.angular();
   public final LerpedFloat headAnimation = LerpedFloat.linear();
   public final ItemStackHandler inventory = new ItemStackHandler(1) {
      public boolean isItemValid(int slot, @NotNull ItemStack stack) {
         return stack.getItem() instanceof FireControlClipboardItem;
      }

      protected void onContentsChanged(int slot) {
         BlazeFireControlBlockEntity.this.notifyUpdate();
         if (BlazeFireControlBlockEntity.this.level != null && !BlazeFireControlBlockEntity.this.level.isClientSide) {
            BlazeFireControlBlockEntity.this.notifyConnectedSentries(false);
         }
      }
   };
   private int focusedEntityId = -1;
   private int focusTimer = 0;
   public static final int FOCUS_DURATION = 600;
   private boolean hasBoundScope = false;
   private int markedEntityId = -1;
   private long[] sentryPositionData = new long[0];
   private long[] projectedSentryPositions = new long[0];
   private Vec3 markedWorldPos = null;
   private int markedContraptionEntityId = -1;
   private BlockPos markedLocalPos = null;
   private boolean isSableMarked = false;
   private Vec3 sableMarkedLocalPos = null;

   public boolean hasBoundScope() {
      return this.hasBoundScope;
   }

   public void setHasBoundScope(boolean val) {
      if (this.hasBoundScope != val) {
         this.hasBoundScope = val;
         this.setChanged();
         this.sendData();
      }
   }

   public int getFocusedEntityId() {
      return this.focusTimer > 0 ? this.focusedEntityId : -1;
   }

   public void setFocusedEntity(int entityId) {
      this.focusedEntityId = entityId;
      this.focusTimer = 600;
      this.setChanged();
      this.sendData();
   }

   public Set<Integer> getMarkedEntityIds() {
      return this.markedEntityId == -1 ? Collections.emptySet() : Collections.singleton(this.markedEntityId);
   }

   public void setMarkedEntityId(int entityId) {
      this.markedEntityId = entityId;
      this.clearMarkedPos();
      this.setChanged();
      this.sendData();
   }

   public void clearMarkedEntity() {
      if (this.markedEntityId != -1) {
         this.markedEntityId = -1;
         this.setChanged();
         this.sendData();
      }
   }

   public Vec3 getMarkedWorldPos() {
      return this.markedWorldPos;
   }

   public int getMarkedContraptionEntityId() {
      return this.markedContraptionEntityId;
   }

   public BlockPos getMarkedLocalPos() {
      return this.markedLocalPos;
   }

   public boolean isSableMarked() {
      return this.isSableMarked;
   }

   public Vec3 getSableMarkedLocalPos() {
      return this.sableMarkedLocalPos;
   }

   public void setMarkedPos(Vec3 worldPos, int contraptionEntityId, BlockPos localPos) {
      this.setMarkedPos(worldPos, contraptionEntityId, localPos, false, null);
   }

   public void setMarkedPos(Vec3 worldPos, int contraptionEntityId, BlockPos localPos, boolean isSable, Vec3 sableLocal) {
      this.markedWorldPos = worldPos;
      this.markedContraptionEntityId = contraptionEntityId;
      this.markedLocalPos = localPos;
      this.isSableMarked = isSable;
      this.sableMarkedLocalPos = isSable ? sableLocal : null;
      this.clearMarkedEntity();
      this.setChanged();
      this.sendData();
   }

   public void clearMarkedPos() {
      if (this.markedWorldPos != null) {
         this.markedWorldPos = null;
         this.markedContraptionEntityId = -1;
         this.markedLocalPos = null;
         this.isSableMarked = false;
         this.sableMarkedLocalPos = null;
         this.setChanged();
         this.sendData();
      }
   }

   public BlazeFireControlBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)SentryRegistry.BLAZE_FIRE_CONTROL_BE.get(), pos, state);
   }

   public IItemHandler getItemHandler() {
      return this.inventory;
   }

   public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
   }

   public void write(CompoundTag compound, Provider registries, boolean clientPacket) {
      super.write(compound, registries, clientPacket);
      compound.put("Inventory", this.inventory.serializeNBT(registries));
      compound.putString("Emoticon", this.currentEmoticon);
      compound.putInt("EmoticonTimer", this.emoticonTimer);
      compound.putFloat("MsgX", this.msgOffsetX);
      compound.putFloat("MsgZ", this.msgOffsetZ);
      compound.putInt("MsgColor", this.msgColor);
      compound.putInt("FocusedEntityId", this.focusedEntityId);
      compound.putInt("FocusTimer", this.focusTimer);
      compound.putBoolean("HasBoundScope", this.hasBoundScope);
      if (this.markedWorldPos != null) {
         compound.putDouble("MarkedPosX", this.markedWorldPos.x);
         compound.putDouble("MarkedPosY", this.markedWorldPos.y);
         compound.putDouble("MarkedPosZ", this.markedWorldPos.z);
         compound.putInt("MarkedContraptionId", this.markedContraptionEntityId);
         if (this.markedLocalPos != null) {
            compound.putLong("MarkedLocalPos", this.markedLocalPos.asLong());
         }

         if (this.isSableMarked && this.sableMarkedLocalPos != null) {
            compound.putBoolean("IsSableMarked", true);
            compound.putDouble("SableLocalX", this.sableMarkedLocalPos.x);
            compound.putDouble("SableLocalY", this.sableMarkedLocalPos.y);
            compound.putDouble("SableLocalZ", this.sableMarkedLocalPos.z);
         }
      }

      compound.putLongArray("SentryPositions", this.sentryPositionData);
      compound.putLongArray("ProjSentryPositions", this.projectedSentryPositions);
   }

   protected void read(CompoundTag compound, Provider registries, boolean clientPacket) {
      super.read(compound, registries, clientPacket);
      this.inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
      this.currentEmoticon = compound.getString("Emoticon");
      this.emoticonTimer = compound.getInt("EmoticonTimer");
      this.msgOffsetX = compound.getFloat("MsgX");
      this.msgOffsetZ = compound.getFloat("MsgZ");
      if (compound.contains("MsgColor")) {
         this.msgColor = compound.getInt("MsgColor");
      } else {
         this.msgColor = 16777215;
      }

      if (compound.contains("FocusedEntityId")) {
         this.focusedEntityId = compound.getInt("FocusedEntityId");
      }

      if (compound.contains("FocusTimer")) {
         this.focusTimer = compound.getInt("FocusTimer");
      }

      if (compound.contains("HasBoundScope")) {
         this.hasBoundScope = compound.getBoolean("HasBoundScope");
      }

      if (compound.contains("SentryPositions")) {
         this.sentryPositionData = compound.getLongArray("SentryPositions");
      } else {
         this.sentryPositionData = new long[0];
      }

      if (compound.contains("ProjSentryPositions")) {
         this.projectedSentryPositions = compound.getLongArray("ProjSentryPositions");
      } else {
         this.projectedSentryPositions = new long[0];
      }

      this.markedEntityId = -1;
      if (compound.contains("MarkedPosX")) {
         this.markedWorldPos = new Vec3(compound.getDouble("MarkedPosX"), compound.getDouble("MarkedPosY"), compound.getDouble("MarkedPosZ"));
         this.markedContraptionEntityId = compound.getInt("MarkedContraptionId");
         if (compound.contains("MarkedLocalPos")) {
            this.markedLocalPos = BlockPos.of(compound.getLong("MarkedLocalPos"));
         } else {
            this.markedLocalPos = null;
         }

         this.isSableMarked = compound.getBoolean("IsSableMarked");
         if (this.isSableMarked) {
            this.sableMarkedLocalPos = new Vec3(compound.getDouble("SableLocalX"), compound.getDouble("SableLocalY"), compound.getDouble("SableLocalZ"));
         } else {
            this.sableMarkedLocalPos = null;
         }
      } else {
         this.markedWorldPos = null;
         this.markedContraptionEntityId = -1;
         this.markedLocalPos = null;
         this.isSableMarked = false;
         this.sableMarkedLocalPos = null;
      }
   }

   public List<String> getTargetList() {
      List<String> targets = new ArrayList<>();
      ItemStack stack = this.inventory.getStackInSlot(0);
      CompoundTag tag = ItemNBTHelper.getTag(stack);
      if (!stack.isEmpty() && tag.contains("TargetList", 9)) {
         for (Tag t : tag.getList("TargetList", 8)) {
            targets.add(t.getAsString());
         }
      }

      return targets;
   }

   public void notifyConnectedSentries(boolean isRemoving) {
      if (this.level != null) {
         List<Long> found = new ArrayList<>();
         List<Long> projected = new ArrayList<>();
         BlockPos.betweenClosedStream(this.worldPosition.offset(-6, -6, -6), this.worldPosition.offset(6, 6, 6)).forEach(pos -> {
            if (this.level.getBlockEntity(pos) instanceof SentryArmBlockEntity sentry) {
               BlockPos connectedPos = sentry.getConnectedFireControl();
               if (connectedPos != null && connectedPos.equals(this.worldPosition)) {
                  if (isRemoving) {
                     sentry.disconnectFireControl();
                  } else {
                     sentry.updateFromFireControl();
                  }

                  found.add(pos.asLong());
                  Vec3 worldPos = AeronauticsHelper.sableSubLevelToWorld(this.level, Vec3.atCenterOf(pos));
                  projected.add(BlockPos.containing(worldPos).asLong());
               }
            }
         });
         this.sentryPositionData = isRemoving ? new long[0] : found.stream().mapToLong(l -> l).toArray();
         this.projectedSentryPositions = isRemoving ? new long[0] : projected.stream().mapToLong(l -> l).toArray();
         this.setChanged();
         this.sendData();
      }
   }

   public List<BlockPos> getSentryPositions() {
      List<BlockPos> result = new ArrayList<>(this.sentryPositionData.length);

      for (long l : this.sentryPositionData) {
         result.add(BlockPos.of(l));
      }

      return result;
   }

   public List<BlockPos> getProjectedSentryPositions() {
      List<BlockPos> result = new ArrayList<>(this.projectedSentryPositions.length);

      for (long l : this.projectedSentryPositions) {
         result.add(BlockPos.of(l));
      }

      return result;
   }

   public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
      ChatFormatting boundColor = this.hasBoundScope ? ChatFormatting.GREEN : ChatFormatting.GRAY;
      tooltip.add(
         Component.literal("    ")
            .append(Component.translatable("overlay.sentrymechanicalarm.scope_status").withStyle(ChatFormatting.GRAY))
            .append(
               Component.translatable(this.hasBoundScope ? "message.sentrymechanicalarm.scope_bound" : "message.sentrymechanicalarm.scope_not_bound")
                  .withStyle(boundColor)
            )
      );
      int sentryCount = this.sentryPositionData.length;
      tooltip.add(
         Component.literal("    ")
            .append(Component.translatable("overlay.sentrymechanicalarm.connected_sentries").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(sentryCount)).withStyle(ChatFormatting.AQUA))
      );
      if (!this.inventory.getStackInSlot(0).isEmpty()) {
         boolean whitelist = this.isWhitelist();
         tooltip.add(
            Component.literal("    ")
               .append(Component.translatable("overlay.sentrymechanicalarm.list_mode").withStyle(ChatFormatting.GRAY))
               .append(
                  Component.translatable(
                        whitelist ? "item.sentrymechanicalarm.fire_control_clipboard.whitelist" : "item.sentrymechanicalarm.fire_control_clipboard.blacklist"
                     )
                     .withStyle(whitelist ? ChatFormatting.GREEN : ChatFormatting.RED)
               )
         );
      }

      return true;
   }

   public void showRandomEmoticon() {
      if (this.level != null && !this.level.isClientSide) {
         this.currentEmoticon = EMOTICONS[this.level.random.nextInt(EMOTICONS.length)];
         this.emoticonTimer = 60;
         this.msgOffsetX = (this.level.random.nextFloat() - 0.5F) * 0.6F;
         this.msgOffsetZ = (this.level.random.nextFloat() - 0.5F) * 0.6F;
         this.msgColor = Mth.hsvToRgb(this.level.random.nextFloat(), 0.8F, 1.0F);
         this.notifyUpdate();
      }
   }

   @OnlyIn(Dist.CLIENT)
   protected void tickAnimation() {
      float target = 0.0F;
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && !player.isInvisible()) {
         double dx = player.getX() - ((double)this.getBlockPos().getX() + 0.5);
         double dz = player.getZ() - ((double)this.getBlockPos().getZ() + 0.5);
         target = AngleHelper.deg(-Mth.atan2(dz, dx)) - 90.0F;
      }

      target = this.headAngle.getValue() + AngleHelper.getShortestAngleDiff((double)this.headAngle.getValue(), (double)target);
      this.headAngle.chase((double)target, 0.25, Chaser.exp(5.0));
      this.headAngle.tickChaser();
      this.headAnimation.chase(0.0, 0.25, Chaser.exp(0.25));
      this.headAnimation.tickChaser();
   }

   @OnlyIn(Dist.CLIENT)
   protected void spawnIdleParticles() {
      RandomSource random = this.level.getRandom();
      if (random.nextInt(7) == 0) {
         this.level
            .addParticle(
               ParticleTypes.END_ROD,
               (double)this.worldPosition.getX() + 0.5 + random.nextGaussian() * 0.3,
               (double)this.worldPosition.getY() + 0.5 + random.nextGaussian() * 0.5,
               (double)this.worldPosition.getZ() + 0.5 + random.nextGaussian() * 0.3,
               0.0,
               0.0,
               0.0
            );
      }
   }

   public boolean isWhitelist() {
      ItemStack stack = this.inventory.getStackInSlot(0);
      if (!stack.isEmpty() && stack.getItem() instanceof FireControlClipboardItem) {
         CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
         return tag.getBoolean("WhitelistMode");
      } else {
         return false;
      }
   }

   public void tick() {
      super.tick();
      if (this.focusTimer > 0) {
         this.focusTimer--;
         if (this.focusTimer == 0) {
            this.focusedEntityId = -1;
            this.notifyConnectedSentries(false);
         }
      }

      if (this.emoticonTimer > 0) {
         this.emoticonTimer--;
      }

      if (!this.level.isClientSide && this.emoticonTimer == 0 && !this.currentEmoticon.isEmpty()) {
         this.currentEmoticon = "";
         this.notifyUpdate();
      }

      if (this.level.isClientSide) {
         this.tickAnimation();
         this.spawnIdleParticles();
      }
   }
}

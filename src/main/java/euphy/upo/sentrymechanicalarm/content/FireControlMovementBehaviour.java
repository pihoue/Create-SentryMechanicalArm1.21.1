package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.commons.lang3.tuple.MutablePair;

public class FireControlMovementBehaviour implements MovementBehaviour {
   public boolean isActive(MovementContext context) {
      return true;
   }

   public void startMoving(MovementContext context) {
      if (!context.data.contains("SyncedInventory") && context.blockEntityData != null && context.blockEntityData.contains("Inventory")) {
         context.data.put("SyncedInventory", context.blockEntityData.getCompound("Inventory"));
      }

      if (!context.data.contains("FocusedEntityId") && context.blockEntityData != null && context.blockEntityData.contains("FocusedEntityId")) {
         context.data.putInt("FocusedEntityId", context.blockEntityData.getInt("FocusedEntityId"));
      }
   }

   public void tick(MovementContext context) {
      FireControlMovementBehaviour.FireControlData data = getOrInitData(context);
      data.refreshTick = context.data.getBoolean("_invDirty") ? 0 : data.refreshTick + 1;
      if (data.refreshTick % 20 == 0) {
         context.data.putBoolean("_invDirty", false);
         refreshLogicData(context, data);
      }

      if (context.world.isClientSide) {
         float target = getTargetAngle(context);
         data.headAngle
            .chase(
               (double)(data.headAngle.getValue() + AngleHelper.getShortestAngleDiff((double)data.headAngle.getValue(), (double)target)), 0.5, Chaser.exp(5.0)
            );
         data.headAngle.tickChaser();
      }
   }

   public static FireControlMovementBehaviour.FireControlData getOrInitData(MovementContext context) {
      if (!(context.temporaryData instanceof FireControlMovementBehaviour.FireControlData)) {
         FireControlMovementBehaviour.FireControlData newData = new FireControlMovementBehaviour.FireControlData(0.0F);
         refreshLogicData(context, newData);
         context.temporaryData = newData;
      }

      return (FireControlMovementBehaviour.FireControlData)context.temporaryData;
   }

   public static void refreshLogicData(MovementContext context, FireControlMovementBehaviour.FireControlData data) {
      if (context.data.contains("SyncedInventory")) {
         ItemStackHandler inventory = new ItemStackHandler(1);
         inventory.deserializeNBT(context.world.registryAccess(), context.data.getCompound("SyncedInventory"));
         ItemStack stack = inventory.getStackInSlot(0);
         data.displayItem = stack;
         data.targetList.clear();
         if (!stack.isEmpty() && ItemNBTHelper.hasTag(stack)) {
            CompoundTag tag = ItemNBTHelper.getTag(stack);
            if (tag.contains("TargetList", 9)) {
               for (Tag t : tag.getList("TargetList", 8)) {
                  data.targetList.add(t.getAsString());
               }
            }

            if (tag.contains("WhitelistMode")) {
               data.isWhitelist = tag.getBoolean("WhitelistMode");
            }
         }

         if (context.data.contains("FocusedEntityId")) {
            data.focusedEntityId = context.data.getInt("FocusedEntityId");
         }
      }
   }

   private static float getTargetAngle(MovementContext context) {
      Entity player = Minecraft.getInstance().cameraEntity;
      if (player != null && !player.isInvisible() && context.position != null) {
         Vec3 vectorToPlayer = player.position().subtract(context.position);
         Vec3 localVector = context.contraption.entity.reverseRotation(vectorToPlayer, 1.0F);
         double dx = localVector.x;
         double dz = localVector.z;
         float rawAngle = AngleHelper.deg(-Mth.atan2(dz, dx)) - 90.0F;
         float blockFacing = 0.0F;
         return rawAngle - blockFacing;
      } else {
         return 0.0F;
      }
   }

   public static void notifyConnectedSentries(MovementContext context) {
      Contraption contraption = context.contraption;
      if (contraption != null) {
         for (MutablePair<StructureBlockInfo, MovementContext> pair : contraption.getActors()) {
            MovementContext ctx = (MovementContext)pair.getValue();
            if (ctx != null) {
               MovementBehaviour behaviour = (MovementBehaviour)MovementBehaviour.REGISTRY.get(ctx.state.getBlock());
               if (behaviour instanceof SentryMovementBehaviour) {
                  ctx.data.remove("SentryTargetId");
                   ctx.data.putBoolean("SentryHasTarget", false);
                   ctx.data.putInt("_TargetId", -1);
                   ctx.data.putInt("_AeroScanCD", 0);
                   ctx.data.putInt("IdleScanTimer", 0);
               }
            }
         }
      }
   }

   @OnlyIn(Dist.CLIENT)
   public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices, MultiBufferSource buffer) {
      BlazeFireControlRenderer.renderInContraption(context, renderWorld, matrices, buffer);
   }

   public void stopMoving(MovementContext context) {
      if (context.data.contains("SyncedInventory")) {
         if (context.blockEntityData == null) {
            context.blockEntityData = new CompoundTag();
         }

         context.blockEntityData.put("Inventory", context.data.getCompound("SyncedInventory"));
      }

      context.temporaryData = null;
   }

   public static FireControlMovementBehaviour.FireControlData findFireControl(Contraption contraption) {
      if (contraption == null) {
         return null;
      } else {
         for (MutablePair<?, MovementContext> pair : contraption.getActors()) {
            MovementContext ctx = (MovementContext)pair.getValue();
            Object var5 = ctx.temporaryData;
            if (var5 instanceof FireControlMovementBehaviour.FireControlData) {
               return (FireControlMovementBehaviour.FireControlData)var5;
            }
         }

         return null;
      }
   }

   public static class FireControlData {
      public List<String> targetList = new ArrayList<>();
      public boolean isWhitelist = false;
      public ItemStack displayItem = ItemStack.EMPTY;
      public final LerpedFloat headAngle;
      public int refreshTick;
      public int focusedEntityId = -1;

      public FireControlData(float initialAngle) {
         this.headAngle = LerpedFloat.angular().startWithValue((double)initialAngle);
      }
   }
}

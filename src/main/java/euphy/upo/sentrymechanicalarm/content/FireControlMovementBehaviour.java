package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.ArrayList;
import java.util.List;

public class FireControlMovementBehaviour implements MovementBehaviour {

    public static class FireControlData {
        public List<String> targetList = new ArrayList<>();
        public boolean isWhitelist = false;
        public ItemStack displayItem = ItemStack.EMPTY;
        public final LerpedFloat headAngle;
        public int refreshTick;
        public int focusedEntityId = -1;

        public FireControlData(float initialAngle) {
            this.headAngle = LerpedFloat.angular().startWithValue(initialAngle);
        }
    }

    @Override
    public boolean isActive(MovementContext context) {
        return true;
    }

    @Override
    public void startMoving(MovementContext context) {
        if (!context.data.contains("SyncedInventory")) {
            if (context.blockEntityData != null && context.blockEntityData.contains("Inventory")) {
                context.data.put("SyncedInventory", context.blockEntityData.getCompound("Inventory"));
            }
        }
        if (!context.data.contains("FocusedEntityId")) {
            if (context.blockEntityData != null && context.blockEntityData.contains("FocusedEntityId")) {
                context.data.putInt("FocusedEntityId", context.blockEntityData.getInt("FocusedEntityId"));
            }
        }
    }

    @Override
    public void tick(MovementContext context) {
        FireControlData data = getOrInitData(context);
        data.refreshTick = context.data.getBoolean("_invDirty") ? 0 : (data.refreshTick + 1);
        if (data.refreshTick % 20 == 0) {
            context.data.putBoolean("_invDirty", false);
            refreshLogicData(context, data);
        }
        if (context.world.isClientSide) {
            float target = getTargetAngle(context);

            data.headAngle.chase(
                    data.headAngle.getValue() + AngleHelper.getShortestAngleDiff(data.headAngle.getValue(), target),
                    0.5f,
                    LerpedFloat.Chaser.exp(5)
            );

            data.headAngle.tickChaser();
        }
    }

    public static FireControlData getOrInitData(MovementContext context) {
        if (!(context.temporaryData instanceof FireControlData)) {
            FireControlData newData = new FireControlData(0f);
            refreshLogicData(context, newData);
            context.temporaryData = newData;
        }
        return (FireControlData) context.temporaryData;
    }

    public static void refreshLogicData(MovementContext context, FireControlData data) {
        if (!context.data.contains("SyncedInventory")) {
            return;
        }

        ItemStackHandler inventory = new ItemStackHandler(1);
        inventory.deserializeNBT(context.world.registryAccess(), context.data.getCompound("SyncedInventory"));
        ItemStack stack = inventory.getStackInSlot(0);
        data.displayItem = stack;

        data.targetList.clear();
        if (!stack.isEmpty() && ItemNBTHelper.hasTag(stack)) {
            CompoundTag tag = ItemNBTHelper.getTag(stack);
            if (tag.contains("TargetList", Tag.TAG_LIST)) {
                ListTag listTag = tag.getList("TargetList", Tag.TAG_STRING);
                for (Tag t : listTag) {
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

    private static float getTargetAngle(MovementContext context) {
        Entity player = Minecraft.getInstance().cameraEntity;

        if (player != null && !player.isInvisible() && context.position != null) {
            Vec3 vectorToPlayer = player.position().subtract(context.position);
            Vec3 localVector = context.contraption.entity.reverseRotation(vectorToPlayer, 1);
            double dx = localVector.x;
            double dz = localVector.z;
            float rawAngle = AngleHelper.deg(-Mth.atan2(dz, dx)) - 90;
            float blockFacing = 0f;

            return rawAngle - blockFacing;
        }
        return 0f;
    }

    public static void notifyConnectedSentries(MovementContext context) {
        Contraption contraption = context.contraption;
        if (contraption == null) return;
        for (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> pair : contraption.getActors()) {
            MovementContext ctx = pair.getValue();
            if (ctx == null) continue;

            MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(ctx.state.getBlock());

            if (behaviour instanceof SentryMovementBehaviour) {
                ctx.data.remove("SentryTargetId");
                ctx.data.putBoolean("SentryHasTarget", false);

                ctx.data.putInt("_TargetId", -1);
                ctx.data.putInt("_ScanCooldown", 0);
                ctx.data.putInt("IdleScanTimer", 0);
            }
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                    ContraptionMatrices matrices, MultiBufferSource buffer) {
        BlazeFireControlRenderer.renderInContraption(context, renderWorld, matrices, buffer);
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.data.contains("SyncedInventory")) {
            if (context.blockEntityData == null) context.blockEntityData = new CompoundTag();
            context.blockEntityData.put("Inventory", context.data.getCompound("SyncedInventory"));
        }
        context.temporaryData = null;
    }

    public static FireControlData findFireControl(Contraption contraption) {
        if (contraption == null) return null;
        for (MutablePair<?, MovementContext> pair : contraption.getActors()) {
            MovementContext ctx = pair.getValue();
            if (ctx.temporaryData instanceof FireControlData data) {
                return data;
            }
        }
        return null;
    }
}
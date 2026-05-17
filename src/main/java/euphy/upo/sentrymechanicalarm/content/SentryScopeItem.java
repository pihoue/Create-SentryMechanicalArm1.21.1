package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class SentryScopeItem extends Item {

    private static final String TAG_LINKED_X = "LinkedFCX";
    private static final String TAG_LINKED_Y = "LinkedFCY";
    private static final String TAG_LINKED_Z = "LinkedFCZ";
    private static final String TAG_MARKED_TARGETS = "MarkedTargets";

    public SentryScopeItem(Properties properties) {
        super(properties);
    }

    public static BlockPos getLinkedFireControlPos(ItemStack stack) {
        CompoundTag tag = ItemNBTHelper.getTag(stack);
        if (!tag.contains(TAG_LINKED_X) || !tag.contains(TAG_LINKED_Y) || !tag.contains(TAG_LINKED_Z))
            return null;
        return new BlockPos(tag.getInt(TAG_LINKED_X), tag.getInt(TAG_LINKED_Y), tag.getInt(TAG_LINKED_Z));
    }

    public static void setLinkedFireControlPos(ItemStack stack, BlockPos pos) {
        CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
        tag.putInt(TAG_LINKED_X, pos.getX());
        tag.putInt(TAG_LINKED_Y, pos.getY());
        tag.putInt(TAG_LINKED_Z, pos.getZ());
        ItemNBTHelper.setTag(stack, tag);
    }

    public static void clearLinkedFireControlPos(ItemStack stack) {
        CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
        tag.remove(TAG_LINKED_X);
        tag.remove(TAG_LINKED_Y);
        tag.remove(TAG_LINKED_Z);
        ItemNBTHelper.setTag(stack, tag);
    }

    public static boolean hasLinkedFireControl(ItemStack stack) {
        CompoundTag tag = ItemNBTHelper.getTag(stack);
        return tag.contains(TAG_LINKED_X) && tag.contains(TAG_LINKED_Y) && tag.contains(TAG_LINKED_Z);
    }

    public static List<Integer> getMarkedTargetIds(ItemStack stack) {
        CompoundTag tag = ItemNBTHelper.getTag(stack);
        int[] arr = tag.getIntArray(TAG_MARKED_TARGETS);
        List<Integer> result = new ArrayList<>();
        for (int id : arr) result.add(id);
        return result;
    }

    public static void addMarkedTargetId(ItemStack stack, int entityId) {
        CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
        int[] arr = tag.getIntArray(TAG_MARKED_TARGETS);
        for (int id : arr) { if (id == entityId) return; }
        int[] newArr = new int[arr.length + 1];
        System.arraycopy(arr, 0, newArr, 0, arr.length);
        newArr[arr.length] = entityId;
        tag.putIntArray(TAG_MARKED_TARGETS, newArr);
        ItemNBTHelper.setTag(stack, tag);
    }

    public static void removeMarkedTargetId(ItemStack stack, int entityId) {
        CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
        int[] arr = tag.getIntArray(TAG_MARKED_TARGETS);
        List<Integer> remaining = new ArrayList<>();
        for (int id : arr) { if (id != entityId) remaining.add(id); }
        tag.putIntArray(TAG_MARKED_TARGETS, remaining.stream().mapToInt(i -> i).toArray());
        ItemNBTHelper.setTag(stack, tag);
    }

    public static int cleanupMarkedTargets(ItemStack stack, Level level, BlockPos fcPos) {
        CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
        int[] arr = tag.getIntArray(TAG_MARKED_TARGETS);
        if (arr.length == 0) return 0;
        double rangeSq = 128.0 * 128.0;
        List<Integer> valid = new ArrayList<>();
        Vec3 fcCenter = fcPos != null ? Vec3.atCenterOf(fcPos) : null;
        for (int id : arr) {
            Entity entity = level.getEntity(id);
            if (entity == null || !entity.isAlive()) continue;
            if (fcCenter != null && entity.distanceToSqr(fcCenter) > rangeSq) continue;
            valid.add(id);
        }
        int removed = arr.length - valid.size();
        if (removed > 0) {
            tag.putIntArray(TAG_MARKED_TARGETS, valid.stream().mapToInt(i -> i).toArray());
            ItemNBTHelper.setTag(stack, tag);
        }
        return removed;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof BlazeFireControlBlockEntity fc && player.isShiftKeyDown()) {
            BlockPos boundPos = getLinkedFireControlPos(context.getItemInHand());
            if (boundPos != null && boundPos.equals(pos)) {
                clearLinkedFireControlPos(context.getItemInHand());
                if (!level.isClientSide) {
                    fc.setHasBoundScope(false);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.sentrymechanicalarm.scope_unbound"),
                            true
                    );
                }
            } else {
                setLinkedFireControlPos(context.getItemInHand(), pos);
                if (!level.isClientSide) {
                    fc.setHasBoundScope(true);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.sentrymechanicalarm.scope_bound"),
                            true
                    );
                }
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 1200;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
}

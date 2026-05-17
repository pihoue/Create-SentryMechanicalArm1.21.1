package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SentryScopeItem extends Item {

    private static final String TAG_LINKED_X = "LinkedFCX";
    private static final String TAG_LINKED_Y = "LinkedFCY";
    private static final String TAG_LINKED_Z = "LinkedFCZ";

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

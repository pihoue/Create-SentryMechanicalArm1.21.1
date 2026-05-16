package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SentryArmBlock extends KineticBlock implements IBE<SentryArmBlockEntity>, ICogWheel {

    public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");
    public static final IntegerProperty COLOR_TYPE = IntegerProperty.create("color_type", 0, 16);

    public SentryArmBlock(ResourceLocation id, Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.defaultBlockState()
                .setValue(CEILING, false)
                .setValue(COLOR_TYPE, 0)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR_TYPE);
        builder.add(CEILING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
 
        return super.getStateForPlacement(ctx).setValue(CEILING, ctx.getClickedFace() == Direction.DOWN);
    }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(CEILING) ? AllShapes.MECHANICAL_ARM_CEILING : AllShapes.MECHANICAL_ARM;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

 
    @Override
    public Class<SentryArmBlockEntity> getBlockEntityClass() {
        return SentryArmBlockEntity.class;
    }

 
    @Override
    public BlockEntityType<? extends SentryArmBlockEntity> getBlockEntityType() {
        return SentryRegistry.SENTRY_ARM_BE.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {

        if (!isMoving && state.getBlock() != newState.getBlock()) {
            SentryArmBlockEntity sentry = this.getBlockEntity(level, pos);
            if (sentry != null) {

                ItemStack gun = sentry.getHeldItem();
                if (!gun.isEmpty()) {
                    Block.popResource(level, pos, gun);
                }
                for (ItemStack box : sentry.attachedAmmoBoxes) {
                    if (!box.isEmpty()) {
                        Block.popResource(level, pos, box);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if ("create:wrench".equals(itemId) || stack.getItem().getDescriptionId().contains("wrench")) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        SentryArmBlockEntity sentry = this.getBlockEntity(level, pos);
        if (sentry == null) return InteractionResult.PASS;

        ItemStack armHeldStack = sentry.getHeldItem();
        if (!armHeldStack.isEmpty()) {
            if (!level.isClientSide) {
                if (!player.getInventory().add(armHeldStack)) {
                    Block.popResource(level, pos, armHeldStack);
                }
                sentry.setHeldItem(ItemStack.EMPTY);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f);
                sentry.setChanged();
                sentry.sendData();
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
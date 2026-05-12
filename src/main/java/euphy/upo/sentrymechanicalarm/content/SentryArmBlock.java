package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.common.Tags;

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
 
        if (state.getBlock() != newState.getBlock()) {
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
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {

        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if ("create:wrench".equals(itemId) || stack.getItem().getDescriptionId().contains("wrench")) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }

        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        SentryArmBlockEntity sentry = this.getBlockEntity(level, pos);
        if (sentry == null) return ItemInteractionResult.FAIL;

        boolean isDye = stack.is(Tags.Items.DYES);

        boolean hasWater = false;
        if (!isDye) {
            hasWater = stack.getItem() == Items.WATER_BUCKET;
        }

        if (isDye || hasWater) {
            DyeColor colorToApply = isDye ? DyeColor.getColor(stack) : null;

            if (sentry.applyColor(colorToApply)) {
                int newColorId = (colorToApply == null) ? 0 : (colorToApply.getId() + 1);

                BlockState newState = state.setValue(COLOR_TYPE, newColorId);
                level.setBlock(pos, newState, 3);

                if (!level.isClientSide) {
                    level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                }
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        ItemStack armHeldStack = sentry.getHeldItem();

        if (stack.getItem() instanceof IGun) {
            if (armHeldStack.isEmpty()) {
                if (!level.isClientSide) {
                    ItemStack gunCopy = stack.copy();
                    gunCopy.setCount(1);
                    sentry.setHeldItem(gunCopy);
                    stack.shrink(1);
                    level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.5f);
                    sentry.setChanged();
                    sentry.sendData();
                }
                return ItemInteractionResult.SUCCESS;
            }
        }

        else if (stack.getItem() instanceof IAmmoBox iBox) {
            if (!armHeldStack.isEmpty() && armHeldStack.getItem() instanceof IGun) {

                boolean isCompatible = iBox.isAmmoBoxOfGun(armHeldStack, stack);
                boolean isEmpty = iBox.getAmmoCount(stack) == 0;

                if (!isCompatible && !isEmpty) {
                    if (!level.isClientSide) {
                        player.displayClientMessage(Component.translatable("sentry.tooltip.ammobox_3").withStyle(ChatFormatting.RED), true);
                    }
                    return ItemInteractionResult.SUCCESS;
                }

                if (sentry.addAmmoBox(stack)) {
                    if (!level.isClientSide) {
                        if(!player.isCreative()) stack.shrink(1);
                        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
                        player.displayClientMessage(Component.translatable("sentry.tooltip.ammobox_2").withStyle(ChatFormatting.GREEN), true);
                    }
                } else {
                    if (!level.isClientSide) {
                        player.displayClientMessage(Component.translatable("sentry.tooltip.ammobox_1").withStyle(ChatFormatting.RED), true);
                    }
                }
                return ItemInteractionResult.SUCCESS;
            }
        }

        else if (stack.isEmpty()) {
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
                return ItemInteractionResult.SUCCESS;
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
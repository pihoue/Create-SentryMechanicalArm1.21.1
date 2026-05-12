package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.block.IBE;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class BlazeFireControlBlock extends Block implements IBE<BlazeFireControlBlockEntity> {

    public BlazeFireControlBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<BlazeFireControlBlockEntity> getBlockEntityClass() {
        return BlazeFireControlBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BlazeFireControlBlockEntity> getBlockEntityType() {
        return SentryRegistry.BLAZE_FIRE_CONTROL_BE.get();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.HEATER_BLOCK_SHAPE;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {

        if (hand == InteractionHand.OFF_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.getItem().getDescriptionId().contains("wrench")) {
            return ItemInteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity be) {

            if (stack.getItem() instanceof ApplePieItem) {
                if (level.isClientSide) {
                    double x = pos.getX() + 0.5;
                    double y = pos.getY() + 1.2;
                    double z = pos.getZ() + 0.5;
                    for (int i = 0; i < 7; i++) {
                        level.addParticle(ParticleTypes.HEART, x, y, z, 0, 0, 0);
                    }
                } else {
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                return ItemInteractionResult.SUCCESS;
            }

            if (stack.getItem() instanceof FireControlClipboardItem) {
                if (be.inventory.getStackInSlot(0).isEmpty()) {
                    if (!level.isClientSide) {
                        ItemStack toInsert = stack.copy();
                        toInsert.setCount(1);
                        be.inventory.setStackInSlot(0, toInsert);
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
                        be.notifyUpdate();
                    }
                    return ItemInteractionResult.SUCCESS;
                }
            }

            if (stack.isEmpty() && !be.inventory.getStackInSlot(0).isEmpty()) {
                if (!level.isClientSide) {
                    ItemStack extracted = be.inventory.getStackInSlot(0);
                    ItemHandlerHelper.giveItemToPlayer(player, extracted.copy());
                    be.inventory.setStackInSlot(0, ItemStack.EMPTY);
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
                    be.notifyUpdate();
                }
                return ItemInteractionResult.SUCCESS;
            }

            if (stack.isEmpty() && be.inventory.getStackInSlot(0).isEmpty()) {
                if (!level.isClientSide) {
                    be.showRandomEmoticon();
                }
                return ItemInteractionResult.SUCCESS;
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }


    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BlazeFireControlBlockEntity fireControl) {

                ItemStack stack = fireControl.inventory.getStackInSlot(0);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }

                fireControl.notifyConnectedSentries(true);
            }
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level.isClientSide) return;
        boolean isTacZBullet = projectile.getClass().getSimpleName().contains("KineticBullet")
                || projectile.getClass().getName().contains("tacz");
        if (isTacZBullet) {
            BlockPos pos = hit.getBlockPos();
            BlockState emptyBurnerState = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(ResourceLocation.fromNamespaceAndPath("create", "blaze_burner"))
                    .defaultBlockState()
                    .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.NONE);

            level.setBlockAndUpdate(pos, emptyBurnerState);

            level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 1.2f);
            level.playSound(null, pos, SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);

            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.1);
            }
        }

        super.onProjectileHit(level, state, hit, projectile);
    }
}
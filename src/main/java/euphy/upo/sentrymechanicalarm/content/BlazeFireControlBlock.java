package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.block.IBE;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class BlazeFireControlBlock extends Block implements IBE<BlazeFireControlBlockEntity> {
   public BlazeFireControlBlock() {
      this(Properties.of());
   }

   public BlazeFireControlBlock(Properties properties) {
      super(properties.mapColor(MapColor.NONE).lightLevel(s -> 14).noOcclusion().isRedstoneConductor((state, world, pos) -> false));
   }

   public Class<BlazeFireControlBlockEntity> getBlockEntityClass() {
      return BlazeFireControlBlockEntity.class;
   }

   public BlockEntityType<? extends BlazeFireControlBlockEntity> getBlockEntityType() {
      return (BlockEntityType<? extends BlazeFireControlBlockEntity>)SentryRegistry.BLAZE_FIRE_CONTROL_BE.get();
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return AllShapes.HEATER_BLOCK_SHAPE;
   }

   public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (hand == InteractionHand.OFF_HAND) {
         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      } else if (stack.getItem().getDescriptionId().contains("wrench")) {
         return ItemInteractionResult.SUCCESS;
      } else {
         if (level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity be) {
            if (stack.getItem() instanceof ApplePieItem) {
               if (level.isClientSide) {
                  double x = (double)pos.getX() + 0.5;
                  double y = (double)pos.getY() + 1.2;
                  double z = (double)pos.getZ() + 0.5;

                  for (int i = 0; i < 7; i++) {
                     level.addParticle(ParticleTypes.HEART, x, y, z, 0.0, 0.0, 0.0);
                  }
               } else {
                  if (!player.isCreative()) {
                     stack.shrink(1);
                  }

                  level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
               }

               return ItemInteractionResult.SUCCESS;
            }

            if (stack.getItem() instanceof FireControlClipboardItem && be.inventory.getStackInSlot(0).isEmpty()) {
               if (!level.isClientSide) {
                  ItemStack toInsert = stack.copy();
                  toInsert.setCount(1);
                  be.inventory.setStackInSlot(0, toInsert);
                  if (!player.isCreative()) {
                     stack.shrink(1);
                  }

                  level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                  be.notifyUpdate();
               }

               return ItemInteractionResult.SUCCESS;
            }
         }

         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      }
   }

   public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity be) {
         if (!be.inventory.getStackInSlot(0).isEmpty()) {
            if (!level.isClientSide) {
               ItemStack extracted = be.inventory.getStackInSlot(0);
               ItemHandlerHelper.giveItemToPlayer(player, extracted.copy());
               be.inventory.setStackInSlot(0, ItemStack.EMPTY);
               level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
               be.notifyUpdate();
            }

            return InteractionResult.SUCCESS;
         } else {
            if (!level.isClientSide) {
               be.showRandomEmoticon();
            }

            return InteractionResult.SUCCESS;
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
      if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity fireControl) {
         ItemStack stack = fireControl.inventory.getStackInSlot(0);
         if (!stack.isEmpty()) {
            Containers.dropItemStack(level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), stack);
         }

         fireControl.notifyConnectedSentries(true);
      }

      super.onRemove(state, level, pos, newState, isMoving);
   }

   public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
      if (!level.isClientSide) {
         boolean isTacZBullet = projectile.getClass().getSimpleName().contains("KineticBullet") || projectile.getClass().getName().contains("tacz");
         if (isTacZBullet) {
            BlockPos pos = hit.getBlockPos();
            BlockState emptyBurnerState = (BlockState)AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.NONE);
            level.setBlockAndUpdate(pos, emptyBurnerState);
            level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
            level.playSound(null, pos, SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
            if (level instanceof ServerLevel serverLevel) {
               serverLevel.sendParticles(
                  ParticleTypes.CLOUD, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.1
               );
            }
         }

         super.onProjectileHit(level, state, hit, projectile);
      }
   }
}

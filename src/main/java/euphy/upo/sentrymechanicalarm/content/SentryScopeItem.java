package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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
      return tag.contains("LinkedFCX") && tag.contains("LinkedFCY") && tag.contains("LinkedFCZ")
         ? new BlockPos(tag.getInt("LinkedFCX"), tag.getInt("LinkedFCY"), tag.getInt("LinkedFCZ"))
         : null;
   }

   public static void setLinkedFireControlPos(ItemStack stack, BlockPos pos) {
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      tag.putInt("LinkedFCX", pos.getX());
      tag.putInt("LinkedFCY", pos.getY());
      tag.putInt("LinkedFCZ", pos.getZ());
      ItemNBTHelper.setTag(stack, tag);
   }

   public static void clearLinkedFireControlPos(ItemStack stack) {
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      tag.remove("LinkedFCX");
      tag.remove("LinkedFCY");
      tag.remove("LinkedFCZ");
      ItemNBTHelper.setTag(stack, tag);
   }

   public static boolean hasLinkedFireControl(ItemStack stack) {
      CompoundTag tag = ItemNBTHelper.getTag(stack);
      return tag.contains("LinkedFCX") && tag.contains("LinkedFCY") && tag.contains("LinkedFCZ");
   }

   public static List<Integer> getMarkedTargetIds(ItemStack stack) {
      CompoundTag tag = ItemNBTHelper.getTag(stack);
      int[] arr = tag.getIntArray("MarkedTargets");
      List<Integer> result = new ArrayList<>();

      for (int id : arr) {
         result.add(id);
      }

      return result;
   }

   public static void addMarkedTargetId(ItemStack stack, int entityId) {
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      int[] arr = tag.getIntArray("MarkedTargets");

      for (int id : arr) {
         if (id == entityId) {
            return;
         }
      }

      int[] newArr = new int[arr.length + 1];
      System.arraycopy(arr, 0, newArr, 0, arr.length);
      newArr[arr.length] = entityId;
      tag.putIntArray("MarkedTargets", newArr);
      ItemNBTHelper.setTag(stack, tag);
   }

   public static void removeMarkedTargetId(ItemStack stack, int entityId) {
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      int[] arr = tag.getIntArray("MarkedTargets");
      List<Integer> remaining = new ArrayList<>();

      for (int id : arr) {
         if (id != entityId) {
            remaining.add(id);
         }
      }

      tag.putIntArray("MarkedTargets", remaining.stream().mapToInt(i -> i).toArray());
      ItemNBTHelper.setTag(stack, tag);
   }

   public static int cleanupMarkedTargets(ItemStack stack, Level level, BlockPos fcPos) {
      CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
      int[] arr = tag.getIntArray("MarkedTargets");
      if (arr.length == 0) {
         return 0;
      } else {
         double rangeSq = 16384.0;
         List<Integer> valid = new ArrayList<>();
         Vec3 fcCenter = fcPos != null ? Vec3.atCenterOf(fcPos) : null;

         for (int id : arr) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive() && (fcCenter == null || !(entity.distanceToSqr(fcCenter) > rangeSq))) {
               valid.add(id);
            }
         }

         int removed = arr.length - valid.size();
         if (removed > 0) {
            tag.putIntArray("MarkedTargets", valid.stream().mapToInt(i -> i).toArray());
            ItemNBTHelper.setTag(stack, tag);
         }

         return removed;
      }
   }

   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      if (player == null) {
         return InteractionResult.PASS;
      } else {
         Level level = context.getLevel();
         BlockPos pos = context.getClickedPos();
         if (level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity fc && player.isShiftKeyDown()) {
            BlockPos boundPos = getLinkedFireControlPos(context.getItemInHand());
            if (boundPos != null && boundPos.equals(pos)) {
               clearLinkedFireControlPos(context.getItemInHand());
               if (!level.isClientSide) {
                  fc.setHasBoundScope(false);
                  player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.scope_unbound"), true);
               }
            } else {
               setLinkedFireControlPos(context.getItemInHand(), pos);
               if (!level.isClientSide) {
                  fc.setHasBoundScope(true);
                  player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.scope_bound"), true);
               }
            }

            return InteractionResult.SUCCESS;
         }

         return InteractionResult.PASS;
      }
   }

   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return 1200;
   }

   public UseAnim getUseAnimation(ItemStack stack) {
      return UseAnim.SPYGLASS;
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      player.startUsingItem(hand);
      return InteractionResultHolder.consume(player.getItemInHand(hand));
   }
}

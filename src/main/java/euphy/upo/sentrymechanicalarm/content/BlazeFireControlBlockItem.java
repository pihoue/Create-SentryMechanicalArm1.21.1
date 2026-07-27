package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.client.BlazeFireControlItemRenderer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class BlazeFireControlBlockItem extends BlockItem {
   public BlazeFireControlBlockItem(Block block, Properties properties) {
      super(block, properties);
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

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(new IClientItemExtensions() {
         private final BlazeFireControlItemRenderer renderer = new BlazeFireControlItemRenderer();

         public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return this.renderer;
         }
      });
   }
}

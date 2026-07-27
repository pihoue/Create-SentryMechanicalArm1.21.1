package euphy.upo.sentrymechanicalarm.registry;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.stress.BlockStressValues;
import euphy.upo.sentrymechanicalarm.content.ApplePieItem;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlock;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockItem;
import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.content.FireControlMenu;
import euphy.upo.sentrymechanicalarm.content.FireControlMovementBehaviour;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.SentryMovementBehaviour;
import euphy.upo.sentrymechanicalarm.content.SentryScopeItem;
import euphy.upo.sentrymechanicalarm.content.UnfinishedAmmoItem;
import java.util.function.DoubleSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties.Builder;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

public class SentryRegistry {
   public static final Blocks BLOCKS = DeferredRegister.createBlocks("sentrymechanicalarm");
   public static final Items ITEMS = DeferredRegister.createItems("sentrymechanicalarm");
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "sentrymechanicalarm");
   public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "sentrymechanicalarm");
   public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "sentrymechanicalarm");
   public static final DeferredHolder<Block, SentryArmBlock> SENTRY_ARM_BLOCK = BLOCKS.register(
      "sentry_mechanical_arm", id -> new SentryArmBlock(id, Properties.of())
   );
   public static final DeferredHolder<Item, ApplePieItem> APPLE_PIE = ITEMS.register(
      "apple_pie", () -> new ApplePieItem(new net.minecraft.world.item.Item.Properties().food(new Builder().nutrition(8).saturationModifier(0.3F).build()))
   );
   public static final DeferredHolder<Item, UnfinishedAmmoItem> UNFINISHED_AMMO = ITEMS.register(
      "unfinished_ammo", id -> new UnfinishedAmmoItem(new net.minecraft.world.item.Item.Properties())
   );
   public static final DeferredHolder<Block, BlazeFireControlBlock> BLAZE_FIRE_CONTROL = BLOCKS.register(
      "blaze_fire_control", id -> new BlazeFireControlBlock(Properties.of())
   );
   public static final DeferredHolder<Item, BlazeFireControlBlockItem> BLAZE_FIRE_CONTROL_ITEM = ITEMS.register(
      "blaze_fire_control", id -> new BlazeFireControlBlockItem((Block)BLAZE_FIRE_CONTROL.get(), new net.minecraft.world.item.Item.Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, BlockItem> SENTRY_ARM_ITEM = ITEMS.register(
      "sentry_mechanical_arm", id -> new BlockItem((Block)SENTRY_ARM_BLOCK.get(), new net.minecraft.world.item.Item.Properties())
   );
   public static final DeferredHolder<Item, FireControlClipboardItem> FIRE_CONTROL_CLIPBOARD = ITEMS.register(
      "fire_control_clipboard", id -> new FireControlClipboardItem(new net.minecraft.world.item.Item.Properties())
   );
   public static final DeferredHolder<Item, SentryScopeItem> SENTRY_SCOPE = ITEMS.register(
      "sentry_scope", id -> new SentryScopeItem(new net.minecraft.world.item.Item.Properties().stacksTo(1))
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SentryArmBlockEntity>> SENTRY_ARM_BE = BLOCK_ENTITIES.register(
      "sentry_mechanical_arm",
      () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SentryArmBlockEntity::new, new Block[]{(Block)SENTRY_ARM_BLOCK.get()})
            .build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlazeFireControlBlockEntity>> BLAZE_FIRE_CONTROL_BE = BLOCK_ENTITIES.register(
      "blaze_fire_control",
      () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(BlazeFireControlBlockEntity::new, new Block[]{(Block)BLAZE_FIRE_CONTROL.get()})
            .build(null)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<FireControlMenu>> FIRE_CONTROL_MENU = MENUS.register(
      "fire_control_menu", () -> new MenuType((IContainerFactory)(id, inventory, buf) -> new FireControlMenu(id, inventory, buf), FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SENTRY_TAB = CREATIVE_TABS.register(
      "main",
      () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.sentrymechanicalarm"))
            .icon(() -> new ItemStack((ItemLike)BLAZE_FIRE_CONTROL_ITEM.get(), 1))
            .displayItems((parameters, output) -> {
               output.accept(new ItemStack((ItemLike)SENTRY_ARM_ITEM.get(), 1));
               output.accept(new ItemStack((ItemLike)BLAZE_FIRE_CONTROL_ITEM.get(), 1));
               output.accept(new ItemStack((ItemLike)FIRE_CONTROL_CLIPBOARD.get(), 1));
               output.accept(new ItemStack((ItemLike)UNFINISHED_AMMO.get(), 1));
               output.accept(new ItemStack((ItemLike)APPLE_PIE.get(), 1));
               output.accept(new ItemStack((ItemLike)SENTRY_SCOPE.get(), 1));
            })
            .build()
   );

   public static void register(IEventBus eventBus) {
      BLOCKS.register(eventBus);
      ITEMS.register(eventBus);
      BLOCK_ENTITIES.register(eventBus);
      MENUS.register(eventBus);
      CREATIVE_TABS.register(eventBus);
   }

   public static void registerAllStressValues() {
      double stressImpact = 3.0;
      BlockStressValues.IMPACTS.register((Block)SENTRY_ARM_BLOCK.get(), (DoubleSupplier)() -> stressImpact);
   }

   public static void registerMovementBehaviours() {
      MovementBehaviour.REGISTRY.register((Block)SENTRY_ARM_BLOCK.get(), new SentryMovementBehaviour());
      MovementBehaviour.REGISTRY.register((Block)BLAZE_FIRE_CONTROL.get(), new FireControlMovementBehaviour());
   }
}

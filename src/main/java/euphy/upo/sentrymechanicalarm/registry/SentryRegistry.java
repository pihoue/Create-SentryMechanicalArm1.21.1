package euphy.upo.sentrymechanicalarm.registry;

import com.simibubi.create.api.stress.BlockStressValues;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static euphy.upo.sentrymechanicalarm.SentryMechanicalArm.MODID;

public class SentryRegistry {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<Block, SentryArmBlock> SENTRY_ARM_BLOCK = BLOCKS.register("sentry_mechanical_arm",
            id -> new SentryArmBlock(id, BlockBehaviour.Properties.of()));

    public static final DeferredHolder<Item, ApplePieItem> APPLE_PIE = ITEMS.register("apple_pie",
            () -> new ApplePieItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.3f).build())));

    public static final DeferredHolder<Item, UnfinishedAmmoItem> UNFINISHED_AMMO = ITEMS.register("unfinished_ammo",
            id -> new UnfinishedAmmoItem(new Item.Properties()));

    public static final DeferredHolder<Block, BlazeFireControlBlock> BLAZE_FIRE_CONTROL = BLOCKS.register("blaze_fire_control",
            id -> new BlazeFireControlBlock(BlockBehaviour.Properties.of()));

    public static final DeferredHolder<Item, BlazeFireControlBlockItem> BLAZE_FIRE_CONTROL_ITEM = ITEMS.register("blaze_fire_control",
            id -> new BlazeFireControlBlockItem(BLAZE_FIRE_CONTROL.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, FireControlClipboardItem> FIRE_CONTROL_CLIPBOARD = ITEMS.register("fire_control_clipboard",
            id -> new FireControlClipboardItem(new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SentryArmBlockEntity>> SENTRY_ARM_BE = BLOCK_ENTITIES.register("sentry_mechanical_arm",
            () -> BlockEntityType.Builder.of((pos, state) -> new SentryArmBlockEntity(null, pos, state), SENTRY_ARM_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlazeFireControlBlockEntity>> BLAZE_FIRE_CONTROL_BE = BLOCK_ENTITIES.register("blaze_fire_control",
            () -> BlockEntityType.Builder.of((pos, state) -> new BlazeFireControlBlockEntity(null, pos, state), BLAZE_FIRE_CONTROL.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<FireControlMenu>> FIRE_CONTROL_MENU = MENUS.register("fire_control_menu",
            () -> new MenuType<>(
                    (IContainerFactory<FireControlMenu>) (id, inventory, buf) -> new FireControlMenu(id, inventory, buf),
                    FeatureFlags.DEFAULT_FLAGS
            ));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SENTRY_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .icon(() -> new net.minecraft.world.item.ItemStack(BLAZE_FIRE_CONTROL.get(), 1))
                    .displayItems((parameters, output) -> {
                        output.accept(SENTRY_ARM_BLOCK.get());
                        output.accept(BLAZE_FIRE_CONTROL.get());
                        output.accept(FIRE_CONTROL_CLIPBOARD.get());
                        output.accept(APPLE_PIE.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
    }

    public static void registerAllStressValues() {
        double stressImpact = 3.0;
        BlockStressValues.IMPACTS.register(SENTRY_ARM_BLOCK.get(), () -> stressImpact);
    }

}
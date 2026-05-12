package euphy.upo.sentrymechanicalarm.datagen;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SentryRecipeProvider extends RecipeProvider implements IConditionBuilder {

    private final Map<String, AmmoConfig> AMMO_CONFIGS = new HashMap<>();

    public SentryRecipeProvider(PackOutput output) {
        super(output);
    }
 
    private void initAmmoConfigs() {

        config("tacz:9mm")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addDeploy(Items.IRON_NUGGET)
                .addPress()
                .outputCount(25);

        config("tacz:45acp")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addDeploy(Items.IRON_NUGGET)
                .addPress()
                .outputCount(15);

        config("tacz:762x25") 
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addDeploy(Items.IRON_NUGGET)
                .addPress()
                .outputCount(25);

        config("tacz:556x45")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(20);

 
        config("tacz:762x39")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(15);

 
        config("tacz:50bmg")
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.TNT)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.TNT)
                .addDeploy(Items.BLAZE_POWDER)
                .addDeploy(Items.LAPIS_BLOCK)
                .addPress()
                .outputCount(16);
 
        config("tacz:40mm")
                .addDeploy(AllItems.IRON_SHEET)
                .addDeploy(AllItems.IRON_SHEET)
                .addDeploy(AllItems.IRON_SHEET)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.TNT)
                .addDeploy(Items.TNT)
                .addPress()
                .outputCount(8);

        config("tacz:12g")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.CARDBOARD)
                .addDeploy(Items.GUNPOWDER)
                .addDeploy(Items.IRON_NUGGET)
                .addDeploy(Items.IRON_NUGGET)
                .addDeploy(Items.IRON_NUGGET)
                .addPress()
                .outputCount(5);

        config("tacz:30_06")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(8);

        config("tacz:58x42")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(15);

        config("tacz:rpg_rocket")
                .addDeploy(AllItems.IRON_SHEET)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.TNT)
                .addSpout(Fluids.LAVA, 250)
                .addPress()
                .outputCount(2);

        config("tacz:50ae")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addDeploy(Items.LAPIS_LAZULI)
                .addPress()
                .outputCount(9);

        config("tacz:68x51fury")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(10);

        config("tacz:338")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addDeploy(Items.GUNPOWDER)
                .addDeploy(Items.LAPIS_LAZULI)
                .addPress()
                .outputCount(8);

        config("tacz:357mag")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addDeploy(Items.LAPIS_LAZULI)
                .addPress()
                .outputCount(10);

        config("tacz:46x30")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(28);

        config("tacz:57x28")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.LAPIS_LAZULI)
                .addDeploy(Items.LAPIS_LAZULI)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(26);

        config("tacz:308")
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.TNT)
                .addDeploy(Items.TNT)
                .addDeploy(Items.LAPIS_LAZULI)
                .addPress()
                .outputCount(60);

        config("tacz:545x39")
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.COPPER_BLOCK)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(18);

        config("tacz:45_70")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addDeploy(Items.LAPIS_LAZULI)
                .addPress()
                .outputCount(9);

        config("tacz:762x54")
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(AllItems.COPPER_SHEET)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(9);

        /*
        Consumer<AmmoConfig> heavyRifle = c -> c
                .addSpout(Fluids.WATER, 100)
                .addDeploy(Items.GUNPOWDER)
                .addPress()
                .outputCount(4);
        heavyRifle.accept(config("tacz:30_06"));
        heavyRifle.accept(config("tacz:win_308"));
        heavyRifle.accept(config("tacz:762x54"));
        heavyRifle.accept(config("tacz:68x51fury"));
         */

    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, SentryRegistry.SENTRY_ARM_BLOCK.get())
                .requires(AllBlocks.MECHANICAL_ARM.get())
                .requires(AllItems.PRECISION_MECHANISM)
                .unlockedBy("has_mechanical_arm", has(AllBlocks.MECHANICAL_ARM.get()))
                .save(writer);

 
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, AllBlocks.MECHANICAL_ARM.get())
                .requires(SentryRegistry.SENTRY_ARM_BLOCK.get()) 
                .unlockedBy("has_sentry_arm", has(SentryRegistry.SENTRY_ARM_BLOCK.get()))
                .save(writer, new ResourceLocation("sentrymechanicalarm", "mechanical_arm_from_sentry"));


        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, SentryRegistry.BLAZE_FIRE_CONTROL.get())
                .requires(AllBlocks.BLAZE_BURNER.get())
                .requires(SentryRegistry.APPLE_PIE.get())
                .unlockedBy("has_apple_pie", has(SentryRegistry.APPLE_PIE.get()))
                .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, SentryRegistry.FIRE_CONTROL_CLIPBOARD.get())
                .requires(AllBlocks.CLIPBOARD.get())
                .unlockedBy("has_clipboard", has(AllBlocks.CLIPBOARD.get()))
                .save(writer);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, AllBlocks.CLIPBOARD.get())
                .requires(SentryRegistry.FIRE_CONTROL_CLIPBOARD.get())
                .unlockedBy("has_fire_control_clipboard", has(SentryRegistry.FIRE_CONTROL_CLIPBOARD.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, SentryRegistry.APPLE_PIE.get())
                .pattern("AAA")
                .pattern("SMS")
                .pattern("FFF")
                .define('A', Items.APPLE)
                .define('S', Items.SUGAR)
                .define('M', Items.MILK_BUCKET)
                .define('F', AllItems.WHEAT_FLOUR.get())
                .unlockedBy("has_apple", has(Items.APPLE))
                .save(writer);

        initAmmoConfigs();
        List<String> defaultAmmoList = List.of(
                "tacz:9mm", "tacz:45acp", "tacz:50ae", "tacz:357mag", "tacz:12g",
                "tacz:30_06", "tacz:50bmg", "tacz:rpg_rocket", "tacz:40mm", "tacz:68x51fury",
                "tacz:338", "tacz:308", "tacz:46x30", "tacz:57x28", "tacz:545x39",
                "tacz:45_70", "tacz:762x25", "tacz:556x45", "tacz:58x42", "tacz:762x39", "tacz:762x54"
        );

        Item taczAmmoItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "ammo"));
        if (taczAmmoItem == null) throw new IllegalStateException("TacZ ammo item not found");

        for (String ammoIdStr : defaultAmmoList) {
            ResourceLocation ammoId = new ResourceLocation(ammoIdStr);
 
            AmmoConfig config = AMMO_CONFIGS.getOrDefault(ammoIdStr, new AmmoConfig().addDeploy(Items.GUNPOWDER).addPress().outputCount(4));

            generateSingleAmmo(writer, ammoId, taczAmmoItem, config);
        }
    }

    private void generateSingleAmmo(Consumer<FinishedRecipe> writer, ResourceLocation ammoId, net.minecraft.world.item.Item taczAmmoItem, AmmoConfig config) {
        String path = ammoId.getPath().replace("/", "_");

        CompoundTag ammoTag = new CompoundTag();
        ammoTag.putString("AmmoId", ammoId.toString());

        ItemStack unfinishedStack = new ItemStack(SentryRegistry.UNFINISHED_AMMO.get());
        unfinishedStack.setTag(ammoTag);

        ItemStack finishedStackOutput = new ItemStack(taczAmmoItem);
        finishedStackOutput.setTag(ammoTag);
        finishedStackOutput.setCount(config.outputCount);

        ItemStack finishedStackIngredient = new ItemStack(taczAmmoItem);
        finishedStackIngredient.setTag(ammoTag);
        finishedStackIngredient.setCount(1);
 
        new ProcessingRecipeBuilder<>(CuttingRecipe::new,
                new ResourceLocation("sentrymechanicalarm", "ammo_cutting/" + path))
                .withItemIngredients(Ingredient.of(AllItems.COPPER_SHEET.get()))
                .output(unfinishedStack)
                .duration(50)
                .build(writer);

 
        new ProcessingRecipeBuilder<>(DeployerApplicationRecipe::new,
                new ResourceLocation("sentrymechanicalarm", "ammo_template/" + path))
                .require(AllItems.COPPER_SHEET.get())
                .require(createPartialNbtIngredient(finishedStackIngredient))
                .output(unfinishedStack)
                .build(writer);
 
        Ingredient inputIngredient = createPartialNbtIngredient(unfinishedStack);

        SequencedAssemblyRecipeBuilder builder = new SequencedAssemblyRecipeBuilder(new ResourceLocation("sentry", "assembly/" + path))
                .require(inputIngredient) 
                .transitionTo(SentryRegistry.UNFINISHED_AMMO.get()) 
                .loops(1);

        for (AssemblyStep step : config.steps) {
            switch (step.type) {
 
                case DEPLOY -> builder.addStep(DeployerApplicationRecipe::new, rb -> rb.require((Ingredient) step.data));
                case PRESS -> builder.addStep(PressingRecipe::new, rb -> rb);
                case SPOUT -> {
                    FluidData fd = (FluidData) step.data;
                    builder.addStep(FillingRecipe::new, rb -> rb.require(fd.fluid, fd.amount));
                }
            }
        }

 
        builder.addOutput(finishedStackOutput, 1.0f).build(writer);
    }

    private AmmoConfig config(String id) {
        AmmoConfig c = new AmmoConfig();
        AMMO_CONFIGS.put(id, c);
        return c;
    }

    private static class AmmoConfig {
        List<AssemblyStep> steps = new ArrayList<>();
        int outputCount = 4;
 
        public AmmoConfig addDeploy(net.minecraft.world.level.ItemLike item) {
            steps.add(new AssemblyStep(StepType.DEPLOY, Ingredient.of(item)));
            return this;
        }

 
        public AmmoConfig addPress() {
            steps.add(new AssemblyStep(StepType.PRESS, null));
            return this;
        }
 
        public AmmoConfig addSpout(net.minecraft.world.level.material.Fluid fluid, int amount) {
            steps.add(new AssemblyStep(StepType.SPOUT, new FluidData(fluid, amount)));
            return this;
        }

        public AmmoConfig outputCount(int count) {
            this.outputCount = count;
            return this;
        }
    }

    private enum StepType { DEPLOY, PRESS, SPOUT }

    private record AssemblyStep(StepType type, Object data) {}

    private record FluidData(net.minecraft.world.level.material.Fluid fluid, int amount) {}


    private Ingredient createPartialNbtIngredient(ItemStack stack) {
        return new Ingredient(java.util.stream.Stream.of(new Ingredient.ItemValue(stack))) {
            @Override
            public com.google.gson.JsonElement toJson() {
                com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                json.addProperty("type", "forge:partial_nbt");
                json.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());

 
                if (stack.hasTag()) {
                    json.add("nbt", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, stack.getTag()));
                }

                return json;
            }
        };
    }









}
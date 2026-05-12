package euphy.upo.sentrymechanicalarm.registry;

import euphy.upo.sentrymechanicalarm.recipe.ClipboardCopyRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SentryRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "sentrymechanicalarm");

    public static final RegistryObject<RecipeSerializer<ClipboardCopyRecipe>> CLIPBOARD_COPY =
            SERIALIZERS.register("clipboard_copy",
                    () -> new SimpleCraftingRecipeSerializer<>(ClipboardCopyRecipe::new));

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
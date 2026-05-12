package euphy.upo.sentrymechanicalarm.registry;

import euphy.upo.sentrymechanicalarm.recipe.ClipboardCopyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SentryRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, "sentrymechanicalarm");

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ClipboardCopyRecipe>> CLIPBOARD_COPY =
            SERIALIZERS.register("clipboard_copy",
                    () -> new SimpleCraftingRecipeSerializer<>(ClipboardCopyRecipe::new));

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
package euphy.upo.sentrymechanicalarm.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SentryPaintings {


    public static final DeferredRegister<PaintingVariant> PAINTING_VARIANTS =
            DeferredRegister.create(Registries.PAINTING_VARIANT, "sentrymechanicalarm");


    public static final DeferredHolder<PaintingVariant, PaintingVariant> SEVENTH_CHAMBER =
            PAINTING_VARIANTS.register("seventh_chamber", () -> new PaintingVariant(32, 32, ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "painting/seventh_chamber")));

    public static void register(IEventBus eventBus) {
        PAINTING_VARIANTS.register(eventBus);
    }
}
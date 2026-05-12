package euphy.upo.sentrymechanicalarm.registry;

import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SentryPaintings {

 
    public static final DeferredRegister<PaintingVariant> PAINTING_VARIANTS =
            DeferredRegister.create(ForgeRegistries.PAINTING_VARIANTS, "sentrymechanicalarm");

 
    public static final RegistryObject<PaintingVariant> SENTRY_OPS =
            PAINTING_VARIANTS.register("sentry_ops", () -> new PaintingVariant(32, 32));

    public static void register(IEventBus eventBus) {
        PAINTING_VARIANTS.register(eventBus);
    }
}
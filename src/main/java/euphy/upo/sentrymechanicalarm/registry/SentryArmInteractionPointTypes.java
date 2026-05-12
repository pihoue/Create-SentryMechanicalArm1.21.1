package euphy.upo.sentrymechanicalarm.registry;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm; 
import euphy.upo.sentrymechanicalarm.content.SentryArmInteraction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SentryArmInteractionPointTypes {

 
    public static final DeferredRegister<ArmInteractionPointType> TYPES = DeferredRegister.create(
            CreateRegistries.ARM_INTERACTION_POINT_TYPE,
            "sentrymechanicalarm" 
    );

 
    public static final DeferredHolder<ArmInteractionPointType, ArmInteractionPointType> SENTRY_POINT = TYPES.register(
            "sentry_arm_point",
            () -> new SentryArmInteraction.Type(
                    ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_arm_point")
            )
    );

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
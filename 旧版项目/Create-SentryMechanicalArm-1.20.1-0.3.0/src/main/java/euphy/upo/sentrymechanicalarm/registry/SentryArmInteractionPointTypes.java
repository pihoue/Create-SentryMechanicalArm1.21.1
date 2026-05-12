package euphy.upo.sentrymechanicalarm.registry;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm; 
import euphy.upo.sentrymechanicalarm.content.SentryArmInteraction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class SentryArmInteractionPointTypes {

 
    public static final DeferredRegister<ArmInteractionPointType> TYPES = DeferredRegister.create(
            CreateRegistries.ARM_INTERACTION_POINT_TYPE,
            "sentrymechanicalarm" 
    );

 
    public static final RegistryObject<ArmInteractionPointType> SENTRY_POINT = TYPES.register(
            "sentry_arm_point",
            () -> new SentryArmInteraction.Type(
                    new ResourceLocation("sentrymechanicalarm", "sentry_arm_point")
            )
    );

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
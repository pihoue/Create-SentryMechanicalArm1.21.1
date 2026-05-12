package euphy.upo.sentrymechanicalarm.registry;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SentryMechanicalArm.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> FIRE_CONTROL_DATA =
            register("fire_control_data", builder -> builder
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> SENTRY_HELD_ITEM_DATA =
            register("sentry_held_item_data", builder -> builder
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> AMMO_ITEM_DATA =
            register("ammo_item_data", builder -> builder
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> GENERIC_ITEM_DATA =
            register("generic_item_data", builder -> builder
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return DATA_COMPONENT_TYPES.register(name, () -> builder.apply(DataComponentType.builder()).build());
    }

    public static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}
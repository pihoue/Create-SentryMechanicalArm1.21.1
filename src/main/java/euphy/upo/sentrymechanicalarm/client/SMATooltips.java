package euphy.upo.sentrymechanicalarm.client;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;

import static euphy.upo.sentrymechanicalarm.SentryMechanicalArm.MODID;

public class SMATooltips {

    public static void init() {
        register(SentryRegistry.FIRE_CONTROL_CLIPBOARD.getId(), SentryRegistry.FIRE_CONTROL_CLIPBOARD.get());
    }

    private static void register(ResourceLocation id, net.minecraft.world.item.Item item) {
        TooltipModifier.REGISTRY.register(
                item,
                new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
        );
    }
}

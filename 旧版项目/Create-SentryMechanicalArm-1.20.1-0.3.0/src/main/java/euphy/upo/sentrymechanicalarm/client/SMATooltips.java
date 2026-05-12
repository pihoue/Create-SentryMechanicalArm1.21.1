package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.createmod.catnip.lang.FontHelper;

public class SMATooltips {

    public static void init() {
        register(SentryRegistry.FIRE_CONTROL_CLIPBOARD);
    }

    private static void register(ItemEntry<?> item) {
        TooltipModifier.REGISTRY.register(
                item.get(),
                new ItemDescription.Modifier(item.get(), FontHelper.Palette.STANDARD_CREATE)
        );
    }

}

package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

public class AmmoRecipeConfig {

    public enum AmmoCategory {
        PISTOL,
        RIFLE,
        HEAVY,
        SHOTGUN,
        DEFAULT
    }

    public record Config(
            AmmoCategory category,
            List<Item> assemblySteps,
            int outputCount
    ) {}

    private static final List<Item> CP = List.of(AllItems.COPPER_SHEET.get());
    private static final List<Item> IR = List.of(AllItems.IRON_SHEET.get());
    private static final List<Item> GP = List.of(Items.GUNPOWDER);

    private static List<Item> steps(int repeat, List<Item> base) {
        var result = new java.util.ArrayList<Item>();
        for (int i = 0; i < repeat; i++) result.addAll(base);
        result.addAll(GP);
        return List.copyOf(result);
    }

    private static List<Item> heavySteps(List<Item> extra) {
        var result = new java.util.ArrayList<Item>();
        result.add(Items.COPPER_BLOCK);
        result.add(AllItems.IRON_SHEET.get());
        result.addAll(extra);
        result.add(Items.GUNPOWDER);
        return List.copyOf(result);
    }

    public static final Config DFLT_PISTOL = new Config(AmmoCategory.PISTOL, steps(2, CP), 20);
    public static final Config DFLT_RIFLE = new Config(AmmoCategory.RIFLE, steps(2, IR), 45);
    public static final Config DFLT_HEAVY = new Config(AmmoCategory.HEAVY,
            heavySteps(List.of(Items.TNT, Items.BLAZE_POWDER)), 8);
    public static final Config DFLT_SHOTGUN = new Config(AmmoCategory.SHOTGUN,
            List.of(AllItems.COPPER_SHEET.get(), AllItems.COPPER_SHEET.get(), Items.PAPER, Items.GUNPOWDER), 12);
    public static final Config DFLT_DEFAULT = new Config(AmmoCategory.DEFAULT, steps(2, CP), 10);

    private static final Map<String, Config> OVERRIDES = Map.ofEntries(
            Map.entry("9mm", new Config(AmmoCategory.PISTOL, steps(2, CP), 25)),
            Map.entry("45acp", new Config(AmmoCategory.PISTOL, steps(2, CP), 20)),
            Map.entry("46x30", new Config(AmmoCategory.PISTOL, steps(2, CP), 20)),
            Map.entry("57x28", new Config(AmmoCategory.PISTOL, steps(2, CP), 20)),
            Map.entry("357mag", new Config(AmmoCategory.PISTOL, steps(2, CP), 10)),
            Map.entry("22wmr", new Config(AmmoCategory.PISTOL, steps(2, CP), 30)),
            Map.entry("500mag", new Config(AmmoCategory.HEAVY,
                    heavySteps(List.of(Items.TNT, Items.BLAZE_POWDER, Items.LAPIS_LAZULI)), 6)),
            Map.entry("50ae", new Config(AmmoCategory.HEAVY,
                    heavySteps(List.of(Items.TNT)), 9)),
            Map.entry("50bmg", new Config(AmmoCategory.HEAVY,
                    heavySteps(List.of(Items.TNT, Items.BLAZE_POWDER, Items.LAPIS_LAZULI, Items.MAGMA_CREAM)), 6)),
            Map.entry("12g", DFLT_SHOTGUN),
            Map.entry("gauge", DFLT_SHOTGUN),
            Map.entry("shotgun", DFLT_SHOTGUN),
            Map.entry("shell", DFLT_SHOTGUN),
            Map.entry("308", new Config(AmmoCategory.RIFLE, steps(3, IR), 60)),
            Map.entry("338", new Config(AmmoCategory.RIFLE, steps(3, IR), 40)),
            Map.entry("556x45", new Config(AmmoCategory.RIFLE, steps(2, IR), 50)),
            Map.entry("762x25", new Config(AmmoCategory.RIFLE, steps(2, IR), 45)),
            Map.entry("762x39", new Config(AmmoCategory.RIFLE, steps(2, IR), 45)),
            Map.entry("762x54", new Config(AmmoCategory.RIFLE, steps(2, IR), 45)),
            Map.entry("545x39", new Config(AmmoCategory.RIFLE, steps(2, IR), 50)),
            Map.entry("58x42", new Config(AmmoCategory.RIFLE, steps(2, IR), 50)),
            Map.entry("792x57", new Config(AmmoCategory.RIFLE, steps(2, IR), 45)),
            Map.entry("68x51", new Config(AmmoCategory.RIFLE, steps(2, IR), 45)),
            Map.entry("30_06", new Config(AmmoCategory.RIFLE, steps(3, IR), 40)),
            Map.entry("45_70", new Config(AmmoCategory.RIFLE, steps(2, IR), 30)),
            Map.entry("40mm", new Config(AmmoCategory.HEAVY,
                    heavySteps(List.of(Items.TNT, Items.BLAZE_POWDER, Items.MAGMA_CREAM)), 2)),
            Map.entry("rpg", new Config(AmmoCategory.HEAVY,
                    heavySteps(List.of(Items.TNT, Items.TNT, Items.BLAZE_POWDER, Items.MAGMA_CREAM)), 1)),
            Map.entry("magnum", new Config(AmmoCategory.HEAVY,
                    List.of(AllItems.IRON_SHEET.get(), AllItems.IRON_SHEET.get(), Items.TNT, Items.GUNPOWDER), 8))
    );

    public static Config getOverride(ResourceLocation ammoId) {
        String path = ammoId.getPath().toLowerCase();
        for (var entry : OVERRIDES.entrySet()) {
            if (path.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    public static Config getCategoryDefault(ResourceLocation ammoId) {
        String lower = ammoId.getPath().toLowerCase();

        if (lower.contains(".50") || lower.contains("50ae") || lower.contains("50bmg") || lower.contains("bmg")
                || lower.contains("magnum") || lower.contains("12.7") || lower.contains("40mm")
                || lower.contains("rpg") || lower.contains("rocket")) {
            return DFLT_HEAVY;
        }
        if (lower.contains("9mm") || lower.contains(".45") || lower.contains("45acp") || lower.contains("acp")
                || lower.contains("46x30") || lower.contains("57x28") || lower.contains("357mag")
                || lower.contains("pistol") || lower.contains("smg") || lower.contains("mp5") || lower.contains("glock")
                || lower.contains("22wmr") || lower.contains("500mag")) {
            return DFLT_PISTOL;
        }
        if (lower.contains("12g") || lower.contains("gauge") || lower.contains("shotgun") || lower.contains("shell")
                || lower.contains("buckshot")) {
            return DFLT_SHOTGUN;
        }
        if (lower.contains("308") || lower.contains("556") || lower.contains("5.56") || lower.contains("762")
                || lower.contains("7.62") || lower.contains("545x39") || lower.contains("58x42")
                || lower.contains("792x57") || lower.contains("68x51") || lower.contains("30_06")
                || lower.contains("45_70") || lower.contains("338") || lower.contains("rifle")
                || lower.contains("m4") || lower.contains("ak")) {
            return DFLT_RIFLE;
        }
        return DFLT_DEFAULT;
    }
}

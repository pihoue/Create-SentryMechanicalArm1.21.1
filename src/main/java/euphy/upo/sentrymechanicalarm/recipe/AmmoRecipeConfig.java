package euphy.upo.sentrymechanicalarm.recipe;

import com.simibubi.create.AllItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class AmmoRecipeConfig {
   private static final List<Item> CP = List.of((Item)AllItems.COPPER_SHEET.get());
   private static final List<Item> IR = List.of((Item)AllItems.IRON_SHEET.get());
   private static final List<Item> GP = List.of(Items.GUNPOWDER);
   public static final AmmoRecipeConfig.Config DFLT_PISTOL = new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.PISTOL, steps(2, CP), 20);
   public static final AmmoRecipeConfig.Config DFLT_RIFLE = new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 45);
   public static final AmmoRecipeConfig.Config DFLT_HEAVY = new AmmoRecipeConfig.Config(
      AmmoRecipeConfig.AmmoCategory.HEAVY, heavySteps(List.of(Items.TNT, Items.BLAZE_POWDER)), 8
   );
   public static final AmmoRecipeConfig.Config DFLT_SHOTGUN = new AmmoRecipeConfig.Config(
      AmmoRecipeConfig.AmmoCategory.SHOTGUN, List.of((Item)AllItems.COPPER_SHEET.get(), (Item)AllItems.COPPER_SHEET.get(), Items.PAPER, Items.GUNPOWDER), 12
   );
   public static final AmmoRecipeConfig.Config DFLT_DEFAULT = new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.DEFAULT, steps(2, CP), 10);
   private static final Map<String, AmmoRecipeConfig.Config> OVERRIDES = Map.ofEntries(
      Map.entry("9mm", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.PISTOL, steps(2, CP), 25)),
      Map.entry("45acp", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.PISTOL, steps(2, CP), 20)),
      Map.entry("46x30", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.PISTOL, steps(2, CP), 20)),
      Map.entry("57x28", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.PISTOL, steps(2, CP), 20)),
      Map.entry("357mag", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.PISTOL, steps(2, CP), 10)),
      Map.entry("22wmr", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.PISTOL, steps(2, CP), 30)),
      Map.entry(
         "500mag", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.HEAVY, heavySteps(List.of(Items.TNT, Items.BLAZE_POWDER, Items.LAPIS_LAZULI)), 6)
      ),
      Map.entry("50ae", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.HEAVY, heavySteps(List.of(Items.TNT)), 9)),
      Map.entry(
         "50bmg",
         new AmmoRecipeConfig.Config(
            AmmoRecipeConfig.AmmoCategory.HEAVY, heavySteps(List.of(Items.TNT, Items.BLAZE_POWDER, Items.LAPIS_LAZULI, Items.MAGMA_CREAM)), 6
         )
      ),
      Map.entry("12g", DFLT_SHOTGUN),
      Map.entry("gauge", DFLT_SHOTGUN),
      Map.entry("shotgun", DFLT_SHOTGUN),
      Map.entry("shell", DFLT_SHOTGUN),
      Map.entry("308", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(3, IR), 60)),
      Map.entry("338", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(3, IR), 40)),
      Map.entry("556x45", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 50)),
      Map.entry("762x25", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 45)),
      Map.entry("762x39", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 45)),
      Map.entry("762x54", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 45)),
      Map.entry("545x39", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 50)),
      Map.entry("58x42", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 50)),
      Map.entry("792x57", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 45)),
      Map.entry("68x51", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 45)),
      Map.entry("30_06", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(3, IR), 40)),
      Map.entry("45_70", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.RIFLE, steps(2, IR), 30)),
      Map.entry(
         "40mm", new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.HEAVY, heavySteps(List.of(Items.TNT, Items.BLAZE_POWDER, Items.MAGMA_CREAM)), 2)
      ),
      Map.entry(
         "rpg",
         new AmmoRecipeConfig.Config(AmmoRecipeConfig.AmmoCategory.HEAVY, heavySteps(List.of(Items.TNT, Items.TNT, Items.BLAZE_POWDER, Items.MAGMA_CREAM)), 1)
      ),
      Map.entry(
         "magnum",
         new AmmoRecipeConfig.Config(
            AmmoRecipeConfig.AmmoCategory.HEAVY, List.of((Item)AllItems.IRON_SHEET.get(), (Item)AllItems.IRON_SHEET.get(), Items.TNT, Items.GUNPOWDER), 8
         )
      )
   );

   private static List<Item> steps(int repeat, List<Item> base) {
      ArrayList<Item> result = new ArrayList<>();

      for (int i = 0; i < repeat; i++) {
         result.addAll(base);
      }

      result.addAll(GP);
      return List.copyOf(result);
   }

   private static List<Item> heavySteps(List<Item> extra) {
      ArrayList<Item> result = new ArrayList<>();
      result.add(Items.COPPER_BLOCK);
      result.add((Item)AllItems.IRON_SHEET.get());
      result.addAll(extra);
      result.add(Items.GUNPOWDER);
      return List.copyOf(result);
   }

   public static AmmoRecipeConfig.Config getOverride(ResourceLocation ammoId) {
      String path = ammoId.getPath().toLowerCase();

      for (Entry<String, AmmoRecipeConfig.Config> entry : OVERRIDES.entrySet()) {
         if (path.contains(entry.getKey())) {
            return entry.getValue();
         }
      }

      return null;
   }

   public static AmmoRecipeConfig.Config getCategoryDefault(ResourceLocation ammoId) {
      String lower = ammoId.getPath().toLowerCase();
      if (lower.contains(".50")
         || lower.contains("50ae")
         || lower.contains("50bmg")
         || lower.contains("bmg")
         || lower.contains("magnum")
         || lower.contains("12.7")
         || lower.contains("40mm")
         || lower.contains("rpg")
         || lower.contains("rocket")) {
         return DFLT_HEAVY;
      } else if (lower.contains("9mm")
         || lower.contains(".45")
         || lower.contains("45acp")
         || lower.contains("acp")
         || lower.contains("46x30")
         || lower.contains("57x28")
         || lower.contains("357mag")
         || lower.contains("pistol")
         || lower.contains("smg")
         || lower.contains("mp5")
         || lower.contains("glock")
         || lower.contains("22wmr")
         || lower.contains("500mag")) {
         return DFLT_PISTOL;
      } else if (lower.contains("12g") || lower.contains("gauge") || lower.contains("shotgun") || lower.contains("shell") || lower.contains("buckshot")) {
         return DFLT_SHOTGUN;
      } else {
         return !lower.contains("308")
               && !lower.contains("556")
               && !lower.contains("5.56")
               && !lower.contains("762")
               && !lower.contains("7.62")
               && !lower.contains("545x39")
               && !lower.contains("58x42")
               && !lower.contains("792x57")
               && !lower.contains("68x51")
               && !lower.contains("30_06")
               && !lower.contains("45_70")
               && !lower.contains("338")
               && !lower.contains("rifle")
               && !lower.contains("m4")
               && !lower.contains("ak")
            ? DFLT_DEFAULT
            : DFLT_RIFLE;
      }
   }

   public static enum AmmoCategory {
      PISTOL,
      RIFLE,
      HEAVY,
      SHOTGUN,
      DEFAULT;
   }

   public static record Config(AmmoRecipeConfig.AmmoCategory category, List<Item> assemblySteps, int outputCount) {
   }
}

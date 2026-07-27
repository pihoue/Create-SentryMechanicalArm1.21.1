package euphy.upo.sentrymechanicalarm;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public class SMAServerConfig {
   public static final ModConfigSpec SPEC;
   public static final BooleanValue ENABLE_DYNAMIC_RECIPES;
   public static final BooleanValue AUTO_MATCH_FROM_TACZ;
   public static final EnumValue<SMAServerConfig.StepScaling> STEP_SCALING;
   public static final IntValue FIXED_SCALE_FACTOR;

   static {
      Builder builder = new Builder();
      builder.push("dynamic_recipes");
      ENABLE_DYNAMIC_RECIPES = builder.comment(
            "Master switch for dynamic ammo recipe injection. Disable to remove all auto-generated ammo cutting/assembly/pressing recipes."
         )
         .define("enableDynamicRecipes", true);
      AUTO_MATCH_FROM_TACZ = builder.comment(
            "Whether to auto-extract materials and output count from TaCZ GunSmithTable recipes. When disabled, only AmmoRecipeConfig OVERRIDES + category fallback are used."
         )
         .define("autoMatchFromTaCZ", true);
      builder.pop();
      builder.push("step_scaling");
      STEP_SCALING = builder.comment(
            new String[]{
               "How to scale assembly step counts from TaCZ recipe ingredient amounts.",
               "RAW  - use the raw count (e.g. 110 copper steps for 9mm)",
               "GCD  - divide all counts and output by their greatest common divisor",
               "FIXED - divide all counts and output by fixedScaleFactor"
            }
         )
         .defineEnum("stepScaling", SMAServerConfig.StepScaling.GCD);
      FIXED_SCALE_FACTOR = builder.comment("Divisor used when stepScaling = FIXED").defineInRange("fixedScaleFactor", 10, 1, 1000);
      builder.pop();
      SPEC = builder.build();
   }

   public static enum StepScaling {
      RAW,
      GCD,
      FIXED;
   }
}

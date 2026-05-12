package euphy.upo.sentrymechanicalarm.util;

import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

import java.util.EnumMap;
import java.util.Map;

public class SentrySpriteShifts {

    public static final Map<DyeColor, SpriteShiftEntry> ARM_TEXTURES = new EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, SpriteShiftEntry> BASE_TEXTURES = new EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, SpriteShiftEntry> COG_TEXTURES = new EnumMap<>(DyeColor.class);
    static {
        for (DyeColor color : DyeColor.values()) {
            String colorName = color.getSerializedName();
            ResourceLocation targetColored = new ResourceLocation("sentrymechanicalarm", "block/colored/arm_" + colorName);
            ResourceLocation originalArm = new ResourceLocation("create", "block/mechanical_arm");
            SpriteShiftEntry armEntry = SpriteShifter.get(originalArm, targetColored);
            ARM_TEXTURES.put(color, armEntry);
            ResourceLocation originalBase = new ResourceLocation("sentrymechanicalarm", "block/sentry_base");
            SpriteShiftEntry baseEntry = SpriteShifter.get(originalBase, targetColored);
            BASE_TEXTURES.put(color, baseEntry);
            ResourceLocation targetCog = new ResourceLocation("sentrymechanicalarm", "block/colored/sentry_cog_" + colorName);
            ResourceLocation originalCog = new ResourceLocation("sentrymechanicalarm", "block/sentry_cog");
            SpriteShiftEntry cogEntry = net.createmod.catnip.render.SpriteShifter.get(originalCog, targetCog);
            COG_TEXTURES.put(color, cogEntry);
        }
    }

    public static void init() {}
}

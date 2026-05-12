package euphy.upo.sentrymechanicalarm.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;

public class DataGenerators {

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        generator.addProvider(true, new SentryRecipeProvider(packOutput));
    }
}
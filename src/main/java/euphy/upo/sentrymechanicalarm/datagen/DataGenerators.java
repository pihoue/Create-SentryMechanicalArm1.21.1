package euphy.upo.sentrymechanicalarm.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public class DataGenerators {

    public static void gatherData(GatherDataEvent event) {
        // Datagen disabled - requires NeoForge 1.21.1 recipe provider updates
        // DataGenerator generator = event.getGenerator();
        // PackOutput packOutput = generator.getPackOutput();
        // generator.addProvider(true, new SentryRecipeProvider(packOutput));
    }
}
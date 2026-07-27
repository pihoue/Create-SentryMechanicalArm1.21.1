package euphy.upo.sentrymechanicalarm;

import com.mojang.logging.LogUtils;
import euphy.upo.sentrymechanicalarm.datagen.DataGenerators;
import euphy.upo.sentrymechanicalarm.registry.ModDataComponents;
import euphy.upo.sentrymechanicalarm.registry.SentryArmInteractionPointTypes;
import euphy.upo.sentrymechanicalarm.registry.SentryRecipeSerializers;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod("sentrymechanicalarm")
public class SentryMechanicalArm {
   public static final String MODID = "sentrymechanicalarm";
   public static final Logger LOGGER = LogUtils.getLogger();

   public SentryMechanicalArm(IEventBus modEventBus, ModContainer modContainer) {
      SentryRegistry.register(modEventBus);
      SentryRecipeSerializers.register(modEventBus);
      SentryArmInteractionPointTypes.register(modEventBus);
      ModDataComponents.register(modEventBus);
      modContainer.registerConfig(Type.SERVER, SMAServerConfig.SPEC);
      modEventBus.addListener(this::commonSetup);
      modEventBus.addListener(DataGenerators::gatherData);
   }

   private void commonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> {
         SentryRegistry.registerAllStressValues();
         SentryRegistry.registerMovementBehaviours();
      });
   }
}

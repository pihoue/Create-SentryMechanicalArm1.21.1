package euphy.upo.sentrymechanicalarm;

import com.mojang.logging.LogUtils;
import euphy.upo.sentrymechanicalarm.datagen.DataGenerators;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.registry.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;


@Mod(SentryMechanicalArm.MODID)
public class SentryMechanicalArm
{
    public static final String MODID = "sentrymechanicalarm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SentryMechanicalArm(IEventBus modEventBus, ModContainer modContainer)
    {
        SentryRegistry.register(modEventBus);
        SentryRecipeSerializers.register(modEventBus);
        SentryArmInteractionPointTypes.register(modEventBus);
        ModDataComponents.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(DataGenerators::gatherData);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(SentryRegistry::registerAllStressValues);
    }


}

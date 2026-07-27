package euphy.upo.sentrymechanicalarm.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlRenderer;
import euphy.upo.sentrymechanicalarm.content.SentryArmRenderer;
import euphy.upo.sentrymechanicalarm.ponder.SMAPonderPlugin;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.SentrySpriteShifts;
import java.util.Map;
import net.createmod.catnip.render.SuperByteBufferCache;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted;
import net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult;
import net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional;
import org.slf4j.Logger;

@EventBusSubscriber(
   modid = "sentrymechanicalarm",
   value = {Dist.CLIENT}
)
public class SentryMechanicalArmClient {
   private static final Logger LOGGER = SentryMechanicalArm.LOGGER;
   private static final PartialModel[] ALL_PARTIALS = new PartialModel[]{
      SentryPartialModels.SENTRU_COG,
      SentryPartialModels.SENTRU_BASE,
      SentryPartialModels.ARM_LOWER_BODY,
      SentryPartialModels.ARM_UPPER_BODY,
      SentryPartialModels.ARM_CLAW_BASE,
      SentryPartialModels.ARM_CLAW_GRIP_UPPER,
      SentryPartialModels.ARM_CLAW_GRIP_LOWER,
      SentryPartialModels.BLAZE_FIRE_CONTROLLER_HEAD,
      SentryPartialModels.RING,
      SentryPartialModels.CLIPBOARD,
      SentryPartialModels.BASE
   };

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      SentryPartialModels.init();
      LOGGER.info("SentryPartialModels initialized");
      event.enqueueWork(() -> {
         SentrySpriteShifts.init();
         SMATooltips.init();
         PonderIndex.addPlugin(new SMAPonderPlugin());
         LOGGER.info("Client setup completed, BlockEntityType: {}", SentryRegistry.SENTRY_ARM_BE.get());
      });
   }

   @SubscribeEvent
   public static void onRegisterRenderers(RegisterRenderers event) {
      event.registerBlockEntityRenderer((BlockEntityType)SentryRegistry.SENTRY_ARM_BE.get(), SentryArmRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType)SentryRegistry.BLAZE_FIRE_CONTROL_BE.get(), BlazeFireControlRenderer::new);
      LOGGER.info("SentryArmRenderer registered for SENTRY_ARM_BE");
   }

   @SubscribeEvent
   public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
      event.register((MenuType)SentryRegistry.FIRE_CONTROL_MENU.get(), FireControlScreen::new);
   }

   @SubscribeEvent
   public static void onRegisterAdditional(RegisterAdditional event) {
      for (PartialModel partial : ALL_PARTIALS) {
         event.register(ModelResourceLocation.standalone(partial.modelLocation()));
      }
   }

   @SubscribeEvent
   public static void onModifyBakingResult(ModifyBakingResult event) {
      Map<ModelResourceLocation, BakedModel> models = event.getModels();
      BakedModel missingModel = models.get(ModelResourceLocation.standalone(ResourceLocation.withDefaultNamespace("builtin/missing")));

      for (PartialModel partial : ALL_PARTIALS) {
         ModelResourceLocation loc = ModelResourceLocation.standalone(partial.modelLocation());
         if (!models.containsKey(loc) || models.get(loc) == null) {
            LOGGER.warn("PartialModel {} was overridden/broken by a resource pack. Injecting fallback model.", partial.modelLocation());
            if (missingModel != null) {
               models.put(loc, missingModel);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onModelBakingCompleted(BakingCompleted event) {
      SuperByteBufferCache.getInstance().invalidate();

      for (PartialModel partial : ALL_PARTIALS) {
         if (partial.get() == null) {
            LOGGER.warn("PartialModel {} is still null after baking and fallback injection!", partial.modelLocation());
         }
      }
   }
}

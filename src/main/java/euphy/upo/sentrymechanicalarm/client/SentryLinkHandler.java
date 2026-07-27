package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.network.SentryLinkPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "sentrymechanicalarm",
   value = {Dist.CLIENT}
)
public class SentryLinkHandler {
   private static BlockPos firstSelectedPos = null;
   private static boolean isFirstSentry = false;
   private static final DustParticleOptions PARTICLE_GREEN = new DustParticleOptions(new Vector3f(0.0F, 1.0F, 0.0F), 1.0F);
   private static final DustParticleOptions PARTICLE_RED = new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 1.0F);
   private static final DustParticleOptions PARTICLE_CYAN = new DustParticleOptions(new Vector3f(0.0F, 1.0F, 1.0F), 1.0F);

   @SubscribeEvent
   public static void onBlockRightClick(RightClickBlock event) {
      if (event.getHand() == InteractionHand.MAIN_HAND) {
         if (event.getLevel().isClientSide) {
            Player player = event.getEntity();
            ItemStack heldItem = player.getMainHandItem();
            if (!heldItem.getItem().getDescriptionId().contains("wrench")) {
               if (firstSelectedPos != null) {
                  firstSelectedPos = null;
               }
            } else {
               Level level = event.getLevel();
               BlockPos pos = event.getPos();
               boolean isSentry = level.getBlockEntity(pos) instanceof SentryArmBlockEntity;
               boolean isFireControl = level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity;
               if (isSentry || isFireControl) {
                  if (firstSelectedPos == null) {
                     firstSelectedPos = pos;
                     isFirstSentry = isSentry;
                     if (isSentry) {
                        player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.select_sentry"), true);
                     } else {
                        player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.select_fire_control"), true);
                     }

                     event.setCanceled(true);
                  } else {
                     if (pos.equals(firstSelectedPos)) {
                        firstSelectedPos = null;
                        player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.cancelled"), true);
                        event.setCanceled(true);
                        return;
                     }

                     boolean validPair = isFirstSentry && isFireControl || !isFirstSentry && isSentry;
                     if (validPair) {
                        PacketDistributor.sendToServer(new SentryLinkPacket(firstSelectedPos, pos), new CustomPacketPayload[0]);
                        firstSelectedPos = null;
                        event.setCanceled(true);
                     } else {
                        player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.invalid_pair"), true);
                        firstSelectedPos = null;
                        event.setCanceled(true);
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_PARTICLES) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null) {
            ItemStack heldItem = mc.player.getMainHandItem();
            boolean hasWrench = heldItem.getItem().getDescriptionId().contains("wrench");
            if (hasWrench || firstSelectedPos != null) {
               BlockPos lookPos = null;
               if (mc.hitResult instanceof BlockHitResult blockHit) {
                  lookPos = blockHit.getBlockPos();
                  if (firstSelectedPos != null) {
                     Vec3 start = Vec3.atCenterOf(firstSelectedPos);
                     Vec3 end = Vec3.atCenterOf(lookPos);
                     double distSqr = start.distanceToSqr(end);
                     boolean inRange = distSqr <= 36.0;
                     DustParticleOptions particle = inRange ? PARTICLE_GREEN : PARTICLE_RED;
                     renderParticleLine(mc.level, start, end, particle);
                     renderOverlayText(event, mc, lookPos, inRange);
                  } else {
                     BlockEntity be = mc.level.getBlockEntity(lookPos);
                     if (be instanceof SentryArmBlockEntity sentry) {
                        BlockPos targetPos = sentry.getConnectedFireControl();
                        if (targetPos != null && isValidFireControl(mc.level, targetPos)) {
                           renderParticleLine(mc.level, Vec3.atCenterOf(lookPos), Vec3.atCenterOf(sentry.getProjectedFireControlPos()), PARTICLE_CYAN);
                        }
                     } else if (be instanceof BlazeFireControlBlockEntity fc) {
                        for (BlockPos checkPos : fc.getProjectedSentryPositions()) {
                           renderParticleLine(mc.level, Vec3.atCenterOf(lookPos), Vec3.atCenterOf(checkPos), PARTICLE_CYAN);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean isValidFireControl(Level level, BlockPos pos) {
      return !level.isLoaded(pos) ? false : level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity;
   }

   private static void renderParticleLine(Level level, Vec3 start, Vec3 end, DustParticleOptions particle) {
      double dist = Math.sqrt(start.distanceToSqr(end));
      if (!(dist < 0.1)) {
         if (!(dist > 100.0)) {
            int steps = (int)(dist * 5.0);

            for (int i = 0; i <= steps; i++) {
               double lerp = (double)i / (double)steps;
               double x = start.x + (end.x - start.x) * lerp;
               double y = start.y + (end.y - start.y) * lerp;
               double z = start.z + (end.z - start.z) * lerp;
               if (level.random.nextInt(4) == 0) {
                  level.addParticle(particle, x, y, z, 0.0, 0.0, 0.0);
               }
            }
         }
      }
   }

   private static void renderOverlayText(RenderLevelStageEvent event, Minecraft mc, BlockPos lookPos, boolean inRange) {
      PoseStack poseStack = event.getPoseStack();
      poseStack.pushPose();
      Vec3 camPos = event.getCamera().getPosition();
      boolean isTargetSentry = mc.level.getBlockEntity(lookPos) instanceof SentryArmBlockEntity;
      boolean isTargetControl = mc.level.getBlockEntity(lookPos) instanceof BlazeFireControlBlockEntity;
      String text = "";
      int textColor = -1;
      if (inRange) {
         if (isFirstSentry && isTargetControl) {
            text = Component.translatable("overlay.sentrymechanicalarm.right_click_connect").getString();
         } else if (!isFirstSentry && isTargetSentry) {
            text = Component.translatable("overlay.sentrymechanicalarm.right_click_connect").getString();
         } else if (!isTargetSentry && !isTargetControl) {
            text = isFirstSentry
               ? Component.translatable("overlay.sentrymechanicalarm.select_fire_control").getString()
               : Component.translatable("overlay.sentrymechanicalarm.select_sentry_turret").getString();
         } else {
            text = Component.translatable("overlay.sentrymechanicalarm.invalid_target_type").getString();
         }
      } else {
         text = Component.translatable("overlay.sentrymechanicalarm.too_far").getString();
         textColor = -65536;
      }

      if (!text.isEmpty()) {
         poseStack.translate((double)lookPos.getX() + 0.5 - camPos.x, (double)lookPos.getY() + 1.5 - camPos.y, (double)lookPos.getZ() + 0.5 - camPos.z);
         poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
         poseStack.scale(-0.025F, -0.025F, 0.025F);
         float x = (float)(-mc.font.width(text)) / 2.0F;
         mc.font.drawInBatch(text, x, 0.0F, textColor, true, poseStack.last().pose(), mc.renderBuffers().bufferSource(), DisplayMode.SEE_THROUGH, 0, 15728880);
      }

      poseStack.popPose();
   }
}

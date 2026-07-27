package euphy.upo.sentrymechanicalarm.content;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import javax.annotation.Nullable;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class BlazeFireControlRenderer extends SafeBlockEntityRenderer<BlazeFireControlBlockEntity> {
   private static final Logger LOGGER = LogUtils.getLogger();

   public BlazeFireControlRenderer(Context context) {
   }

   protected void renderSafe(BlazeFireControlBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
      try {
         this.renderSafeInner(be, partialTicks, ms, buffer, light, overlay);
      } catch (Exception var8) {
         LOGGER.warn("Failed to render BlazeFireControlBlockEntity", var8);
      }
   }

   private void renderSafeInner(BlazeFireControlBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
      if (!(be.getLevel() instanceof VirtualRenderWorld)) {
         Level level = be.getLevel();
         if (level != null) {
            float renderTime = AnimationTickHolder.getRenderTime(level);
            float animation = be.headAnimation.getValue(partialTicks) * 0.175F;
            float horizontalAngle = AngleHelper.rad((double)be.headAngle.getValue(partialTicks));
            int seed = be.hashCode();
            float seededRenderTime = renderTime + (float)(seed % 13) * 16.0F;
            float offset = Mth.sin(seededRenderTime / 16.0F % (float) (Math.PI * 2)) / 16.0F;
            float headY = offset - animation * 0.75F;
            ms.pushPose();
            renderShared(ms, null, buffer, be.getBlockState(), be.inventory.getStackInSlot(0), headY, horizontalAngle);
            if (be.hasBoundScope()) {
               renderScopeItem(ms, buffer, light, headY, horizontalAngle, be.getBlockState());
            }

            if (be != null && !be.currentEmoticon.isEmpty()) {
               ms.pushPose();
               float maxTime = 60.0F;
               float currentTime = (float)be.emoticonTimer - partialTicks;
               float progress = 1.0F - currentTime / maxTime;
               progress = Mth.clamp(progress, 0.0F, 1.0F);
               double floatHeight = 0.5;
               double baseY = 1.2;
               double currentY = baseY + (double)progress * floatHeight;
               ms.translate(0.5 + (double)be.msgOffsetX, currentY, 0.5 + (double)be.msgOffsetZ);
               ms.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
               float scale = 0.025F;
               ms.scale(-scale, -scale, scale);
               float alpha;
               if (progress < 0.4F) {
                  alpha = progress / 0.2F;
               } else if (progress > 0.6F) {
                  alpha = (1.0F - progress) / 0.2F;
               } else {
                  alpha = 1.0F;
               }

               alpha = Mth.clamp(alpha, 0.0F, 1.0F);
               int baseColor = be.msgColor & 16777215;
               int alphaInt = (int)(alpha * 255.0F) & 0xFF;
               int finalColor = alphaInt << 24 | baseColor;
               Font font = Minecraft.getInstance().font;
               String text = be.currentEmoticon;
               float width = (float)font.width(text);
               font.drawInBatch(Component.literal(text), -width / 2.0F, 0.0F, finalColor, false, ms.last().pose(), buffer, DisplayMode.NORMAL, 0, 15728880);
               ms.popPose();
            }

            ms.popPose();
         }
      }
   }

   public static void renderShared(
      PoseStack ms,
      @Nullable PoseStack modelTransform,
      MultiBufferSource bufferSource,
      BlockState state,
      ItemStack itemStack,
      float headY,
      float horizontalAngle
   ) {
      if (SentryPartialModels.BLAZE_FIRE_CONTROLLER_HEAD.get() != null && SentryPartialModels.RING.get() != null && SentryPartialModels.CLIPBOARD.get() != null
         )
       {
         SuperByteBuffer headBuffer = CachedBuffers.partial(SentryPartialModels.BLAZE_FIRE_CONTROLLER_HEAD, state);
         if (modelTransform != null) {
            headBuffer.transform(modelTransform);
         }

         headBuffer.translate(0.0F, headY, 0.0F);
         draw(headBuffer, horizontalAngle, ms, bufferSource.getBuffer(RenderType.cutout()));
         PartialModel ringModel = SentryPartialModels.RING;
         SuperByteBuffer ringBuffer = CachedBuffers.partial(ringModel, state);
         if (modelTransform != null) {
            ringBuffer.transform(modelTransform);
         }

         ringBuffer.translate(0.0F, headY, 0.0F);
         ringBuffer.light(15728880);
         draw(ringBuffer, horizontalAngle, ms, bufferSource.getBuffer(RenderType.cutout()));
         if (!itemStack.isEmpty()) {
            float scale = 0.5F;
            float offsetX = 0.3F;
            float offsetY = 0.3F;
            float offsetZ = 0.15F;
            float rotX = 65.0F;
            float rotY = 180.0F;
            float rotZ = 35.0F;
            PartialModel clipboardModel = SentryPartialModels.CLIPBOARD;
            SuperByteBuffer clipboardBuffer = CachedBuffers.partial(clipboardModel, state);
            if (modelTransform != null) {
               clipboardBuffer.transform(modelTransform);
            }

            ((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)clipboardBuffer.translate(
                                    0.0F, headY, 0.0F
                                 ))
                                 .rotateCentered(horizontalAngle, Direction.UP))
                              .translate(offsetX, offsetY, offsetZ))
                           .rotate(AngleHelper.rad((double)rotX), Axis.X))
                        .rotate(AngleHelper.rad((double)rotY), Axis.Y))
                     .rotate(AngleHelper.rad((double)rotZ), Axis.Z))
                  .scale(scale))
               .light(240);
            clipboardBuffer.renderInto(ms, bufferSource.getBuffer(RenderType.cutout()));
         }
      }
   }

   public static void renderScopeItem(PoseStack ms, MultiBufferSource bufferSource, int light, float headY, float horizontalAngle, BlockState state) {
      ItemStack scopeStack = new ItemStack((ItemLike)SentryRegistry.SENTRY_SCOPE.get());
      if (!scopeStack.isEmpty()) {
         ms.pushPose();
         float scale = 0.4F;
         float offsetX = -0.3F;
         float offsetY = 0.15F;
         float offsetZ = 0.15F;
         ms.translate(0.5, 0.0, 0.5);
         ms.translate(0.0F, headY, 0.0F);
         ms.mulPose(com.mojang.math.Axis.YN.rotation(horizontalAngle));
         ms.translate(offsetX, offsetY, offsetZ);
         ms.scale(scale, scale, scale);
         ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(65.0F));
         ms.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(35.0F));
         ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
         itemRenderer.renderStatic(scopeStack, ItemDisplayContext.GROUND, light, 0, ms, bufferSource, Minecraft.getInstance().level, 0);
         ms.popPose();
      }
   }

   private static void draw(SuperByteBuffer buffer, float horizontalAngle, PoseStack ms, VertexConsumer vc) {
      ((SuperByteBuffer)buffer.rotateCentered(horizontalAngle, Direction.UP)).renderInto(ms, vc);
   }

   public static void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices, MultiBufferSource buffer) {
      FireControlMovementBehaviour.FireControlData data = FireControlMovementBehaviour.getOrInitData(context);
      ItemStack itemToRender = data.displayItem;
      float horizontalAngle = AngleHelper.rad((double)data.headAngle.getValue(AnimationTickHolder.getPartialTicks(context.world)));
      float renderTime = AnimationTickHolder.getRenderTime(context.world);
      int seed = context.localPos.hashCode();
      float seededRenderTime = renderTime + (float)(seed % 13) * 16.0F;
      float offset = Mth.sin(seededRenderTime / 16.0F % (float) (Math.PI * 2)) / 16.0F;
      renderShared(matrices.getViewProjection(), matrices.getModel(), buffer, context.state, itemToRender, offset, horizontalAngle);
   }
}

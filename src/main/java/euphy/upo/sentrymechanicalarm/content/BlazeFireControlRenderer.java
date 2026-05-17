package euphy.upo.sentrymechanicalarm.content;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class BlazeFireControlRenderer extends SafeBlockEntityRenderer<BlazeFireControlBlockEntity> {

    public BlazeFireControlRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(BlazeFireControlBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() instanceof VirtualRenderWorld) {
            return;
        }

        Level level = be.getLevel();
        if (level == null) return;

        float renderTime = AnimationTickHolder.getRenderTime(level);
        float animation = be.headAnimation.getValue(partialTicks) * .175f;
        float horizontalAngle = AngleHelper.rad(be.headAngle.getValue(partialTicks));

        int seed = be.hashCode();
        float seededRenderTime = renderTime + (seed % 13) * 16f;
        float offset = Mth.sin((seededRenderTime / 16f) % (2 * Mth.PI)) / 16f; 
        float headY = offset - (animation * .75f); 

        ms.pushPose();

        renderShared(ms, null, buffer, be.getBlockState(), be.inventory.getStackInSlot(0), headY, horizontalAngle);

        if (be.hasBoundScope()) {
            renderScopeItem(ms, buffer, light, headY, horizontalAngle, be.getBlockState());
        }

        if (be != null && !be.currentEmoticon.isEmpty()) {
            ms.pushPose();

            float maxTime = (float) be.MAX_EMOTICON_TIME;
            float currentTime = be.emoticonTimer - partialTicks;
            float progress = 1.0f - (currentTime / maxTime);
            progress = Mth.clamp(progress, 0.0f, 1.0f);
            double floatHeight = 0.5;
            double baseY = 1.2;
            double currentY = baseY + (progress * floatHeight);
            ms.translate(0.5 + be.msgOffsetX, currentY, 0.5 + be.msgOffsetZ);
            ms.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            float scale = 0.025f;
            ms.scale(-scale, -scale, scale);
            float alpha;
            if (progress < 0.4f) {
                alpha = progress / 0.2f;
            } else if (progress > 0.6f) {
                alpha = (1.0f - progress) / 0.2f;
            } else {
                alpha = 1.0f;
            }
            alpha = Mth.clamp(alpha, 0.0f, 1.0f);
            int baseColor = be.msgColor & 0x00FFFFFF;
            int alphaInt = (int) (alpha * 255) & 0xFF;
            int finalColor = (alphaInt << 24) | baseColor;
            Font font = Minecraft.getInstance().font;
            String text = be.currentEmoticon;
            float width = font.width(text);

            font.drawInBatch(
                    Component.literal(text),
                    -width / 2,
                    0,
                    finalColor,
                    false,
                    ms.last().pose(),
                    buffer,
                    Font.DisplayMode.NORMAL,
                    0,
                    LightTexture.FULL_BRIGHT
            );
            ms.popPose();

        }
        ms.popPose();
    }

    public static void renderShared(PoseStack ms, @Nullable PoseStack modelTransform, MultiBufferSource bufferSource,
                                    BlockState state, ItemStack itemStack, float headY, float horizontalAngle) {

        PartialModel headModel = SentryPartialModels.BLAZE_FIRE_CONTROLLER_HEAD;
        SuperByteBuffer headBuffer = CachedBuffers.partial(headModel, state);
        if (modelTransform != null) headBuffer.transform(modelTransform);
        headBuffer.translate(0, headY, 0);
        draw(headBuffer, horizontalAngle, ms, bufferSource.getBuffer(RenderType.cutout()));

        PartialModel ringModel = SentryPartialModels.RING;
        SuperByteBuffer ringBuffer = CachedBuffers.partial(ringModel, state);
        if (modelTransform != null) ringBuffer.transform(modelTransform);
        ringBuffer.translate(0, headY, 0);
        ringBuffer.light(LightTexture.FULL_BRIGHT);
        draw(ringBuffer, horizontalAngle, ms, bufferSource.getBuffer(RenderType.cutout()));

        if (!itemStack.isEmpty()) {
            float scale = 0.5f;
            float offsetX = 0.3f;
            float offsetY = 0.3f;
            float offsetZ = 0.15f;
            float rotX = 65.0f;
            float rotY = 180.0f;
            float rotZ = 35.0f;

            PartialModel clipboardModel = SentryPartialModels.CLIPBOARD;
            SuperByteBuffer clipboardBuffer = CachedBuffers.partial(clipboardModel, state);

            if (modelTransform != null) clipboardBuffer.transform(modelTransform);

            clipboardBuffer
                    .translate(0, headY, 0)
                    .rotateCentered(horizontalAngle, Direction.UP)
                    .translate(offsetX, offsetY, offsetZ)
                    .rotate(AngleHelper.rad(rotX), Direction.Axis.X)
                    .rotate(AngleHelper.rad(rotY), Direction.Axis.Y)
                    .rotate(AngleHelper.rad(rotZ), Direction.Axis.Z)
                    .scale(scale)
                    .light(LightTexture.FULL_BLOCK);

            clipboardBuffer.renderInto(ms, bufferSource.getBuffer(RenderType.cutout()));
        }
    }

    public static void renderScopeItem(PoseStack ms, MultiBufferSource bufferSource, int light,
                                        float headY, float horizontalAngle, BlockState state) {
        ItemStack scopeStack = new ItemStack(SentryRegistry.SENTRY_SCOPE.get());
        if (scopeStack.isEmpty()) return;

        ms.pushPose();
        float scale = 0.4f;
        float offsetX = -0.3f;
        float offsetY = 0.15f;
        float offsetZ = 0.15f;

        ms.translate(0.5, 0, 0.5);
        ms.translate(0, headY, 0);
        ms.mulPose(com.mojang.math.Axis.YN.rotation(horizontalAngle));
        ms.translate(offsetX, offsetY, offsetZ);
        ms.scale(scale, scale, scale);
        ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(65));
        ms.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(35));

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(scopeStack, ItemDisplayContext.GROUND, light, 0, ms, bufferSource, Minecraft.getInstance().level, 0);
        ms.popPose();
    }

    private static void draw(SuperByteBuffer buffer, float horizontalAngle, PoseStack ms, VertexConsumer vc) {
        buffer.rotateCentered(horizontalAngle, Direction.UP)
                .renderInto(ms, vc);
    }
    public static void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                           ContraptionMatrices matrices, MultiBufferSource buffer) {

        FireControlMovementBehaviour.FireControlData data = FireControlMovementBehaviour.getOrInitData(context);

        ItemStack itemToRender = data.displayItem;
        float horizontalAngle = AngleHelper.rad(data.headAngle.getValue(AnimationTickHolder.getPartialTicks(context.world)));

        float renderTime = AnimationTickHolder.getRenderTime(context.world);
        int seed = context.localPos.hashCode();
        float seededRenderTime = renderTime + (seed % 13) * 16f;
        float offset = Mth.sin((seededRenderTime / 16f) % (2 * Mth.PI)) / 16f;
        float headY = offset;
        renderShared(
                matrices.getViewProjection(),
                matrices.getModel(),
                buffer,
                context.state,
                itemToRender,
                headY,
                horizontalAngle
        );
    }
}

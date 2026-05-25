package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockItem;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class BlazeFireControlItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation SCOPE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SentryMechanicalArm.MODID, "textures/item/blaze_fire_control_scope.png");

    public BlazeFireControlItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        boolean isUsing = mc.player != null && mc.player.isUsingItem() && mc.player.getUseItem() == stack;

        if (isUsing && displayContext != ItemDisplayContext.GUI) {
            ms.pushPose();
            ms.translate(0.5F, 1.0F, 0.0F);
            ms.scale(2.0F, 2.0F, 1.0F);
            RenderSystem.setShaderTexture(0, SCOPE_TEXTURE);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            BufferBuilder bb = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bb.addVertex(ms.last().pose(), -0.5F, -0.5F, 0.0F).setUv(0.0F, 0.0F);
            bb.addVertex(ms.last().pose(), 0.5F, -0.5F, 0.0F).setUv(1.0F, 0.0F);
            bb.addVertex(ms.last().pose(), 0.5F, 0.5F, 0.0F).setUv(1.0F, 1.0F);
            bb.addVertex(ms.last().pose(), -0.5F, 0.5F, 0.0F).setUv(0.0F, 1.0F);
            BufferUploader.drawWithShader(bb.build());
            ms.popPose();
            return;
        }

        BlockState state = SentryRegistry.BLAZE_FIRE_CONTROL.get().defaultBlockState();

        ms.pushPose();

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ms, buffer, packedLight, packedOverlay);

        float time = AnimationTickHolder.getRenderTime();
        float offset = Mth.sin((time / 16f) % (2 * Mth.PI)) / 16f;

        SuperByteBuffer headBuffer = CachedBuffers.partial(SentryPartialModels.BLAZE_FIRE_CONTROLLER_HEAD, state);
        if (headBuffer != null) {
            headBuffer.translate(0.0, offset, 0.0)
                    .rotateCentered(0, Direction.UP)
                    .light(LightTexture.FULL_BRIGHT)
                    .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        }
 
        SuperByteBuffer ringBuffer = CachedBuffers.partial(SentryPartialModels.RING, state);
        if (ringBuffer != null) {
            ringBuffer.translate(0.0, offset, 0.0)
                    .rotateCentered(0, Direction.UP)
                    .light(LightTexture.FULL_BRIGHT)
                    .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        }

        ms.popPose();
    }
}
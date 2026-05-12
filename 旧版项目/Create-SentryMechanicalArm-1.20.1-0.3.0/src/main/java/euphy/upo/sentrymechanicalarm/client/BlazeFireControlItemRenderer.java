package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class BlazeFireControlItemRenderer extends BlockEntityWithoutLevelRenderer {

    public BlazeFireControlItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffer, int packedLight, int packedOverlay) {

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
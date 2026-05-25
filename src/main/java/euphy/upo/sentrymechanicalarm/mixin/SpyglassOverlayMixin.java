package euphy.upo.sentrymechanicalarm.mixin;

import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class SpyglassOverlayMixin {

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void sentrymechanicalarm$cancelSpyglassOverlay(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUseItem().getItem() instanceof BlazeFireControlBlockItem) {
            ci.cancel();
        }
    }
}

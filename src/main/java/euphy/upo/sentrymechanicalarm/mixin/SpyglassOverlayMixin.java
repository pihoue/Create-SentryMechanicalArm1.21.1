package euphy.upo.sentrymechanicalarm.mixin;

import euphy.upo.sentrymechanicalarm.content.SentryScopeItem;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class SpyglassOverlayMixin {

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void sentrymechanicalarm$cancelSpyglassOverlay(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Item useItem = mc.player.getUseItem().getItem();
        if (useItem instanceof BlazeFireControlBlockItem || useItem instanceof SentryScopeItem) {
            ci.cancel();
        }
    }
}

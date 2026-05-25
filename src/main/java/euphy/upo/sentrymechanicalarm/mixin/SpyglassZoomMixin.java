package euphy.upo.sentrymechanicalarm.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class SpyglassZoomMixin {

    @Inject(method = "isScoping", at = @At("HEAD"), cancellable = true)
    private void sentrymechanicalarm$isScoping(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.isUsingItem() && self.getUseItem().getUseAnimation() == UseAnim.SPYGLASS) {
            cir.setReturnValue(true);
        }
    }
}

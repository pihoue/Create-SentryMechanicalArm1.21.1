package euphy.upo.sentrymechanicalarm.mixin;

import com.tacz.guns.client.gui.overlay.InteractKeyTextOverlay;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InteractKeyTextOverlay.class)
public class InteractKeyTextOverlayMixin {

    @Redirect(
            method = "renderBlockText",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/config/util/InteractKeyConfigRead;canInteractBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z"
            ),
            remap = false
    )
    private static boolean injectArmInteractCheck(BlockState state) {
        if (InteractKeyConfigRead.canInteractBlock(state)) {
            return true;
        }
        if (state.getBlock() instanceof SentryArmBlock) {
            return true;
        }
        return false;
    }
}
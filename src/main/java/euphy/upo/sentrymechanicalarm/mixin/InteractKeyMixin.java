package euphy.upo.sentrymechanicalarm.mixin;

import com.tacz.guns.client.input.InteractKey;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.network.SentryInteractPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InteractKey.class)
public class InteractKeyMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onInteractBlock(BlockHitResult blockHitResult, LocalPlayer player, Minecraft mc, CallbackInfo ci) {
        BlockPos blockPos = blockHitResult.getBlockPos();
        BlockState state = player.level().getBlockState(blockPos);
        if (state.getBlock() instanceof SentryArmBlock) {
            PacketDistributor.sendToServer(new SentryInteractPacket(blockPos, InteractionHand.MAIN_HAND, false));
            ci.cancel();
        }
    }
}
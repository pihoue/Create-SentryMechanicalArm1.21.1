package euphy.upo.sentrymechanicalarm.mixin;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"squeek.appleskin.network.SyncHandler"}
)
public class AppleSkinCompatMixin {
   @Inject(
      method = {"sendOptionalPayloadToPlayer"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private static void sentrymechanicalarm$onSendOptionalPayloadToPlayer(ServerPlayer player, CustomPacketPayload payload, CallbackInfo ci) {
      if (player.connection == null || player.connection.getConnection() == null || player.connection.getConnection().channel() == null) {
         ci.cancel();
      }
   }
}

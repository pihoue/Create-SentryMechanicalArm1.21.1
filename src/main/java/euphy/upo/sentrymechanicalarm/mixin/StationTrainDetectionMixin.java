package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.entity.Train;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(GlobalStation.class)
public class StationTrainDetectionMixin {

    @Inject(method = "getImminentTrain", at = @At("HEAD"), remap = false)
    private void onGetImminentTrain(CallbackInfoReturnable<Train> cir) {
        SentryMechanicalArm.LOGGER.info("[STATION] getImminentTrain called for station");
    }

    @Inject(method = "getPresentTrain", at = @At("HEAD"), remap = false)
    private void onGetPresentTrain(CallbackInfoReturnable<Train> cir) {
        SentryMechanicalArm.LOGGER.info("[STATION] getPresentTrain called for station");
    }

    @Inject(method = "getNearestTrain", at = @At("HEAD"), remap = false)
    private void onGetNearestTrain(CallbackInfoReturnable<Train> cir) {
        SentryMechanicalArm.LOGGER.info("[STATION] getNearestTrain called for station");
    }
}

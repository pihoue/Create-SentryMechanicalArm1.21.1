package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerHandler;
import com.tacz.guns.api.item.IAmmo;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(DeployerBlockEntity.class)
public abstract class DeployerBlockEntityMixin {


    @Shadow protected DeployerFakePlayer player;
    @Shadow protected abstract Vec3 getMovementVector();

    private static Method activateMethod;
    private static Object modeUse;

    @Inject(method = "activate", at = @At("HEAD"), cancellable = true, remap = false)
    private void bypassSafetyCheckForAmmo(CallbackInfo ci) {
        DeployerBlockEntity self = (DeployerBlockEntity) (Object) this;

        Direction direction = (Direction) self.getBlockState().getValue(DirectionalKineticBlock.FACING);

        if (direction != Direction.DOWN) return;

        if (this.player == null) return;
        ItemStack held = this.player.getMainHandItem();
        if (held.isEmpty() || IAmmo.getIAmmoOrNull(held) == null) return;

        Vec3 center = Vec3.atCenterOf(self.getBlockPos());
        BlockPos clickedPos = self.getBlockPos().relative(direction, 2);
        Vec3 movementVector = this.getMovementVector();

 
        try {
            if (activateMethod == null || modeUse == null) {
 
                Class<?> modeClass = Class.forName("com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity$Mode");

 
                Object[] constants = modeClass.getEnumConstants();
                for (Object obj : constants) {
                    if (obj.toString().equals("USE")) {
                        modeUse = obj;
                        break;
                    }
                }
                activateMethod = DeployerHandler.class.getDeclaredMethod("activate",
                        DeployerFakePlayer.class,
                        Vec3.class,
                        BlockPos.class,
                        Vec3.class,
                        modeClass);
                activateMethod.setAccessible(true);
            }
 
            if (modeUse != null) {
 
                activateMethod.invoke(null, this.player, center, clickedPos, movementVector, modeUse);
                self.setChanged();
                ci.cancel();
            }

        } catch (Exception e) {
            e.printStackTrace(); 
        }
    }
}
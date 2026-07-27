/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
 *  com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity
 *  com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer
 *  com.simibubi.create.content.kinetics.deployer.DeployerHandler
 *  com.tacz.guns.api.item.IAmmo
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Vec3i
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.phys.Vec3
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package euphy.upo.sentrymechanicalarm.mixin;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerHandler;
import com.tacz.guns.api.item.IAmmo;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={DeployerBlockEntity.class})
public abstract class DeployerBlockEntityMixin {
    @Shadow
    protected DeployerFakePlayer player;
    private static Method activateMethod;
    private static Object modeUse;

    @Shadow
    protected abstract Vec3 getMovementVector();

    @Inject(method={"activate"}, at={@At(value="HEAD")}, cancellable=true, remap=false)
    private void bypassSafetyCheckForAmmo(CallbackInfo ci) {
        DeployerBlockEntity self = (DeployerBlockEntity)(Object)this;
        Direction direction = (Direction)self.getBlockState().getValue((Property)DirectionalKineticBlock.FACING);
        if (direction != Direction.DOWN) {
            return;
        }
        if (this.player == null) {
            return;
        }
        ItemStack held = this.player.getMainHandItem();
        if (held.isEmpty() || IAmmo.getIAmmoOrNull((ItemStack)held) == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf((Vec3i)self.getBlockPos());
        BlockPos clickedPos = self.getBlockPos().relative(direction, 2);
        Vec3 movementVector = this.getMovementVector();
        try {
            if (activateMethod == null || modeUse == null) {
                Object[] constants;
                Class<?> modeClass = Class.forName("com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity$Mode");
                for (Object obj : constants = modeClass.getEnumConstants()) {
                    if (!obj.toString().equals("USE")) continue;
                    modeUse = obj;
                    break;
                }
                activateMethod = DeployerHandler.class.getDeclaredMethod("activate", DeployerFakePlayer.class, Vec3.class, BlockPos.class, Vec3.class, modeClass);
                activateMethod.setAccessible(true);
            }
            if (modeUse != null) {
                activateMethod.invoke(null, this.player, center, clickedPos, movementVector, modeUse);
                self.setChanged();
                ci.cancel();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

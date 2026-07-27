package euphy.upo.sentrymechanicalarm.mixin;

import com.tacz.guns.util.EntityUtil;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Makes every TaCZ entity-raytrace path ignore the contraption that owns a
 * sentry fake player. TaCZ's single-hit path checks the shared root vehicle,
 * but its piercing/multi-hit path does not, so the latter can otherwise hit
 * and consume the bullet on its own contraption.
 */
@Mixin(value = EntityUtil.class, remap = false)
public abstract class TaczEntityUtilMixin {
   @ModifyArgs(
      method = {"findEntityOnPath", "findEntitiesOnPath"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
      ),
      remap = false
   )
   private static void sentrymechanicalarm$ignoreOwningContraption(Args args) {
      Entity except = args.get(0);
      if (!(except instanceof Projectile projectile)) {
         return;
      }

      Entity contraptionRoot = SentryFakePlayer.getContraptionRoot(projectile.getOwner());
      if (contraptionRoot == null) {
         return;
      }

      Predicate<Entity> original = args.get(2);
      args.set(
         2,
         original.and(
            candidate -> candidate != contraptionRoot
               && candidate.getRootVehicle() != contraptionRoot
         )
      );
   }
}

package euphy.upo.sentrymechanicalarm.util;

import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared, side-independent aiming helpers. Gameplay code should derive both
 * its alignment check and its shot rotation from the same barrel pose.
 */
public final class SentryAimUtil {
   private static final double MIN_VECTOR_LENGTH_SQR = 1.0E-8;

   private SentryAimUtil() {
   }

   public static BarrelPose barrelPose(Vec3 muzzlePosition, Vec3 forward) {
      Vec3 normalized = forward.lengthSqr() > MIN_VECTOR_LENGTH_SQR ? forward.normalize() : new Vec3(0.0, 0.0, 1.0);
      double horizontal = Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z);
      float yaw = (float)Math.toDegrees(Mth.atan2(-normalized.x, normalized.z));
      float pitch = (float)(-Math.toDegrees(Mth.atan2(normalized.y, horizontal)));
      return new BarrelPose(muzzlePosition, normalized, yaw, pitch);
   }

   public static boolean isAligned(BarrelPose barrel, Vec3 aimPoint, double toleranceDegrees) {
      Vec3 toTarget = aimPoint.subtract(barrel.position());
      if (toTarget.lengthSqr() <= MIN_VECTOR_LENGTH_SQR) {
         return true;
      }

      double dot = Mth.clamp(barrel.forward().dot(toTarget.normalize()), -1.0, 1.0);
      return dot >= Math.cos(Math.toRadians(toleranceDegrees));
   }

   /**
    * Uses the center of TaCZ's configured head hitbox. For entity types without
    * an explicit hitbox, TaCZ treats eye height +/- 0.25 blocks as the headshot
    * region, so eye position is the matching fallback aim point.
    */
   public static Vec3 headAimPoint(LivingEntity target) {
      AABB headBox = HeadShotAABBConfigRead.getAABB(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
      if (headBox != null) {
         Vec3 configuredCenter = target.position().add(headBox.getCenter());
         if (isFinite(configuredCenter)) {
            return configuredCenter;
         }
      }

      float eyeHeight = target.getEyeHeight();
      Vec3 eyePosition = target.position().add(0.0, eyeHeight, 0.0);
      AABB entityBox = target.getBoundingBox();
      if (Float.isFinite(eyeHeight)
         && eyeHeight > 0.0F
         && isFinite(eyePosition)
         && eyePosition.y >= entityBox.minY - 0.25
         && eyePosition.y <= entityBox.maxY + 0.25) {
         return eyePosition;
      }

      return entityBox.getCenter();
   }

   private static boolean isFinite(Vec3 position) {
      return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
   }

   public record BarrelPose(Vec3 position, Vec3 forward, float yaw, float pitch) {
   }
}

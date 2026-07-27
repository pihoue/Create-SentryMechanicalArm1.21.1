package euphy.upo.sentrymechanicalarm.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class AeronauticsHelper {
   private static Boolean aeronauticsLoaded = null;
   private static Class<?> shipClass;
   private static Class<?> shipDataClass;

   public static boolean isAeronauticsLoaded() {
      return aeronauticsLoaded != null && aeronauticsLoaded;
   }

   public static boolean isInSableSubLevel(Level level, BlockPos pos) {
      return level == null ? false : SableCompanion.INSTANCE.isInPlotGrid(level, pos);
   }

   public static Vec3 sableSubLevelToWorld(Level level, Vec3 localPos) {
      if (level == null) {
         return localPos;
      } else {
         SubLevelAccess access = SableCompanion.INSTANCE.getContaining(level, BlockPos.containing(localPos));
         return access == null ? localPos : access.logicalPose().transformPosition(localPos);
      }
   }

   public static Vec3 sableWorldToSubLevel(Level level, Vec3 worldPos, BlockPos queryPos) {
      if (level == null) {
         return worldPos;
      } else {
         SubLevelAccess access = SableCompanion.INSTANCE.getContaining(level, queryPos);
         return access == null ? worldPos : access.logicalPose().transformPositionInverse(worldPos);
      }
   }

   public static Quaternionf getShipRotation(Level level, Vec3 worldPos) {
      if (isAeronauticsLoaded() && level != null) {
         try {
            Object vsGame = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
               .getMethod("getShipObjectManagingPos", Level.class, Vec3.class)
               .invoke(null, level, worldPos);
            if (vsGame == null) {
               return null;
            } else {
               Object transform = shipClass.getMethod("getTransform").invoke(vsGame);
               if (transform == null) {
                  return null;
               } else {
                  double[] quatArray = (double[])shipDataClass.getMethod("getShipToWorldRotation").invoke(transform);
                  return quatArray != null && quatArray.length >= 4
                     ? new Quaternionf((float)quatArray[0], (float)quatArray[1], (float)quatArray[2], (float)quatArray[3])
                     : null;
               }
            }
         } catch (Exception var5) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static Vec3 getShipPosition(Level level, Vec3 worldPos) {
      if (isAeronauticsLoaded() && level != null) {
         try {
            Object vsGame = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
               .getMethod("getShipObjectManagingPos", Level.class, Vec3.class)
               .invoke(null, level, worldPos);
            if (vsGame == null) {
               return null;
            } else {
               Object transform = shipClass.getMethod("getTransform").invoke(vsGame);
               if (transform == null) {
                  return null;
               } else {
                  Object posInWorld = shipDataClass.getMethod("getPositionInWorld").invoke(transform);
                  if (posInWorld == null) {
                     return null;
                  } else {
                     double x = (Double)posInWorld.getClass().getMethod("x").invoke(posInWorld);
                     double y = (Double)posInWorld.getClass().getMethod("y").invoke(posInWorld);
                     double z = (Double)posInWorld.getClass().getMethod("z").invoke(posInWorld);
                     return new Vec3(x, y, z);
                  }
               }
            }
         } catch (Exception var11) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static Vec3 localToSimulatedWorld(Level level, Vec3 contraptionLocalPos, Vec3 queryWorldPos) {
      if (isAeronauticsLoaded() && level != null) {
         try {
            Object vsGame = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
               .getMethod("getShipObjectManagingPos", Level.class, Vec3.class)
               .invoke(null, level, queryWorldPos);
            if (vsGame == null) {
               return contraptionLocalPos;
            } else {
               Object transform = shipClass.getMethod("getTransform").invoke(vsGame);
               if (transform == null) {
                  return contraptionLocalPos;
               } else {
                  double[] quat = (double[])shipDataClass.getMethod("getShipToWorldRotation").invoke(transform);
                  Object posInWorld = shipDataClass.getMethod("getPositionInWorld").invoke(transform);
                  if (quat != null && quat.length >= 4 && posInWorld != null) {
                     double sx = (Double)posInWorld.getClass().getMethod("x").invoke(posInWorld);
                     double sy = (Double)posInWorld.getClass().getMethod("y").invoke(posInWorld);
                     double sz = (Double)posInWorld.getClass().getMethod("z").invoke(posInWorld);
                     Quaternionf rot = new Quaternionf((float)quat[0], (float)quat[1], (float)quat[2], (float)quat[3]);
                     Vector3f v = new Vector3f((float)contraptionLocalPos.x, (float)contraptionLocalPos.y, (float)contraptionLocalPos.z);
                     rot.transform(v);
                     Vec3 result = new Vec3(sx + (double)v.x, sy + (double)v.y, sz + (double)v.z);
                     Vec3 sourcePos = new Vec3(
                        queryWorldPos.x + contraptionLocalPos.x, queryWorldPos.y + contraptionLocalPos.y, queryWorldPos.z + contraptionLocalPos.z
                     );
                     if (result.distanceTo(sourcePos) > 0.01) {
                        SentryMechanicalArm.LOGGER.info("[AeroTransform] localToSimulatedWorld ...");
                     }

                     return result;
                  } else {
                     return contraptionLocalPos;
                  }
               }
            }
         } catch (Exception var17) {
            return contraptionLocalPos;
         }
      } else {
         return contraptionLocalPos;
      }
   }

   public static boolean isPositionOnShip(Level level, Vec3 worldPos) {
      if (isAeronauticsLoaded() && level != null) {
         try {
            Object vsGame = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
               .getMethod("getShipObjectManagingPos", Level.class, Vec3.class)
               .invoke(null, level, worldPos);
            return vsGame != null;
         } catch (Exception var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static Vec3 worldToShipSpace(Level level, Vec3 worldPos, Vec3 worldDirection) {
      if (isAeronauticsLoaded() && level != null) {
         Quaternionf shipRot = getShipRotation(level, worldPos);
         if (shipRot == null) {
            return worldDirection;
         } else {
            Vector3f dir = new Vector3f((float)worldDirection.x, (float)worldDirection.y, (float)worldDirection.z);
            shipRot.conjugate().transform(dir);
            return new Vec3((double)dir.x, (double)dir.y, (double)dir.z);
         }
      } else {
         return worldDirection;
      }
   }

   public static Vec3 projectOutOfSubLevel(Level level, Vec3 localPos, Vec3 queryWorldPos) {
      return localToSimulatedWorld(level, localPos, queryWorldPos);
   }

   public static Vec3 toLocalVector(Level level, Vec3 worldPos, Vec3 worldDir) {
      return worldToShipSpace(level, worldPos, worldDir);
   }

   public static float getShipYaw(Level level, Vec3 worldPos) {
      if (isAeronauticsLoaded() && level != null) {
         Quaternionf rot = getShipRotation(level, worldPos);
         if (rot == null) {
            return 0.0F;
         } else {
            Vector3f angles = new Vector3f();
            rot.getEulerAnglesZYX(angles);
            return (float)Math.toDegrees((double)angles.y);
         }
      } else {
         return 0.0F;
      }
   }

   public static float getShipRoll(Level level, Vec3 worldPos) {
      if (isAeronauticsLoaded() && level != null) {
         Quaternionf rot = getShipRotation(level, worldPos);
         if (rot == null) {
            return 0.0F;
         } else {
            Vector3f angles = new Vector3f();
            rot.getEulerAnglesZYX(angles);
            return (float)Math.toDegrees((double)angles.z);
         }
      } else {
         return 0.0F;
      }
   }

   static {
      try {
         shipClass = Class.forName("org.valkyrienskies.core.api.ships.Ship");
         shipDataClass = Class.forName("org.valkyrienskies.core.api.ships.ShipTransform");
         aeronauticsLoaded = true;
         SentryMechanicalArm.LOGGER.info("Aeronautics/Valkyrien Skies detected - enabling ship rotation compensation");
      } catch (ClassNotFoundException var1) {
         aeronauticsLoaded = false;
         shipClass = null;
         shipDataClass = null;
      }
   }
}

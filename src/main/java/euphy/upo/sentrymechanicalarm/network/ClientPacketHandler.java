package euphy.upo.sentrymechanicalarm.network;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlock;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.mixin.GunDisplayInstanceAccessor;
import euphy.upo.sentrymechanicalarm.util.ArmSoundHelper;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import euphy.upo.sentrymechanicalarm.util.SentryTrailManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import org.apache.commons.lang3.tuple.MutablePair;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

public class ClientPacketHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<ResourceLocation, GunDisplayInstance> SENTRY_DISPLAYS = new HashMap<>();

   private static Vec3 calculateExactMuzzle(SentryArmBlockEntity sentry, ItemStack gunStack) {
      Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(gunStack);
      if (displayOpt.isEmpty()) {
         return null;
      } else {
         GunDisplayInstance display = displayOpt.get();
         BedrockGunModel gunModel = display.getGunModel();
         if (gunModel == null) {
            return null;
         } else {
            List<BedrockPart> handPath = gunModel.getThirdPersonHandOriginPath();
            List<BedrockPart> muzzlePath = gunModel.getMuzzleFlashPosPath();
            if (muzzlePath != null && !muzzlePath.isEmpty()) {
               boolean isCeiling = sentry.getBlockState().hasProperty(SentryArmBlock.CEILING)
                  && (Boolean)sentry.getBlockState().getValue(SentryArmBlock.CEILING);
               BlockPos pos = sentry.getBlockPos();
               float baseAngle = sentry.baseAngle.getValue();
               float lowerArmAngle = sentry.lowerArmAngle.getValue() - 135.0F;
               float upperArmAngle = sentry.upperArmAngle.getValue() - 90.0F;
               float headAngle = sentry.headAngle.getValue();
               String gunPath = gunStack.getItem() instanceof IGun iGun2 ? iGun2.getGunId(gunStack).getPath().toLowerCase() : "";
               PoseStack ps = new PoseStack();
               Matrix4f worldMatrix = new Matrix4f();
               worldMatrix.translate((float)pos.getX() + 0.5F, (float)pos.getY() + 0.5F, (float)pos.getZ() + 0.5F);
               ps.last().pose().set(worldMatrix);
               ps.translate(0.0F, 0.25F, 0.0F);
               ps.mulPose(Axis.YP.rotationDegrees(baseAngle));
               ps.translate(0.0F, 0.125F, 0.0F);
               ps.mulPose(Axis.XP.rotationDegrees(lowerArmAngle + 135.0F));
               ps.translate(0.0F, 0.0F, -0.875F);
               ps.mulPose(Axis.XP.rotationDegrees(upperArmAngle - 90.0F));
               ps.translate(0.0F, 0.0F, -0.9375F);
               ps.mulPose(Axis.XP.rotationDegrees(headAngle - 45.0F));
               if (isCeiling) {
                  ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
               }

               ps.translate(0.0F, 0.0F, -0.375F);
               ps.mulPose(Axis.XP.rotationDegrees(90.0F));
               ps.translate(0.0F, -0.625F, 0.0F);
               ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
               ps.translate(0.0F, 0.18F, 0.0F);
               if (gunPath.contains("minigun")) {
                  ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
                  ps.translate(0.0F, -0.7F, 0.2F);
               }

               float armScale = 1.5F;
               ps.scale(armScale, armScale, armScale);
               ps.translate(0.0F, 1.5F, 0.0F);
               ps.scale(-1.0F, -1.0F, 1.0F);
               Vector3f displayScale = new Vector3f(1.0F, 1.0F, 1.0F);
               if (display.getTransform() != null && display.getTransform().getScale() != null) {
                  Vector3f ts = display.getTransform().getScale().getThirdPerson();
                  if (ts != null) {
                     displayScale = ts;
                  }
               }

               if (handPath != null && !handPath.isEmpty()) {
                  ps.translate(0.0F, 1.5F, 0.0F);

                  for (int i = handPath.size() - 1; i >= 0; i--) {
                     BedrockPart t = handPath.get(i);
                     ps.mulPose(Axis.XN.rotation(t.xRot));
                     ps.mulPose(Axis.YN.rotation(t.yRot));
                     ps.mulPose(Axis.ZN.rotation(t.zRot));
                     if (t.getParent() != null) {
                        ps.translate(-t.x * displayScale.x() / 16.0F, -t.y * displayScale.y() / 16.0F, -t.z * displayScale.z() / 16.0F);
                     } else {
                        ps.translate(-t.x * displayScale.x() / 16.0F, (1.5F - t.y / 16.0F) * displayScale.y(), -t.z * displayScale.z() / 16.0F);
                     }
                  }

                  ps.translate(0.0F, -1.5F, 0.0F);
               }

               ps.translate(0.0F, 1.5F, 0.0F);
               ps.scale(displayScale.x(), displayScale.y(), displayScale.z());
               ps.translate(0.0F, -1.5F, 0.0F);

               for (BedrockPart part : muzzlePath) {
                  part.translateAndRotateAndScale(ps);
               }

               Vector4f result = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
               ps.last().pose().transform(result);
               return new Vec3((double)result.x(), (double)result.y(), (double)result.z());
            } else {
               return null;
            }
         }
      }
   }

   private static void triggerSentryAnimation(ItemStack gunStack, String input) {
      IGun iGun = IGun.getIGunOrNull(gunStack);
      if (iGun != null) {
         ResourceLocation gunId = iGun.getGunId(gunStack);
         if (gunId != null) {
            GunDisplayInstance sentryDisplay = SENTRY_DISPLAYS.computeIfAbsent(gunId, id -> {
               Optional<GunDisplayInstance> original = TimelessAPI.getGunDisplay(gunStack);
               if (original.isEmpty()) {
                  return null;
               } else {
                  GunDisplay display = ((GunDisplayInstanceAccessor)original.get()).sentrymechanicalarm$getDisplay();
                  return display == null ? null : GunDisplayInstance.create(id, display);
               }
            });
            if (sentryDisplay != null) {
               LuaAnimationStateMachine<GunAnimationStateContext> sm = sentryDisplay.getAnimationStateMachine();
               if (sm != null) {
                  sm.trigger(input);
               }
            }
         }
      }
   }

   public static void handleSentryShoot(SentryShootPacket msg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (mc.level.getBlockEntity(msg.pos()) instanceof SentryArmBlockEntity sentry) {
            sentry.updateAmmoFromPacket(msg.slotIndex(), msg.itemTag());
            ItemStack gunStack = sentry.getHeldItem();
            if (!gunStack.isEmpty() && gunStack.getItem() instanceof IGun iGun) {
               ClientLevel var20 = mc.level;
               Vec3 center = sentry.getBlockPos().getCenter();
               Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(gunStack);
               if (!displayOpt.isEmpty()) {
                  GunDisplayInstance display = displayOpt.get();
                  switch (msg.actionType()) {
                     case CHARGE:
                        triggerSentryAnimation(gunStack, "bolt");
                        ArmSoundHelper.playChargeSound(var20, center, gunStack, display);
                        break;
                     case BOLT:
                        ArmSoundHelper.playBoltSound(var20, center, display);
                        break;
                     case RELOAD_EMPTY:
                        ArmSoundHelper.playReloadSound(var20, center, display, true);
                        break;
                     case RELOAD_TACTICAL:
                        ArmSoundHelper.playReloadSound(var20, center, display, false);
                        break;
                     case SHOOT:
                        sentry.triggerShootEffects();
                        triggerSentryAnimation(gunStack, "shoot");
                        TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack))
                           .ifPresent(index -> ArmSoundHelper.playFireEffects(var20, center, gunStack, index.getGunData()));
                        Vec3 modelMuzzle = calculateExactMuzzle(sentry, gunStack);
                        Vec3 realStart = modelMuzzle != null ? modelMuzzle : msg.realStart();
                        if (!SentryFakePlayer.hasEntityBullet(gunStack)) {
                           Vec3 direction = msg.realEnd().subtract(msg.realStart()).normalize();
                           double totalDistance = realStart.distanceTo(msg.realEnd());
                           double offsetDistance = 0.8;
                           Vec3 adjustedStart = totalDistance > offsetDistance ? realStart.add(direction.scale(offsetDistance)) : realStart;
                           double adjustedDist = totalDistance > offsetDistance ? totalDistance - offsetDistance : totalDistance;
                           SentryTrailManager.addTracer(adjustedStart, direction, 8.0, 2.0, adjustedDist);
                        }
                  }
               }
            }
         }
      }
   }

   public static void handleSentryContraptionShoot(SentryContraptionShootPacket msg) {
      Minecraft mc = Minecraft.getInstance();
      Level level = mc.level;
      if (level != null) {
         if (level.getEntity(msg.contraptionId()) instanceof AbstractContraptionEntity ace) {
            Contraption contraption = ace.getContraption();
            if (contraption != null) {
               for (MutablePair<StructureBlockInfo, MovementContext> actor : contraption.getActors()) {
                  if (((StructureBlockInfo)actor.getKey()).pos().equals(msg.localPos())) {
                     MovementContext context = (MovementContext)actor.getValue();
                     SentryArmBlockEntity be = null;
                     LOGGER.info(
                        "[ContraptionShellPacket] processing pos=({}) tempData={} blockData={}",
                        new Object[]{
                           context.localPos,
                           context.temporaryData != null ? context.temporaryData.getClass().getSimpleName() : "null",
                           context.blockEntityData != null ? "present" : "null"
                        }
                     );
                     if (context.temporaryData instanceof SentryArmBlockEntity s) {
                        be = s;
                     } else if (context.blockEntityData != null) {
                        VirtualSentryArmBlockEntity vbe = VirtualSentryArmBlockEntity.fromData(
                           context.localPos, context.state, context.blockEntityData, context.world
                        );
                        context.temporaryData = vbe;
                        be = vbe;
                     }

                     if (be != null) {
                        be.setLastShootTime(System.currentTimeMillis());
                        be.triggerShootEffects();
                     }
                     break;
                  }
               }
            }
         }

         if (!SentryFakePlayer.hasEntityBullet(msg.gunStack())) {
            Vec3 direction = msg.realEnd().subtract(msg.realStart()).normalize();
            double totalDistance = msg.realStart().distanceTo(msg.realEnd());
            double offsetDistance = 0.6;
            Vec3 adjustedStart = totalDistance > offsetDistance ? msg.realStart().add(direction.scale(offsetDistance)) : msg.realStart();
            double adjustedDist = totalDistance > offsetDistance ? totalDistance - offsetDistance : totalDistance;
            SentryTrailManager.addTracer(adjustedStart, direction, 8.0, 2.0, adjustedDist);
         }

         if (msg.gunStack().getItem() instanceof IGun iGun) {
            triggerSentryAnimation(msg.gunStack(), "shoot");
            TimelessAPI.getCommonGunIndex(iGun.getGunId(msg.gunStack()))
               .ifPresent(index -> ArmSoundHelper.playFireEffects(level, msg.realStart(), msg.gunStack(), index.getGunData()));
         }
      }
   }

   public static void handleSentryContraptionTarget(SentryContraptionTargetPacket msg) {
      Minecraft mc = Minecraft.getInstance();
      Level level = mc.level;
      if (level == null || !(level.getEntity(msg.contraptionId()) instanceof AbstractContraptionEntity ace)) {
         return;
      }

      Contraption contraption = ace.getContraption();
      if (contraption == null) {
         return;
      }

      for (MutablePair<StructureBlockInfo, MovementContext> actor : contraption.getActors()) {
         if (((StructureBlockInfo)actor.getKey()).pos().equals(msg.localPos())) {
            MovementContext context = actor.getValue();
            context.data.putInt("_TargetId", msg.targetId());
            if (!(context.temporaryData instanceof VirtualSentryArmBlockEntity) && context.blockEntityData != null) {
               context.temporaryData = VirtualSentryArmBlockEntity.fromData(
                  context.localPos, context.state, context.blockEntityData, context.world
               );
            }

            if (msg.targetId() != -1 && context.temporaryData instanceof VirtualSentryArmBlockEntity virtualBE) {
               virtualBE.baseAngle.chase(msg.baseAngle(), 0.5, Chaser.EXP);
               virtualBE.lowerArmAngle.chase(msg.lowerArmAngle(), 0.5, Chaser.EXP);
               virtualBE.upperArmAngle.chase(msg.upperArmAngle(), 0.5, Chaser.EXP);
               virtualBE.headAngle.chase(msg.headAngle(), 0.5, Chaser.EXP);
            }
            return;
         }
      }
   }
}

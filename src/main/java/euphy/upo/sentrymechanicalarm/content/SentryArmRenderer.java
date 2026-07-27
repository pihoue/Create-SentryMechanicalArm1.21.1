package euphy.upo.sentrymechanicalarm.content;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.client.resource.pojo.display.gun.ShellEjection;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.util.ArmSoundHelper;
import euphy.upo.sentrymechanicalarm.util.SentryShellManager;
import euphy.upo.sentrymechanicalarm.util.SentrySpriteShifts;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

public class SentryArmRenderer extends KineticBlockEntityRenderer<SentryArmBlockEntity> {
   public static final Logger LOGGER = LogUtils.getLogger();

   public SentryArmRenderer(Context context) {
      super(context);
   }

   public boolean shouldRenderOffScreen(SentryArmBlockEntity be) {
      return true;
   }

   protected void renderSafe(SentryArmBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
      try {
         this.renderSafeInner(be, pt, ms, buffer, light, overlay);
      } catch (Exception var8) {
         LOGGER.warn("Failed to render SentryArmBlockEntity", var8);
      }
   }

   private void renderSafeInner(SentryArmBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
      BlockState blockState = be.getBlockState();
      this.renderCog(be, ms, buffer, light, be.color);
      ItemStack item = be.getHeldItem();
      boolean hasItem = !item.isEmpty();
      boolean isBlockItem = false;
      if (hasItem) {
         try {
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            BakedModel bakedModel = itemRenderer.getModel(item, be.getLevel(), (LivingEntity)null, 0);
            isBlockItem = item.getItem() instanceof BlockItem && bakedModel.isGui3d();
         } catch (Exception var26) {
            isBlockItem = false;
         }
      }

      VertexConsumer builder = buffer.getBuffer(RenderType.solid());
      PoseStack msLocal = new PoseStack();
      PoseTransformStack msr = TransformStack.of(msLocal);
      boolean inverted = (Boolean)blockState.getValue(SentryArmBlock.CEILING);
      float baseAngle = be.baseAngle.getValue(pt);
      float lowerArmAngle = be.lowerArmAngle.getValue(pt) - 135.0F;
      float upperArmAngle = be.upperArmAngle.getValue(pt) - 90.0F;
      float headAngle = be.headAngle.getValue(pt);
      if (AeronauticsHelper.isAeronauticsLoaded() && be.getLevel() != null && !be.isInSableSubLevel()) {
         Vec3 worldPos = be.getBlockPos().getCenter();
         float shipYaw = AeronauticsHelper.getShipYaw(be.getLevel(), worldPos);
         float shipRoll = AeronauticsHelper.getShipRoll(be.getLevel(), worldPos);
         baseAngle -= shipYaw;
         if (Math.abs(shipRoll) > 1.0F) {
            msr.rotateZDegrees(-shipRoll);
         }
      }

      int color = 16777215;
      msr.center();
      if (inverted) {
         msr.rotateXDegrees(180.0F);
      }

      this.renderArm(
         builder, ms, msLocal, msr, blockState, color, baseAngle, lowerArmAngle, upperArmAngle, headAngle, inverted, hasItem, isBlockItem, light, be.color
      );
      if (hasItem) {
         ms.pushPose();
         msr.rotateXDegrees(90.0F);
         msLocal.translate(0.0F, isBlockItem ? -0.5625F : -0.625F, 0.0F);
         if (item.getItem() instanceof IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(item);
            msLocal.mulPose(Axis.XP.rotationDegrees(-90.0F));
            msLocal.translate(0.0, 0.18, 0.0);
            if (gunId.getPath().contains("minigun")) {
               msLocal.mulPose(Axis.XP.rotationDegrees(-90.0F));
               msLocal.translate(0.0, -0.7, 0.2);
            }

            float armScale = 1.5F;
            msLocal.scale(armScale, armScale, armScale);
            if (be.shouldEjectShell()) {
               Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(item);
               displayOpt.ifPresent(display -> {
                  if (display.getShellEjection() != null) {
                     this.tryManualEject(be, item, display, msLocal, display.getShellEjection());
                  }
               });
               be.setShellEjected();
            }

            ms.pushPose();
            ms.last().pose().mul(msLocal.last().pose());

            try {
               ItemDisplayContext displayContext = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
               Minecraft.getInstance().getItemRenderer().renderStatic(item, displayContext, light, overlay, ms, buffer, be.getLevel(), 0);
            } catch (Exception var25) {
               LOGGER.debug("Failed to render held gun item", var25);
            }

            this.renderAmmoBoxes(be, ms, buffer, light, overlay);
            this.renderMuzzleFlash(be, item, ms, buffer);
            ms.popPose();
         } else {
            float itemScale = isBlockItem ? 0.5F : 0.625F;
            msLocal.scale(itemScale, itemScale, itemScale);
            ms.last().pose().mul(msLocal.last().pose());

            try {
               Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
            } catch (Exception var24) {
               LOGGER.debug("Failed to render held non-gun item", var24);
            }
         }

         ms.popPose();
      }
   }

   private void renderCog(SentryArmBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, Optional<DyeColor> color) {
      if (SentryPartialModels.SENTRU_COG.get() != null) {
         BlockState blockState = be.getBlockState();
         SuperByteBuffer cog = CachedBuffers.partial(SentryPartialModels.SENTRU_COG, blockState);
         net.minecraft.core.Direction.Axis axis = net.minecraft.core.Direction.Axis.Y;
         float angle = getAngleForBe(be, be.getBlockPos(), axis);
         kineticRotationTransform(cog, be, axis, angle, light);
         applyDye(cog, color, SentrySpriteShifts.COG_TEXTURES);
         cog.renderInto(ms, buffer.getBuffer(RenderType.solid()));
      }
   }

   private void renderMuzzleFlash(SentryArmBlockEntity sentry, ItemStack stack, PoseStack ms, MultiBufferSource buffer) {
      boolean isSilenced = ArmSoundHelper.isSilenced(stack);
      if (!isSilenced) {
         long timeSinceShoot = System.currentTimeMillis() - sentry.getLastShootTime();
         if (timeSinceShoot >= 0L && timeSinceShoot <= 50L) {
            Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(stack);
            if (!displayOpt.isEmpty()) {
               GunDisplayInstance display = displayOpt.get();
               BedrockGunModel gunModel = display.getGunModel();
               if (gunModel != null) {
                  MuzzleFlash muzzleFlash = display.getMuzzleFlash();
                  if (muzzleFlash != null) {
                     ms.pushPose();
                     ms.translate(0.0, 1.5, 0.0);
                     ms.scale(-1.0F, -1.0F, 1.0F);
                     Vector3f transformScale = null;
                     if (display.getTransform() != null && display.getTransform().getScale() != null) {
                        transformScale = display.getTransform().getScale().getThirdPerson();
                     }

                     this.applyPositioningNodeTransform(gunModel.getThirdPersonHandOriginPath(), ms, transformScale);
                     applyScaleTransform(ms, transformScale);
                     List<BedrockPart> path = gunModel.getMuzzleFlashPosPath();
                     if (path != null) {
                        for (BedrockPart part : path) {
                           part.translateAndRotateAndScale(ms);
                        }
                     }

                     float flashScale = (float)(0.5 * (double)muzzleFlash.getScale());
                     float randomRotate = (float)(Math.random() * 360.0);
                     ms.mulPose(Axis.ZP.rotationDegrees(randomRotate));
                     ms.scale(flashScale, flashScale, flashScale);
                     VertexConsumer consumerBg = buffer.getBuffer(RenderType.entityTranslucent(muzzleFlash.getTexture()));
                     this.drawCrossQuad(ms, consumerBg, 1.0F);
                     VertexConsumer consumerFg = buffer.getBuffer(RenderType.entityTranslucent(muzzleFlash.getTexture()));
                     ms.pushPose();
                     ms.scale(0.5F, 0.5F, 0.5F);
                     this.drawCrossQuad(ms, consumerFg, 1.0F);
                     ms.popPose();
                     ms.popPose();
                  }
               }
            }
         }
      }
   }

   private void renderAmmoBoxes(SentryArmBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
      ItemStack heldItem = be.getHeldItem();
      boolean isMinigun = false;
      if (heldItem.getItem() instanceof IGun iGun) {
         ResourceLocation gunId = iGun.getGunId(heldItem);
         if (gunId.getPath().contains("minigun")) {
            isMinigun = true;
         }
      }

      ms.pushPose();
      if (isMinigun) {
         float invScale = 0.6666667F;
         ms.scale(invScale, invScale, invScale);
         ms.translate(0.0, 0.7, -0.2);
         ms.mulPose(Axis.XP.rotationDegrees(90.0F));
         ms.scale(1.5F, 1.5F, 1.5F);
      }

      List<ItemStack> boxes = be.attachedAmmoBoxes;

      for (int i = 0; i < boxes.size(); i++) {
         ItemStack box = boxes.get(i);
         if (!box.isEmpty()) {
            ms.pushPose();
            float boxScale = 0.3F;
            ms.scale(boxScale, boxScale, boxScale);
            if (i == 0) {
               ms.translate(0.35, -0.5, 0.6);
               ms.mulPose(Axis.YP.rotationDegrees(-90.0F));
            } else {
               ms.translate(-0.35, -0.5, 0.6);
               ms.mulPose(Axis.YP.rotationDegrees(90.0F));
            }

            try {
               Minecraft.getInstance().getItemRenderer().renderStatic(box, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
            } catch (Exception var13) {
               LOGGER.debug("Failed to render ammo box", var13);
            }

            ms.popPose();
         }
      }

      ms.popPose();
   }

   private void applyPositioningNodeTransform(List<BedrockPart> nodePath, PoseStack poseStack, Vector3f scale) {
      if (nodePath != null) {
         if (scale == null) {
            scale = new Vector3f(1.0F, 1.0F, 1.0F);
         }

         poseStack.translate(0.0F, 1.5F, 0.0F);

         for (int i = nodePath.size() - 1; i >= 0; i--) {
            BedrockPart t = nodePath.get(i);
            poseStack.mulPose(Axis.XN.rotation(t.xRot));
            poseStack.mulPose(Axis.YN.rotation(t.yRot));
            poseStack.mulPose(Axis.ZN.rotation(t.zRot));
            if (t.getParent() != null) {
               poseStack.translate(-t.x * scale.x() / 16.0F, -t.y * scale.y() / 16.0F, -t.z * scale.z() / 16.0F);
            } else {
               poseStack.translate(-t.x * scale.x() / 16.0F, (1.5F - t.y / 16.0F) * scale.y(), -t.z * scale.z() / 16.0F);
            }
         }

         poseStack.translate(0.0F, -1.5F, 0.0F);
      }
   }

   private static void applyScaleTransform(PoseStack poseStack, Vector3f scale) {
      if (scale != null) {
         poseStack.translate(0.0F, 1.5F, 0.0F);
         poseStack.scale(scale.x(), scale.y(), scale.z());
         poseStack.translate(0.0F, -1.5F, 0.0F);
      }
   }

   private void drawCrossQuad(PoseStack ms, VertexConsumer consumer, float alpha) {
      Matrix4f pose = ms.last().pose();
      Matrix3f normal = ms.last().normal();
      float size = 1.0F;
      float min = -size;
      float u0 = 0.0F;
      float u1 = 1.0F;
      float v0 = 0.0F;
      float v1 = 1.0F;
      this.vertex(consumer, pose, normal, min, size, 0.0F, u0, v1);
      this.vertex(consumer, pose, normal, size, size, 0.0F, u1, v1);
      this.vertex(consumer, pose, normal, size, min, 0.0F, u1, v0);
      this.vertex(consumer, pose, normal, min, min, 0.0F, u0, v0);
      ms.pushPose();
      ms.mulPose(Axis.YP.rotationDegrees(90.0F));
      Matrix4f pose2 = ms.last().pose();
      Matrix3f normal2 = ms.last().normal();
      this.vertex(consumer, pose2, normal2, min, size, 0.0F, u0, v1);
      this.vertex(consumer, pose2, normal2, size, size, 0.0F, u1, v1);
      this.vertex(consumer, pose2, normal2, size, min, 0.0F, u1, v0);
      this.vertex(consumer, pose2, normal2, min, min, 0.0F, u0, v0);
      ms.popPose();
   }

   private void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v) {
      consumer.addVertex(pose, x, y, z)
         .setColor(255, 255, 255, 255)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(15728880)
         .setNormal(0.0F, 1.0F, 0.0F);
   }

   private void renderArm(
      VertexConsumer builder,
      PoseStack ms,
      PoseStack msLocal,
      TransformStack msr,
      BlockState blockState,
      int color,
      float baseAngle,
      float lowerArmAngle,
      float upperArmAngle,
      float headAngle,
      boolean inverted,
      boolean hasItem,
      boolean isBlockItem,
      int light,
      Optional<DyeColor> dyeColor
   ) {
      if (SentryPartialModels.SENTRU_BASE.get() != null
         && SentryPartialModels.ARM_LOWER_BODY.get() != null
         && SentryPartialModels.ARM_UPPER_BODY.get() != null
         && SentryPartialModels.ARM_CLAW_BASE.get() != null
         && SentryPartialModels.ARM_CLAW_GRIP_UPPER.get() != null
         && SentryPartialModels.ARM_CLAW_GRIP_LOWER.get() != null) {
         SuperByteBuffer base = CachedBuffers.partial(SentryPartialModels.SENTRU_BASE, blockState);
         SuperByteBuffer lowerBody = CachedBuffers.partial(SentryPartialModels.ARM_LOWER_BODY, blockState);
         SuperByteBuffer upperBody = CachedBuffers.partial(SentryPartialModels.ARM_UPPER_BODY, blockState);
         SuperByteBuffer claw = CachedBuffers.partial(SentryPartialModels.ARM_CLAW_BASE, blockState);
         SuperByteBuffer upperClawGrip = CachedBuffers.partial(SentryPartialModels.ARM_CLAW_GRIP_UPPER, blockState);
         SuperByteBuffer lowerClawGrip = CachedBuffers.partial(SentryPartialModels.ARM_CLAW_GRIP_LOWER, blockState);
         base.light(light);
         lowerBody.light(light);
         upperBody.light(light);
         claw.light(light);
         upperClawGrip.light(light);
         lowerClawGrip.light(light);
         applyDye(base, dyeColor, SentrySpriteShifts.BASE_TEXTURES);
         applyDye(lowerBody, dyeColor, SentrySpriteShifts.ARM_TEXTURES);
         applyDye(upperBody, dyeColor, SentrySpriteShifts.ARM_TEXTURES);
         applyDye(claw, dyeColor, SentrySpriteShifts.ARM_TEXTURES);
         applyDye(upperClawGrip, dyeColor, SentrySpriteShifts.ARM_TEXTURES);
         applyDye(lowerClawGrip, dyeColor, SentrySpriteShifts.ARM_TEXTURES);
         transformBase(msr, baseAngle);
         ((SuperByteBuffer)base.transform(msLocal)).renderInto(ms, builder);
         transformLowerArm(msr, lowerArmAngle);
         ((SuperByteBuffer)lowerBody.color(color).transform(msLocal)).renderInto(ms, builder);
         transformUpperArm(msr, upperArmAngle);
         ((SuperByteBuffer)upperBody.color(color).transform(msLocal)).renderInto(ms, builder);
         transformHead(msr, headAngle);
         if (inverted) {
            msr.rotateZDegrees(180.0F);
         }

         ((SuperByteBuffer)claw.transform(msLocal)).renderInto(ms, builder);
         if (inverted) {
            msr.rotateZDegrees(180.0F);
         }

         for (int flip : Iterate.positiveAndNegative) {
            msLocal.pushPose();
            transformClawHalf(msr, hasItem, isBlockItem, flip);
            ((SuperByteBuffer)(flip > 0 ? lowerClawGrip : upperClawGrip).transform(msLocal)).renderInto(ms, builder);
            msLocal.popPose();
         }
      }
   }

   private static void applyDye(SuperByteBuffer buffer, Optional<DyeColor> color, Map<DyeColor, SpriteShiftEntry> shiftMap) {
      color.ifPresent(dye -> {
         SpriteShiftEntry entry = shiftMap.get(dye);
         if (entry != null) {
            buffer.shiftUV(entry);
         }
      });
   }

   private static void transformClawHalf(TransformStack msr, boolean hasItem, boolean isBlockItem, int flip) {
      msr.translate(0.0F, (float)(-flip) * (hasItem ? (isBlockItem ? 0.1875F : 0.078125F) : 0.0625F), -0.375F);
   }

   private static void transformHead(TransformStack msr, float headAngle) {
      msr.translate(0.0F, 0.0F, -0.9375F);
      msr.rotateXDegrees(headAngle - 45.0F);
   }

   private static void transformUpperArm(TransformStack msr, float upperArmAngle) {
      msr.translate(0.0F, 0.0F, -0.875F);
      msr.rotateXDegrees(upperArmAngle - 90.0F);
   }

   private static void transformLowerArm(TransformStack msr, float lowerArmAngle) {
      msr.translate(0.0F, 0.125F, 0.0F);
      msr.rotateXDegrees(lowerArmAngle + 135.0F);
   }

   private static void transformBase(TransformStack msr, float baseAngle) {
      msr.translate(0.0F, 0.25F, 0.0F);
      msr.rotateYDegrees(baseAngle);
   }

   private static boolean findPathRecursive(BedrockPart current, String targetName, List<BedrockPart> path) {
      if (current == null) {
         return false;
      } else {
         path.add(current);
         if (targetName.equals(current.name)) {
            return true;
         } else {
            if (current.children != null) {
               ObjectListIterator var3 = current.children.iterator();

               while (var3.hasNext()) {
                  BedrockPart child = (BedrockPart)var3.next();
                  if (findPathRecursive(child, targetName, path)) {
                     return true;
                  }
               }
            }

            path.remove(path.size() - 1);
            return false;
         }
      }
   }

   private void tryManualEject(SentryArmBlockEntity be, ItemStack stack, GunDisplayInstance display, PoseStack msLocal, ShellEjection ejection) {
      BedrockGunModel gunModel = display.getGunModel();
      if (gunModel != null) {
         List<BedrockPart> shellPath = new ArrayList<>();
         if (!findPathRecursive(gunModel.getRootNode(), "shell", shellPath)) {
            findPathRecursive(gunModel.getRootNode(), "shell_ejection", shellPath);
         }

         if (!shellPath.isEmpty()) {
            List<BedrockPart> handPath = gunModel.getThirdPersonHandOriginPath();
            if (handPath != null && !handPath.isEmpty()) {
               PoseStack handMs = new PoseStack();

               for (BedrockPart part : handPath) {
                  part.translateAndRotateAndScale(handMs);
               }

               Vector4f handPos = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
               handMs.last().pose().transform(handPos);
               PoseStack shellMs = new PoseStack();

               for (BedrockPart part : shellPath) {
                  part.translateAndRotateAndScale(shellMs);
               }

               Vector4f shellPos = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
               shellMs.last().pose().transform(shellPos);
               float dx = shellPos.x() - handPos.x();
               float dy = shellPos.y() - handPos.y();
               float dz = shellPos.z() - handPos.z();
               float gunScale = 0.6F;
               if (display.getTransform() != null && display.getTransform().getScale() != null) {
                  Vector3f s = display.getTransform().getScale().getThirdPerson();
                  if (s != null) {
                     gunScale = s.x();
                  }
               }

               PoseStack finalMs = new PoseStack();
               finalMs.last().pose().set(msLocal.last().pose());
               finalMs.translate(-dx * gunScale, -dy * gunScale, dz * gunScale);
               Matrix4f poseMatrix = finalMs.last().pose();
               Vector4f offsetVec = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
               poseMatrix.transform(offsetVec);
               Vec3 calculatedPos = new Vec3(
                  (double)((float)be.getBlockPos().getX() + offsetVec.x()),
                  (double)((float)be.getBlockPos().getY() + offsetVec.y()),
                  (double)((float)be.getBlockPos().getZ() + offsetVec.z())
               );
               Vector3f calculatedVel = new Vector3f(0.05F, 0.02F, -0.01F);
               Matrix3f normalMatrix = new Matrix3f();
               poseMatrix.get3x3(normalMatrix);
               IGun iGun = (IGun)stack.getItem();
               ResourceLocation gunId = iGun.getGunId(stack);
               if (gunId.getPath().contains("minigun")) {
                  Vector3f minigunLocalOffset = new Vector3f(0.2F, 0.65F, 0.55F);
                  normalMatrix.transform(minigunLocalOffset);
                  calculatedPos = calculatedPos.add((double)minigunLocalOffset.x(), (double)minigunLocalOffset.y(), (double)minigunLocalOffset.z());
                  calculatedVel = new Vector3f(-0.11F, 0.08F, -0.05F);
               }

               poseMatrix.get3x3(normalMatrix);
               normalMatrix.transform(calculatedVel);
               Vec3 finalSpawnPos = calculatedPos;
               Vector3f finalVelocity = calculatedVel;
               Vector3f accel = new Vector3f(ejection.getAcceleration());
               accel.mul(0.03F);
               TimelessAPI.getCommonGunIndex(gunId)
                  .ifPresent(
                     index -> SentryShellManager.addShell(
                           index.getGunData().getAmmoId(), finalSpawnPos, finalVelocity, ejection.getAngularVelocity(), accel, (double)ejection.getLivingTime()
                        )
                  );
            }
         }
      }
   }

   public static void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices, MultiBufferSource buffer) {
      if (SentryPartialModels.SENTRU_BASE.get() != null
         && SentryPartialModels.ARM_LOWER_BODY.get() != null
         && SentryPartialModels.ARM_UPPER_BODY.get() != null
         && SentryPartialModels.ARM_CLAW_BASE.get() != null
         && SentryPartialModels.ARM_CLAW_GRIP_UPPER.get() != null
         && SentryPartialModels.ARM_CLAW_GRIP_LOWER.get() != null
         && SentryPartialModels.SENTRU_COG.get() != null) {
         if (context.temporaryData == null || !(context.temporaryData instanceof VirtualSentryArmBlockEntity)) {
            VirtualSentryArmBlockEntity newBE = new VirtualSentryArmBlockEntity(BlockPos.ZERO, context.state);
            if (context.blockEntityData != null) {
               if (context.blockEntityData.contains("Angles")) {
                  CompoundTag angles = context.blockEntityData.getCompound("Angles");
                  newBE.baseAngle.setValue((double)angles.getFloat("Base"));
                  newBE.lowerArmAngle.setValue((double)angles.getFloat("Lower"));
                  newBE.upperArmAngle.setValue((double)angles.getFloat("Upper"));
                  newBE.headAngle.setValue((double)angles.getFloat("Head"));
               }

               if (context.blockEntityData.contains("Speed")) {
                  newBE.setSpeed(context.blockEntityData.getFloat("Speed"));
               }

               if (context.blockEntityData.contains("SentryHeldItem")) {
                  newBE.setHeldItem(ItemStack.parseOptional(renderWorld.registryAccess(), context.blockEntityData.getCompound("SentryHeldItem")));
               }

               if (context.blockEntityData.contains("SentryAmmoBoxes")) {
                  ContainerHelper.loadAllItems(context.blockEntityData.getCompound("SentryAmmoBoxes"), newBE.attachedAmmoBoxes, renderWorld.registryAccess());
               }

               if (context.blockEntityData.contains("color")) {
                  int colorId = context.blockEntityData.getInt("color");
                  newBE.color = Optional.of(DyeColor.byId(colorId));
               }
            }

            context.temporaryData = newBE;
         }

         VirtualSentryArmBlockEntity virtualBE = (VirtualSentryArmBlockEntity)context.temporaryData;
         BlockState blockState = context.state;
         float pt = AnimationTickHolder.getPartialTicks();
         float baseAngle = virtualBE.baseAngle.getValue(pt);
         float lowerArmAngle = virtualBE.lowerArmAngle.getValue(pt) - 135.0F;
         float upperArmAngle = virtualBE.upperArmAngle.getValue(pt) - 90.0F;
         float headAngle = virtualBE.headAngle.getValue(pt);
         boolean inverted = (Boolean)blockState.getValue(SentryArmBlock.CEILING);
         int light = 15728880;
         if (context.contraption != null && context.contraption.entity != null) {
            Vec3 localPos = VecHelper.getCenterOf(context.localPos);
            Vec3 globalPos = context.contraption.entity.toGlobalVector(localPos, pt);
            light = LevelRenderer.getLightColor(context.world, BlockPos.containing(globalPos));
         }

         ItemStack heldItem = virtualBE.getHeldItem();
         if (heldItem.isEmpty() && context.blockEntityData != null && context.blockEntityData.contains("SentryHeldItem")) {
            heldItem = ItemStack.parseOptional(renderWorld.registryAccess(), context.blockEntityData.getCompound("SentryHeldItem"));
            virtualBE.setHeldItem(heldItem);
         }

         boolean hasItem = !heldItem.isEmpty();
         boolean isBlockItem = false;
         if (hasItem) {
            try {
               BakedModel bakedmodel = Minecraft.getInstance().getItemRenderer().getModel(heldItem, renderWorld, null, 0);
               isBlockItem = bakedmodel.isGui3d();
            } catch (Exception var38) {
               isBlockItem = false;
            }
         }

         VertexConsumer builder = buffer.getBuffer(RenderType.solid());
         PoseStack ms = matrices.getModel();
         ms.pushPose();

         try {
            PoseTransformStack msr = TransformStack.of(ms);
            msr.center();
            if (inverted) {
               msr.rotateXDegrees(180.0F);
            }

            SuperByteBuffer baseBuffer = CachedBuffers.partial(SentryPartialModels.SENTRU_BASE, blockState);
            if (baseBuffer != null) {
               ms.pushPose();
               transformBase(msr, baseAngle);
               applyDye(baseBuffer, virtualBE.color, SentrySpriteShifts.BASE_TEXTURES);
               ((SuperByteBuffer)baseBuffer.light(light).transform(ms)).renderInto(matrices.getViewProjection(), builder);
               ms.popPose();
            }

            SuperByteBuffer lowerBodyBuffer = CachedBuffers.partial(SentryPartialModels.ARM_LOWER_BODY, blockState);
            SuperByteBuffer upperBodyBuffer = CachedBuffers.partial(SentryPartialModels.ARM_UPPER_BODY, blockState);
            SuperByteBuffer clawBaseBuffer = CachedBuffers.partial(SentryPartialModels.ARM_CLAW_BASE, blockState);
            if (lowerBodyBuffer != null && upperBodyBuffer != null && clawBaseBuffer != null) {
               ms.pushPose();
               transformBase(msr, baseAngle);
               transformLowerArm(msr, lowerArmAngle);
               applyDye(lowerBodyBuffer, virtualBE.color, SentrySpriteShifts.ARM_TEXTURES);
               ((SuperByteBuffer)lowerBodyBuffer.light(light).transform(ms)).renderInto(matrices.getViewProjection(), builder);
               transformUpperArm(msr, upperArmAngle);
               applyDye(upperBodyBuffer, virtualBE.color, SentrySpriteShifts.ARM_TEXTURES);
               ((SuperByteBuffer)upperBodyBuffer.light(light).transform(ms)).renderInto(matrices.getViewProjection(), builder);
               transformHead(msr, headAngle);
               if (inverted) {
                  msr.rotateZDegrees(180.0F);
               }

               applyDye(clawBaseBuffer, virtualBE.color, SentrySpriteShifts.ARM_TEXTURES);
               ((SuperByteBuffer)clawBaseBuffer.light(light).transform(ms)).renderInto(matrices.getViewProjection(), builder);
               ms.popPose();
            }

            Matrix4f clawTipWorldMatrix = null;
            ms.pushPose();
            transformBase(msr, baseAngle);
            transformLowerArm(msr, lowerArmAngle);
            transformUpperArm(msr, upperArmAngle);
            transformHead(msr, headAngle);

            for (int flip : Iterate.positiveAndNegative) {
               ms.pushPose();
               transformClawHalf(msr, hasItem, isBlockItem, flip);
               if (flip > 0) {
                  clawTipWorldMatrix = new Matrix4f(matrices.getWorld());
                  clawTipWorldMatrix.mul(ms.last().pose());
               }

               PartialModel gripModel = flip > 0 ? SentryPartialModels.ARM_CLAW_GRIP_LOWER : SentryPartialModels.ARM_CLAW_GRIP_UPPER;
               SuperByteBuffer gripBuffer = CachedBuffers.partial(gripModel, blockState);
               if (gripBuffer != null) {
                  applyDye(gripBuffer, virtualBE.color, SentrySpriteShifts.ARM_TEXTURES);
                  ((SuperByteBuffer)gripBuffer.light(light).transform(ms)).renderInto(matrices.getViewProjection(), builder);
               }

               ms.popPose();
            }

            ms.popPose();
            ms.popPose();
            SuperByteBuffer cogBuffer = CachedBuffers.partial(SentryPartialModels.SENTRU_COG, blockState);
            if (cogBuffer != null) {
               ms.pushPose();
               msr.center();
               float speed = virtualBE.getSpeed();
               float time = AnimationTickHolder.getRenderTime();
               float cogAngle = time * speed * 3.0F / 10.0F % 360.0F;
               ms.mulPose(Axis.YP.rotationDegrees(cogAngle));
               msr.uncenter();
               applyDye(cogBuffer, virtualBE.color, SentrySpriteShifts.COG_TEXTURES);
               ((SuperByteBuffer)cogBuffer.light(light).transform(ms)).renderInto(matrices.getViewProjection(), builder);
               ms.popPose();
            }

            if (clawTipWorldMatrix != null) {
               renderHeldItem(buffer, renderWorld, virtualBE, light, clawTipWorldMatrix, context);
               Level ammoLevel = (Level)(context.world != null ? context.world : renderWorld);
               Matrix4f ammoMatrix = new Matrix4f(clawTipWorldMatrix);
               Vector4f ammoWorldPos = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
               ammoMatrix.transform(ammoWorldPos);
               Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
               Vec3 cameraPos = camera.getPosition();

               for (int slot = 0; slot < virtualBE.attachedAmmoBoxes.size(); slot++) {
                  ItemStack box = (ItemStack)virtualBE.attachedAmmoBoxes.get(slot);
                  if (!box.isEmpty()) {
                     PoseStack boxStack = new PoseStack();
                     boxStack.translate((double)ammoWorldPos.x() - cameraPos.x, (double)ammoWorldPos.y() - cameraPos.y, (double)ammoWorldPos.z() - cameraPos.z);
                     Quaternionf boxRot = new Quaternionf();
                     ammoMatrix.getUnnormalizedRotation(boxRot);
                     boxStack.mulPose(boxRot);
                     boxStack.scale(0.5F, 0.5F, 0.5F);
                     if (slot == 0) {
                        boxStack.translate(0.35, 0.2, 0.9);
                        boxStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                     } else {
                        boxStack.translate(-0.35, 0.2, 0.9);
                        boxStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                     }

                     Minecraft.getInstance()
                        .getItemRenderer()
                        .renderStatic(box, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, boxStack, buffer, ammoLevel, 0);
                  }
               }
            }
         } catch (Exception var39) {
            LOGGER.warn("renderInContraption failed", var39);
         } finally {
            ms.popPose();
         }
      }
   }

   private static void renderHeldItem(
      MultiBufferSource buffer,
      VirtualRenderWorld renderWorld,
      VirtualSentryArmBlockEntity virtualBE,
      int light,
      Matrix4f clawTipWorldMatrix,
      MovementContext context
   ) {
      ItemStack heldItem = virtualBE.getHeldItem();
      if (!heldItem.isEmpty()) {
         boolean isCeiling = false;
         if (context != null && context.state != null && context.state.hasProperty(SentryArmBlock.CEILING)) {
            isCeiling = (Boolean)context.state.getValue(SentryArmBlock.CEILING);
         }

         BakedModel heldItemModel = Minecraft.getInstance().getItemRenderer().getModel(heldItem, renderWorld, null, 0);
         boolean isBlockItem = heldItemModel != null && heldItemModel.isGui3d();
         PoseStack gunStack = new PoseStack();
         gunStack.last().pose().set(clawTipWorldMatrix);
         gunStack.mulPose(Axis.XP.rotationDegrees(90.0F));
         gunStack.translate(0.0F, isBlockItem ? -0.5625F : -0.625F, 0.0F);
         boolean isGun = heldItem.getItem() instanceof IGun;
         if (isGun) {
            ResourceLocation gunId = ((IGun)heldItem.getItem()).getGunId(heldItem);
            gunStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            if (isCeiling) {
               gunStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }

            float yOffset = isCeiling ? 0.1F : 0.42F;
            gunStack.translate(0.0F, yOffset, 0.3F);
            if (gunId.getPath().contains("minigun")) {
               gunStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
               gunStack.translate(0.0, -0.7, 0.1);
            }
         }

         Matrix4f finalMatrix = gunStack.last().pose();
         Vector4f worldPos = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
         finalMatrix.transform(worldPos);
         Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
         Vec3 cameraPos = camera.getPosition();
         PoseStack viewStack = new PoseStack();
         viewStack.translate((double)worldPos.x() - cameraPos.x, (double)worldPos.y() - cameraPos.y, (double)worldPos.z() - cameraPos.z);
         Quaternionf worldRot = new Quaternionf();
         finalMatrix.getUnnormalizedRotation(worldRot);
         viewStack.mulPose(worldRot);
         if (isGun) {
            viewStack.scale(1.5F, 1.5F, 1.5F);
         } else {
            float s = isBlockItem ? 0.5F : 0.625F;
            viewStack.scale(s, s, s);
         }

         BufferSource cleanBuffer = Minecraft.getInstance().renderBuffers().bufferSource();

         try {
            Minecraft.getInstance()
               .getItemRenderer()
               .renderStatic(
                  heldItem,
                  isGun ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.FIXED,
                  light,
                  OverlayTexture.NO_OVERLAY,
                  viewStack,
                  cleanBuffer,
                  renderWorld,
                  0
               );
            if (isGun) {
               renderMuzzleFlashStatic(virtualBE, heldItem, viewStack, cleanBuffer);
               long now = System.currentTimeMillis();
               long lst = virtualBE.getLastShootTime();
               LOGGER.info("[ContraptionShellCheck] lst={} now-lst={} lastEject={}", new Object[]{lst, now - lst, virtualBE.lastShellEjectTime});
               if (lst > 0L && now - lst < 200L && lst != virtualBE.lastShellEjectTime) {
                  Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(heldItem);
                  displayOpt.ifPresent(
                     display -> {
                        if (display.getShellEjection() != null) {
                           BedrockGunModel gunModel = display.getGunModel();
                           if (gunModel != null && gunModel.getRootNode() != null) {
                              List<BedrockPart> shellPath = new ArrayList<>();
                              if (!findPathRecursive(gunModel.getRootNode(), "shell", shellPath)) {
                                 findPathRecursive(gunModel.getRootNode(), "shell_ejection", shellPath);
                              }

                              List<BedrockPart> handPath = gunModel.getThirdPersonHandOriginPath();
                              if (!shellPath.isEmpty() && handPath != null && !handPath.isEmpty()) {
                                 PoseStack handCalc = new PoseStack();

                                 for (BedrockPart p : handPath) {
                                    p.translateAndRotateAndScale(handCalc);
                                 }

                                 Vector4f handLocal = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
                                 handCalc.last().pose().transform(handLocal);
                                 PoseStack shellCalc = new PoseStack();

                                 for (BedrockPart p : shellPath) {
                                    p.translateAndRotateAndScale(shellCalc);
                                 }

                                 Vector4f shellLocal = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
                                 shellCalc.last().pose().transform(shellLocal);
                                 float dx = shellLocal.x() - handLocal.x();
                                 float dy = shellLocal.y() - handLocal.y();
                                 float dz = shellLocal.z() - handLocal.z();
                                 float gs = 0.6F;
                                 if (display.getTransform() != null && display.getTransform().getScale() != null) {
                                    Vector3f ts = display.getTransform().getScale().getThirdPerson();
                                    if (ts != null) {
                                       gs = ts.x();
                                    }
                                 }

                                 Matrix4f shellMatrix = new Matrix4f(finalMatrix);
                                 shellMatrix.mul(new Matrix4f().translate(-dx * gs, -dy * gs, dz * gs));
                                 Vector4f worldShellPos = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
                                 shellMatrix.transform(worldShellPos);
                                 Vec3 calculatedPos = new Vec3((double)worldShellPos.x(), (double)worldShellPos.y(), (double)worldShellPos.z());
                                 Vector3f vel = new Vector3f(0.05F, 0.02F, -0.01F);
                                 Matrix3f nm = new Matrix3f();
                                 shellMatrix.get3x3(nm);
                                 if (((IGun)heldItem.getItem()).getGunId(heldItem).getPath().contains("minigun")) {
                                    Vector3f mg = new Vector3f(0.2F, 0.65F, 0.55F);
                                    nm.transform(mg);
                                    calculatedPos = calculatedPos.add((double)mg.x(), (double)mg.y(), (double)mg.z());
                                    vel = new Vector3f(-0.11F, 0.08F, -0.05F);
                                 }

                                 nm.transform(vel);
                                 Vector3f accel = new Vector3f(display.getShellEjection().getAcceleration());
                                 accel.mul(0.03F);
                                 Vec3 fPos = calculatedPos;
                                 Vector3f fVel = vel;
                                 TimelessAPI.getCommonGunIndex(((IGun)heldItem.getItem()).getGunId(heldItem))
                                    .ifPresent(
                                       idx -> SentryShellManager.addShell(
                                             idx.getGunData().getAmmoId(),
                                             fPos,
                                             fVel,
                                             display.getShellEjection().getAngularVelocity(),
                                             accel,
                                             (double)display.getShellEjection().getLivingTime()
                                          )
                                    );
                                 LOGGER.info(
                                    "[ContraptionShell] added at ({},{},{}) lst={} diff={}",
                                    new Object[]{String.format("%.1f", fPos.x), String.format("%.1f", fPos.y), String.format("%.1f", fPos.z), lst, now - lst}
                                 );
                              }
                           }
                        }
                     }
                  );
                  virtualBE.lastShellEjectTime = lst;
               }
            }

            cleanBuffer.endBatch();
         } catch (Exception var27) {
            var27.printStackTrace();
         } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
         }
      }
   }

   private static void renderMuzzleFlashStatic(SentryArmBlockEntity sentry, ItemStack stack, PoseStack ms, MultiBufferSource buffer) {
      boolean isSilenced = ArmSoundHelper.isSilenced(stack);
      if (!isSilenced) {
         long timeSinceShoot = System.currentTimeMillis() - sentry.getLastShootTime();
         if (timeSinceShoot >= 0L && timeSinceShoot <= 50L) {
            Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(stack);
            if (!displayOpt.isEmpty()) {
               GunDisplayInstance display = displayOpt.get();
               BedrockGunModel gunModel = display.getGunModel();
               if (gunModel != null) {
                  MuzzleFlash muzzleFlash = display.getMuzzleFlash();
                  if (muzzleFlash != null) {
                     ms.pushPose();
                     ms.translate(0.0, 1.5, 0.0);
                     ms.scale(-1.0F, -1.0F, 1.0F);
                     Vector3f transformScale = new Vector3f(1.0F, 1.0F, 1.0F);
                     if (display.getTransform() != null && display.getTransform().getScale() != null) {
                        transformScale = display.getTransform().getScale().getThirdPerson();
                     }

                     applyPositioningNodeTransformStatic(gunModel.getThirdPersonHandOriginPath(), ms, transformScale);
                     applyScaleTransformStatic(ms, transformScale);
                     List<BedrockPart> path = gunModel.getMuzzleFlashPosPath();
                     if (path != null) {
                        for (BedrockPart part : path) {
                           part.translateAndRotateAndScale(ms);
                        }
                     }

                     float flashScale = (float)(0.5 * (double)muzzleFlash.getScale());
                     float randomRotate = (float)(Math.random() * 360.0);
                     ms.mulPose(Axis.ZP.rotationDegrees(randomRotate));
                     ms.scale(flashScale, flashScale, flashScale);
                     VertexConsumer consumerBg = buffer.getBuffer(RenderType.entityTranslucent(muzzleFlash.getTexture()));
                     drawCrossQuadStatic(ms, consumerBg);
                     VertexConsumer consumerFg = buffer.getBuffer(RenderType.energySwirl(muzzleFlash.getTexture(), 0.0F, 0.0F));
                     ms.pushPose();
                     ms.scale(0.5F, 0.5F, 0.5F);
                     drawCrossQuadStatic(ms, consumerFg);
                     ms.popPose();
                     ms.popPose();
                  }
               }
            }
         }
      }
   }

   private static void applyPositioningNodeTransformStatic(List<BedrockPart> nodePath, PoseStack poseStack, Vector3f scale) {
      if (nodePath != null) {
         if (scale == null) {
            scale = new Vector3f(1.0F, 1.0F, 1.0F);
         }

         poseStack.translate(0.0F, 1.5F, 0.0F);

         for (int i = nodePath.size() - 1; i >= 0; i--) {
            BedrockPart t = nodePath.get(i);
            poseStack.mulPose(Axis.XN.rotation(t.xRot));
            poseStack.mulPose(Axis.YN.rotation(t.yRot));
            poseStack.mulPose(Axis.ZN.rotation(t.zRot));
            if (t.getParent() != null) {
               poseStack.translate(-t.x * scale.x() / 16.0F, -t.y * scale.y() / 16.0F, -t.z * scale.z() / 16.0F);
            } else {
               poseStack.translate(-t.x * scale.x() / 16.0F, (1.5F - t.y / 16.0F) * scale.y(), -t.z * scale.z() / 16.0F);
            }
         }

         poseStack.translate(0.0F, -1.5F, 0.0F);
      }
   }

   private static void applyScaleTransformStatic(PoseStack poseStack, Vector3f scale) {
      if (scale != null) {
         poseStack.translate(0.0F, 1.5F, 0.0F);
         poseStack.scale(scale.x(), scale.y(), scale.z());
         poseStack.translate(0.0F, -1.5F, 0.0F);
      }
   }

   private static void drawCrossQuadStatic(PoseStack ms, VertexConsumer consumer) {
      Matrix4f pose = ms.last().pose();
      Matrix3f normal = ms.last().normal();
      float size = 1.0F;
      float min = -size;
      float u0 = 0.0F;
      float u1 = 1.0F;
      float v0 = 0.0F;
      float v1 = 1.0F;
      vertexStatic(consumer, pose, normal, min, size, 0.0F, u0, v1);
      vertexStatic(consumer, pose, normal, size, size, 0.0F, u1, v1);
      vertexStatic(consumer, pose, normal, size, min, 0.0F, u1, v0);
      vertexStatic(consumer, pose, normal, min, min, 0.0F, u0, v0);
      ms.pushPose();
      ms.mulPose(Axis.YP.rotationDegrees(90.0F));
      Matrix4f pose2 = ms.last().pose();
      Matrix3f normal2 = ms.last().normal();
      vertexStatic(consumer, pose2, normal2, min, size, 0.0F, u0, v1);
      vertexStatic(consumer, pose2, normal2, size, size, 0.0F, u1, v1);
      vertexStatic(consumer, pose2, normal2, size, min, 0.0F, u1, v0);
      vertexStatic(consumer, pose2, normal2, min, min, 0.0F, u0, v0);
      ms.popPose();
   }

   private static void vertexStatic(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v) {
      consumer.addVertex(pose, x, y, z)
         .setColor(255, 255, 255, 255)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(15728880)
         .setNormal(0.0F, 1.0F, 0.0F);
   }
}

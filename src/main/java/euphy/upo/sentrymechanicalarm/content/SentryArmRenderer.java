package euphy.upo.sentrymechanicalarm.content;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import euphy.upo.sentrymechanicalarm.util.ArmSoundHelper;
import euphy.upo.sentrymechanicalarm.util.SentryShellManager;
import euphy.upo.sentrymechanicalarm.util.SentrySpriteShifts;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import org.slf4j.Logger;

import java.lang.Math;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SentryArmRenderer extends KineticBlockEntityRenderer<SentryArmBlockEntity> {
    public static final Logger LOGGER = LogUtils.getLogger();

    public SentryArmRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRenderOffScreen(SentryArmBlockEntity be) {
        return true;
    }

    @Override
    protected void renderSafe(SentryArmBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {

        BlockState blockState = be.getBlockState();
        renderCog(be, ms, buffer, light, be.color);

        ItemStack item = be.getHeldItem();
        boolean hasItem = !item.isEmpty();

        boolean isBlockItem = false;
        if (hasItem) {
            try {
                ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
                BakedModel bakedModel = itemRenderer.getModel(item, be.getLevel(), (LivingEntity) null, 0);
                isBlockItem = item.getItem() instanceof BlockItem && bakedModel.isGui3d();
            } catch (Exception e) {
                isBlockItem = false;
            }
        }

        VertexConsumer builder = buffer.getBuffer(RenderType.solid());

        PoseStack msLocal = new PoseStack();
        PoseTransformStack msr = TransformStack.of(msLocal);

        boolean inverted = blockState.getValue(SentryArmBlock.CEILING);

        float baseAngle = be.baseAngle.getValue(pt);
        float lowerArmAngle = be.lowerArmAngle.getValue(pt) - 135.0F;
        float upperArmAngle = be.upperArmAngle.getValue(pt) - 90.0F;
        float headAngle = be.headAngle.getValue(pt);

        if (AeronauticsHelper.isAeronauticsLoaded() && be.getLevel() != null) {
            Vec3 worldPos = be.getBlockPos().getCenter();
            float shipYaw = AeronauticsHelper.getShipYaw(be.getLevel(), worldPos);
            float shipRoll = AeronauticsHelper.getShipRoll(be.getLevel(), worldPos);
            baseAngle -= shipYaw;
            if (Math.abs(shipRoll) > 1f) {
                msr.rotateZDegrees(-shipRoll);
            }
        }

        int color = 0xFFFFFF;

        msr.center();
        if (inverted) {
            msr.rotateXDegrees(180.0F);
        }

        this.renderArm(builder, ms, msLocal, msr, blockState, color, baseAngle, lowerArmAngle, upperArmAngle, headAngle, inverted, hasItem, isBlockItem, light, be.color);

        if (hasItem) {
            ms.pushPose();
            msr.rotateXDegrees(90.0F);
            msLocal.translate(0.0F, isBlockItem ? -0.5625F : -0.625F, 0.0F);

            if (item.getItem() instanceof IGun iGun) {

                ResourceLocation gunId = iGun.getGunId(item);
                msLocal.mulPose(Axis.XP.rotationDegrees(-90));
                msLocal.translate(0, 0.18, 0);

                if (gunId.getPath().contains("minigun")) {
                    msLocal.mulPose(Axis.XP.rotationDegrees(-90));
                    msLocal.translate(0, -0.7, 0.2);
                }
 

                float armScale = 1.5f;
                msLocal.scale(armScale, armScale, armScale);
                if (be.shouldEjectShell()) {
                    Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(item);
                    displayOpt.ifPresent(display -> {
                        if (display.getShellEjection() != null) {
 
                            tryManualEject(be, item, display, msLocal, display.getShellEjection());
                        }
                    });
                    be.setShellEjected();
                }
                ms.pushPose();
                ms.last().pose().mul(msLocal.last().pose());
                ItemDisplayContext displayContext = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                Minecraft.getInstance().getItemRenderer().renderStatic(item, displayContext, light, overlay, ms, buffer, be.getLevel(), 0);

                renderAmmoBoxes(be, ms, buffer, light, overlay);
                renderMuzzleFlash(be, item, ms, buffer);

                ms.popPose();

            } else {
 
                float itemScale = isBlockItem ? 0.5F : 0.625F;
                msLocal.scale(itemScale, itemScale, itemScale);
                ms.last().pose().mul(msLocal.last().pose());
                Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
            }

            ms.popPose();
        }
    }

    private void renderCog(SentryArmBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, Optional<DyeColor> color) {
        BlockState blockState = be.getBlockState();
        SuperByteBuffer cog = CachedBuffers.partial(SentryPartialModels.SENTRU_COG, blockState);

        Direction.Axis axis = Direction.Axis.Y;
        float angle = getAngleForBe(be, be.getBlockPos(), axis);

        kineticRotationTransform(cog, be, axis, angle, light);

        applyDye(cog, color, SentrySpriteShifts.COG_TEXTURES);

        cog.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private void renderMuzzleFlash(SentryArmBlockEntity sentry, ItemStack stack, PoseStack ms, MultiBufferSource buffer) {
        boolean isSilenced = ArmSoundHelper.isSilenced(stack);
        if (isSilenced) return;

        long timeSinceShoot = System.currentTimeMillis() - sentry.getLastShootTime();
        if (timeSinceShoot < 0 || timeSinceShoot > 50) return;
 
        Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(stack);
        if (displayOpt.isEmpty()) return;
        GunDisplayInstance display = displayOpt.get();

        BedrockGunModel gunModel = display.getGunModel();
        if (gunModel == null) return;

        MuzzleFlash muzzleFlash = display.getMuzzleFlash();
        if (muzzleFlash == null) return;

        ms.pushPose();

 
        ms.translate(0, 1.5, 0);
        ms.scale(-1.0f, -1.0f, 1.0f); 

        Vector3f transformScale = null;
        if (display.getTransform() != null && display.getTransform().getScale() != null) {
            transformScale = display.getTransform().getScale().getThirdPerson();
        }
        applyPositioningNodeTransform(gunModel.getThirdPersonHandOriginPath(), ms, transformScale);
        applyScaleTransform(ms, transformScale);

 
        List<BedrockPart> path = gunModel.getMuzzleFlashPosPath();
        if (path != null) {
            for (BedrockPart part : path) {
                part.translateAndRotateAndScale(ms);
            }
        }
 
        float flashScale = (float) (0.5 * muzzleFlash.getScale());
        float randomRotate = (float) (Math.random() * 360.0);
        ms.mulPose(Axis.ZP.rotationDegrees(randomRotate));

        ms.scale(flashScale, flashScale, flashScale);

        VertexConsumer consumerBg = buffer.getBuffer(RenderType.entityTranslucent(muzzleFlash.getTexture()));
        drawCrossQuad(ms, consumerBg, 1.0f);

        VertexConsumer consumerFg = buffer.getBuffer(RenderType.entityTranslucent(muzzleFlash.getTexture()));
        ms.pushPose();
        ms.scale(0.5f, 0.5f, 0.5f);
        drawCrossQuad(ms, consumerFg, 1.0f);
        ms.popPose();

        ms.popPose();
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
 
            float invScale = 1.0f / 1.5f;
            ms.scale(invScale, invScale, invScale);

            ms.translate(0, 0.7, -0.2);

            ms.mulPose(Axis.XP.rotationDegrees(90));
            ms.scale(1.5f, 1.5f, 1.5f);
        }


        List<ItemStack> boxes = be.attachedAmmoBoxes;
        for (int i = 0; i < boxes.size(); i++) {
            ItemStack box = boxes.get(i);
            if (box.isEmpty()) continue;

            ms.pushPose();
            float boxScale = 0.3f;
            ms.scale(boxScale, boxScale, boxScale);

            if (i == 0) {
                ms.translate(0.35, -0.5, 0.6);
                ms.mulPose(Axis.YP.rotationDegrees(-90));
            } else {
                ms.translate(-0.35, -0.5, 0.6);
                ms.mulPose(Axis.YP.rotationDegrees(90));
            }
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    box,
                    ItemDisplayContext.FIXED,
                    light,
                    overlay,
                    ms,
                    buffer,
                    be.getLevel(),
                    0
            );
            ms.popPose();
        }
        ms.popPose();

    }

    private void applyPositioningNodeTransform(List<BedrockPart> nodePath, PoseStack poseStack, Vector3f scale) {
        if (nodePath != null) {
            if (scale == null) {
                scale = new Vector3f(1.0F, 1.0F, 1.0F);
            }
            poseStack.translate(0.0F, 1.5F, 0.0F);
            for (int i = nodePath.size() - 1; i >= 0; --i) {
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
        float size = 1.0f;
        float min = -size;
        float max = size;
        float u0 = 0, u1 = 1;
        float v0 = 0, v1 = 1;

        vertex(consumer, pose, normal, min, max, 0, u0, v1);
        vertex(consumer, pose, normal, max, max, 0, u1, v1);
        vertex(consumer, pose, normal, max, min, 0, u1, v0);
        vertex(consumer, pose, normal, min, min, 0, u0, v0);

        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(90));
        Matrix4f pose2 = ms.last().pose();
        Matrix3f normal2 = ms.last().normal();

        vertex(consumer, pose2, normal2, min, max, 0, u0, v1);
        vertex(consumer, pose2, normal2, max, max, 0, u1, v1);
        vertex(consumer, pose2, normal2, max, min, 0, u1, v0);
        vertex(consumer, pose2, normal2, min, min, 0, u0, v0);

        ms.popPose();
    }

    private void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0, 1, 0);
    }

    private void renderArm(VertexConsumer builder, PoseStack ms, PoseStack msLocal, TransformStack msr, BlockState blockState, int color, float baseAngle, float lowerArmAngle, float upperArmAngle, float headAngle, boolean inverted, boolean hasItem, boolean isBlockItem, int light, Optional<DyeColor> dyeColor) {
 
        SuperByteBuffer base = CachedBuffers.partial(SentryPartialModels.SENTRU_BASE, blockState).light(light);
        SuperByteBuffer lowerBody = CachedBuffers.partial(SentryPartialModels.ARM_LOWER_BODY, blockState).light(light);
        SuperByteBuffer upperBody = CachedBuffers.partial(SentryPartialModels.ARM_UPPER_BODY, blockState).light(light);
        SuperByteBuffer claw = CachedBuffers.partial(SentryPartialModels.ARM_CLAW_BASE, blockState).light(light);
        SuperByteBuffer upperClawGrip = CachedBuffers.partial(SentryPartialModels.ARM_CLAW_GRIP_UPPER, blockState).light(light);
        SuperByteBuffer lowerClawGrip = CachedBuffers.partial(SentryPartialModels.ARM_CLAW_GRIP_LOWER, blockState).light(light);

        applyDye(base, dyeColor, SentrySpriteShifts.BASE_TEXTURES);
        applyDye(lowerBody, dyeColor, SentrySpriteShifts.ARM_TEXTURES);
        applyDye(upperBody, dyeColor, SentrySpriteShifts.ARM_TEXTURES);
        applyDye(claw, dyeColor, SentrySpriteShifts.ARM_TEXTURES);
        applyDye(upperClawGrip, dyeColor, SentrySpriteShifts.ARM_TEXTURES);
        applyDye(lowerClawGrip, dyeColor, SentrySpriteShifts.ARM_TEXTURES);

        transformBase(msr, baseAngle);
        base.transform(msLocal).renderInto(ms, builder);
        transformLowerArm(msr, lowerArmAngle);
        lowerBody.color(color).transform(msLocal).renderInto(ms, builder);

        transformUpperArm(msr, upperArmAngle);
        upperBody.color(color).transform(msLocal).renderInto(ms, builder);

        transformHead(msr, headAngle);
        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }
        claw.transform(msLocal).renderInto(ms, builder);
        if (inverted) {
            msr.rotateZDegrees(180.0F);
        }
        for (int flip : Iterate.positiveAndNegative) {
            msLocal.pushPose();
            transformClawHalf(msr, hasItem, isBlockItem, flip);
            (flip > 0 ? lowerClawGrip : upperClawGrip).transform(msLocal).renderInto(ms, builder);
            msLocal.popPose();
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
        msr.translate(0.0F, -flip * (hasItem ? (isBlockItem ? 0.1875F : 0.078125F) : 0.0625F), -0.375F);
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

    private boolean findPathRecursive(BedrockPart current, String targetName, List<BedrockPart> path) {
        if (current == null) return false;
        path.add(current);
        if (targetName.equals(current.name)) return true;
        if (current.children != null) {
            for (BedrockPart child : current.children) {
                if (findPathRecursive(child, targetName, path)) return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }


    private void tryManualEject(SentryArmBlockEntity be, ItemStack stack, GunDisplayInstance display, PoseStack msLocal, com.tacz.guns.client.resource.pojo.display.gun.ShellEjection ejection) {
        BedrockGunModel gunModel = display.getGunModel();
        if (gunModel == null) return;

        List<BedrockPart> shellPath = new ArrayList<>();
        if (!findPathRecursive(gunModel.getRootNode(), "shell", shellPath)) {
            findPathRecursive(gunModel.getRootNode(), "shell_ejection", shellPath);
        }
        if (shellPath.isEmpty()) return;

        List<BedrockPart> handPath = gunModel.getThirdPersonHandOriginPath();
        if (handPath == null || handPath.isEmpty()) return;

        PoseStack handMs = new PoseStack();
        for (BedrockPart part : handPath) {
            part.translateAndRotateAndScale(handMs);
        }
        Vector4f handPos = new Vector4f(0, 0, 0, 1);
        handMs.last().pose().transform(handPos);

        PoseStack shellMs = new PoseStack();
        for (BedrockPart part : shellPath) {
            part.translateAndRotateAndScale(shellMs);
        }
        Vector4f shellPos = new Vector4f(0, 0, 0, 1);
        shellMs.last().pose().transform(shellPos);

        float dx = shellPos.x() - handPos.x();
        float dy = shellPos.y() - handPos.y();
        float dz = shellPos.z() - handPos.z();

        float gunScale = 0.6f;
        if (display.getTransform() != null && display.getTransform().getScale() != null) {
            Vector3f s = display.getTransform().getScale().getThirdPerson();
            if (s != null) gunScale = s.x();
        }

        PoseStack finalMs = new PoseStack();
        finalMs.last().pose().set(msLocal.last().pose());
        finalMs.translate(-dx * gunScale, -dy * gunScale, dz * gunScale);

        Matrix4f poseMatrix = finalMs.last().pose();
        Vector4f offsetVec = new Vector4f(0, 0, 0, 1);
        poseMatrix.transform(offsetVec);

        Vec3 calculatedPos = new Vec3(
                be.getBlockPos().getX() + offsetVec.x(),
                be.getBlockPos().getY() + offsetVec.y(),
                be.getBlockPos().getZ() + offsetVec.z()
        );

        Vector3f calculatedVel = new Vector3f(0.05f, 0.02f, -0.01f);
        Matrix3f normalMatrix = new Matrix3f();
        poseMatrix.get3x3(normalMatrix);
        IGun iGun = (IGun) stack.getItem();
        ResourceLocation gunId = iGun.getGunId(stack);

 
        if (gunId.getPath().contains("minigun")) {
            Vector3f minigunLocalOffset = new Vector3f(0.2f, 0.65f, 0.55f);
            normalMatrix.transform(minigunLocalOffset);
            calculatedPos = calculatedPos.add(minigunLocalOffset.x(), minigunLocalOffset.y(), minigunLocalOffset.z());

            calculatedVel = new Vector3f(-0.11f, 0.08f, -0.05f);
        }
        poseMatrix.get3x3(normalMatrix);
        normalMatrix.transform(calculatedVel);

        final Vec3 finalSpawnPos = calculatedPos;
        final Vector3f finalVelocity = calculatedVel;
        final Vector3f accel = new Vector3f(ejection.getAcceleration());
        accel.mul(0.03f);

        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
           SentryShellManager.addShell(
                    index.getGunData().getAmmoId(),
                   finalSpawnPos,
                   finalVelocity,
                    ejection.getAngularVelocity(),
                    accel,
                    ejection.getLivingTime()
            );
        });

    }

    public static void renderInContraption(com.simibubi.create.content.contraptions.behaviour.MovementContext context,
                                           com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld renderWorld,
                                           com.simibubi.create.content.contraptions.render.ContraptionMatrices matrices,
                                           MultiBufferSource buffer) {
        if (context.temporaryData == null || !(context.temporaryData instanceof VirtualSentryArmBlockEntity)) {
            VirtualSentryArmBlockEntity newBE = new VirtualSentryArmBlockEntity(BlockPos.ZERO, context.state);
            if (context.blockEntityData != null) {
                if (context.blockEntityData.contains("Angles")) {
                    CompoundTag angles = context.blockEntityData.getCompound("Angles");
                    newBE.baseAngle.setValue(angles.getFloat("Base"));
                    newBE.lowerArmAngle.setValue(angles.getFloat("Lower"));
                    newBE.upperArmAngle.setValue(angles.getFloat("Upper"));
                    newBE.headAngle.setValue(angles.getFloat("Head"));
                }
                if (context.blockEntityData.contains("Speed")) {
                    newBE.setSpeed(context.blockEntityData.getFloat("Speed"));
                }
                if (context.blockEntityData.contains("SentryHeldItem")) {
                    newBE.setHeldItem(ItemStack.parseOptional(renderWorld.registryAccess(), context.blockEntityData.getCompound("SentryHeldItem")));
                }
                if (context.blockEntityData.contains("SentryAmmoBoxes")) {
                    net.minecraft.world.ContainerHelper.loadAllItems(context.blockEntityData.getCompound("SentryAmmoBoxes"), newBE.attachedAmmoBoxes, renderWorld.registryAccess());
                }
                if (context.blockEntityData.contains("color")) {
                    int colorId = context.blockEntityData.getInt("color");
                    newBE.color = Optional.of(DyeColor.byId(colorId));
                }
            }
            context.temporaryData = newBE;
        }
        VirtualSentryArmBlockEntity virtualBE = (VirtualSentryArmBlockEntity) context.temporaryData;

        BlockState blockState = context.state;
        float pt = net.createmod.catnip.animation.AnimationTickHolder.getPartialTicks();
        float baseAngle = virtualBE.baseAngle.getValue(pt);
        float lowerArmAngle = virtualBE.lowerArmAngle.getValue(pt) - 135.0F;
        float upperArmAngle = virtualBE.upperArmAngle.getValue(pt) - 90.0F;
        float headAngle = virtualBE.headAngle.getValue(pt);
        boolean inverted = blockState.getValue(SentryArmBlock.CEILING);
        int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        if (context.contraption != null && context.contraption.entity != null) {
            Vec3 localPos = net.createmod.catnip.math.VecHelper.getCenterOf(context.localPos);
            Vec3 globalPos = context.contraption.entity.toGlobalVector(localPos, pt);
            light = net.minecraft.client.renderer.LevelRenderer.getLightColor(context.world, BlockPos.containing(globalPos));
        }

        ItemStack heldItem = virtualBE.getHeldItem();
        boolean hasItem = !heldItem.isEmpty();
        boolean isBlockItem = false;
        if (hasItem) {
            try {
                net.minecraft.client.resources.model.BakedModel bakedmodel = Minecraft.getInstance().getItemRenderer().getModel(heldItem, renderWorld, null, 0);
                isBlockItem = bakedmodel.isGui3d();
            } catch (Exception e) {
                isBlockItem = false;
            }
        }

        VertexConsumer builder = buffer.getBuffer(RenderType.solid());
        PoseStack ms = matrices.getModel();
        ms.pushPose();
        try {
            dev.engine_room.flywheel.lib.transform.PoseTransformStack msr = dev.engine_room.flywheel.lib.transform.TransformStack.of(ms);
            msr.center();

            if (inverted) {
                msr.rotateXDegrees(180.0F);
            }
            ms.pushPose();
            transformBase(msr, baseAngle);
            SuperByteBuffer baseBuffer = CachedBuffers.partial(SentryPartialModels.SENTRU_BASE, blockState);
            applyDye(baseBuffer, virtualBE.color, SentrySpriteShifts.BASE_TEXTURES);
            baseBuffer.light(light).transform(ms).renderInto(matrices.getViewProjection(), builder);
            ms.popPose();

            ms.pushPose();
            transformBase(msr, baseAngle);
            transformLowerArm(msr, lowerArmAngle);
            SuperByteBuffer lowerBodyBuffer = CachedBuffers.partial(SentryPartialModels.ARM_LOWER_BODY, blockState);
            applyDye(lowerBodyBuffer, virtualBE.color, SentrySpriteShifts.ARM_TEXTURES);
            lowerBodyBuffer.light(light).transform(ms).renderInto(matrices.getViewProjection(), builder);

            transformUpperArm(msr, upperArmAngle);
            SuperByteBuffer upperBodyBuffer = CachedBuffers.partial(SentryPartialModels.ARM_UPPER_BODY, blockState);
            applyDye(upperBodyBuffer, virtualBE.color, SentrySpriteShifts.ARM_TEXTURES);
            upperBodyBuffer.light(light).transform(ms).renderInto(matrices.getViewProjection(), builder);

            transformHead(msr, headAngle);
            if (inverted) msr.rotateZDegrees(180.0F);
            SuperByteBuffer clawBaseBuffer = CachedBuffers.partial(SentryPartialModels.ARM_CLAW_BASE, blockState);
            applyDye(clawBaseBuffer, virtualBE.color, SentrySpriteShifts.ARM_TEXTURES);
            clawBaseBuffer.light(light).transform(ms).renderInto(matrices.getViewProjection(), builder);

            org.joml.Matrix4f clawTipWorldMatrix = null;
            for (int flip : net.createmod.catnip.data.Iterate.positiveAndNegative) {
                ms.pushPose();
                transformClawHalf(msr, hasItem, isBlockItem, flip);
                if (flip > 0) {
                    clawTipWorldMatrix = new org.joml.Matrix4f(matrices.getWorld());
                    clawTipWorldMatrix.mul(ms.last().pose());
                }
                PartialModel gripModel = (flip > 0) ? SentryPartialModels.ARM_CLAW_GRIP_LOWER : SentryPartialModels.ARM_CLAW_GRIP_UPPER;
                SuperByteBuffer gripBuffer = CachedBuffers.partial(gripModel, blockState);
                applyDye(gripBuffer, virtualBE.color, SentrySpriteShifts.ARM_TEXTURES);
                gripBuffer.light(light).transform(ms).renderInto(matrices.getViewProjection(), builder);
                ms.popPose();
            }
            ms.popPose();

            ms.pushPose();
            msr.uncenter();
            msr.center();
            float speed = virtualBE.getSpeed();
            float time = net.createmod.catnip.animation.AnimationTickHolder.getRenderTime();
            float cogAngle = (time * speed * 3f / 10f) % 360;
            ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(cogAngle));
            msr.uncenter();
            SuperByteBuffer cogBuffer = CachedBuffers.partial(SentryPartialModels.SENTRU_COG, blockState);
            applyDye(cogBuffer, virtualBE.color, SentrySpriteShifts.COG_TEXTURES);
            cogBuffer.light(light).transform(ms).renderInto(matrices.getViewProjection(), builder);
            ms.popPose();

            if (clawTipWorldMatrix != null) {
                renderHeldItem(buffer, renderWorld, virtualBE, light, clawTipWorldMatrix, context);
            }
        } catch (Exception e) {
            SentryMechanicalArm.LOGGER.debug("[render] renderInContraption failed: {}", e.getMessage());
        } finally {
            ms.popPose();
        }
    }

    private static void renderHeldItem(MultiBufferSource buffer, VirtualRenderWorld renderWorld,
                                       VirtualSentryArmBlockEntity virtualBE, int light,
                                       Matrix4f clawTipWorldMatrix, 
                                       com.simibubi.create.content.contraptions.behaviour.MovementContext context) {

        ItemStack heldItem = virtualBE.getHeldItem();
        if (heldItem.isEmpty()) return;

        boolean isCeiling = false;
        if (context != null && context.state != null && context.state.hasProperty(SentryArmBlock.CEILING)) {
            isCeiling = context.state.getValue(SentryArmBlock.CEILING);
        }

        boolean isBlockItem = Minecraft.getInstance().getItemRenderer().getModel(heldItem, renderWorld, null, 0).isGui3d();

        PoseStack gunStack = new PoseStack();
        gunStack.last().pose().set(clawTipWorldMatrix);
        gunStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        gunStack.translate(0.0F, isBlockItem ? -0.5625F : -0.625F, 0.0F);

        boolean isGun = heldItem.getItem() instanceof IGun;

        if (isGun) {
            ResourceLocation gunId = ((IGun) heldItem.getItem()).getGunId(heldItem);
            gunStack.mulPose(Axis.XP.rotationDegrees(-90));

            if (isCeiling) {
                gunStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
            float yOffset = isCeiling ? 0.1f : 0.42f;

            gunStack.translate(0, yOffset, 0.3f);

            if (gunId.getPath().contains("minigun")) {
                gunStack.mulPose(Axis.XP.rotationDegrees(-90));
                gunStack.translate(0, -0.7, 0.1);
            }
        }

        Matrix4f finalMatrix = gunStack.last().pose();
        Vector4f worldPos = new Vector4f(0, 0, 0, 1);
        finalMatrix.transform(worldPos);

        Quaternionf worldRot = new Quaternionf();
        finalMatrix.getUnnormalizedRotation(worldRot);

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        double renderX = worldPos.x() - cameraPos.x;
        double renderY = worldPos.y() - cameraPos.y;
        double renderZ = worldPos.z() - cameraPos.z;
 
        Vector4f viewDelta = new Vector4f((float)renderX, (float)renderY, (float)renderZ, 1.0f);
        Matrix4f cameraRotMat = new Matrix4f();
        cameraRotMat.rotate(Axis.XP.rotationDegrees(camera.getXRot()));
        cameraRotMat.rotate(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        cameraRotMat.transform(viewDelta);

        PoseStack viewStack = new PoseStack();
        viewStack.translate(viewDelta.x(), viewDelta.y(), viewDelta.z());

 
        Quaternionf cameraInverseRot = new Quaternionf();
        cameraRotMat.getUnnormalizedRotation(cameraInverseRot);
        cameraInverseRot.mul(worldRot);
        viewStack.mulPose(cameraInverseRot);

 
        if (isGun) {
            viewStack.scale(1.5f, 1.5f, 1.5f);

        } else {
            float s = isBlockItem ? 0.5F : 0.625F;
            viewStack.scale(s, s, s);
        }
 
        MultiBufferSource.BufferSource cleanBuffer = Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            Minecraft.getInstance().getItemRenderer().renderStatic(
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
            }


            cleanBuffer.endBatch();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        }
    }


    private static void renderMuzzleFlashStatic(SentryArmBlockEntity sentry, ItemStack stack, PoseStack ms, MultiBufferSource buffer) {
        boolean isSilenced = ArmSoundHelper.isSilenced(stack);
        if (isSilenced) return;

        long timeSinceShoot = System.currentTimeMillis() - sentry.getLastShootTime();
        if (timeSinceShoot < 0 || timeSinceShoot > 50) return;

        Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(stack);
        if (displayOpt.isEmpty()) return;
        GunDisplayInstance display = displayOpt.get();

        BedrockGunModel gunModel = display.getGunModel();
        if (gunModel == null) return;

        MuzzleFlash muzzleFlash = display.getMuzzleFlash();
        if (muzzleFlash == null) return;

        ms.pushPose();

        ms.translate(0, 1.5, 0);
        ms.scale(-1.0f, -1.0f, 1.0f);

        Vector3f transformScale = new Vector3f(1.0f, 1.0f, 1.0f);
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

        float flashScale = (float) (0.5 * muzzleFlash.getScale());
        float randomRotate = (float) (Math.random() * 360.0);
        ms.mulPose(Axis.ZP.rotationDegrees(randomRotate));
        ms.scale(flashScale, flashScale, flashScale);

        VertexConsumer consumerBg = buffer.getBuffer(RenderType.entityTranslucent(muzzleFlash.getTexture()));
        drawCrossQuadStatic(ms, consumerBg);

        VertexConsumer consumerFg = buffer.getBuffer(RenderType.energySwirl(muzzleFlash.getTexture(), 0, 0));
        ms.pushPose();
        ms.scale(0.5f, 0.5f, 0.5f);
        drawCrossQuadStatic(ms, consumerFg);
        ms.popPose();

        ms.popPose();
    }

    private static void applyPositioningNodeTransformStatic(List<BedrockPart> nodePath, PoseStack poseStack, Vector3f scale) {
        if (nodePath != null) {
            if (scale == null) {
                scale = new Vector3f(1.0F, 1.0F, 1.0F);
            }
            poseStack.translate(0.0F, 1.5F, 0.0F);
            for (int i = nodePath.size() - 1; i >= 0; --i) {
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
        float size = 1.0f;
        float min = -size;
        float max = size;
        float u0 = 0, u1 = 1;
        float v0 = 0, v1 = 1;

        vertexStatic(consumer, pose, normal, min, max, 0, u0, v1);
        vertexStatic(consumer, pose, normal, max, max, 0, u1, v1);
        vertexStatic(consumer, pose, normal, max, min, 0, u1, v0);
        vertexStatic(consumer, pose, normal, min, min, 0, u0, v0);

        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(90));
        Matrix4f pose2 = ms.last().pose();
        Matrix3f normal2 = ms.last().normal();

        vertexStatic(consumer, pose2, normal2, min, max, 0, u0, v1);
        vertexStatic(consumer, pose2, normal2, max, max, 0, u1, v1);
        vertexStatic(consumer, pose2, normal2, max, min, 0, u1, v0);
        vertexStatic(consumer, pose2, normal2, min, min, 0, u0, v0);

        ms.popPose();
    }

    private static void vertexStatic(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0, 1, 0);
    }



}
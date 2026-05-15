package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.network.NetworkHandler;
import euphy.upo.sentrymechanicalarm.network.SentryLinkPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Vector3f;

@EventBusSubscriber(modid = euphy.upo.sentrymechanicalarm.SentryMechanicalArm.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SentryLinkHandler {

    private static BlockPos firstSelectedPos = null;
    private static boolean isFirstSentry = false; 

    @SubscribeEvent
    public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getLevel().isClientSide) return;

        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();

 
        if (!heldItem.getItem().getDescriptionId().contains("wrench")) {
 
            if (firstSelectedPos != null) firstSelectedPos = null;
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        boolean isSentry = level.getBlockEntity(pos) instanceof SentryArmBlockEntity;
        boolean isFireControl = level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity;

        if (!isSentry && !isFireControl) return;
 
        if (firstSelectedPos == null) {
            firstSelectedPos = pos;
            isFirstSentry = isSentry;
            if (isSentry) {
                player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.select_sentry"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.select_fire_control"), true);
            }
            event.setCanceled(true); 
        }
 
        else {
 
            if (pos.equals(firstSelectedPos)) {
                firstSelectedPos = null;
                player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.cancelled"), true);
                event.setCanceled(true);
                return;
            }
 
            boolean validPair = (isFirstSentry && isFireControl) || (!isFirstSentry && isSentry);

            if (validPair) {
 
                PacketDistributor.sendToServer(new SentryLinkPacket(firstSelectedPos, pos));
 
                firstSelectedPos = null;
                event.setCanceled(true);
            } else {
 
                player.displayClientMessage(Component.translatable("message.sentrymechanicalarm.invalid_pair"), true);
                firstSelectedPos = null; 
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

 
        ItemStack heldItem = mc.player.getMainHandItem();
        boolean hasWrench = heldItem.getItem().getDescriptionId().contains("wrench");
        if (!hasWrench) {
 
            if (firstSelectedPos == null) return;
        }

 
        BlockPos lookPos = null;
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            lookPos = blockHit.getBlockPos();
        } else {
            return; 
        }
 
        if (firstSelectedPos != null) {
            Vec3 start = Vec3.atCenterOf(firstSelectedPos);
            Vec3 end = Vec3.atCenterOf(lookPos);

            double distSqr = start.distanceToSqr(end);
            boolean inRange = distSqr <= 36.0;

 
            Vector3f color = inRange ? new Vector3f(0.0f, 1.0f, 0.0f) : new Vector3f(1.0f, 0.0f, 0.0f);
            renderParticleLine(mc.level, start, end, color);

 
            renderOverlayText(event, mc, lookPos, inRange);
        }

        else {
            BlockEntity be = mc.level.getBlockEntity(lookPos);

            Vector3f establishedColor = new Vector3f(0.0f, 1.0f, 1.0f); 

 
            if (be instanceof SentryArmBlockEntity sentry) {
                BlockPos targetPos = sentry.getConnectedFireControl();
 
                if (targetPos != null && isValidFireControl(mc.level, targetPos)) {
                    renderParticleLine(mc.level, Vec3.atCenterOf(lookPos), Vec3.atCenterOf(targetPos), establishedColor);
                }
            }
 
            else if (be instanceof BlazeFireControlBlockEntity) {
                for (int x = -6; x <= 6; x++) {
                    for (int y = -6; y <= 6; y++) {
                        for (int z = -6; z <= 6; z++) {
                            BlockPos checkPos = lookPos.offset(x, y, z);
 
                            if (mc.level.getBlockEntity(checkPos) instanceof SentryArmBlockEntity linkedSentry) {
                                BlockPos linkedTarget = linkedSentry.getConnectedFireControl();
 
                                if (linkedTarget != null && linkedTarget.equals(lookPos)) {
                                    renderParticleLine(mc.level, Vec3.atCenterOf(lookPos), Vec3.atCenterOf(checkPos), establishedColor);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isValidFireControl(Level level, BlockPos pos) {
 
        if (!level.isLoaded(pos)) return false;
 
        return level.getBlockEntity(pos) instanceof BlazeFireControlBlockEntity;
    }

 
    private static void renderParticleLine(Level level, Vec3 start, Vec3 end, Vector3f color) {
        double dist = Math.sqrt(start.distanceToSqr(end));
        if (dist < 0.1) return;

        DustParticleOptions particle = new DustParticleOptions(color, 1.0f);
        int steps = (int) (dist * 5); 

        for (int i = 0; i <= steps; i++) {
            double lerp = i / (double) steps;
            double x = start.x + (end.x - start.x) * lerp;
            double y = start.y + (end.y - start.y) * lerp;
            double z = start.z + (end.z - start.z) * lerp;

 
            if (level.random.nextInt(4) == 0) {
                level.addParticle(particle, x, y, z, 0, 0, 0);
            }
        }
    }

    private static void renderOverlayText(RenderLevelStageEvent event, Minecraft mc, BlockPos lookPos, boolean inRange) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Vec3 camPos = event.getCamera().getPosition();
        boolean isTargetSentry = mc.level.getBlockEntity(lookPos) instanceof SentryArmBlockEntity;
        boolean isTargetControl = mc.level.getBlockEntity(lookPos) instanceof BlazeFireControlBlockEntity;

        String text = "";
        int textColor = 0xFFFFFFFF;

        if (inRange) {
            if (isFirstSentry && isTargetControl) {
                text = Component.translatable("overlay.sentrymechanicalarm.right_click_connect").getString();
            } else if (!isFirstSentry && isTargetSentry) {
                text = Component.translatable("overlay.sentrymechanicalarm.right_click_connect").getString();
            } else if (isTargetSentry || isTargetControl) {
                text = Component.translatable("overlay.sentrymechanicalarm.invalid_target_type").getString();
            } else {
                text = isFirstSentry
                        ? Component.translatable("overlay.sentrymechanicalarm.select_fire_control").getString()
                        : Component.translatable("overlay.sentrymechanicalarm.select_sentry_turret").getString();
            }
        } else {
            text = Component.translatable("overlay.sentrymechanicalarm.too_far").getString();
            textColor = 0xFFFF0000;
        }

        if (!text.isEmpty()) {
            poseStack.translate(lookPos.getX() + 0.5 - camPos.x, lookPos.getY() + 1.5 - camPos.y, lookPos.getZ() + 0.5 - camPos.z);
            poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);

            float x = -mc.font.width(text) / 2.0f;
            mc.font.drawInBatch(text, x, 0, textColor, true, poseStack.last().pose(),
                    mc.renderBuffers().bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                    0, 0xF000F0);
        }
        poseStack.popPose();
    }
}
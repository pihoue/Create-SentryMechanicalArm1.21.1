package euphy.upo.sentrymechanicalarm.client;

import org.lwjgl.glfw.GLFW;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.content.FireControlMovementBehaviour;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.SentryScopeItem;
import euphy.upo.sentrymechanicalarm.network.SentryFocusPacket;
import euphy.upo.sentrymechanicalarm.network.SentryRecordTargetPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID, value = Dist.CLIENT)
public class SentryClientInputHandler {

    private static long lastMarkTime = 0;
    private static long lastFocusTime = 0;

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (Minecraft.getInstance().screen != null) return;

        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean isHoldingScope = player.getMainHandItem().getItem() instanceof SentryScopeItem;
        boolean isUsingScope = player.isUsingItem() && player.getUseItem().getItem() instanceof SentryScopeItem;
        boolean isHoldingSpyglass = player.getMainHandItem().getItem() == Items.SPYGLASS;
        boolean isUsingSpyglass = player.isUsingItem() && player.getUseItem().getItem() == Items.SPYGLASS;
        boolean isOffhandClipboard = player.getOffhandItem().getItem() instanceof FireControlClipboardItem;

        // Middle-click: record target (spyglass + clipboard)
        if (event.getButton() == 2) {
            if (isHoldingSpyglass && isUsingSpyglass && isOffhandClipboard) {
                if (System.currentTimeMillis() - lastMarkTime < 500) {
                    event.setCanceled(true);
                    return;
                }

                Entity target = getLookedAtEntity(player, 256.0);

                if (target != null) {
                    PacketDistributor.sendToServer(new SentryRecordTargetPacket(target.getId()));
                    player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.5f);
                    lastMarkTime = System.currentTimeMillis();
                }

                event.setCanceled(true);
            }
            return;
        }

        // Below: only attack key (left-click by default) — focus mode
        if (!mc.options.keyAttack.matchesMouse(event.getButton())) {
            return;
        }

        if (isHoldingScope && isUsingScope) {
            BlockPos fcPos = SentryScopeItem.getLinkedFireControlPos(player.getMainHandItem());
            if (fcPos == null) return;

            if (System.currentTimeMillis() - lastFocusTime < 500) {
                event.setCanceled(true);
                return;
            }

            Entity target = getLookedAtEntity(player, 256.0);
            if (target != null) {
                int targetId = target.getId();
                int foundAceId = -1;
                BlockPos foundLocalPos = fcPos;

                net.minecraft.world.phys.AABB searchBounds = new net.minecraft.world.phys.AABB(fcPos).inflate(256);
                for (AbstractContraptionEntity ace : player.level().getEntitiesOfClass(AbstractContraptionEntity.class, searchBounds)) {
                    Contraption contraption = ace.getContraption();
                    if (contraption == null) continue;
                    double distToTarget = ace.position().distanceTo(target.position());
                    if (distToTarget > 128.0) continue;
                    Vec3 localCenter = ace.toLocalVector(Vec3.atCenterOf(fcPos), 0);
                    BlockPos queryLocalPos = BlockPos.containing(localCenter);
                    for (org.apache.commons.lang3.tuple.MutablePair<?, MovementContext> actor : contraption.getActors()) {
                        MovementContext ctx = actor.getValue();
                        if (ctx.temporaryData instanceof FireControlMovementBehaviour.FireControlData fcData
                                && ctx.localPos.equals(queryLocalPos)) {
                            if (fcData.focusedEntityId == targetId) {
                                targetId = -1;
                            }
                            fcData.focusedEntityId = targetId;
                            ctx.data.putInt("FocusedEntityId", targetId);
                            foundAceId = ace.getId();
                            foundLocalPos = queryLocalPos;
                        }
                        if (ctx.temporaryData instanceof euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity) {
                            ctx.data.putInt("_TargetId", -1);
                            ctx.data.putInt("_ScanCooldown", 0);
                        }
                    }
                }

                if (foundAceId == -1) {
                    net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(fcPos);
                    if (be instanceof BlazeFireControlBlockEntity fc) {
                        if (fc.getFocusedEntityId() == targetId) {
                            targetId = -1;
                        }
                    }
                    PacketDistributor.sendToServer(new SentryFocusPacket(-1, fcPos, targetId));
                    event.setCanceled(true);
                    player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.8f);
                    lastFocusTime = System.currentTimeMillis();
                    return;
                }
                PacketDistributor.sendToServer(new SentryFocusPacket(foundAceId, foundLocalPos, targetId));

                player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.8f);
                lastFocusTime = System.currentTimeMillis();
            }

            event.setCanceled(true);
        }
    }

    static boolean isPlayerLookingAtNoAmmoSentry(LocalPlayer player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getViewVector(1.0F);
        Vec3 traceEnd = eyePos.add(viewVec.scale(range));

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eyePos, traceEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (blockHit.getType() == HitResult.Type.MISS) return false;

        BlockPos hitPos = blockHit.getBlockPos();
        if (player.level().getBlockEntity(hitPos) instanceof SentryArmBlockEntity sentry) {
            return sentry.getSentryStatus() == SentryArmBlockEntity.SentryStatus.NO_AMMO;
        }
        return false;
    }

    static Entity getLookedAtEntity(LocalPlayer player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getViewVector(1.0F);
        Vec3 traceEnd = eyePos.add(viewVec.scale(range));

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eyePos, traceEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        double actualLimit = range;

        if (blockHit.getType() != HitResult.Type.MISS) {
            actualLimit = blockHit.getLocation().distanceTo(eyePos);
            traceEnd = blockHit.getLocation();
        }

        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(actualLimit)).inflate(1.0D, 1.0D, 1.0D);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                eyePos,
                traceEnd,
                searchBox,
                (e) -> !e.isSpectator() && e.isPickable() && e instanceof net.minecraft.world.entity.LivingEntity,
                actualLimit * actualLimit
        );

        if (entityHit != null) {
            return entityHit.getEntity();
        }

        return null;
    }
}
package euphy.upo.sentrymechanicalarm.client;

import org.lwjgl.glfw.GLFW;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.content.FireControlMovementBehaviour;
import euphy.upo.sentrymechanicalarm.content.SentryScopeItem;
import euphy.upo.sentrymechanicalarm.network.SentryFocusPacket;
import euphy.upo.sentrymechanicalarm.network.SentryRecordTargetPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
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

    private static long lastRecordTime = 0;

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (Minecraft.getInstance().screen != null) return;

        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!mc.options.keyAttack.matchesMouse(event.getButton())) {
            return;
        }
        boolean isHoldingSpyglass = player.getMainHandItem().getItem() == Items.SPYGLASS;
        boolean isUsingSpyglass = player.isUsingItem() && player.getUseItem().getItem() == Items.SPYGLASS;
        boolean isOffhandClipboard = player.getOffhandItem().getItem() instanceof FireControlClipboardItem;

        if (isHoldingSpyglass && isUsingSpyglass && isOffhandClipboard) {
            if (System.currentTimeMillis() - lastRecordTime < 500) {
                event.setCanceled(true);
                return;
            }

            Entity target = getLookedAtEntity(player, 256.0);

            if (target != null) {
                PacketDistributor.sendToServer(new SentryRecordTargetPacket(target.getId()));
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.5f);
                lastRecordTime = System.currentTimeMillis();
            }

            event.setCanceled(true);
            return;
        }

        boolean isHoldingScope = player.getMainHandItem().getItem() instanceof SentryScopeItem;
        boolean isUsingScope = player.isUsingItem() && player.getUseItem().getItem() instanceof SentryScopeItem;
        if (isHoldingScope && isUsingScope) {
            BlockPos fcPos = SentryScopeItem.getLinkedFireControlPos(player.getMainHandItem());
            if (fcPos == null) return;

            if (System.currentTimeMillis() - lastRecordTime < 500) {
                event.setCanceled(true);
                return;
            }

            Entity target = getLookedAtEntity(player, 256.0);
            if (target != null) {
                PacketDistributor.sendToServer(new SentryFocusPacket(fcPos, target.getId()));

                int targetId = target.getId();
                net.minecraft.world.phys.AABB worldBounds = new net.minecraft.world.phys.AABB(
                    -30000000, -30000000, -30000000, 30000000, 30000000, 30000000);
                for (AbstractContraptionEntity ace : player.level().getEntitiesOfClass(AbstractContraptionEntity.class, worldBounds)) {
                    Contraption contraption = ace.getContraption();
                    if (contraption == null) continue;
                    if (!contraption.getBlocks().containsKey(fcPos)) continue;
                    for (org.apache.commons.lang3.tuple.MutablePair<?, MovementContext> actor : contraption.getActors()) {
                        if (actor.getValue().temporaryData instanceof FireControlMovementBehaviour.FireControlData fcData
                                && actor.getValue().localPos.equals(fcPos)) {
                            fcData.focusedEntityId = targetId;
                            actor.getValue().data.putInt("FocusedEntityId", targetId);
                        }
                    }
                }

                player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.8f);
                lastRecordTime = System.currentTimeMillis();
            }

            event.setCanceled(true);
        }
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
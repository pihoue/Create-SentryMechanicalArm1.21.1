package euphy.upo.sentrymechanicalarm.client;

import org.lwjgl.glfw.GLFW;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.network.SentryRecordTargetPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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

@EventBusSubscriber(modid = SentryMechanicalArm.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
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

        if (!isHoldingSpyglass || !isUsingSpyglass || !isOffhandClipboard) {
            return;
        }

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
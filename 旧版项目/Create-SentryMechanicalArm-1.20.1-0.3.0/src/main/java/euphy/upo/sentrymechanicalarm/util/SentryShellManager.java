package euphy.upo.sentrymechanicalarm.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class SentryShellManager {

    private static final List<Shell> shells = new ArrayList<>();

    public static class Shell {
        ResourceLocation ammoId;
        Vec3 pos;
        Vec3 velocity;
        Vector3f rotation;
        Vector3f angularVelocity; 
        Vec3 acceleration;
        long spawnTime;
        double lifeTime;

        public Shell(ResourceLocation ammoId, Vec3 pos, Vec3 vel, Vector3f rotVel, Vec3 acc, double lifeTime) {
            this.ammoId = ammoId;
            this.pos = pos;
            this.velocity = vel;
            this.rotation = new Vector3f(0, 0, 0); 
            this.angularVelocity = rotVel;
            this.acceleration = acc;
            this.spawnTime = System.currentTimeMillis();
            this.lifeTime = lifeTime;
        }
    }

    public static void addShell(ResourceLocation ammoId, Vec3 pos, Vector3f velocity, Vector3f angularVel, Vector3f acceleration, double lifeTime) {
        shells.add(new Shell(
                ammoId,
                pos,
                new Vec3(velocity.x, velocity.y, velocity.z),
                angularVel,
                new Vec3(acceleration.x, acceleration.y, acceleration.z),
                lifeTime
        ));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            Iterator<Shell> it = shells.iterator();
            while (it.hasNext()) {
                Shell shell = it.next();
 
                double age = (System.currentTimeMillis() - shell.spawnTime) / 1000.0;
                if (age > shell.lifeTime) {
                    it.remove();
                    continue;
                }
                shell.pos = shell.pos.add(shell.velocity);
                shell.velocity = shell.velocity.add(shell.acceleration.scale(0.05));
                shell.rotation.add(shell.angularVelocity);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (shells.isEmpty()) return;

        PoseStack ms = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 camPos = event.getCamera().getPosition();
        float pt = event.getPartialTick();

        ms.pushPose();
        ms.translate(-camPos.x, -camPos.y, -camPos.z);

        for (Shell shell : shells) {
            renderSingleShell(shell, ms, buffer, pt);
        }

        buffer.endBatch();
        ms.popPose();
    }

    private static void renderSingleShell(Shell shell, PoseStack ms, MultiBufferSource buffer, float pt) {
        Optional<ClientAmmoIndex> indexOpt = TimelessAPI.getClientAmmoIndex(shell.ammoId);
        if (indexOpt.isEmpty()) return;

        ClientAmmoIndex index = indexOpt.get();
        BedrockAmmoModel model = index.getShellModel();
        ResourceLocation texture = index.getShellTextureLocation();

        if (model == null || texture == null) return;

        Vec3 renderPos = shell.pos.add(shell.velocity.scale(pt));

        ms.pushPose();
        ms.translate(renderPos.x, renderPos.y, renderPos.z);

        ms.mulPose(Axis.XP.rotationDegrees(shell.rotation.x()));
        ms.mulPose(Axis.YP.rotationDegrees(shell.rotation.y()));
        ms.mulPose(Axis.ZP.rotationDegrees(shell.rotation.z()));

        ms.translate(0, -1.5, 0); 

        model.render(ms, ItemDisplayContext.NONE, RenderType.entityCutout(texture), 15728880, OverlayTexture.NO_OVERLAY);

        ms.popPose();
    }
}
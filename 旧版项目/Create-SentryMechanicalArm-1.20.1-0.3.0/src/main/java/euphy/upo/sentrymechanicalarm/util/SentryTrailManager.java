package euphy.upo.sentrymechanicalarm.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class SentryTrailManager {

    private static final List<Tracer> tracers = new ArrayList<>();

    private static class Tracer {
        Vec3 startPos;
        Vec3 direction;
        double speed;
        double length;
        double maxDistance;
        double traveled;


        public Tracer(Vec3 start, Vec3 dir, double speed, double length, double maxDist) {
            this.startPos = start;
            this.direction = dir;
            this.speed = speed;
            this.length = length;
            this.maxDistance = maxDist;
            this.traveled = 0;
        }
    }

    public static void addTracer(Vec3 start, Vec3 direction, double speed, double length, double distance) {
        tracers.add(new Tracer(start, direction, speed, length, distance));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            Iterator<Tracer> it = tracers.iterator();
            while (it.hasNext()) {
                Tracer t = it.next();
                t.traveled += t.speed;

                if (t.traveled - t.length > t.maxDistance) {
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (tracers.isEmpty()) return;

        PoseStack ms = event.getPoseStack();
        ms.pushPose();

        Vec3 camPos = event.getCamera().getPosition();
        Vector3f cameraPosition = new Vector3f((float)camPos.x, (float)camPos.y, (float)camPos.z);
        ms.translate(-camPos.x, -camPos.y, -camPos.z);


        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer builder = bufferSource.getBuffer(RenderType.lightning());

        float partialTicks = event.getPartialTick();
        float width = 0.020f;

        for (Tracer t : tracers) {
            double currentDist = t.traveled + t.speed * partialTicks;

            double headDist = Math.min(currentDist, t.maxDistance);
            double tailDist = Math.max(0, currentDist - t.length);

            if (headDist <= 0 || tailDist >= t.maxDistance || tailDist >= headDist) continue;

            Vec3 headPos = t.startPos.add(t.direction.scale(headDist));
            Vec3 tailPos = t.startPos.add(t.direction.scale(tailDist));

            renderTracerAsBillboard(builder, ms.last().pose(), tailPos, headPos, width, cameraPosition);        }


        bufferSource.endBatch(RenderType.lightning());

        ms.popPose();
    }

    private static void renderTracerAsBillboard(VertexConsumer builder, Matrix4f matrix, Vec3 start, Vec3 end, float width, Vector3f camPos) {
        float r = 1.0f; float g = 1.0f; float b = 1.0f; float a = 1.0f;

        Vector3f startV = new Vector3f((float)start.x, (float)start.y, (float)start.z);
        Vector3f endV = new Vector3f((float)end.x, (float)end.y, (float)end.z);

        Vector3f beamDir = new Vector3f(endV).sub(startV).normalize();

        Vector3f toCam = new Vector3f(camPos).sub(startV).normalize();

        Vector3f offset = new Vector3f();
        beamDir.cross(toCam, offset).normalize().mul(width);


        Vector3f v1 = new Vector3f(startV).add(offset);
        Vector3f v2 = new Vector3f(startV).sub(offset);
        Vector3f v3 = new Vector3f(endV).sub(offset);
        Vector3f v4 = new Vector3f(endV).add(offset);


        builder.vertex(matrix, v1.x, v1.y, v1.z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, v2.x, v2.y, v2.z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, v3.x, v3.y, v3.z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, v4.x, v4.y, v4.z).color(r, g, b, a).endVertex();

        builder.vertex(matrix, v4.x, v4.y, v4.z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, v3.x, v3.y, v3.z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, v2.x, v2.y, v2.z).color(r, g, b, a).endVertex();
        builder.vertex(matrix, v1.x, v1.y, v1.z).color(r, g, b, a).endVertex();
    }
}

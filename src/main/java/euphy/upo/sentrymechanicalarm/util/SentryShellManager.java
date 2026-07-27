package euphy.upo.sentrymechanicalarm.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "sentrymechanicalarm",
   value = {Dist.CLIENT}
)
public class SentryShellManager {
   private static final List<SentryShellManager.Shell> shells = new ArrayList<>();

   public static void addShell(ResourceLocation ammoId, Vec3 pos, Vector3f velocity, Vector3f angularVel, Vector3f acceleration, double lifeTime) {
      shells.add(
         new SentryShellManager.Shell(
            ammoId,
            pos,
            new Vec3((double)velocity.x, (double)velocity.y, (double)velocity.z),
            angularVel,
            new Vec3((double)acceleration.x, (double)acceleration.y, (double)acceleration.z),
            lifeTime
         )
      );
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      if (!Minecraft.getInstance().isPaused()) {
         Iterator<SentryShellManager.Shell> it = shells.iterator();

         while (it.hasNext()) {
            SentryShellManager.Shell shell = it.next();
            double age = (double)(System.currentTimeMillis() - shell.spawnTime) / 1000.0;
            if (age > shell.lifeTime) {
               it.remove();
            } else {
               shell.pos = shell.pos.add(shell.velocity);
               shell.velocity = shell.velocity.add(shell.acceleration.scale(0.05));
               shell.rotation.add(shell.angularVelocity);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderWorld(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_TRANSLUCENT_BLOCKS) {
         if (!shells.isEmpty()) {
            PoseStack ms = event.getPoseStack();
            BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
            Vec3 camPos = event.getCamera().getPosition();
            float pt = event.getPartialTick().getGameTimeDeltaTicks();
            ms.pushPose();
            ms.translate(-camPos.x, -camPos.y, -camPos.z);

            for (SentryShellManager.Shell shell : shells) {
               renderSingleShell(shell, ms, buffer, pt);
            }

            buffer.endBatch();
            ms.popPose();
         }
      }
   }

   private static void renderSingleShell(SentryShellManager.Shell shell, PoseStack ms, MultiBufferSource buffer, float pt) {
      Optional<ClientAmmoIndex> indexOpt = TimelessAPI.getClientAmmoIndex(shell.ammoId);
      if (!indexOpt.isEmpty()) {
         ClientAmmoIndex index = indexOpt.get();
         BedrockAmmoModel model = index.getShellModel();
         ResourceLocation texture = index.getShellTextureLocation();
         if (model != null && texture != null) {
            Vec3 renderPos = shell.pos.add(shell.velocity.scale((double)pt));
            ms.pushPose();
            ms.translate(renderPos.x, renderPos.y, renderPos.z);
            ms.mulPose(Axis.XP.rotationDegrees(shell.rotation.x()));
            ms.mulPose(Axis.YP.rotationDegrees(shell.rotation.y()));
            ms.mulPose(Axis.ZP.rotationDegrees(shell.rotation.z()));
            ms.translate(0.0, -1.5, 0.0);
            model.render(ms, ItemDisplayContext.NONE, RenderType.entityCutout(texture), 15728880, OverlayTexture.NO_OVERLAY);
            ms.popPose();
         }
      }
   }

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
         this.rotation = new Vector3f(0.0F, 0.0F, 0.0F);
         this.angularVelocity = rotVel;
         this.acceleration = acc;
         this.spawnTime = System.currentTimeMillis();
         this.lifeTime = lifeTime;
      }
   }
}

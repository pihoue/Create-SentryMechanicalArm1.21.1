package euphy.upo.sentrymechanicalarm.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.content.FireControlMovementBehaviour;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.SentryScopeItem;
import euphy.upo.sentrymechanicalarm.content.VirtualSentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.network.SentryFocusPacket;
import euphy.upo.sentrymechanicalarm.network.SentryMarkPosPacket;
import euphy.upo.sentrymechanicalarm.network.SentryMarkTargetPacket;
import euphy.upo.sentrymechanicalarm.network.SentryRecordTargetPacket;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.tuple.MutablePair;

@EventBusSubscriber(
   modid = "sentrymechanicalarm",
   value = {Dist.CLIENT}
)
public class SentryClientInputHandler {
   private static long lastMarkTime = 0L;
   private static long lastFocusTime = 0L;

   @SubscribeEvent
   public static void onMouseInput(Pre event) {
      if (Minecraft.getInstance().screen == null) {
         if (event.getAction() == 1) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null) {
               boolean isHoldingScope = player.getMainHandItem().getItem() instanceof SentryScopeItem;
               boolean isUsingScope = player.isUsingItem() && player.getUseItem().getItem() instanceof SentryScopeItem;
               boolean isHoldingSpyglass = player.getMainHandItem().getItem() == Items.SPYGLASS;
               boolean isUsingSpyglass = player.isUsingItem() && player.getUseItem().getItem() == Items.SPYGLASS;
               boolean isOffhandClipboard = player.getOffhandItem().getItem() instanceof FireControlClipboardItem;
               if (event.getButton() == 2) {
                  if (isHoldingSpyglass && isUsingSpyglass && isOffhandClipboard) {
                     if (System.currentTimeMillis() - lastMarkTime < 500L) {
                        event.setCanceled(true);
                     } else {
                        Entity target = getLookedAtEntity(player, 256.0);
                        if (target != null) {
                           PacketDistributor.sendToServer(new SentryRecordTargetPacket(target.getId()), new CustomPacketPayload[0]);
                           player.playSound((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.5F);
                           lastMarkTime = System.currentTimeMillis();
                        }

                        event.setCanceled(true);
                     }
                  } else if (isHoldingScope && isUsingScope) {
                     if (System.currentTimeMillis() - lastMarkTime < 500L) {
                        event.setCanceled(true);
                     } else {
                        BlockPos fcPos = SentryScopeItem.getLinkedFireControlPos(player.getMainHandItem());
                        if (fcPos != null) {
                           Vec3 eyePos = player.getEyePosition();
                           Vec3 viewVec = player.getViewVector(1.0F);
                           Vec3 traceEnd = eyePos.add(viewVec.scale(256.0));
                           BlockHitResult blockHit = player.level().clip(new ClipContext(eyePos, traceEnd, Block.COLLIDER, Fluid.NONE, player));
                           Vec3 hitPos = blockHit.getLocation();
                           boolean inSubLevel = AeronauticsHelper.isInSableSubLevel(player.level(), player.blockPosition());
                           int contraptionId = -1;
                           BlockPos localPos = BlockPos.ZERO;
                           Vec3 contraptionCheckPos = hitPos;
                           Vec3 packetPos = hitPos;
                           Vec3 sableLocalPos = null;
                           if (inSubLevel) {
                              sableLocalPos = hitPos;
                              packetPos = AeronauticsHelper.sableSubLevelToWorld(player.level(), hitPos);
                           }

                           for (AbstractContraptionEntity ace : player.level()
                              .getEntitiesOfClass(AbstractContraptionEntity.class, new AABB(fcPos).inflate(256.0))) {
                              Contraption contraption = ace.getContraption();
                              if (contraption != null && ace.getBoundingBox().inflate(2.0).contains(contraptionCheckPos)) {
                                 Vec3 localVec = ace.toLocalVector(contraptionCheckPos, 0.0F);
                                 contraptionId = ace.getId();
                                 localPos = BlockPos.containing(localVec);
                                 break;
                              }
                           }

                           PacketDistributor.sendToServer(
                              new SentryMarkPosPacket(fcPos, packetPos, contraptionId, localPos, inSubLevel, Optional.ofNullable(sableLocalPos)),
                              new CustomPacketPayload[0]
                           );
                           player.playSound((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.5F);
                           lastMarkTime = System.currentTimeMillis();
                        }

                        event.setCanceled(true);
                     }
                  }
               } else if (mc.options.keyAttack.matchesMouse(event.getButton())) {
                  if (isHoldingScope && isUsingScope) {
                     BlockPos fcPos = SentryScopeItem.getLinkedFireControlPos(player.getMainHandItem());
                     if (fcPos == null) {
                        return;
                     }

                     if (System.currentTimeMillis() - lastFocusTime < 500L) {
                        event.setCanceled(true);
                        return;
                     }

                     Entity target = getLookedAtEntity(player, 256.0);
                     if (target != null) {
                        int targetId = target.getId();
                        int foundAceId = -1;
                        BlockPos foundLocalPos = fcPos;
                        PacketDistributor.sendToServer(new SentryMarkTargetPacket(fcPos, targetId, true), new CustomPacketPayload[0]);
                        AABB searchBounds = new AABB(fcPos).inflate(256.0);

                        for (AbstractContraptionEntity acex : player.level().getEntitiesOfClass(AbstractContraptionEntity.class, searchBounds)) {
                           Contraption contraption = acex.getContraption();
                           if (contraption != null) {
                              double distToTarget = acex.position().distanceTo(target.position());
                              if (!(distToTarget > 128.0)) {
                                 Vec3 localCenter = acex.toLocalVector(Vec3.atCenterOf(fcPos), 0.0F);
                                 BlockPos queryLocalPos = BlockPos.containing(localCenter);

                                 for (MutablePair<?, MovementContext> actor : contraption.getActors()) {
                                    MovementContext ctx = (MovementContext)actor.getValue();
                                    if (ctx.temporaryData instanceof FireControlMovementBehaviour.FireControlData fcData && ctx.localPos.equals(queryLocalPos)) {
                                       if (fcData.focusedEntityId == targetId) {
                                          targetId = -1;
                                       }

                                       fcData.focusedEntityId = targetId;
                                       ctx.data.putInt("FocusedEntityId", targetId);
                                       foundAceId = acex.getId();
                                       foundLocalPos = queryLocalPos;
                                    }

                                    if (ctx.temporaryData instanceof VirtualSentryArmBlockEntity) {
                                       ctx.data.putInt("_TargetId", -1);
                                       ctx.data.putInt("_AeroScanCD", 0);
                                    }
                                 }
                              }
                           }
                        }

                        if (foundAceId == -1) {
                           if (player.level().getBlockEntity(fcPos) instanceof BlazeFireControlBlockEntity fc && fc.getFocusedEntityId() == targetId) {
                              targetId = -1;
                           }

                           PacketDistributor.sendToServer(new SentryFocusPacket(-1, fcPos, targetId), new CustomPacketPayload[0]);
                           event.setCanceled(true);
                           player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 0.8F, 1.8F);
                           lastFocusTime = System.currentTimeMillis();
                           return;
                        }

                        PacketDistributor.sendToServer(new SentryFocusPacket(foundAceId, foundLocalPos, targetId), new CustomPacketPayload[0]);
                        player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 0.8F, 1.8F);
                        lastFocusTime = System.currentTimeMillis();
                     }

                     event.setCanceled(true);
                  }
               }
            }
         }
      }
   }

   static boolean isPlayerLookingAtNoAmmoSentry(LocalPlayer player, double range) {
      Vec3 eyePos = player.getEyePosition();
      Vec3 viewVec = player.getViewVector(1.0F);
      Vec3 traceEnd = eyePos.add(viewVec.scale(range));
      BlockHitResult blockHit = player.level().clip(new ClipContext(eyePos, traceEnd, Block.COLLIDER, Fluid.NONE, player));
      if (blockHit.getType() == Type.MISS) {
         return false;
      } else {
         BlockPos hitPos = blockHit.getBlockPos();
         return player.level().getBlockEntity(hitPos) instanceof SentryArmBlockEntity sentry
            ? sentry.getSentryStatus() == SentryArmBlockEntity.SentryStatus.NO_AMMO
            : false;
      }
   }

   static Entity getLookedAtEntity(LocalPlayer player, double range) {
      Vec3 eyePos = player.getEyePosition();
      Vec3 viewVec = player.getViewVector(1.0F);
      Vec3 traceEnd = eyePos.add(viewVec.scale(range));
      BlockHitResult blockHit = player.level().clip(new ClipContext(eyePos, traceEnd, Block.COLLIDER, Fluid.NONE, player));
      double actualLimit = range;
      if (blockHit.getType() != Type.MISS) {
         actualLimit = blockHit.getLocation().distanceTo(eyePos);
         traceEnd = blockHit.getLocation();
      }

      AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(actualLimit)).inflate(1.0, 1.0, 1.0);
      if (!(searchBox.getXsize() > 1000.0) && !(searchBox.getYsize() > 1000.0) && !(searchBox.getZsize() > 1000.0)) {
         try {
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
               player, eyePos, traceEnd, searchBox, ex -> !ex.isSpectator() && ex.isPickable() && ex instanceof LivingEntity, actualLimit * actualLimit
            );
            return entityHit != null ? entityHit.getEntity() : null;
         } catch (Exception var11) {
            return null;
         }
      } else {
         return null;
      }
   }
}

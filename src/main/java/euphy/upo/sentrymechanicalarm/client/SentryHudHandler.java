package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.content.FireControlMovementBehaviour;
import euphy.upo.sentrymechanicalarm.content.SentryScopeItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.apache.commons.lang3.tuple.MutablePair;

@EventBusSubscriber(
   modid = "sentrymechanicalarm",
   value = {Dist.CLIENT}
)
public class SentryHudHandler {
   private static final Component MARK_TEXT = Component.translatable("message.sentrymechanicalarm.can_mark").withStyle(ChatFormatting.GREEN);
   private static final Component FOCUS_TEXT = Component.translatable("message.sentrymechanicalarm.can_focus").withStyle(ChatFormatting.RED);
   private static final Component NO_BIND_TEXT = Component.translatable("message.sentrymechanicalarm.scope_not_bound_hud").withStyle(ChatFormatting.GRAY);
   private static final Component BOUND_TEXT = Component.translatable("message.sentrymechanicalarm.scope_bound_hud").withStyle(ChatFormatting.GREEN);
   private static final Component NO_CLIPBOARD_TEXT = Component.translatable("message.sentrymechanicalarm.no_clipboard").withStyle(ChatFormatting.GRAY);
   private static final Component NO_AMMO_TEXT = Component.translatable("message.sentrymechanicalarm.no_ammo").withStyle(ChatFormatting.RED);
   private static int hudFrame = 0;
   private static final int HUD_REFRESH = 5;
   private static int noAmmoFrame = 0;
   private static boolean cachedNoAmmo = false;
   private static int entityFrame = 0;
   private static Entity cachedEntity = null;
   private static int contraptionFrame = 0;
   private static boolean cachedCHasClipboard = false;
   private static boolean cachedCIsWhitelist = false;
   private static List<String> cachedCTargetList = null;
   private static int cachedCFocusedId = -1;
   private static final ResourceLocation SCOPE_OVERLAY = ResourceLocation.fromNamespaceAndPath(
      "sentrymechanicalarm", "textures/misc/blaze_fire_control_scope_overlay.png"
   );
   public static final Layer OVERLAY = SentryHudHandler::renderOverlay;

   @SubscribeEvent
   public static void registerGuiOverlays(RegisterGuiLayersEvent event) {
      event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath("sentrymechanicalarm", "sentry_overlay"), OVERLAY);
   }

   private static void renderOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.options.hideGui && mc.player != null) {
         hudFrame++;
         if (hudFrame % 5 == 0) {
            cachedEntity = null;
            cachedNoAmmo = false;
            noAmmoFrame = -1;
         }

         LocalPlayer player = mc.player;
         if (player.isUsingItem() && player.getUseItem().getItem() instanceof SentryScopeItem) {
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            RenderSystem.setShaderTexture(0, SCOPE_OVERLAY);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            BufferBuilder bufferbuilder = RenderSystem.renderThreadTesselator().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.addVertex(0.0F, (float)h, -0.1F).setUv(0.0F, 1.0F);
            bufferbuilder.addVertex((float)w, (float)h, -0.1F).setUv(1.0F, 1.0F);
            bufferbuilder.addVertex((float)w, 0.0F, -0.1F).setUv(1.0F, 0.0F);
            bufferbuilder.addVertex(0.0F, 0.0F, -0.1F).setUv(0.0F, 0.0F);
            BufferUploader.drawWithShader(bufferbuilder.build());
            RenderSystem.disableBlend();
         }

         boolean isHoldingSpyglass = player.getMainHandItem().getItem() == Items.SPYGLASS;
         boolean isUsingSpyglass = player.isUsingItem() && player.getUseItem().getItem() == Items.SPYGLASS;
         boolean isOffhandClipboard = player.getOffhandItem().getItem() instanceof FireControlClipboardItem;
         if (isHoldingSpyglass && isUsingSpyglass && isOffhandClipboard) {
            if (entityFrame != hudFrame / 5) {
               entityFrame = hudFrame / 5;
               cachedEntity = SentryClientInputHandler.getLookedAtEntity(player, 256.0);
            }

            if (cachedEntity != null) {
               renderPrompt(guiGraphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), MARK_TEXT, 15);
            }
         } else {
            if (noAmmoFrame != hudFrame / 5) {
               noAmmoFrame = hudFrame / 5;
               cachedNoAmmo = SentryClientInputHandler.isPlayerLookingAtNoAmmoSentry(player, 32.0);
            }

            if (cachedNoAmmo) {
               renderPrompt(guiGraphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), NO_AMMO_TEXT, 5);
            }

            boolean isHoldingScope = player.getMainHandItem().getItem() instanceof SentryScopeItem;
            boolean isUsingScope = player.isUsingItem() && player.getUseItem().getItem() instanceof SentryScopeItem;
            if (isHoldingScope) {
               BlockPos fcPos = SentryScopeItem.getLinkedFireControlPos(player.getMainHandItem());
               if (fcPos == null) {
                  renderPrompt(guiGraphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), NO_BIND_TEXT, 15);
                  return;
               }

               renderPrompt(guiGraphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), BOUND_TEXT, 15);
               if (isUsingScope) {
                  if (entityFrame != hudFrame / 5) {
                     entityFrame = hudFrame / 5;
                     cachedEntity = SentryClientInputHandler.getLookedAtEntity(player, 256.0);
                  }

                  if (cachedEntity != null) {
                     renderPrompt(guiGraphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), FOCUS_TEXT, 25);
                  }

                  renderMarkList(guiGraphics, mc, player.getMainHandItem());
               }
            }
         }
      }
   }

   private static void renderMarkList(GuiGraphics guiGraphics, Minecraft mc, ItemStack scopeStack) {
      ClientLevel level = mc.level;
      if (level != null) {
         BlockPos fcPos = SentryScopeItem.getLinkedFireControlPos(scopeStack);
         if (fcPos != null) {
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int x = screenWidth - 10;
            int y = 10;
            BlockEntity be = level.getBlockEntity(fcPos);
            if (be instanceof BlazeFireControlBlockEntity fc) {
               Vec3 markedPos = fc.getMarkedWorldPos();
               if (markedPos != null) {
                  Component coordTitle = Component.literal("--- 坐标攻击 ---").withStyle(ChatFormatting.GOLD);
                  guiGraphics.drawString(mc.font, coordTitle, x - mc.font.width(coordTitle), y, 16777215, true);
                  y += 9 + 2;
                  Component posText = Component.literal(String.format("X: %.1f", markedPos.x)).withStyle(ChatFormatting.WHITE);
                  guiGraphics.drawString(mc.font, posText, x - mc.font.width(posText), y, 16777215, true);
                  y += 9 + 1;
                  posText = Component.literal(String.format("Y: %.1f", markedPos.y)).withStyle(ChatFormatting.WHITE);
                  guiGraphics.drawString(mc.font, posText, x - mc.font.width(posText), y, 16777215, true);
                  y += 9 + 1;
                  posText = Component.literal(String.format("Z: %.1f", markedPos.z)).withStyle(ChatFormatting.WHITE);
                  guiGraphics.drawString(mc.font, posText, x - mc.font.width(posText), y, 16777215, true);
                  y += 9 + 2;
                  int markedEntityId = fc.getMarkedEntityIds().isEmpty() ? -1 : fc.getMarkedEntityIds().iterator().next();
                  if (markedEntityId != -1) {
                     Component markedEnt = Component.literal("已标记: 实体 #" + markedEntityId).withStyle(ChatFormatting.GRAY);
                     guiGraphics.drawString(mc.font, markedEnt, x - mc.font.width(markedEnt), y, 16777215, true);
                     y += 9 + 2;
                  }

                  return;
               }
            }

            List<String> targetList = null;
            boolean isWhitelist = false;
            boolean hasClipboard = false;
            int focusedEntityId = -1;
            if (be instanceof BlazeFireControlBlockEntity fcx) {
               if (!fcx.inventory.getStackInSlot(0).isEmpty()) {
                  hasClipboard = true;
                  isWhitelist = fcx.isWhitelist();
                  targetList = fcx.getTargetList();
               }

               focusedEntityId = fcx.getFocusedEntityId();
            }

            if (!hasClipboard) {
               if (contraptionFrame != hudFrame / 10) {
                  contraptionFrame = hudFrame / 10;
                  cachedCHasClipboard = false;
                  cachedCIsWhitelist = false;
                  cachedCTargetList = null;
                  cachedCFocusedId = -1;
                  AABB searchBounds = new AABB(fcPos).inflate(2.0);

                  for (AbstractContraptionEntity ace : level.getEntitiesOfClass(AbstractContraptionEntity.class, searchBounds)) {
                     Contraption contraption = ace.getContraption();
                     if (contraption != null) {
                        Vec3 localCenter = ace.toLocalVector(Vec3.atCenterOf(fcPos), 0.0F);
                        BlockPos queryLocalPos = BlockPos.containing(localCenter);

                        for (MutablePair<?, MovementContext> actor : contraption.getActors()) {
                           if (((MovementContext)actor.getValue()).localPos.equals(queryLocalPos)
                              && ((MovementContext)actor.getValue()).temporaryData instanceof FireControlMovementBehaviour.FireControlData fcData) {
                              if (!fcData.displayItem.isEmpty()) {
                                 cachedCHasClipboard = true;
                                 cachedCIsWhitelist = fcData.isWhitelist;
                                 cachedCTargetList = fcData.targetList;
                              }

                              if (fcData.focusedEntityId != -1) {
                                 cachedCFocusedId = fcData.focusedEntityId;
                              }
                              break;
                           }
                        }

                        if (cachedCHasClipboard) {
                           break;
                        }
                     }
                  }
               }

               hasClipboard = cachedCHasClipboard;
               isWhitelist = cachedCIsWhitelist;
               targetList = cachedCTargetList;
               if (focusedEntityId == -1) {
                  focusedEntityId = cachedCFocusedId;
               }
            }

            if (!hasClipboard) {
               guiGraphics.drawString(mc.font, NO_CLIPBOARD_TEXT, x - mc.font.width(NO_CLIPBOARD_TEXT), y, 16777215, true);
               y += 9 + 2;
            } else {
               Component modeText = Component.literal(isWhitelist ? "[Whitelist]" : "[Blacklist]")
                  .withStyle(isWhitelist ? ChatFormatting.GREEN : ChatFormatting.RED);
               guiGraphics.drawString(mc.font, modeText, x - mc.font.width(modeText), y, 16777215, true);
               y += 9 + 3;
               if (targetList != null && !targetList.isEmpty()) {
                  int shown = 0;
                  int screenHeight = mc.getWindow().getGuiScaledHeight();

                  for (String name : targetList) {
                     if (y > screenHeight - 30) {
                        break;
                     }

                     Component entry = Component.literal("- " + name).withStyle(ChatFormatting.WHITE);
                     guiGraphics.drawString(mc.font, entry, x - mc.font.width(entry), y, 16777215, true);
                     y += 9 + 1;
                     shown++;
                  }

                  if (shown < targetList.size()) {
                     Component more = Component.literal("... +" + (targetList.size() - shown)).withStyle(ChatFormatting.GRAY);
                     guiGraphics.drawString(mc.font, more, x - mc.font.width(more), y, 16777215, true);
                     y += 9 + 2;
                  }
               } else {
                  Component emptyText = Component.translatable("message.sentrymechanicalarm.list_empty").withStyle(ChatFormatting.DARK_GRAY);
                  guiGraphics.drawString(mc.font, emptyText, x - mc.font.width(emptyText), y, 16777215, true);
                  y += 9 + 2;
               }
            }

            y += 4;
            if (focusedEntityId != -1 && level.getEntity(focusedEntityId) instanceof LivingEntity living && living.isAlive()) {
               String name = living.getName().getString();
               int color = 16733525;
               Component sep = Component.literal("--- Focus ---").withStyle(ChatFormatting.DARK_RED);
               guiGraphics.drawString(mc.font, sep, x - mc.font.width(sep), y, 16777215, true);
               y += 9 + 1;
               Component focusComp = Component.literal(">> " + name).withStyle(ChatFormatting.RED);
               guiGraphics.drawString(mc.font, focusComp, x - mc.font.width(focusComp), y, color, true);
               y += 9 + 2;
            }
         }
      }
   }

   private static void renderPrompt(GuiGraphics guiGraphics, int screenWidth, int screenHeight, Component text, int yOffset) {
      int x = (screenWidth - Minecraft.getInstance().font.width(text)) / 2;
      int y = screenHeight / 2 + yOffset;
      guiGraphics.drawString(Minecraft.getInstance().font, text, x, y, 16777215, true);
   }
}

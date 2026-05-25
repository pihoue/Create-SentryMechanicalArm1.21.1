package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockItem;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.content.FireControlMovementBehaviour;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.SentryScopeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID, value = Dist.CLIENT)
public class SentryHudHandler {

    private static final Component MARK_TEXT = Component.translatable("message.sentrymechanicalarm.can_mark")
            .withStyle(ChatFormatting.GREEN);
    private static final Component FOCUS_TEXT = Component.translatable("message.sentrymechanicalarm.can_focus")
            .withStyle(ChatFormatting.RED);
    private static final Component NO_BIND_TEXT = Component.translatable("message.sentrymechanicalarm.scope_not_bound_hud")
            .withStyle(ChatFormatting.GRAY);
    private static final Component BOUND_TEXT = Component.translatable("message.sentrymechanicalarm.scope_bound_hud")
            .withStyle(ChatFormatting.GREEN);
    private static final Component NO_CLIPBOARD_TEXT = Component.translatable("message.sentrymechanicalarm.no_clipboard")
            .withStyle(ChatFormatting.GRAY);
    private static final Component NO_AMMO_TEXT = Component.translatable("message.sentrymechanicalarm.no_ammo")
            .withStyle(ChatFormatting.RED);

    private static final ResourceLocation SCOPE_OVERLAY = ResourceLocation.fromNamespaceAndPath(
            SentryMechanicalArm.MODID, "textures/misc/blaze_fire_control_scope_overlay.png");

    public static final LayeredDraw.Layer OVERLAY = SentryHudHandler::renderOverlay;

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(SentryMechanicalArm.MODID, "sentry_overlay"),
                OVERLAY
        );
    }

    private static void renderOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        var player = mc.player;

        if (player.isUsingItem() && player.getUseItem().getItem() instanceof BlazeFireControlBlockItem) {
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            RenderSystem.setShaderTexture(0, SCOPE_OVERLAY);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            BufferBuilder bufferbuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
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
            Entity target = SentryClientInputHandler.getLookedAtEntity(player, 256.0);
            if (target != null) {
                renderPrompt(guiGraphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), MARK_TEXT, 15);
            }
            return;
        }

        if (SentryClientInputHandler.isPlayerLookingAtNoAmmoSentry(player, 32.0)) {
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
                Entity target = SentryClientInputHandler.getLookedAtEntity(player, 256.0);
                if (target != null) {
                    renderPrompt(guiGraphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), FOCUS_TEXT, 25);
                }

                renderMarkList(guiGraphics, mc, player.getMainHandItem());
            }
        }
    }

    private static void renderMarkList(GuiGraphics guiGraphics, Minecraft mc, ItemStack scopeStack) {
        var level = mc.level;
        if (level == null) return;

        BlockPos fcPos = SentryScopeItem.getLinkedFireControlPos(scopeStack);
        if (fcPos == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int x = screenWidth - 10;
        int y = 10;

        List<String> targetList = null;
        boolean isWhitelist = false;
        boolean hasClipboard = false;
        int focusedEntityId = -1;

        BlockEntity be = level.getBlockEntity(fcPos);
        if (be instanceof BlazeFireControlBlockEntity fc) {
            if (!fc.inventory.getStackInSlot(0).isEmpty()) {
                hasClipboard = true;
                isWhitelist = fc.isWhitelist();
                targetList = fc.getTargetList();
            }
            focusedEntityId = fc.getFocusedEntityId();
        }

        if (!hasClipboard) {
            net.minecraft.world.phys.AABB searchBounds = new net.minecraft.world.phys.AABB(fcPos).inflate(2);
            for (AbstractContraptionEntity ace : level.getEntitiesOfClass(AbstractContraptionEntity.class, searchBounds)) {
                Contraption contraption = ace.getContraption();
                if (contraption == null) continue;
                Vec3 localCenter = ace.toLocalVector(Vec3.atCenterOf(fcPos), 0);
                BlockPos queryLocalPos = BlockPos.containing(localCenter);
                for (org.apache.commons.lang3.tuple.MutablePair<?, MovementContext> actor : contraption.getActors()) {
                    if (!actor.getValue().localPos.equals(queryLocalPos)) continue;
                    if (actor.getValue().temporaryData instanceof FireControlMovementBehaviour.FireControlData fcData) {
                        if (!fcData.displayItem.isEmpty()) {
                            hasClipboard = true;
                            isWhitelist = fcData.isWhitelist;
                            targetList = fcData.targetList;
                        }
                        if (fcData.focusedEntityId != -1) {
                            focusedEntityId = fcData.focusedEntityId;
                        }
                        break;
                    }
                }
                if (hasClipboard) break;
            }
        }

        if (!hasClipboard) {
            guiGraphics.drawString(mc.font, NO_CLIPBOARD_TEXT, x - mc.font.width(NO_CLIPBOARD_TEXT), y, 0xFFFFFF, true);
            y += mc.font.lineHeight + 2;
        } else {
            Component modeText = Component.literal(isWhitelist ? "[Whitelist]" : "[Blacklist]")
                    .withStyle(isWhitelist ? ChatFormatting.GREEN : ChatFormatting.RED);
            guiGraphics.drawString(mc.font, modeText, x - mc.font.width(modeText), y, 0xFFFFFF, true);
            y += mc.font.lineHeight + 3;

            if (targetList != null && !targetList.isEmpty()) {
                int shown = 0;
                int screenHeight = mc.getWindow().getGuiScaledHeight();
                for (String name : targetList) {
                    if (y > screenHeight - 30) break;
                    Component entry = Component.literal("- " + name).withStyle(ChatFormatting.WHITE);
                    guiGraphics.drawString(mc.font, entry, x - mc.font.width(entry), y, 0xFFFFFF, true);
                    y += mc.font.lineHeight + 1;
                    shown++;
                }
                if (shown < targetList.size()) {
                    Component more = Component.literal("... +" + (targetList.size() - shown))
                            .withStyle(ChatFormatting.GRAY);
                    guiGraphics.drawString(mc.font, more, x - mc.font.width(more), y, 0xFFFFFF, true);
                    y += mc.font.lineHeight + 2;
                }
            } else {
                Component emptyText = Component.translatable("message.sentrymechanicalarm.list_empty")
                        .withStyle(ChatFormatting.DARK_GRAY);
                guiGraphics.drawString(mc.font, emptyText, x - mc.font.width(emptyText), y, 0xFFFFFF, true);
                y += mc.font.lineHeight + 2;
            }
        }

        y += 4;
        if (focusedEntityId != -1) {
            Entity focusEntity = level.getEntity(focusedEntityId);
            if (focusEntity instanceof LivingEntity living && living.isAlive()) {
                String name = living.getName().getString();
                int color = 0xFF5555;
                Component sep = Component.literal("--- Focus ---").withStyle(ChatFormatting.DARK_RED);
                guiGraphics.drawString(mc.font, sep, x - mc.font.width(sep), y, 0xFFFFFF, true);
                y += mc.font.lineHeight + 1;
                Component focusComp = Component.literal(">> " + name).withStyle(ChatFormatting.RED);
                guiGraphics.drawString(mc.font, focusComp, x - mc.font.width(focusComp), y, color, true);
                y += mc.font.lineHeight + 2;
            }
        }

    }

    private static void renderPrompt(GuiGraphics guiGraphics, int screenWidth, int screenHeight, Component text, int yOffset) {
        int x = (screenWidth - Minecraft.getInstance().font.width(text)) / 2;
        int y = (screenHeight / 2) + yOffset;

        guiGraphics.drawString(Minecraft.getInstance().font, text, x, y, 0xFFFFFF, true);
    }
}
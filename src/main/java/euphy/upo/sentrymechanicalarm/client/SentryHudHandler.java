package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import euphy.upo.sentrymechanicalarm.content.SentryScopeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
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
            }
        }
    }

    private static void renderPrompt(GuiGraphics guiGraphics, int screenWidth, int screenHeight, Component text, int yOffset) {
        int x = (screenWidth - Minecraft.getInstance().font.width(text)) / 2;
        int y = (screenHeight / 2) + yOffset;

        guiGraphics.drawString(Minecraft.getInstance().font, text, x, y, 0xFFFFFF, true);
    }
}
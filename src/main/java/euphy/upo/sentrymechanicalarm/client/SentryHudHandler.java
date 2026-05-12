package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(value = Dist.CLIENT)
public class SentryHudHandler {

    private static final Component MARK_TEXT = Component.translatable("message.sentrymechanicalarm.can_mark")
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

        if (!isHoldingSpyglass || !isUsingSpyglass || !isOffhandClipboard) {
            return;
        }

        Entity target = SentryClientInputHandler.getLookedAtEntity(player, 256.0);

        if (target != null) {
            renderMarkPrompt(guiGraphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    private static void renderMarkPrompt(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        int x = (screenWidth - Minecraft.getInstance().font.width(MARK_TEXT)) / 2;
        int y = (screenHeight / 2) + 15;

        guiGraphics.drawString(Minecraft.getInstance().font, MARK_TEXT, x, y, 0xFFFFFF, true);
    }
}
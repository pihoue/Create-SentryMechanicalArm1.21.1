package euphy.upo.sentrymechanicalarm.client;

import euphy.upo.sentrymechanicalarm.content.FireControlClipboardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SentryHudHandler {

    private static final Component MARK_TEXT = Component.translatable("message.sentrymechanicalarm.can_mark")
            .withStyle(ChatFormatting.GREEN);

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.SPYGLASS.type() &&
                event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean isHoldingSpyglass = player.getMainHandItem().getItem() == Items.SPYGLASS;
        boolean isUsingSpyglass = player.isUsingItem() && player.getUseItem().getItem() == Items.SPYGLASS;
        boolean isOffhandClipboard = player.getOffhandItem().getItem() instanceof FireControlClipboardItem;

        if (!isHoldingSpyglass || !isUsingSpyglass || !isOffhandClipboard) {
            return;
        }

        Entity target = SentryClientInputHandler.getLookedAtEntity(player, 256.0);

        if (target != null) {
            renderMarkPrompt(event.getGuiGraphics(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    private static void renderMarkPrompt(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        int x = (screenWidth - Minecraft.getInstance().font.width(MARK_TEXT)) / 2;
        int y = (screenHeight / 2) + 15;

        guiGraphics.drawString(Minecraft.getInstance().font, MARK_TEXT, x, y, 0xFFFFFF, true);
    }
}
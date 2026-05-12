package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import euphy.upo.sentrymechanicalarm.content.FireControlMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import euphy.upo.sentrymechanicalarm.network.ClipboardPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class FireControlScreen extends AbstractContainerScreen<FireControlMenu> {

    private static final ResourceLocation BOOK_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    private static final int BTN_WIDTH = 23;
    private static final int BTN_HEIGHT = 13;
    private IconButton modeButton;
    private boolean clientWhitelistState;
    private static final int LIST_START_X = 70;
    private static final int LIST_START_Y = 45;
    private static final int LINE_HEIGHT = 16;
    private static final int ITEMS_PER_PAGE = 12;
    private int currentPage = 0;

    private int btnPrevX, btnPrevY, btnPrevW, btnPrevH;
    private int btnNextX, btnNextY, btnNextW, btnNextH;

    public FireControlScreen(FireControlMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();

        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop = (this.height - this.imageHeight) / 2;
        this.btnPrevX = guiLeft + 40;
        this.btnPrevY = guiTop + 225;
        this.btnPrevW = 40;
        this.btnPrevH = 12;
        this.btnNextX = guiLeft + 180;
        this.btnNextY = guiTop + 225;
        this.btnNextW = 40;
        this.btnNextH = 12;
        this.clientWhitelistState = this.menu.isWhitelist;
        int btnX = guiLeft + 180;
        int btnY = guiTop + 40;
        this.modeButton = new IconButton(btnX, btnY, AllIcons.I_BLACKLIST);
        this.modeButton.withCallback(() -> {
            PacketDistributor.sendToServer(new ClipboardPacket(0, 0));
            this.clientWhitelistState = !this.clientWhitelistState;
            updateModeButtonVisuals();
            playClickSound();
        });

        updateModeButtonVisuals();
        this.addRenderableWidget(this.modeButton);
    }

    private void updateModeButtonVisuals() {
        if (clientWhitelistState) {
            modeButton.setIcon(AllIcons.I_WHITELIST);
            modeButton.setToolTip(Component.translatable("message.sentrymechanicalarm.whitelist")
                    .append("\n")
                    .append(Component.translatable("message.sentrymechanicalarm.whitelist_des").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append("\n")
                    .append(Component.translatable("message.sentrymechanicalarm.whitelist_warn").withStyle(ChatFormatting.DARK_RED)));

        } else {
            modeButton.setIcon(AllIcons.I_BLACKLIST);
            modeButton.setToolTip(Component.translatable("message.sentrymechanicalarm.blacklist")
                    .append(Component.translatable("message.sentrymechanicalarm.blacklist_des").withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop = (this.height - this.imageHeight) / 2;
        AllGuiTextures.CLIPBOARD.render(guiGraphics, guiLeft - 1, guiTop - 5);

        List<String> allTargets = this.menu.getTargetList();
        int totalItems = allTargets.size();
        int maxPage = Math.max(0, (totalItems - 1) / ITEMS_PER_PAGE);
        if (currentPage > maxPage) currentPage = maxPage;

        String pageStr = (currentPage + 1) + "/" + (maxPage + 1);
        guiGraphics.drawString(this.font, pageStr, guiLeft + 118, guiTop + 15, 0x555555, false);

        if (allTargets.isEmpty()) {

        } else {
            int start = currentPage * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, totalItems);

            for (int i = start; i < end; i++) {
                int relativeIndex = i - start;
                String name = allTargets.get(i);
                int lineY = guiTop + LIST_START_Y + (relativeIndex * LINE_HEIGHT);
                int lineX = guiLeft + LIST_START_X;
                String indexStr = (i + 1) + ". ";
                int indexWidth = this.font.width(indexStr);

                int headWidth = 0;
                int nameWidth = this.font.width(name);
                int totalContentWidth = indexWidth + headWidth + nameWidth;

                boolean isHovering = isHoveringArea(mouseX, mouseY, lineX, lineY, totalContentWidth, LINE_HEIGHT);
                int color = isHovering ? 0xFF0000 : 0x000000;
                guiGraphics.drawString(this.font, indexStr, lineX, lineY + 1, color, false);

                int nameX = lineX + indexWidth + headWidth;
                guiGraphics.drawString(this.font, name, nameX, lineY + 1, color, false);

                if (isHovering) {
                    guiGraphics.fill(lineX, lineY + 6, lineX + totalContentWidth, lineY + 7, 0xFFFF0000);
                }
            }
        }

        if (currentPage > 0 || currentPage < maxPage) {

            if (currentPage > 0) {
                boolean hover = isHoveringArea(mouseX, mouseY, btnPrevX, btnPrevY, BTN_WIDTH, BTN_HEIGHT);

                int u = hover ? 23 : 0;
                int v = 205;
                guiGraphics.blit(BOOK_TEXTURE, btnPrevX, btnPrevY, u, v, BTN_WIDTH, BTN_HEIGHT);
            }

            if (currentPage < maxPage) {
                boolean hover = isHoveringArea(mouseX, mouseY, btnNextX, btnNextY, BTN_WIDTH, BTN_HEIGHT);

                int u = hover ? 23 : 0;
                int v = 192;
                guiGraphics.blit(BOOK_TEXTURE, btnNextX, btnNextY, u, v, BTN_WIDTH, BTN_HEIGHT);
            }
        }
    }

    private boolean isHoveringArea(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int guiLeft = (this.width - this.imageWidth) / 2;
            int guiTop = (this.height - this.imageHeight) / 2;
            List<String> targets = this.menu.getTargetList();
            int totalItems = targets.size();
            int maxPage = Math.max(0, (totalItems - 1) / ITEMS_PER_PAGE);

            if (currentPage > 0 && isHoveringArea((int)mouseX, (int)mouseY, btnPrevX, btnPrevY, BTN_WIDTH, BTN_HEIGHT)) {
                currentPage--;
                playClickSound();
                return true;
            }
            if (currentPage < maxPage && isHoveringArea((int)mouseX, (int)mouseY, btnNextX, btnNextY, BTN_WIDTH, BTN_HEIGHT)) {
                currentPage++;
                playClickSound();
                return true;
            }

            int start = currentPage * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, totalItems);

            for (int i = start; i < end; i++) {
                int relativeIndex = i - start;
                String name = targets.get(i);

                String indexStr = (i + 1) + ". ";
                int indexWidth = this.font.width(indexStr);

                boolean hasSkin = false;
                int headWidth = hasSkin ? 10 : 0;
                int nameWidth = this.font.width(name);
                int totalContentWidth = indexWidth + headWidth + nameWidth;

                int lineY = guiTop + LIST_START_Y + (relativeIndex * LINE_HEIGHT);
                int lineX = guiLeft + LIST_START_X;

                if (isHoveringArea((int)mouseX, (int)mouseY, lineX, lineY, totalContentWidth, LINE_HEIGHT)) {
                    PacketDistributor.sendToServer(new ClipboardPacket(1, i));
                    playClickSound();
                    if (i < targets.size()) targets.remove(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {  }
}
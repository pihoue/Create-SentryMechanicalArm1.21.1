package euphy.upo.sentrymechanicalarm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import euphy.upo.sentrymechanicalarm.content.FireControlMenu;
import euphy.upo.sentrymechanicalarm.network.ClipboardPacket;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

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
   private int btnPrevX;
   private int btnPrevY;
   private int btnPrevW;
   private int btnPrevH;
   private int btnNextX;
   private int btnNextY;
   private int btnNextW;
   private int btnNextH;

   public FireControlScreen(FireControlMenu menu, Inventory playerInventory, Component title) {
      super(menu, playerInventory, title);
      this.imageWidth = 256;
      this.imageHeight = 256;
   }

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
      this.clientWhitelistState = ((FireControlMenu)this.menu).isWhitelist;
      int btnX = guiLeft + 180;
      int btnY = guiTop + 40;
      this.modeButton = new IconButton(btnX, btnY, AllIcons.I_BLACKLIST);
      this.modeButton.withCallback(() -> {
         PacketDistributor.sendToServer(new ClipboardPacket(0, 0), new CustomPacketPayload[0]);
         this.clientWhitelistState = !this.clientWhitelistState;
         this.updateModeButtonVisuals();
         this.playClickSound();
      });
      this.updateModeButtonVisuals();
      this.addRenderableWidget(this.modeButton);
   }

   private void updateModeButtonVisuals() {
      if (this.clientWhitelistState) {
         this.modeButton.setIcon(AllIcons.I_WHITELIST);
         this.modeButton
            .setToolTip(
               Component.translatable("message.sentrymechanicalarm.whitelist")
                  .append("\n")
                  .append(Component.translatable("message.sentrymechanicalarm.whitelist_des").withStyle(ChatFormatting.GRAY))
                  .append("\n")
                  .append(Component.translatable("message.sentrymechanicalarm.whitelist_warn").withStyle(ChatFormatting.DARK_RED))
            );
      } else {
         this.modeButton.setIcon(AllIcons.I_BLACKLIST);
         this.modeButton
            .setToolTip(
               Component.translatable("message.sentrymechanicalarm.blacklist")
                  .append(Component.translatable("message.sentrymechanicalarm.blacklist_des").withStyle(ChatFormatting.GRAY))
            );
      }
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      this.renderTooltip(guiGraphics, mouseX, mouseY);
   }

   protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      int guiLeft = (this.width - this.imageWidth) / 2;
      int guiTop = (this.height - this.imageHeight) / 2;
      AllGuiTextures.CLIPBOARD.render(guiGraphics, guiLeft - 1, guiTop - 5);
      List<String> allTargets = ((FireControlMenu)this.menu).getTargetList();
      int totalItems = allTargets.size();
      int maxPage = Math.max(0, (totalItems - 1) / 12);
      if (this.currentPage > maxPage) {
         this.currentPage = maxPage;
      }

      String pageStr = this.currentPage + 1 + "/" + (maxPage + 1);
      guiGraphics.drawString(this.font, pageStr, guiLeft + 118, guiTop + 15, 5592405, false);
      if (!allTargets.isEmpty()) {
         int start = this.currentPage * 12;
         int end = Math.min(start + 12, totalItems);

         for (int i = start; i < end; i++) {
            int relativeIndex = i - start;
            String name = allTargets.get(i);
            int lineY = guiTop + 45 + relativeIndex * 16;
            int lineX = guiLeft + 70;
            String indexStr = i + 1 + ". ";
            int indexWidth = this.font.width(indexStr);
            int headWidth = 0;
            int nameWidth = this.font.width(name);
            int totalContentWidth = indexWidth + headWidth + nameWidth;
            boolean isHovering = this.isHoveringArea(mouseX, mouseY, lineX, lineY, totalContentWidth, 16);
            int color = isHovering ? 16711680 : 0;
            guiGraphics.drawString(this.font, indexStr, lineX, lineY + 1, color, false);
            int nameX = lineX + indexWidth + headWidth;
            guiGraphics.drawString(this.font, name, nameX, lineY + 1, color, false);
            if (isHovering) {
               guiGraphics.fill(lineX, lineY + 6, lineX + totalContentWidth, lineY + 7, -65536);
            }
         }
      }

      if (this.currentPage > 0 || this.currentPage < maxPage) {
         if (this.currentPage > 0) {
            boolean hover = this.isHoveringArea(mouseX, mouseY, this.btnPrevX, this.btnPrevY, 23, 13);
            int u = hover ? 23 : 0;
            int v = 205;
            guiGraphics.blit(BOOK_TEXTURE, this.btnPrevX, this.btnPrevY, u, v, 23, 13);
         }

         if (this.currentPage < maxPage) {
            boolean hover = this.isHoveringArea(mouseX, mouseY, this.btnNextX, this.btnNextY, 23, 13);
            int u = hover ? 23 : 0;
            int v = 192;
            guiGraphics.blit(BOOK_TEXTURE, this.btnNextX, this.btnNextY, u, v, 23, 13);
         }
      }
   }

   private boolean isHoveringArea(int mouseX, int mouseY, int x, int y, int w, int h) {
      return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + h;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         int guiLeft = (this.width - this.imageWidth) / 2;
         int guiTop = (this.height - this.imageHeight) / 2;
         List<String> targets = ((FireControlMenu)this.menu).getTargetList();
         int totalItems = targets.size();
         int maxPage = Math.max(0, (totalItems - 1) / 12);
         if (this.currentPage > 0 && this.isHoveringArea((int)mouseX, (int)mouseY, this.btnPrevX, this.btnPrevY, 23, 13)) {
            this.currentPage--;
            this.playClickSound();
            return true;
         }

         if (this.currentPage < maxPage && this.isHoveringArea((int)mouseX, (int)mouseY, this.btnNextX, this.btnNextY, 23, 13)) {
            this.currentPage++;
            this.playClickSound();
            return true;
         }

         int start = this.currentPage * 12;
         int end = Math.min(start + 12, totalItems);

         for (int i = start; i < end; i++) {
            int relativeIndex = i - start;
            String name = targets.get(i);
            String indexStr = i + 1 + ". ";
            int indexWidth = this.font.width(indexStr);
            boolean hasSkin = false;
            int headWidth = hasSkin ? 10 : 0;
            int nameWidth = this.font.width(name);
            int totalContentWidth = indexWidth + headWidth + nameWidth;
            int lineY = guiTop + 45 + relativeIndex * 16;
            int lineX = guiLeft + 70;
            if (this.isHoveringArea((int)mouseX, (int)mouseY, lineX, lineY, totalContentWidth, 16)) {
               PacketDistributor.sendToServer(new ClipboardPacket(1, i), new CustomPacketPayload[0]);
               this.playClickSound();
               if (i < targets.size()) {
                  targets.remove(i);
               }

               return true;
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   private void playClickSound() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
   }
}

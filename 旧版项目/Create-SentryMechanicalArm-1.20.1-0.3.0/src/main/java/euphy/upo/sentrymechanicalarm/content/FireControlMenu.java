package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FireControlMenu extends AbstractContainerMenu {

    private final List<String> targetList;
    public boolean isWhitelist;

    public FireControlMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        super(SentryRegistry.FIRE_CONTROL_MENU.get(), containerId);

        extraData.readBlockPos();

        this.isWhitelist = extraData.readBoolean();

        this.targetList = new ArrayList<>();
        int size = extraData.readVarInt();
        for (int i = 0; i < size; i++) {
            this.targetList.add(extraData.readUtf());
        }
    }

    public FireControlMenu(int containerId, Inventory playerInventory, List<String> targetList, boolean isWhitelist) {
        super(SentryRegistry.FIRE_CONTROL_MENU.get(), containerId);
        this.targetList = targetList;
        this.isWhitelist = isWhitelist;
    }

    public List<String> getTargetList() {
        return this.targetList;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem().getItem() instanceof FireControlClipboardItem ||
                player.getOffhandItem().getItem() instanceof FireControlClipboardItem;
    }
}
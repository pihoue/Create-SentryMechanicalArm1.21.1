package euphy.upo.sentrymechanicalarm.util;

import net.minecraft.world.item.ItemStack;

public interface IArmAmmoStorage {
    ItemStack getAmmoBox();
    void setAmmoBox(ItemStack stack);
}
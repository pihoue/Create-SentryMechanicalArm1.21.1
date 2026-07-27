package euphy.upo.sentrymechanicalarm.mixin;

import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({GunDisplayInstance.class})
public interface GunDisplayInstanceAccessor {
   @Accessor("display")
   GunDisplay sentrymechanicalarm$getDisplay();
}

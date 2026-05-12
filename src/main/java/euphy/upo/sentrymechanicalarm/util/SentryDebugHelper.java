package euphy.upo.sentrymechanicalarm.util; 

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.Map;

 /*
@Mod.EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class SentryDebugHelper {

    private static boolean DEBUG_MODE = false;
 
    private static final String DEBUG_TAG = "SentryDebugModeActive";

    public static boolean isEnabled() {
        return DEBUG_MODE;
    }

    public static void toggle() {
        DEBUG_MODE = !DEBUG_MODE;
    }

    public static void applyDebugState(FakePlayer fp, IGunOperator operator) {
 
        if (DEBUG_MODE) {
 
            fp.getPersistentData().putBoolean(DEBUG_TAG, true);
        } else {
 
            if (fp.getPersistentData().contains(DEBUG_TAG)) {
                fp.getPersistentData().remove(DEBUG_TAG);
            }
        }

 
        if (DEBUG_MODE) {
            forceExtremeSpread(operator);
        }
    }

    private static void forceExtremeSpread(IGunOperator operator) {
        AttachmentCacheProperty cache = operator.getCacheProperty();
        if (cache != null) {
            String inaccuracyId = GunProperties.INACCURACY.name();
            @SuppressWarnings("unchecked")
            Map<InaccuracyType, Float> inaccuracyMap = (Map<InaccuracyType, Float>) cache.getCache(inaccuracyId);

            if (inaccuracyMap != null) {
                float extremeSpread = 45.0f; 
                inaccuracyMap.put(InaccuracyType.AIM, extremeSpread);
                inaccuracyMap.put(InaccuracyType.STAND, extremeSpread);
                inaccuracyMap.put(InaccuracyType.SNEAK, extremeSpread);
                inaccuracyMap.put(InaccuracyType.LIE, extremeSpread);
            }
        }
    }


    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity living && living.getPersistentData().contains(DEBUG_TAG)) {
 
            event.setAmount(0.0f);
        }
    }
}
  */
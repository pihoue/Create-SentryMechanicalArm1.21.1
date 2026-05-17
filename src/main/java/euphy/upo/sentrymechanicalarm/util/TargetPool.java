package euphy.upo.sentrymechanicalarm.util;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public class TargetPool {

    private static final Map<Integer, String> CLAIMS = new HashMap<>();
    private static final Map<String, Integer> OWNER_CLAIMS = new HashMap<>();

    public static boolean tryAcquire(int entityId, String ownerKey) {
        String currentOwner = CLAIMS.get(entityId);
        if (currentOwner != null && !currentOwner.equals(ownerKey)) {
            return false;
        }
        CLAIMS.put(entityId, ownerKey);
        OWNER_CLAIMS.put(ownerKey, entityId);
        return true;
    }

    public static void releaseByOwner(String ownerKey) {
        Integer entityId = OWNER_CLAIMS.remove(ownerKey);
        if (entityId != null) {
            CLAIMS.remove(entityId);
        }
    }

    public static boolean isClaimedByOther(int entityId, String ownerKey) {
        String currentOwner = CLAIMS.get(entityId);
        return currentOwner != null && !currentOwner.equals(ownerKey);
    }

    public static void cleanup() {
        CLAIMS.entrySet().removeIf(e -> {
            OWNER_CLAIMS.values().remove(e.getKey());
            return true;
        });
    }
}

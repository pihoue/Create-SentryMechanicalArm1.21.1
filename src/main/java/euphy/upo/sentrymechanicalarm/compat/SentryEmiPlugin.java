package euphy.upo.sentrymechanicalarm.compat;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SentryEmiPlugin implements EmiPlugin {
   public void register(EmiRegistry registry) {
   }
}

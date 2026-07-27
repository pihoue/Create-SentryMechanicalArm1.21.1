package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class SentryArmInteraction {
   public static class SentryPoint extends ArmInteractionPoint {
      public SentryPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
         super(type, level, pos, state);
      }

      protected Vec3 getInteractionPositionVector() {
         return Vec3.atCenterOf(this.pos).add(0.0, 1.5, 0.0);
      }
   }

   public static class Type extends ArmInteractionPointType {
      public Type(ResourceLocation id) {
      }

      public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
         return level.getBlockEntity(pos) instanceof SentryArmBlockEntity;
      }

      @Nullable
      public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
         return new SentryArmInteraction.SentryPoint(this, level, pos, state);
      }
   }
}

package euphy.upo.sentrymechanicalarm.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

 
public class VirtualSentryArmBlockEntity extends SentryArmBlockEntity {
    private BlockPos virtualPos;
    public VirtualSentryArmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.virtualPos = pos;
    }

    public void setVirtualLevel(Level level) {
        this.level = level;
    }

    public void setVirtualPos(BlockPos pos) {
        this.virtualPos = pos;
 
    }
    @Override
    public BlockPos getBlockPos() {
        return this.virtualPos;
    }

    @Override
    public void tick() {
 
    }

    @Override
    public BlockState getBlockState() {
        return super.getBlockState(); 
    }
}
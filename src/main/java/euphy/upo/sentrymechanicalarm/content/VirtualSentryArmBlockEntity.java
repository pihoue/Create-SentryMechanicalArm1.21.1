package euphy.upo.sentrymechanicalarm.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class VirtualSentryArmBlockEntity extends SentryArmBlockEntity {
    private BlockPos virtualPos;
    private float contraptionSpeed;

    public VirtualSentryArmBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.virtualPos = pos;
    }

    public void setVirtualLevel(Level level) {
        this.level = level;
    }

    public void setVirtualPos(BlockPos pos) {
        this.virtualPos = pos;
    }

    public void setContraptionSpeed(float speed) {
        this.contraptionSpeed = speed;
    }

    public float getContraptionSpeed() {
        return contraptionSpeed;
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
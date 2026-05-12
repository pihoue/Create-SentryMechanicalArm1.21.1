package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BlazeFireControlBlockEntity extends SmartBlockEntity {

    private static final String[] EMOTICONS = {
            "(OwO)", "(>_<)", "^_^", "(='X'=)", "(*^▽^*)", "(¬_¬ )", "(ToT)", "(o_o)"
    };

    public String currentEmoticon = "";
    public int emoticonTimer = 0;
    public final int MAX_EMOTICON_TIME = 60;

    public int msgColor = 0xFFFFFF;
    public float msgOffsetX = 0;
    public float msgOffsetZ = 0;
    public final LerpedFloat headAngle = LerpedFloat.angular();
    public final LerpedFloat headAnimation = LerpedFloat.linear();
    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem() instanceof FireControlClipboardItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            notifyUpdate();
            if (level != null && !level.isClientSide) {
                notifyConnectedSentries(false);
            }
        }

    };

    protected LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> inventory);

    public BlazeFireControlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        inventory.deserializeNBT(compound.getCompound("Inventory"));
        this.currentEmoticon = compound.getString("Emoticon");
        this.emoticonTimer = compound.getInt("EmoticonTimer");
        this.msgOffsetX = compound.getFloat("MsgX");
        this.msgOffsetZ = compound.getFloat("MsgZ");
        if (compound.contains("MsgColor")) {
            this.msgColor = compound.getInt("MsgColor");
        } else {
            this.msgColor = 0xFFFFFF;
        }
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.put("Inventory", inventory.serializeNBT());
        compound.putString("Emoticon", currentEmoticon);
        compound.putInt("EmoticonTimer", emoticonTimer);
        compound.putFloat("MsgX", msgOffsetX);
        compound.putFloat("MsgZ", msgOffsetZ);
        compound.putInt("MsgColor", msgColor);

    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
    }

    public List<String> getTargetList() {
        List<String> targets = new ArrayList<>();
        ItemStack stack = inventory.getStackInSlot(0);

        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("TargetList", Tag.TAG_LIST)) {
            ListTag listTag = stack.getTag().getList("TargetList", Tag.TAG_STRING);
            for (Tag t : listTag) {
                targets.add(t.getAsString());
            }
        }
        return targets;
    }

    public void notifyConnectedSentries(boolean isRemoving) {
        if (level == null) return;

        BlockPos.betweenClosedStream(
                this.worldPosition.offset(-6, -6, -6),
                this.worldPosition.offset(6, 6, 6)
        ).forEach(pos -> {
            if (level.getBlockEntity(pos) instanceof SentryArmBlockEntity sentry) {
                BlockPos connectedPos = sentry.getConnectedFireControl();
                if (connectedPos != null && connectedPos.equals(this.worldPosition)) {
                    if (isRemoving) {
                        sentry.disconnectFireControl();
                    } else {
                        sentry.updateFromFireControl();
                    }
                }
            }
        });
    }


    @Override
    public void tick() {
        super.tick();

        if (emoticonTimer > 0) {
            emoticonTimer--;
        }

        if (!level.isClientSide) {
            if (emoticonTimer == 0 && !currentEmoticon.isEmpty()) {
                currentEmoticon = "";
                notifyUpdate();
            }
        }

        if (level.isClientSide) {
            tickAnimation();
            spawnIdleParticles();
        }
    }

    public void showRandomEmoticon() {
        if (level == null || level.isClientSide) return;

        this.currentEmoticon = EMOTICONS[level.random.nextInt(EMOTICONS.length)];
        this.emoticonTimer = 60;
        this.msgOffsetX = (level.random.nextFloat() - 0.5f) * 0.6f;
        this.msgOffsetZ = (level.random.nextFloat() - 0.5f) * 0.6f;
        this.msgColor = Mth.hsvToRgb(level.random.nextFloat(), 0.8f, 1.0f);

        notifyUpdate();
    }


    @OnlyIn(Dist.CLIENT)
    protected void tickAnimation() {
        float target = 0;
        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null && !player.isInvisible()) {
            double dx = player.getX() - (getBlockPos().getX() + 0.5);
            double dz = player.getZ() - (getBlockPos().getZ() + 0.5);
            target = AngleHelper.deg(-Mth.atan2(dz, dx)) - 90;
        }

        target = headAngle.getValue() + AngleHelper.getShortestAngleDiff(headAngle.getValue(), target);
        headAngle.chase(target, .25f, Chaser.exp(5));
        headAngle.tickChaser();

        headAnimation.chase(0, .25f, Chaser.exp(.25f));
        headAnimation.tickChaser();
    }

    @OnlyIn(Dist.CLIENT)
    protected void spawnIdleParticles() {
 
        RandomSource random = level.getRandom();
        if (random.nextInt(7) == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    worldPosition.getX() + 0.5 + random.nextGaussian() * 0.3,
                    worldPosition.getY() + 0.5 + random.nextGaussian() * 0.5,
                    worldPosition.getZ() + 0.5 + random.nextGaussian() * 0.3,
                    0, 0, 0);
        }
    }
    public boolean isWhitelist() {
        ItemStack stack = inventory.getStackInSlot(0);
        if (!stack.isEmpty() && stack.getItem() instanceof FireControlClipboardItem) {
            CompoundTag tag = stack.getOrCreateTag();
            return tag.getBoolean("WhitelistMode");
        }
        return false;
    }


}
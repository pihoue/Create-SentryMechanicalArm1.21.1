package euphy.upo.sentrymechanicalarm.content;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import euphy.upo.sentrymechanicalarm.util.ItemNBTHelper;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

public class BlazeFireControlBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

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

    private int focusedEntityId = -1;
    private int focusTimer = 0;
    public static final int FOCUS_DURATION = 600;
    private boolean hasBoundScope = false;
    private int markedEntityId = -1;

    public boolean hasBoundScope() { return hasBoundScope; }
    public void setHasBoundScope(boolean val) {
        if (hasBoundScope != val) {
            hasBoundScope = val;
            setChanged();
            sendData();
        }
    }

    public int getFocusedEntityId() {
        if (focusTimer > 0) return focusedEntityId;
        return -1;
    }

    public void setFocusedEntity(int entityId) {
        this.focusedEntityId = entityId;
        this.focusTimer = FOCUS_DURATION;
        setChanged();
        sendData();
    }

    public Set<Integer> getMarkedEntityIds() {
        return markedEntityId == -1 ? Collections.emptySet() : Collections.singleton(markedEntityId);
    }

    public void setMarkedEntityId(int entityId) {
        this.markedEntityId = entityId;
        clearMarkedPos();
        setChanged();
        sendData();
    }

    public void clearMarkedEntity() {
        if (markedEntityId != -1) {
            markedEntityId = -1;
            setChanged();
            sendData();
        }
    }

    private Vec3 markedWorldPos = null;
    private int markedContraptionEntityId = -1;
    private BlockPos markedLocalPos = null;

    public Vec3 getMarkedWorldPos() { return markedWorldPos; }
    public int getMarkedContraptionEntityId() { return markedContraptionEntityId; }
    public BlockPos getMarkedLocalPos() { return markedLocalPos; }

    public void setMarkedPos(Vec3 worldPos, int contraptionEntityId, BlockPos localPos) {
        this.markedWorldPos = worldPos;
        this.markedContraptionEntityId = contraptionEntityId;
        this.markedLocalPos = localPos;
        clearMarkedEntity();
        setChanged();
        sendData();
    }

    public void clearMarkedPos() {
        if (markedWorldPos != null) {
            markedWorldPos = null;
            markedContraptionEntityId = -1;
            markedLocalPos = null;
            setChanged();
            sendData();
        }
    }

    public BlazeFireControlBlockEntity(BlockPos pos, BlockState state) {
        super(SentryRegistry.BLAZE_FIRE_CONTROL_BE.get(), pos, state);
    }

    public IItemHandler getItemHandler() {
        return inventory;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void write(CompoundTag compound, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.putString("Emoticon", currentEmoticon);
        compound.putInt("EmoticonTimer", emoticonTimer);
        compound.putFloat("MsgX", msgOffsetX);
        compound.putFloat("MsgZ", msgOffsetZ);
        compound.putInt("MsgColor", msgColor);
        compound.putInt("FocusedEntityId", focusedEntityId);
        compound.putInt("FocusTimer", focusTimer);
        compound.putBoolean("HasBoundScope", hasBoundScope);
        if (markedWorldPos != null) {
            compound.putDouble("MarkedPosX", markedWorldPos.x);
            compound.putDouble("MarkedPosY", markedWorldPos.y);
            compound.putDouble("MarkedPosZ", markedWorldPos.z);
            compound.putInt("MarkedContraptionId", markedContraptionEntityId);
            if (markedLocalPos != null) {
                compound.putLong("MarkedLocalPos", markedLocalPos.asLong());
            }
        }
    }

    @Override
    protected void read(CompoundTag compound, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        this.currentEmoticon = compound.getString("Emoticon");
        this.emoticonTimer = compound.getInt("EmoticonTimer");
        this.msgOffsetX = compound.getFloat("MsgX");
        this.msgOffsetZ = compound.getFloat("MsgZ");
        if (compound.contains("MsgColor")) {
            this.msgColor = compound.getInt("MsgColor");
        } else {
            this.msgColor = 0xFFFFFF;
        }
        if (compound.contains("FocusedEntityId")) {
            focusedEntityId = compound.getInt("FocusedEntityId");
        }
        if (compound.contains("FocusTimer")) {
            focusTimer = compound.getInt("FocusTimer");
        }
        if (compound.contains("HasBoundScope")) {
            hasBoundScope = compound.getBoolean("HasBoundScope");
        }
        markedEntityId = -1;
        if (compound.contains("MarkedPosX")) {
            markedWorldPos = new Vec3(compound.getDouble("MarkedPosX"), compound.getDouble("MarkedPosY"), compound.getDouble("MarkedPosZ"));
            markedContraptionEntityId = compound.getInt("MarkedContraptionId");
            if (compound.contains("MarkedLocalPos")) {
                markedLocalPos = BlockPos.of(compound.getLong("MarkedLocalPos"));
            } else {
                markedLocalPos = null;
            }
        } else {
            markedWorldPos = null;
            markedContraptionEntityId = -1;
            markedLocalPos = null;
        }
    }

    public List<String> getTargetList() {
        List<String> targets = new ArrayList<>();
        ItemStack stack = inventory.getStackInSlot(0);

        CompoundTag tag = ItemNBTHelper.getTag(stack);
        if (!stack.isEmpty() && tag.contains("TargetList", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("TargetList", Tag.TAG_STRING);
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
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        ChatFormatting boundColor = hasBoundScope ? ChatFormatting.GREEN : ChatFormatting.GRAY;
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("overlay.sentrymechanicalarm.scope_status")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.translatable(hasBoundScope
                        ? "message.sentrymechanicalarm.scope_bound"
                        : "message.sentrymechanicalarm.scope_not_bound")
                        .withStyle(boundColor)));

        int sentryCount = 0;
        if (level != null) {
            for (BlockPos bp : BlockPos.betweenClosed(
                    worldPosition.offset(-6, -6, -6),
                    worldPosition.offset(6, 6, 6))) {
                if (level.getBlockEntity(bp) instanceof SentryArmBlockEntity sentry) {
                    BlockPos connected = sentry.getConnectedFireControl();
                    if (connected != null && connected.equals(worldPosition)) {
                        sentryCount++;
                    }
                }
            }
        }
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("overlay.sentrymechanicalarm.connected_sentries")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(sentryCount))
                        .withStyle(ChatFormatting.AQUA)));

        if (!inventory.getStackInSlot(0).isEmpty()) {
            boolean whitelist = isWhitelist();
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable("overlay.sentrymechanicalarm.list_mode")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable(whitelist
                            ? "item.sentrymechanicalarm.fire_control_clipboard.whitelist"
                            : "item.sentrymechanicalarm.fire_control_clipboard.blacklist")
                            .withStyle(whitelist ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }

        return true;
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
            CompoundTag tag = ItemNBTHelper.getOrCreateTag(stack);
            return tag.getBoolean("WhitelistMode");
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (focusTimer > 0) {
            focusTimer--;
            if (focusTimer == 0) {
                focusedEntityId = -1;
                notifyConnectedSentries(false);
            }
        }

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

}

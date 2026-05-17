package euphy.upo.sentrymechanicalarm.content;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import euphy.upo.sentrymechanicalarm.SentryMechanicalArm;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Iterator;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = SentryMechanicalArm.MODID)
public class SentryEventHandler {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String SENTRY_KILL_TAG = "SentryKillerPos";

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (event.getShooter().level().isClientSide()) return;
        if (event.getShooter() instanceof FakePlayer fp && fp.getGameProfile().getName().startsWith("Sentry_")) {
            SentryFakePlayer.markFired(fp);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity killedEntity = event.getEntity();
        if (killedEntity.level().isClientSide()) return;

        Entity attacker = event.getSource().getEntity();

        if (attacker instanceof FakePlayer fp) {
            SentryArmBlockEntity arm = SentryFakePlayer.getArmFromPlayer(fp);
            if (arm != null) {
                killedEntity.getPersistentData().putLong(SENTRY_KILL_TAG, arm.getBlockPos().asLong());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag data = entity.getPersistentData();

        if (data.contains(SENTRY_KILL_TAG) && entity.level() instanceof ServerLevel serverLevel) {
            BlockPos armPos = BlockPos.of(data.getLong(SENTRY_KILL_TAG));
            BlockEntity be = serverLevel.getBlockEntity(armPos);

            if (be instanceof SentryArmBlockEntity arm) {
                int originalXp = event.getDroppedExperience();
                if (originalXp <= 0) return;

                BlockPos targetPos = arm.getBlockPos().below();
                BlockEntity targetBE = serverLevel.getBlockEntity(targetPos);

                if (targetBE != null) {
                    IItemHandler handler = serverLevel.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, Direction.UP);

                    if (handler != null) {
                        event.setDroppedExperience(0);
                        int nuggetCount = (int) Math.ceil(originalXp / 3.0f);
                        ItemStack nuggets = new ItemStack(Items.EXPERIENCE_BOTTLE, nuggetCount);

                        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, nuggets, false);

                        if (!remainder.isEmpty()) {
                            ItemEntity itemEntity = new ItemEntity(serverLevel, entity.getX(), entity.getY(), entity.getZ(), remainder);
                            serverLevel.addFreshEntity(itemEntity);
                        }
                        return;
                    }
                }

            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag data = entity.getPersistentData();

        if (data.contains(SENTRY_KILL_TAG) && entity.level() instanceof ServerLevel serverLevel) {
            BlockPos armPos = BlockPos.of(data.getLong(SENTRY_KILL_TAG));
            BlockEntity be = serverLevel.getBlockEntity(armPos);

            if (be instanceof SentryArmBlockEntity arm) {
                Collection<ItemEntity> drops = event.getDrops();
                Iterator<ItemEntity> iterator = drops.iterator();
                while (iterator.hasNext()) {
                    ItemEntity dropEntity = iterator.next();
                    ItemStack stack = dropEntity.getItem();

                    ItemStack remainder = tryInsertIntoContainer(arm, stack);

                    if (remainder.isEmpty()) {
                        iterator.remove();
                    } else {
                        dropEntity.setItem(remainder);
                    }
                }
            }
        }
    }

    private static ItemStack tryInsertIntoContainer(SentryArmBlockEntity arm, ItemStack stack) {
        if (stack.isEmpty()) return stack;
        if (arm.getLevel() == null) return stack;

        BlockPos targetPos = arm.getBlockPos().below();
        BlockEntity targetBE = arm.getLevel().getBlockEntity(targetPos);

        if (targetBE != null) {
            IItemHandler handler = arm.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, targetPos, Direction.UP);
            if (handler != null) {
                return ItemHandlerHelper.insertItemStacked(handler, stack, false);
            }
        }
        return stack;
    }
}

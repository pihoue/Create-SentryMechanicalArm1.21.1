package euphy.upo.sentrymechanicalarm.ponder.scene;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import euphy.upo.sentrymechanicalarm.content.BlazeFireControlBlockEntity;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class FireControlScene {

    public static void control(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("fire_control", "火控台");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos sentryPos = util.grid().at(1, 1, 2);
        BlockPos firePose = util.grid().at(3, 1, 2);
        BlockPos villager = util.grid().at(3, 1, 2);
        BlockPos villagerPos = util.grid().at(4, 0, 2);
        Vec3 villagertext = util.vector().of(3.5, 2, 0.5);

        Selection sentrySelect = util.select().position(sentryPos);
        Selection fireControlSelect = util.select().position(firePose);

        ItemStack wrench = new ItemStack(AllItems.WRENCH);
        ItemStack list =  new ItemStack(SentryRegistry.FIRE_CONTROL_CLIPBOARD);

        scene.idle(20);

        scene.world().showSection(sentrySelect, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("哨戒动力臂默认只会攻击敌对生物")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(70);

        scene.world().showSection(fireControlSelect, Direction.DOWN);

        scene.overlay().showText(60)
                .text("使用火控台，可以指定攻击特定的生物")
                .pointAt(util.vector().topOf(firePose))
                .placeNearTarget().attachKeyFrame();
        scene.idle(70);

        scene.overlay().showControls(firePose.getCenter(), Pointing.RIGHT, 50)
                .rightClick()
                .withItem(wrench);
        scene.idle(7);
        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", fireControlSelect, 50);
        scene.overlay().showText(50)
                .text("扳手右键二者其一……")
                .pointAt(util.vector().topOf(firePose))
                .placeNearTarget();
        scene.idle(60);

        scene.overlay().showControls(sentryPos.getCenter(), Pointing.RIGHT, 30)
                .rightClick()
                .withItem(wrench);
        scene.idle(7);
        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", sentrySelect, 30);
        scene.overlay().showText(30)
                .text("再右键另一个")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(40);

        scene.overlay().showText(60)
                .text("即可在二者间建立连接")
                .pointAt(util.vector().topOf(firePose))
                .placeNearTarget();
        for (int i = 0; i < 18; i++) {
            drawGreenLine(scene, sentryPos, firePose, util, 8);
            scene.idle(4);
        }
        scene.idle(20);

        scene.world().setKineticSpeed(util.select().everywhere(), 128);

        ElementLink<EntityElement> villagerLink = scene.world().createEntity(w -> {
            Villager entity = EntityType.VILLAGER.create(w);
            Vec3 p = util.vector().of(3.5, 1, 0.5);
            entity.setPos(p.x, p.y, p.z);
            entity.xo = p.x;
            entity.yo = p.y;
            entity.zo = p.z;
            float rotation = 180.0f;
            entity.yRotO = rotation;
            entity.setYRot(rotation);
            entity.yHeadRotO = rotation;
            entity.yHeadRot = rotation;

            return entity;
        });

        scene.idle(20);


        scene.overlay().showControls(villagerPos.east().getCenter(), Pointing.RIGHT, 60)
                .rightClick()
                .withItem(list);
        scene.idle(7);
        scene.overlay().showText(60)
                .text("使用跨境追缉许可右键生物，即可将其加入名单，包括玩家")
                .pointAt(villagertext)
                .placeNearTarget().attachKeyFrame();
        scene.idle(70);

        scene.overlay().showControls(firePose.east().getCenter(), Pointing.RIGHT, 60)
                .rightClick()
                .withItem(list);
        scene.overlay().showText(60)
                .text("将名单交付火控台……")
                .pointAt(util.vector().topOf(firePose))
                .placeNearTarget();
        scene.idle(30);
        scene.world().modifyBlockEntity(firePose, BlazeFireControlBlockEntity.class, be -> {
            be.inventory.setStackInSlot(0, list);
        });
        scene.effects().indicateSuccess(firePose);
        scene.idle(40);

        scene.overlay().showText(80)
                .text("与之相连的哨戒动力臂便只会攻击名单上的生物")
                .pointAt(sentryPos.getCenter())
                .placeNearTarget();
        scene.idle(40);


        Vec3 villagerVec = util.vector().of(3.5, 1, 0.5);
        Vec3 targetVec = villagerVec.add(0, 1.5, 0);
        Vec3 sentryCenter = util.vector().centerOf(sentryPos);
        Vec3 diff = targetVec.subtract(sentryCenter);
        double yawRad = Math.atan2(diff.z, diff.x);
        double yawDeg = Math.toDegrees(yawRad);
        float targetBaseAngle = (float) (yawDeg);

        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        double pitchRad = Math.atan2(diff.y, horizontalDist);
        double pitchDeg = Math.toDegrees(pitchRad);

        float targetHeadAngle = (float) (-pitchDeg);
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {

            be.idleTargetYaw = targetBaseAngle;
            be.idleTargetPitch = targetHeadAngle;
        });

        scene.idle(30);

        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.world().modifyEntity(villagerLink, entity -> {
            if (entity instanceof LivingEntity living) {
                living.hurtTime = 60;
                living.animateHurt(0);
            }
        });
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.world().modifyEntity(villagerLink, entity -> {
            if (entity instanceof LivingEntity living) {
                living.hurtTime = 60;
                living.animateHurt(0);
            }
        });
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);
        scene.idle(2);
        fire(scene, sentryPos, villager, util);

        scene.world().modifyEntity(villagerLink, entity -> {
            if (entity instanceof LivingEntity living) {
                living.setHealth(0.0f);

                living.deathTime = 1;
            }
        });
        scene.idle(30);

        scene.markAsFinished();

    }

    private static void drawGreenLine(CreateSceneBuilder scene, BlockPos startPos, BlockPos endPos, SceneBuildingUtil util, int density) {
        Vec3 startVec = util.vector().centerOf(startPos);
        Vec3 endVec = util.vector().centerOf(endPos);
        Vec3 difference = endVec.subtract(startVec);
        double distance = difference.length();

        int particleCount = (int) (distance * density);
        if (particleCount < 1) particleCount = 1;

        DustParticleOptions greenDust = new DustParticleOptions(new Vector3f(0.0f, 1.0f, 0.0f), 1.0f);

        ParticleEmitter greenEmitter = (level, x, y, z) -> {
            level.addParticle(greenDust, x, y, z, 0, 0, 0);
        };

        for (int i = 0; i <= particleCount; i++) {
            double t = (double) i / particleCount;
            Vec3 currentPos = startVec.add(difference.scale(t));
            scene.effects().emitParticles(currentPos, greenEmitter, 1.0f, 1);
        }
    }


    private static void fire(CreateSceneBuilder scene, BlockPos sentryPos, BlockPos targetPos, SceneBuildingUtil util) {
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.setLastShootTime(System.currentTimeMillis());
            be.setShellEjected();
        });
    }
}

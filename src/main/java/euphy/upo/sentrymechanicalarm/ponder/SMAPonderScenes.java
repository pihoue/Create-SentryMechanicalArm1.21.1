package euphy.upo.sentrymechanicalarm.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;

public class SMAPonderScenes {

    public static void sentryArmIntro(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("sentry_mechanical_arm_intro", "Sentry Mechanical Arm");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().everywhere(), Direction.UP);

        scene.idle(20);

        scene.overlay().showText(80)
                .text("The Sentry Mechanical Arm is a robotic arm that can wield TaCZ firearms")
                .pointAt(util.vector().topOf(2, 1, 2));

        scene.idle(100);

        scene.markAsFinished();
    }

    public static void fireControlIntro(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("blaze_fire_control_intro", "Blaze Fire Control");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().everywhere(), Direction.UP);

        scene.idle(20);

        scene.overlay().showText(80)
                .text("The Blaze Fire Control block provides targeting for the Mechanical Arm")
                .pointAt(util.vector().topOf(2, 1, 2));

        scene.idle(100);

        scene.overlay().showText(80)
                .text("Right-click with a Fire Control Clipboard to configure")
                .pointAt(util.vector().topOf(2, 1, 2));

        scene.idle(100);

        scene.markAsFinished();
    }
}
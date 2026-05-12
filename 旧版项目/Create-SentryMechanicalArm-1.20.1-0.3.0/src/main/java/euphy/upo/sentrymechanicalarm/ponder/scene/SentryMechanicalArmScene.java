package euphy.upo.sentrymechanicalarm.ponder.scene;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.foundation.ponder.element.BeltItemElement;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public class SentryMechanicalArmScene {

    public static void introducing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("sentry_arm_intro", "哨兵机械臂简介");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos sentryPos = util.grid().at(2, 1, 2);
        BlockPos cog_1 = util.grid().at(1, 1, 2);
        BlockPos cog_2 = util.grid().at(0, 1, 3);
        Selection sentrySelect = util.select().position(sentryPos);
        Selection cogS1 = util.select().position(cog_1);
        Selection cogS2 = util.select().position(cog_2);

        BlockPos targetPos = util.grid().at(2, 2, 2);

        Item ammoItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "ammo_box"));
        ItemStack ammoStack = new ItemStack(ammoItem);

        scene.idle(20);

        ElementLink<WorldSectionElement> sentryLink = scene.world().showIndependentSection(sentrySelect, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(50)
                .text("哨戒动力臂是一种强大的火力平台")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(60);

        scene.overlay().showText(60)
                .text("手持一把来自TacZ的枪械，按[O]即可将其部署")
                .pointAt(util.vector().topOf(sentryPos))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);

        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> be.setHeldItem(createGun("tacz:qbz_191")));
        scene.effects().indicateSuccess(sentryPos.above());
        scene.idle(30);

        scene.idle(10);

        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> be.setHeldItem(createGun("tacz:ump45")));
        scene.effects().indicateSuccess(sentryPos.above());
        scene.idle(20);

        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> be.setHeldItem(createGun("tacz:ak47")));
        scene.effects().indicateSuccess(sentryPos.above());
        scene.idle(20);

        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> be.setHeldItem(createGun("tacz:scar_h")));
        scene.effects().indicateSuccess(sentryPos.above());
        scene.idle(20);

        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> be.setHeldItem(createGun("tacz:minigun")));
        scene.effects().indicateSuccess(sentryPos.above());
        scene.idle(35);

        scene.overlay().showText(60)
                .text("手持弹药盒右键装载，SHIFT右键可取下")
                .pointAt(util.vector().topOf(sentryPos))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);

        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.attachedAmmoBoxes.set(1, ammoStack);
        });
        scene.idle(30);

        ElementLink<WorldSectionElement> cog1Link = scene.world().showIndependentSection(cogS1, Direction.DOWN);
        ElementLink<WorldSectionElement> cog2Link = scene.world().showIndependentSection(cogS2, Direction.DOWN);
        scene.idle(20);

        scene.world().setKineticSpeed(util.select().everywhere(), -64);
        scene.world().setKineticSpeed(cogS1, 64);
        scene.idle(12);

        scene.overlay().showText(60)
                .text("接入应力，它就会开始工作，自动索敌所有的敌对生物")
                .pointAt(util.vector().topOf(sentryPos))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(70);

        for (int i = 0; i < 10; i++) {
            fire(scene, sentryPos, targetPos, util);
            scene.idle(3);
        }
        scene.idle(25);

        scene.world().moveSection(sentryLink, util.vector().of(0, 1, 0), 20);
        scene.world().moveSection(cog1Link, util.vector().of(0, 1, 0), 20);
        scene.world().moveSection(cog2Link, util.vector().of(0, 1, 0), 20);
        scene.idle(25);

        BlockPos tempBarrelSpawnPos = util.grid().at(4, 1, 2);
        scene.world().setBlock(tempBarrelSpawnPos, Blocks.BARREL.defaultBlockState(), false);

        ElementLink<WorldSectionElement> barrelLink = scene.world().showIndependentSection(util.select().position(tempBarrelSpawnPos), Direction.NORTH);

        scene.world().moveSection(barrelLink, util.vector().of(-2, 0, 0), 0);
        scene.idle(15);

        scene.overlay().showText(120)
                .text("若下方有容器，则会收集掉落物和经验并放入下方的容器中。")
                .pointAt(util.vector().blockSurface(sentryPos, Direction.WEST))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(40);

        Vec3 barrelTop = util.vector().topOf(sentryPos);

        scene.overlay().showControls(barrelTop.add(0, -1.5, 0), Pointing.UP, 40)
                .withItem(new ItemStack(Items.ROTTEN_FLESH));
        scene.idle(15);

        scene.overlay().showControls(barrelTop.add(0.5, -1, 0), Pointing.RIGHT, 40)
                .withItem(new ItemStack(Items.BONE));
        scene.idle(40);

        scene.markAsFinished();
    }

    public static void supplying(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("sentry_arm_supply", "后勤供应");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.scaleSceneView(0.8f);

        BlockPos sentryPos = util.grid().at(1, 1, 2);
        BlockPos supply_1 = util.grid().at(4, 1, 3);
        BlockPos supply_2 = util.grid().at(2, 1, 5);
        BlockPos cog_1 = util.grid().at(2, 1, 2);
        BlockPos cog_2 = util.grid().at(2, 1, 3);
        BlockPos cog_3 = util.grid().at(3, 1, 4);
        BlockPos depotPos = util.grid().at(1, 1, 4);
        BlockPos beltStart = util.grid().at(4, 1, 7);
        BlockPos beltEnd = util.grid().at(3, 1, 2);
        BlockPos deployerPos = util.grid().at(7, 3, 4);
        BlockPos deployerPos_2 = util.grid().at(7, 3, 5);
        BlockPos beltPos = util.grid().at(7, 1, 4);
        Selection sentrySelect = util.select().position(sentryPos);
        Selection supplySelect_1 = util.select().position(supply_1);
        Selection supplySelect_2 = util.select().position(supply_2);
        Selection cogS1 = util.select().position(cog_1);
        Selection cogS2 = util.select().position(cog_2);
        Selection cogS3 = util.select().position(cog_3);
        Selection supplyLine_1 = util.select().fromTo(2, 1, 2, 8, 3, 8);
        Selection supplyLine_2 = util.select().fromTo(1, 1, 4, 1, 1, 7);
        Selection S1Selection = util.select().fromTo(4, 1, 3, 4, 2, 3);
        Selection S2Selection = util.select().fromTo(2, 1, 5, 2, 2, 5);
        Selection deployerSelection = util.select().position(deployerPos);
        Selection deployerSelection_2 = util.select().position(deployerPos_2);

        Item ammoItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "ammo_box"));
        ItemStack ammoStack = new ItemStack(ammoItem);
        CompoundTag tag = ammoStack.getOrCreateTag();
        tag.putString("AmmoId", "tacz:58x42");
        tag.putInt("AmmoCount", 30);

        Item ammo = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "ammo"));
        ItemStack ammo_Stack = new ItemStack(ammo, 40);
        ammo_Stack.getOrCreateTag().putString("AmmoId", "tacz:58x42");
 
        scene.idle(20);

        scene.world().showSection(sentrySelect, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(50)
                .text("如果想要哨戒动力臂拥有持久的火力……")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(60);


        scene.idle(15);

        scene.world().showSection(supplyLine_1, Direction.DOWN);
        scene.world().showSection(supplyLine_2, Direction.DOWN);
        scene.overlay().showText(60)
                .text("你需要为它打造一套后勤供应")
                .pointAt(util.vector().topOf(sentryPos))
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(80);

        scene.world().setKineticSpeed(util.select().everywhere(), -64);
        scene.world().setKineticSpeed(cogS1, 64);
        scene.world().setKineticSpeed(cogS3, 64);
        scene.world().setKineticSpeed(util.select().fromTo(7, 1, 2, 7, 1, 6), 64);
        scene.world().setKineticSpeed(util.select().fromTo(7, 1, 7, 2, 1, 7), 64);
        scene.idle(10);

        scene.world().createItemOnBelt(beltStart, Direction.UP, ammoStack);

        scene.idle(40);

        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", S2Selection, 80);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("动力臂和漏斗可以给哨戒动力臂供弹")
                .pointAt(util.vector().blockSurface(supply_2, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);


        scene.world().setKineticSpeed(supplySelect_2, -64);
        scene.idle(5);
        scene.world().instructArm(supply_2, ArmBlockEntity.Phase.MOVE_TO_INPUT, ItemStack.EMPTY, 0);
        scene.idle(24);
        scene.world().removeItemsFromBelt(depotPos);
        scene.world().instructArm(supply_2, ArmBlockEntity.Phase.SEARCH_OUTPUTS, ammoStack, 0);
        scene.idle(24);
        scene.world().instructArm(supply_2, ArmBlockEntity.Phase.MOVE_TO_OUTPUT, ammoStack, 0);
        scene.idle(24);
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.attachedAmmoBoxes.set(1, ammoStack);
        });
        scene.world().instructArm(supply_2, ArmBlockEntity.Phase.SEARCH_INPUTS, ItemStack.EMPTY, 0);
        scene.idle(25);

        for (int i = 0; i < 16; i++) {
            fire(scene, sentryPos, util.grid().at(4, 2, 1), util);
            scene.idle(2);
        }
        scene.idle(20);

        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", S1Selection, 80);
        scene.overlay().showText(45)
                .text("动力臂会自动取出打空的弹药盒")
                .pointAt(util.vector().blockSurface(supply_1, Direction.EAST))
                .placeNearTarget();
        scene.idle(55);

        scene.world().setKineticSpeed(supplySelect_1, -64);
        scene.idle(5);
        scene.world().instructArm(supply_1, ArmBlockEntity.Phase.MOVE_TO_INPUT, ItemStack.EMPTY, 0);
        scene.idle(24);
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.attachedAmmoBoxes.set(1, ItemStack.EMPTY);
        });
        scene.world().instructArm(supply_1, ArmBlockEntity.Phase.SEARCH_OUTPUTS, ammoStack, 0);
        scene.idle(24);
        scene.world().instructArm(supply_1, ArmBlockEntity.Phase.MOVE_TO_OUTPUT, ammoStack, 0);
        scene.idle(24);
        scene.world().createItemOnBelt(beltEnd, Direction.UP, ammoStack);
        scene.world().instructArm(supply_1, ArmBlockEntity.Phase.SEARCH_INPUTS, ItemStack.EMPTY, 0);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.GREEN, "frame_1", deployerSelection, 60);
        scene.overlay().showText(80)
                .text("持有弹药的机械手会为下方的弹药盒装弹")
                .pointAt(util.vector().blockSurface(deployerPos, Direction.EAST))
                .placeNearTarget().attachKeyFrame();
        scene.idle(20);

        scene.world().modifyBlockEntityNBT(deployerSelection, DeployerBlockEntity.class, nbt -> {
            nbt.put("HeldItem", ammo_Stack.serializeNBT());
        });
        scene.world().modifyBlockEntityNBT(deployerSelection_2, DeployerBlockEntity.class, nbt -> {
            nbt.put("HeldItem", ammo_Stack.serializeNBT());
        });

        scene.world().setKineticSpeed(deployerSelection, -64);

        scene.world().moveDeployer(deployerPos, 1.0f, 25);
        scene.idle(26);
        scene.world().moveDeployer(deployerPos, -1.0f, 25);
        scene.effects().indicateSuccess(beltPos);
        scene.world().removeItemsFromBelt(deployerPos.below(2));
        ElementLink<BeltItemElement> itemLink =scene.world().createItemOnBelt(deployerPos.below(2), Direction.UP, ammoStack);
        scene.world().stallBeltItem(itemLink, true);
        scene.idle(15);
        scene.world().stallBeltItem(itemLink, false);
        scene.idle(10);

        scene.world().moveDeployer(deployerPos_2, 1.0f, 25);
        scene.idle(26);
        scene.world().moveDeployer(deployerPos_2, -1.0f, 25);

        scene.world().removeItemsFromBelt(deployerPos_2.below(2));
        ElementLink<BeltItemElement> itemLink_2 =scene.world().createItemOnBelt(deployerPos_2.below(2), Direction.UP, ammoStack);
        scene.world().stallBeltItem(itemLink_2, true);
        scene.idle(15);
        scene.world().stallBeltItem(itemLink_2, false);
        scene.idle(55);

        scene.overlay().showText(80)
                .text("如此，哨戒动力臂便有了源源不断的火力")
                .pointAt(util.vector().topOf(sentryPos))
                .placeNearTarget();
        scene.idle(80);

        scene.markAsFinished();

    }

    public static void ammo(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("sentry_arm_ammo", "子弹生产");
        scene.configureBasePlate(0, 0, 10);
        scene.showBasePlate();
        scene.scaleSceneView(0.7f);

        BlockPos depotPos = util.grid().at(4, 1, 3);
        BlockPos saw = util.grid().at(8, 1, 4);
        BlockPos beltStart = util.grid().at(8, 1, 2);
        BlockPos d1 = util.grid().at(7, 3, 7);
        BlockPos d2 = util.grid().at(6, 3, 7);
        BlockPos d3 = util.grid().at(5, 3, 7);
        BlockPos d4 = util.grid().at(4, 3, 7);
        BlockPos p5 = util.grid().at(3, 3, 7);

        Selection depotSelect = util.select().position(depotPos);
        Selection deployerSelect = util.select().position(depotPos.above(2));
        Selection shaftSelect = util.select().fromTo(3, 3, 3,0, 3, 3);
        Selection inPut = util.select().fromTo(8, 2, 6,8, 1, 1);
        Selection process = util.select().fromTo(8, 1, 7,0, 3, 7);
        Selection out = util.select().fromTo(1, 1, 7,1, 1, 2);
        Selection sawSelect = util.select().position(saw);
        Selection d1s = util.select().position(d1);
        Selection d2s = util.select().position(d2);
        Selection d3s = util.select().position(d3);
        Selection d4s = util.select().position(d4);

        Item ammo = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "ammo"));
        ItemStack ammo_Stack = new ItemStack(ammo, 40); 
        ammo_Stack.getOrCreateTag().putString("AmmoId", "tacz:58x42"); 
        ItemStack sheet =  new ItemStack(AllItems.COPPER_SHEET);
        ItemStack unfinishedAmmo = new ItemStack (SentryRegistry.UNFINISHED_AMMO);
        ItemStack filter = new ItemStack (AllItems.FILTER);
        ItemStack gunPowder = new ItemStack(Items.GUNPOWDER);
        Class<MechanicalPressBlockEntity> type = MechanicalPressBlockEntity.class;



        scene.idle(20);
        scene.world().showSection(depotSelect, Direction.DOWN);


        scene.overlay().showText(50)
                .text("要生产子弹，你需要先获得子弹模板")
                .pointAt(util.vector().topOf(depotPos))
                .placeNearTarget();
        scene.idle(60);
        scene.world().showSection(deployerSelect, Direction.DOWN);
        scene.world().showSection(shaftSelect, Direction.DOWN);
        scene.idle(20);
        scene.world().setKineticSpeed(deployerSelect, -32);
        scene.world().setKineticSpeed(shaftSelect, -32);

        scene.idle(20);

        scene.world().modifyBlockEntityNBT(deployerSelect, DeployerBlockEntity.class, nbt -> {
            nbt.put("HeldItem", ammo_Stack.serializeNBT());
        });
        scene.world().createItemOnBeltLike(depotPos, Direction.UP, sheet);
        scene.idle(20);

        scene.overlay().showControls(depotPos.getCenter(), Pointing.UP, 30).withItem(sheet);

        scene.idle(20);


        scene.overlay().showText(60)
                .text("将铜板放在置物台上，子弹放在机械手上……")
                .pointAt(util.vector().topOf(depotPos))
                .placeNearTarget().attachKeyFrame();
        scene.idle(70);

        scene.world().moveDeployer(depotPos.above(2), 1.0f, 25);
        scene.idle(26);
        scene.effects().indicateSuccess(depotPos.above());
        scene.world().moveDeployer(depotPos.above(2), -1.0f, 25);
        scene.world().removeItemsFromBelt(depotPos);
        scene.world().createItemOnBeltLike(depotPos, Direction.UP, unfinishedAmmo);
        scene.idle(25);
        scene.overlay().showText(60)
                .text("即可得到对应的子弹模板")
                .pointAt(util.vector().topOf(depotPos))
                .placeNearTarget();
        scene.idle(80);

        scene.world().removeItemsFromBelt(depotPos);
        scene.world().hideSection(deployerSelect, Direction.UP);
        scene.world().hideSection(shaftSelect, Direction.UP);
        scene.world().hideSection(depotSelect, Direction.UP);

        scene.idle(30);

        scene.world().showSection(inPut, Direction.DOWN);

        scene.idle(30);

        scene.overlay().showText(60)
                .text("你需要一个动力锯来处理原材料")
                .pointAt(util.vector().topOf(saw))
                .placeNearTarget().attachKeyFrame();
        scene.idle(70);

        scene.world().modifyBlockEntityNBT(sawSelect, SawBlockEntity.class, nbt -> {
            nbt.put("Filter", filter.serializeNBT());
        });
        scene.overlay().showControls(saw.getCenter(), Pointing.UP, 150).withItem(filter);
        scene.overlay().showFilterSlotInput(util.vector().blockSurface(saw, Direction.UP), Direction.DOWN, 10);
        scene.overlay().showText(160)
                .text("把子弹模板放入列表过滤器，并勾选匹配物品属性，插入动力锯的过滤槽中")
                .pointAt(util.vector().topOf(saw))
                .placeNearTarget();
        scene.idle(170);

        scene.overlay().showText(60)
                .text("之后的铜板都会被加工成这种子弹模板")
                .pointAt(util.vector().topOf(saw))
                .placeNearTarget();
        scene.idle(60);

        scene.world().setKineticSpeed(util.select().everywhere(), 48);
        scene.world().setKineticSpeed(sawSelect, -48);
        scene.idle(10);


        scene.world().setFilterData(util.select().position(saw), SawBlockEntity.class, unfinishedAmmo);


        scene.world().createItemOnBelt(beltStart, Direction.UP, sheet);

        scene.idle(40);

        scene.world().setFilterData(util.select().position(saw), SawBlockEntity.class, filter);

        scene.world().showSection(process, Direction.DOWN);
        scene.world().setKineticSpeed(out, -48);
        scene.world().showSection(out, Direction.DOWN);
        scene.idle(10);


        scene.world().modifyBlockEntityNBT(d1s, DeployerBlockEntity.class, nbt -> {
            nbt.put("HeldItem", sheet.serializeNBT());
        });
        scene.world().modifyBlockEntityNBT(d2s, DeployerBlockEntity.class, nbt -> {
            nbt.put("HeldItem", sheet.serializeNBT());
        });
        scene.world().modifyBlockEntityNBT(d3s, DeployerBlockEntity.class, nbt -> {
            nbt.put("HeldItem", sheet.serializeNBT());
        });
        scene.world().modifyBlockEntityNBT(d4s, DeployerBlockEntity.class, nbt -> {
            nbt.put("HeldItem", gunPowder.serializeNBT());
        });

        scene.idle(30);

        scene.overlay().showText(60)
                .text("依次序列组装……")
                .pointAt(util.vector().blockSurface(d1, Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();

        scene.world().moveDeployer(d1, 1.0f, 25);
        scene.overlay().showControls(d1.below(2).getCenter(), Pointing.UP, 30).withItem(sheet);
        scene.idle(26);
        scene.world().moveDeployer(d1, -1.0f, 25);
        scene.effects().indicateSuccess(d1.below(2));
        scene.world().removeItemsFromBelt(d1.below(2));
        ElementLink<BeltItemElement> itemLink =scene.world().createItemOnBelt(d1.below(2), Direction.UP, unfinishedAmmo);
        scene.world().stallBeltItem(itemLink, true);
        scene.idle(15);
        scene.world().stallBeltItem(itemLink, false);
        scene.idle(10);

        scene.world().moveDeployer(d2, 1.0f, 25);
        scene.overlay().showControls(d2.below(2).getCenter(), Pointing.UP, 30).withItem(sheet);
        scene.idle(26);
        scene.world().moveDeployer(d2, -1.0f, 25);
        scene.effects().indicateSuccess(d2.below(2));
        scene.world().removeItemsFromBelt(d2.below(2));
        ElementLink<BeltItemElement> itemLink_2 =scene.world().createItemOnBelt(d2.below(2), Direction.UP, unfinishedAmmo);
        scene.world().stallBeltItem(itemLink_2, true);
        scene.idle(15);
        scene.world().stallBeltItem(itemLink_2, false);
        scene.idle(10);

        scene.world().moveDeployer(d3, 1.0f, 25);
        scene.overlay().showControls(d3.below(2).getCenter(), Pointing.UP, 30).withItem(sheet);
        scene.idle(26);
        scene.world().moveDeployer(d3, -1.0f, 25);
        scene.effects().indicateSuccess(d3.below(2));
        scene.world().removeItemsFromBelt(d3.below(2));
        ElementLink<BeltItemElement> itemLink_3 =scene.world().createItemOnBelt(d3.below(2), Direction.UP, unfinishedAmmo);
        scene.world().stallBeltItem(itemLink_3, true);
        scene.idle(15);
        scene.world().stallBeltItem(itemLink_3, false);
        scene.idle(10);

        scene.world().moveDeployer(d4, 1.0f, 25);
        scene.overlay().showControls(d4.below(2).getCenter(), Pointing.UP, 30).withItem(gunPowder);
        scene.idle(26);
        scene.world().moveDeployer(d4, -1.0f, 25);
        scene.effects().indicateSuccess(d4.below(2));
        scene.world().removeItemsFromBelt(d4.below(2));
        ElementLink<BeltItemElement> itemLink_4 =scene.world().createItemOnBelt(d4.below(2), Direction.UP, unfinishedAmmo);
        scene.world().stallBeltItem(itemLink_4, true);
        scene.idle(15);
        scene.world().stallBeltItem(itemLink_4, false);

        scene.idle(10);
        scene.world().stallBeltItem(itemLink_4, true);

        scene.world().modifyBlockEntity(p5, type, pte -> pte.getPressingBehaviour()
                .start(PressingBehaviour.Mode.BELT));
        scene.idle(30);
        scene.world().removeItemsFromBelt(p5.below(2));
        ElementLink<BeltItemElement> itemLink_5 =scene.world().createItemOnBelt(p5.below(2), Direction.UP, ammo_Stack);

        scene.world().stallBeltItem(itemLink_5, true);
        scene.idle(15);
        scene.world().stallBeltItem(itemLink_5, false);
        scene.idle(10);

        scene.markAsFinished();

    }

    public static void movement(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("sentry_arm_move", "动态结构");
        scene.configureBasePlate(0, 0, 10);
        scene.scaleSceneView(0.7f);
        scene.showBasePlate();
        scene.idle(30);

        Selection train = util.select().fromTo(0, 1, 4, 9, 4, 6);
        ElementLink<WorldSectionElement> movement = scene.world().showIndependentSection(util.select().fromTo(6, 1, 2, 2, 2, 2), Direction.DOWN);
        ElementLink<WorldSectionElement> piston_pos = scene.world().showIndependentSection(util.select().fromTo(4, 1, 3, 4, 1, 3), Direction.DOWN);
        Item ammoItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "ammo_box"));
        ItemStack ammoStack = new ItemStack(ammoItem);


        scene.world().moveSection(piston_pos, util.vector().of(0, 0, -1), 0);

        scene.idle(20);

        scene.world().moveSection(movement, util.vector().of(-2, 0, 0), 20);
        scene.world().setKineticSpeed(util.select().everywhere(), 32f);
        scene.idle(25);
        scene.world().setKineticSpeed(util.select().everywhere(), 0);

        scene.idle(10);

        scene.overlay().showText(60)
                .text("哨戒动力臂可以在动态结构上使用")
                .pointAt(util.vector().topOf(util.grid().at(0, 2, 2)))
                .placeNearTarget();
        scene.idle(70);

        scene.world().moveSection(movement, util.vector().of(2, 0, 0), 40);
        scene.world().setKineticSpeed(util.select().everywhere(), -16f);
        scene.idle(25);
        scene.world().setKineticSpeed(util.select().everywhere(), 0);

        scene.idle(10);

        scene.overlay().showText(60)
                .text("炮台转速正比于移动速度")
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 2)))
                .placeNearTarget();
        scene.idle(70);

        scene.world().hideIndependentSection(movement, Direction.UP);
        scene.world().hideIndependentSection(piston_pos, Direction.UP);
        scene.idle(20);


        scene.world().showIndependentSection(train, Direction.DOWN);
        scene.idle(20);
        scene.world().setKineticSpeed(util.select().everywhere(), -64f);
        scene.idle(20);


        scene.overlay().showText(60)
                .text("哨戒动力臂会使用结构中容器内的弹药")
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 2)))
                .placeNearTarget().attachKeyFrame();
        scene.overlay().showControls(util.grid().at(3, 4, 5).getCenter(), Pointing.UP, 60).withItem(ammoStack);
        scene.idle(70);


        scene.overlay().showText(60)
                .text("移动结构上的火控台依然有效")
                .pointAt(util.vector().topOf(util.grid().at(1, 4, 5)))
                .placeNearTarget();
        scene.idle(80);

        scene.markAsFinished();

    }


    private static ItemStack createGun(String gunId) {
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "modern_kinetic_gun")));
        stack.getOrCreateTag().putString("GunId", gunId);
        return stack;
    }

    private static void fire(CreateSceneBuilder scene, BlockPos sentryPos, BlockPos targetPos, SceneBuildingUtil util) {
        scene.world().modifyBlockEntity(sentryPos, SentryArmBlockEntity.class, be -> {
            be.setLastShootTime(System.currentTimeMillis());
            be.setShellEjected();
        });
    }
}

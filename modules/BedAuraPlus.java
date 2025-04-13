package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.enums.SwingHand;
import kassuk.addon.blackout.managers.Managers;
import kassuk.addon.blackout.utils.*;
import kassuk.addon.blackout.utils.meteor.BODamageUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BedItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author OLEPOSSU
 */

public class BedAuraPlus extends BlackOutModule {
    public BedAuraPlus() {
        super(BlackOut.BLACKOUT, "Bed Aura+", "Automatically places and breaks beds to cause damage to your opponents but better.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlacing = settings.createGroup("Placing");
    private final SettingGroup sgDamage = settings.createGroup("Damage");
    private final SettingGroup sgRender = settings.createGroup("Render");

    //--------------------通用设置--------------------//
    private final Setting<Boolean> fiveB = sgGeneral.add(new BoolSetting.Builder()
        .name("5B5T模式")
        .description("适配特殊服务器环境（需双地板支撑/允许实体内放置）")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
        .name("暂停进食")
        .description("在食用食物时暂停模块运行")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> doubleInteract = sgGeneral.add(new BoolSetting.Builder()
        .name("双点交互")
        .description("每次操作同时点击床的两个组成部分")
        .defaultValue(true)
        .build()
    );
    private final Setting<LogicMode> logicMode = sgGeneral.add(new EnumSetting.Builder<LogicMode>()
        .name("逻辑模式")
        .description("床陷阱的运作逻辑")
        .defaultValue(LogicMode.PlaceBreak)
        .build()
    );
    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("切换模式")
        .description("物品切换方式：静默模式最稳定可靠")
        .defaultValue(SwitchMode.Silent)
        .build()
    );
    private final Setting<RotationMode> rotMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("旋转模式")
        .description("数据包-每个床发送单独的数据包 | 管理器-通过修改运动数据包实现")
        .defaultValue(RotationMode.Manager)
        .build()
    );

    //--------------------放置设置--------------------//
    private final Setting<SpeedMode> speedMode = sgPlacing.add(new EnumSetting.Builder<SpeedMode>()
        .name("速率模式")
        .description("常规模式适用于除5B5T外的所有环境")
        .defaultValue(SpeedMode.Normal)
        .build()
    );
    private final Setting<Double> speed = sgPlacing.add(new DoubleSetting.Builder()
        .name("触发速率")
        .description("每秒可引爆的床数量(次/秒)")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .visible(() -> speedMode.get() == SpeedMode.Normal)
        .build()
    );
    private final Setting<Double> damageSpeed = sgPlacing.add(new DoubleSetting.Builder()
        .name("伤害倍率")
        .description("基于伤害计算触发速率(倍)")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 20)
        .visible(() -> speedMode.get() == SpeedMode.Damage)
        .build()
    );
    private final Setting<Double> maxSpeed = sgPlacing.add(new DoubleSetting.Builder()
        .name("最大速率")
        .description("伤害模式下的最大触发速率(次/秒)")
        .defaultValue(12)
        .min(0)
        .sliderRange(0, 20)
        .visible(() -> speedMode.get() == SpeedMode.Damage)
        .build()
    );

    //--------------------伤害设置--------------------//
    private final Setting<Double> minDmg = sgDamage.add(new DoubleSetting.Builder()
        .name("最小伤害")
        .description("触发陷阱所需的最低伤害值")
        .defaultValue(8)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> maxDmg = sgDamage.add(new DoubleSetting.Builder()
        .name("最大自伤")
        .description("自身可承受的最大伤害值")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> maxFriendDmg = sgDamage.add(new DoubleSetting.Builder()
        .name("队友伤害上限")
        .description("允许对队友造成的最大伤害值")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> minRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("最小伤害比例")
        .description("自身伤害与敌方伤害的最小比值")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> minFriendRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("队友伤害比例")
        .description("队友伤害与敌方伤害的最小比值")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> forcePop = sgDamage.add(new DoubleSetting.Builder()
        .name("强制击杀")
        .description("当敌方生命值低于（伤害值×此系数）时忽略伤害检查")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> antiPop = sgDamage.add(new DoubleSetting.Builder()
        .name("自伤反制")
        .description("当自身生命值低于（伤害值×此系数）时取消操作")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> antiFriendPop = sgDamage.add(new DoubleSetting.Builder()
        .name("队友保护")
        .description("当队友生命值低于（伤害值×此系数）时取消操作")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Boolean> friendSacrifice = sgDamage.add(new BoolSetting.Builder()
        .name("牺牲队友")
        .description("当可同时击杀敌方时允许牺牲队友")
        .defaultValue(true)
        .build()
    );

    //--------------------渲染设置--------------------//
    private final Setting<Boolean> placeSwing = sgRender.add(new BoolSetting.Builder()
        .name("放置动画")
        .description("放置方块时显示手臂摆动动画")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwingHand> placeHand = sgRender.add(new EnumSetting.Builder<SwingHand>()
        .name("手持方位")
        .description("指定触发动画的手部")
        .defaultValue(SwingHand.真实手持)
        .visible(placeSwing::get)
        .build()
    );
    private final Setting<Boolean> interactSwing = sgRender.add(new BoolSetting.Builder()
        .name("交互动画")
        .description("与方块交互时显示手臂摆动动画")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwingHand> interactHand = sgRender.add(new EnumSetting.Builder<SwingHand>()
        .name("交互方位")
        .description("指定交互时触发动画的手部")
        .defaultValue(SwingHand.真实手持)
        .visible(interactSwing::get)
        .build()
    );
    public final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .description("选择渲染方块的哪些部分")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("头部轮廓颜色")
        .description("头部方块的轮廓线颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );
    public final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("头部填充颜色")
        .description("头部方块的填充颜色（含透明度效果）")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );
    private final Setting<SettingColor> fLineColor = sgRender.add(new ColorSetting.Builder()
        .name("脚部轮廓颜色")
        .description("脚部方块的轮廓线颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );
    public final Setting<SettingColor> fColor = sgRender.add(new ColorSetting.Builder()
        .name("脚部填充颜色")
        .description("脚部方块的填充颜色（含透明度效果）")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    private int lastIndex = 0;
    private int length = 0;
    private long tickTime = -1;
    private double bestDmg = 0;
    private long lastTime = 0;

    private BlockPos placePos = null;
    private Direction bedDir = null;
    private PlaceData placeData = null;
    private BlockPos calcPos = null;
    private Direction calcDir = null;
    private PlaceData calcData = null;
    private BlockPos renderPos = null;
    private Direction renderDir = null;
    private BlockPos[] blocks = new BlockPos[]{};
    private final List<PlayerEntity> targets = new ArrayList<>();
    private final List<PlayerEntity> friends = new ArrayList<>();
    private final List<Bed> beds = new ArrayList<>();

    private double timer = 0;

    private double dmg;
    private double enemyHP;
    private double self;
    private double selfHP;
    private double friend;
    private double friendHP;

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTickPre(TickEvent.Post event) {
        calculate(length - 1);
        renderPos = calcPos;
        placePos = calcPos;
        renderDir = calcDir;
        bedDir = calcDir;
        placeData = calcData;

        blocks = getBlocks(mc.player.getEyePos(), Math.max(SettingUtils.getPlaceRange(), SettingUtils.getPlaceWallsRange()));

        // Reset stuff
        tickTime = System.currentTimeMillis();
        length = blocks.length;
        lastIndex = 0;
        bestDmg = 0;
        calcPos = null;
        calcDir = null;
        calcData = null;

        updateTargets();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        double delta = (System.currentTimeMillis() - lastTime) / 1000f;
        timer += delta;
        lastTime = System.currentTimeMillis();

        List<Bed> toRemove = new ArrayList<>();
        beds.forEach(bed -> {
            if (System.currentTimeMillis() - bed.time > 500) {
                toRemove.add(bed);
            }
        });
        toRemove.forEach(beds::remove);

        if (tickTime < 0 || mc.player == null || mc.world == null) return;

        if (pauseCheck()) {
            update();
        }

        int index = Math.min((int) Math.ceil((System.currentTimeMillis() - tickTime) / 50f * length), length - 1);
        calculate(index);

        if (renderPos != null && pauseCheck()) {
            event.renderer.box(bedBox(renderPos), color.get(), lineColor.get(), shapeMode.get(), 0);
            if (renderDir != null) {
                event.renderer.box(bedBox(renderPos.offset(renderDir)), fColor.get(), fLineColor.get(), shapeMode.get(), 0);
            }
        }
    }

    private boolean pauseCheck() {
        return !pauseEat.get() || !mc.player.isUsingItem();
    }

    private void calculate(int index) {
        BlockPos pos;

        for (int i = lastIndex; i < index; i++) {
            pos = blocks[i];

            damageCalc(pos);

            if (!dmgCheck()) continue;

            for (Direction dir : Direction.Type.HORIZONTAL) {
                PlaceData data = getData(pos, dir);

                if (!data.valid()) continue;

                if (!OLEPOSSUtils.replaceable(pos.offset(dir)) && !(mc.world.getBlockState(pos.offset(dir)).getBlock() instanceof BedBlock))
                    continue;

                if (!SettingUtils.inPlaceRange(data.pos())) continue;

                if (!fiveB.get() && EntityUtils.intersectsWithEntity(new Box(pos.offset(dir)), entity -> !(entity instanceof ItemEntity)))
                    continue;

                calcData = data;
                calcPos = pos;
                calcDir = dir;
                bestDmg = dmg;
            }
        }
        lastIndex = index;
    }

    private void updateTargets() {
        friends.clear();
        targets.clear();

        List<PlayerEntity> players = new ArrayList<>();

        double closestDist = 1000;
        PlayerEntity closest;
        double dist;

        for (int i = 3; i > 0; i--) {

            closest = null;
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (players.contains(player) || Friends.get().isFriend(player) || player == mc.player) continue;

                dist = player.distanceTo(mc.player);

                if (dist > 15) continue;

                if (closest == null || dist < closestDist) {
                    closestDist = dist;
                    closest = player;
                }
            }
            if (closest != null) {
                players.add(closest);
                if (Friends.get().isFriend(closest)) {
                    friends.add(closest);
                } else {
                    targets.add(closest);
                }
            }
        }
    }

    private BlockPos[] getBlocks(Vec3d middle, double radius) {
        ArrayList<BlockPos> result = new ArrayList<>();
        int i = (int) Math.ceil(radius);
        BlockPos pos;

        for (int x = -i; x <= i; x++) {
            for (int y = -i; y <= i; y++) {
                for (int z = -i; z <= i; z++) {
                    pos = BlockPos.ofFloored(middle).add(x, y, z);

                    if (!OLEPOSSUtils.replaceable(pos) && !(mc.world.getBlockState(pos).getBlock() instanceof BedBlock))
                        continue;

                    if (fiveB.get() && (mc.world.getBlockState(pos.down()).getBlock() == Blocks.AIR || mc.world.getBlockState(pos.down()).hasBlockEntity()))
                        continue;

                    if (!inRangeToTargets(pos)) continue;
                    result.add(pos);
                }
            }
        }
        return result.toArray(new BlockPos[0]);
    }

    private boolean inRangeToTargets(BlockPos pos) {
        for (PlayerEntity target : targets) {
            if (target.getPos().add(0, 1, 0).distanceTo(pos.toCenterPos()) < 3.5) {
                return true;
            }
        }
        return false;
    }

    private void update() {
        if (placePos == null || placeData == null || !placeData.valid() || bedDir == null) return;

        if (logicMode.get() == LogicMode.PlaceBreak) {
            List<BlockPos> in = interactUpdate();
            if (in != null && !in.isEmpty()) {
                in.forEach(this::removeBed);
            }

            if (timer <= 1 / getSpeed()) return;

            if (OLEPOSSUtils.replaceable(placePos) && OLEPOSSUtils.replaceable(placePos.offset(bedDir)) && placeUpdate()) {
                removeBed2(placePos);
                beds.add(new Bed(placePos, placePos.offset(bedDir), true, System.currentTimeMillis()));
                timer = 0;
            }
        } else {
            if (!isBed(placePos) && !isBed(placePos.offset(bedDir)) && placeUpdate()) {
                removeBed2(placePos);
                beds.add(new Bed(placePos, placePos.offset(bedDir), true, System.currentTimeMillis()));
            }

            if (timer <= 1 / getSpeed()) return;

            List<BlockPos> in = interactUpdate();
            if (in != null && !in.isEmpty()) {
                in.forEach(this::removeBed);
                timer = 0;
            }
        }
    }

    private void removeBed(BlockPos pos) {
        List<Bed> toRemove = new ArrayList<>();
        beds.forEach(bed -> {
            if (bed.feetBlock.equals(pos) || bed.headBlock.equals(pos)) {
                toRemove.add(bed);
            }
        });
        toRemove.forEach(bed -> {
            beds.remove(bed);
            beds.add(new Bed(bed.feetBlock, bed.headBlock, false, System.currentTimeMillis()));
        });
    }

    private void removeBed2(BlockPos pos) {
        List<Bed> toRemove = new ArrayList<>();
        beds.forEach(bed -> {
            if (bed.feetBlock.equals(pos) || bed.headBlock.equals(pos)) {
                toRemove.add(bed);
            }
        });
        toRemove.forEach(beds::remove);
    }

    private void place(Hand hand) {
        placeBlock(hand, placeData.pos().toCenterPos(), placeData.dir(), placeData.pos());

        if (placeSwing.get()) clientSwing(placeHand.get(), hand);
    }

    private List<BlockPos> interactUpdate() {
        if (doubleInteract.get()) {
            if (SettingUtils.shouldRotate(RotationType.交互) && !Managers.ROTATION.start(placePos, priority, RotationType.交互, Objects.hash(name + "explode"))) {
                return null;
            }

            List<BlockPos> list = new ArrayList<>();

            if (isBed(placePos) || isBed(placePos.offset(bedDir))) {
                if (SettingUtils.inPlaceRange(placePos) && interact(placePos)) {
                    list.add(placePos);
                }
                if (SettingUtils.inPlaceRange(placePos.offset(bedDir)) && interact(placePos.offset(bedDir))) {
                    list.add(placePos.offset(bedDir));
                }
            }

            if (SettingUtils.shouldRotate(RotationType.交互)) {
                Managers.ROTATION.end(Objects.hash(name + "explode"));
            }

            return list;
        }

        BlockPos interactPos = getInteractPos();

        if (interactPos == null) {
            return null;
        }

        Direction interactDir = SettingUtils.getPlaceOnDirection(interactPos);

        if (interactDir == null) {
            return null;
        }

        if (SettingUtils.shouldRotate(RotationType.交互) && !Managers.ROTATION.start(interactPos, priority, RotationType.交互, Objects.hash(name + "explode"))) {
            return null;
        }

        interactBlock(Hand.MAIN_HAND, interactPos.toCenterPos(), interactDir, interactPos);

        if (interactSwing.get()) clientSwing(interactHand.get(), Hand.MAIN_HAND);

        if (SettingUtils.shouldRotate(RotationType.交互)) {
            Managers.ROTATION.end(Objects.hash(name + "explode"));
        }
        List<BlockPos> list = new ArrayList<>();
        list.add(interactPos);
        return list;
    }

    private boolean interact(BlockPos pos) {
        Direction dir = SettingUtils.getPlaceOnDirection(pos);

        if (dir == null) {
            return false;
        }

        interactBlock(Hand.MAIN_HAND, pos.toCenterPos(), dir, pos);

        if (interactSwing.get()) clientSwing(interactHand.get(), Hand.MAIN_HAND);
        return true;
    }

    private BlockPos getInteractPos() {
        if (isBed(placePos.offset(bedDir)) && SettingUtils.inPlaceRange(placePos.offset(bedDir)) && SettingUtils.getPlaceOnDirection(placePos.offset(bedDir)) != null) {
            return placePos.offset(bedDir);
        }
        if (isBed(placePos) && SettingUtils.inPlaceRange(placePos) && SettingUtils.getPlaceOnDirection(placePos) != null) {
            return placePos;
        }
        return null;
    }

    private boolean isBed(BlockPos pos) {
        for (Bed bed : beds) {
            if (bed.feetBlock.equals(pos) || bed.headBlock.equals(pos)) {
                return bed.isBed;
            }
        }
        return mc.world.getBlockState(pos).getBlock() instanceof BedBlock;
    }

    private boolean placeUpdate() {
        Hand hand = Managers.HOLDING.getStack().getItem() instanceof BedItem ? Hand.MAIN_HAND : mc.player.getOffHandStack().getItem() instanceof BedItem ? Hand.OFF_HAND : null;

        int beds = hand == Hand.MAIN_HAND ? Managers.HOLDING.getStack().getCount() :
            hand == Hand.OFF_HAND ? mc.player.getOffHandStack().getCount() : 0;

        if (hand == null) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    FindItemResult result = InvUtils.findInHotbar(item -> item.getItem() instanceof BedItem);
                    beds = result.count();
                }
                case PickSilent, InvSwitch -> {
                    FindItemResult result = InvUtils.find(item -> item.getItem() instanceof BedItem);
                    beds = result.slot() >= 0 ? result.count() : -1;
                }
            }
        }

        if (beds <= 0) {
            return false;
        }

        if (SettingUtils.shouldRotate(RotationType.方块放置) && !Managers.ROTATION.start(placeData.pos(), priority, RotationType.方块放置, Objects.hash(name + "placing"))) {
            return false;
        }

        boolean switched = hand != null;

        if (rotMode.get() == RotationMode.Packet) {
            sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(bedDir.getOpposite().asRotation(), Managers.ROTATION.lastDir[1], Managers.ON_GROUND.isOnGround()));
        } else {
            Managers.ROTATION.startYaw(bedDir.getOpposite().asRotation(), priority, RotationType.Other, Objects.hash(name + "placing"));
            if (Math.abs(RotationUtils.yawAngle(Managers.ROTATION.lastDir[0], bedDir.getOpposite().asRotation())) > 45) {
                return false;
            }
        }

        if (!switched) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    FindItemResult result = InvUtils.findInHotbar(item -> item.getItem() instanceof BedItem);
                    InvUtils.swap(result.slot(), true);
                    switched = true;
                }
                case PickSilent -> {
                    FindItemResult result = InvUtils.find(item -> item.getItem() instanceof BedItem);
                    switched = BOInvUtils.pickSwitch(result.slot());
                }
                case InvSwitch -> {
                    FindItemResult result = InvUtils.find(item -> item.getItem() instanceof BedItem);
                    switched = BOInvUtils.invSwitch(result.slot());
                }
            }
        }

        if (!switched) {
            return false;
        }

        place(hand == null ? Hand.MAIN_HAND : hand);

        if (SettingUtils.shouldRotate(RotationType.方块放置)) {
            Managers.ROTATION.end(Objects.hash(name + "placing"));
        }

        if (hand == null) {
            switch (switchMode.get()) {
                case Silent -> InvUtils.swapBack();
                case PickSilent -> BOInvUtils.pickSwapBack();
                case InvSwitch -> BOInvUtils.swapBack();
            }
        }
        return true;
    }

    private boolean dmgCheck() {
        if (dmg < bestDmg) {
            return false;
        }

        if (self * antiPop.get() >= selfHP) {
            return false;
        }

        if (!friendSacrifice.get() && friendHP >= 0 && friend * antiFriendPop.get() >= friendHP) {
            return false;
        }
        if (enemyHP >= 0 && dmg * forcePop.get() >= enemyHP) {
            return true;
        }
        if (friendHP >= 0 && friend * antiFriendPop.get() >= friendHP) {
            return false;
        }

        if (dmg < minDmg.get()) {
            return false;
        }

        if (self > maxDmg.get()) {
            return false;
        }
        if (friend > maxFriendDmg.get()) {
            return false;
        }

        if (dmg / self < minRatio.get()) {
            return false;
        }

        return !(friendHP >= 0) || !(dmg / friend < minFriendRatio.get());
    }

    private double getDmg(BlockPos pos) {
        double highest = -1;
        for (PlayerEntity target : targets) {
            highest = Math.max(highest, BODamageUtils.anchorDamage(target, target.getBoundingBox(), pos.toCenterPos(), pos, false));
        }
        return highest;
    }

    private void damageCalc(BlockPos pos) {
        // Enemy
        double highest = -1;
        double highestHP = -1;
        for (PlayerEntity target : targets) {
            if (target.getHealth() <= 0) continue;

            highest = Math.max(highest, BODamageUtils.anchorDamage(target, target.getBoundingBox(), pos.toCenterPos(), pos, true));
            highestHP = target.getHealth() + target.getAbsorptionAmount();
        }
        dmg = highest;
        enemyHP = highestHP;

        // Self
        self = BODamageUtils.anchorDamage(mc.player, mc.player.getBoundingBox(), pos.toCenterPos(), pos, false);
        selfHP = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        // Friend
        highest = -1;
        highestHP = -1;
        for (PlayerEntity friend : friends) {
            if (friend.getHealth() <= 0) continue;

            highest = Math.max(highest, BODamageUtils.anchorDamage(friend, friend.getBoundingBox(), pos.toCenterPos(), pos, true));
            highestHP = friend.getHealth() + friend.getAbsorptionAmount();
        }
        friend = highest;
        friendHP = highestHP;
    }

    private Box bedBox(BlockPos pos) {
        return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 0.5, pos.getZ() + 1);
    }

    private PlaceData getData(BlockPos pos, Direction dir) {
        if (fiveB.get()) {
            return SettingUtils.getPlaceDataAND(pos.offset(dir), direction -> direction == Direction.DOWN, pos1 -> !(mc.world.getBlockState(pos1).getBlock() instanceof BedBlock));
        } else {
            return SettingUtils.getPlaceDataAND(pos.offset(dir), direction -> direction != dir, pos1 -> !(mc.world.getBlockState(pos1).getBlock() instanceof BedBlock));
        }
    }

    private double getSpeed() {
        switch (speedMode.get()) {
            case Normal -> {
                return speed.get();
            }
            case Damage -> {
                if (placePos == null) {
                    return maxSpeed.get();
                }

                double dmg = getDmg(placePos);
                return Math.min(dmg * damageSpeed.get(), maxSpeed.get());
            }
        }
        return 2;
    }

    public enum LogicMode {
        PlaceBreak,
        BreakPlace
    }

    public enum RotationMode {
        Packet,
        Manager
    }

    public enum SwitchMode {
        Silent,
        Normal,
        PickSilent,
        InvSwitch,
        Disabled
    }

    public enum SpeedMode {
        Normal,
        Damage
    }

    private record Bed(BlockPos feetBlock, BlockPos headBlock, boolean isBed, long time) {
    }
}

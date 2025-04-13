package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.enums.SwingHand;
import kassuk.addon.blackout.enums.SwingState;
import kassuk.addon.blackout.enums.SwingType;
import kassuk.addon.blackout.managers.Managers;
import kassuk.addon.blackout.timers.TimerList;
import kassuk.addon.blackout.utils.BOInvUtils;
import kassuk.addon.blackout.utils.OLEPOSSUtils;
import kassuk.addon.blackout.utils.PlaceData;
import kassuk.addon.blackout.utils.SettingUtils;
import kassuk.addon.blackout.utils.meteor.BODamageUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author OLEPOSSU
 */

public class SurroundPlus extends BlackOutModule {
    public SurroundPlus() {
        super(BlackOut.BLACKOUT, "Surround+", "在玩家腿部周围放置方块以抵御爆炸伤害");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgToggle = settings.createGroup("Toggle");
    private final SettingGroup sgSpeed = settings.createGroup("Speed");
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgAttack = settings.createGroup("Attack");
    private final SettingGroup sgRender = settings.createGroup("Render");

    //--------------------General--------------------//
    //--------------------通用设置--------------------//
    private final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
        .name("居中处理")
        .description("在放置方块前自动移动到方块中心位置")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> smartCenter = sgGeneral.add(new BoolSetting.Builder()
        .name("智能居中")
        .description("仅移动至玩家碰撞箱完全进入目标方块")
        .defaultValue(true)
        .visible(center::get)
        .build()
    );
    private final Setting<Boolean> phaseCenter = sgGeneral.add(new BoolSetting.Builder()
        .name("相位穿透")
        .description("当玩家卡在方块内时不执行居中操作")
        .defaultValue(true)
        .visible(center::get)
        .build()
    );
    private final Setting<Boolean> pauseEat = addPauseEat(sgGeneral);
    private final Setting<Boolean> packet = sgGeneral.add(new BoolSetting.Builder()
        .name("数据包模式")
        .description("通过网络数据包进行方块操作（可绕过某些反作弊）")
        .defaultValue(false)
        .build()
    );
    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("切换模式")
        .description("物品切换方式：静默切换最稳定可靠")
        .defaultValue(SwitchMode.Silent)
        .build()
    );
    private final Setting<Boolean> extend = sgGeneral.add(new BoolSetting.Builder()
        .name("范围扩展")
        .description("将保护范围扩展到附近队友的周围")
        .defaultValue(true)
        .build()
    );

    //--------------------General--------------------//
    //--------------------切换设置--------------------//
    private final Setting<Boolean> toggleMove = sgToggle.add(new BoolSetting.Builder()
        .name("移动切换")
        .description("当玩家水平移动时自动关闭模块")
        .defaultValue(false)
        .build()
    );
    private final Setting<VerticalToggleMode> toggleVertical = sgToggle.add(new EnumSetting.Builder<VerticalToggleMode>()
        .name("垂直切换模式")
        .description("选择垂直移动时触发模块关闭的检测方向")
        .defaultValue(VerticalToggleMode.Up)
        .build()
    );

    //--------------------速度设置--------------------//
    private final Setting<PlaceDelayMode> placeDelayMode = sgSpeed.add(new EnumSetting.Builder<PlaceDelayMode>()
        .name("延迟模式")
        .description("选择延迟计算方式：按游戏刻或实际秒数")
        .defaultValue(PlaceDelayMode.Ticks)
        .build()
    );
    private final Setting<Integer> placeDelayT = sgSpeed.add(new IntSetting.Builder()
        .name("游戏刻延迟")
        .description("每次放置方块之间的游戏刻间隔（20刻=1秒）")
        .defaultValue(1)
        .min(1)
        .sliderRange(0, 20)
        .visible(() -> placeDelayMode.get() == PlaceDelayMode.Ticks)
        .build()
    );
    private final Setting<Double> placeDelayS = sgSpeed.add(new DoubleSetting.Builder()
        .name("实际秒数延迟")
        .description("每次放置方块之间的实际秒数间隔")
        .defaultValue(0.1)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> placeDelayMode.get() == PlaceDelayMode.Seconds)
        .build()
    );
    private final Setting<Integer> places = sgSpeed.add(new IntSetting.Builder()
        .name("单次数量")
        .description("每次触发时连续放置的方块数量")
        .defaultValue(1)
        .min(1)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> cooldown = sgSpeed.add(new DoubleSetting.Builder()
        .name("多重冷却")
        .description("当多个方块缺失时，在同一位置重复操作前的等待时间（秒）")
        .defaultValue(0.3)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );
    private final Setting<Double> singleCooldown = sgSpeed.add(new DoubleSetting.Builder()
        .name("单次冷却")
        .description("当单个方块缺失时，重复操作前的等待时间（秒）")
        .defaultValue(0.02)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    //--------------------Blocks--------------------//
    //--------------------方块设置--------------------//
    private final Setting<List<Block>> blocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("主要方块")
        .description("用于构建基础防护结构的主要方块类型")
        .defaultValue(Blocks.OBSIDIAN)
        .build()
    );
    private final Setting<List<Block>> supportBlocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("支撑方块")
        .description("用于搭建临时支撑结构的辅助方块类型（防止方块倒塌）")
        .defaultValue(Blocks.OBSIDIAN)
        .build()
    );

    //--------------------攻击设置--------------------//
    private final Setting<Boolean> attack = sgAttack.add(new BoolSetting.Builder()
        .name("自动攻击")
        .description("自动摧毁阻碍防护结构的末影水晶")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> attackSpeed = sgAttack.add(new DoubleSetting.Builder()
        .name("攻击频率")
        .description("每秒尝试攻击水晶的最大次数（过高可能导致封禁）")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Boolean> alwaysAttack = sgAttack.add(new BoolSetting.Builder()
        .name("持续攻击")
        .description("即使防护方块完好也持续攻击周围水晶")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> antiCev = sgAttack.add(new BoolSetting.Builder()
        .name("反CEV策略")
        .description("主动攻击放置在防护方块上的水晶（防御CEV爆破）")
        .defaultValue(false)
        .build()
    );

    //--------------------渲染设置--------------------//
    private final Setting<Boolean> placeSwing = sgRender.add(new BoolSetting.Builder()
        .name("方块挥动动画")
        .description("放置方块时显示手持物品的挥动效果")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwingHand> placeHand = sgRender.add(new EnumSetting.Builder<SwingHand>()
        .name("挥动手部")
        .description("选择显示挥动动画的手部")
        .defaultValue(SwingHand.真实手持)
        .visible(placeSwing::get)
        .build()
    );
    private final Setting<Boolean> attackSwing = sgRender.add(new BoolSetting.Builder()
        .name("攻击挥动动画")
        .description("攻击水晶时显示手持物品的挥动效果")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwingHand> attackHand = sgRender.add(new EnumSetting.Builder<SwingHand>()
        .name("攻击挥动手部")
        .description("选择攻击时显示动画的手部")
        .defaultValue(SwingHand.真实手持)
        .visible(attackSwing::get)
        .build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("选择渲染盒子的显示部分（轮廓线/填充面/两者）")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("轮廓颜色")
        .description(BlackOut.COLOR)
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("填充颜色")
        .description(BlackOut.COLOR)
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );
    private final Setting<ShapeMode> supportShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("支撑形状模式")
        .description("选择支撑结构的渲染显示方式")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> supportLineColor = sgRender.add(new ColorSetting.Builder()
        .name("支撑轮廓颜色")
        .description(BlackOut.COLOR)
        .defaultValue(new SettingColor(255, 0, 0, 150))
        .build()
    );
    private final Setting<SettingColor> supportSideColor = sgRender.add(new ColorSetting.Builder()
        .name("支撑填充颜色")
        .description(BlackOut.COLOR)
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    private int tickTimer = 0;
    private double timer = 0;
    private final List<BlockPos> insideBlocks = new ArrayList<>();
    public final List<BlockPos> surroundBlocks = new ArrayList<>();
    private final List<BlockPos> supportPositions = new ArrayList<>();
    private final List<BlockPos> valids = new ArrayList<>();
    private final TimerList<BlockPos> placed = new TimerList<>();
    private final List<Render> render = new ArrayList<>();
    private boolean support = false;
    private Hand hand = null;
    private int blocksLeft = 0;
    private int placesLeft = 0;
    private FindItemResult result = null;
    private boolean switched = false;
    private BlockPos lastPos = null;
    private boolean centered = false;
    private long lastAttack = 0;
    private BlockPos currentPos = null;

    public static boolean placing = false;

    @Override
    public void onActivate() {
        tickTimer = placeDelayT.get();
        timer = placeDelayS.get();
        placesLeft = places.get();
        centered = false;
        lastPos = getPos();
        currentPos = getPos();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlock(BlockUpdateEvent event) {
        if (event.oldState.getBlock() != event.newState.getBlock() && !OLEPOSSUtils.replaceable(event.pos) && surroundBlocks.contains(event.pos)) {
            render.add(new Render(event.pos, System.currentTimeMillis()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        tickTimer++;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        placed.update();

        placing = false;
        timer += event.frameTime;

        lastPos = currentPos;
        currentPos = getPos();

        setBB();

        if (checkToggle()) return;

        updateBlocks();
        updateSupport();

        surroundBlocks.stream().filter(OLEPOSSUtils::replaceable).forEach(block -> event.renderer.box(block, sideColor.get(), lineColor.get(), shapeMode.get(), 0));
        supportPositions.forEach(block -> event.renderer.box(block, supportSideColor.get(), supportLineColor.get(), supportShapeMode.get(), 0));
        render.removeIf(r -> System.currentTimeMillis() - r.time > 1000);

        render.forEach(r -> {
            double progress = 1 - Math.min(System.currentTimeMillis() - r.time, 500) / 500d;

            event.renderer.box(r.pos, new Color(sideColor.get().r, sideColor.get().g, sideColor.get().b, (int) Math.round(sideColor.get().a * progress)), new Color(lineColor.get().r, lineColor.get().g, lineColor.get().b, (int) Math.round(lineColor.get().a * progress)), shapeMode.get(), 0);
        });

        if (pauseEat.get() && mc.player.isUsingItem()) {return;}

        placeBlocks();
    }

    private void updateAttack() {
        if (!attack.get()) return;
        if (System.currentTimeMillis() - lastAttack < 1000 / attackSpeed.get()) return;

        Entity blocking = getBlocking();

        if (blocking == null) return;

        if (SettingUtils.shouldRotate(RotationType.攻击) && !Managers.ROTATION.start(blocking.getBoundingBox(), priority - 0.1, RotationType.攻击, Objects.hash(name + "attacking"))) return;

        SettingUtils.swing(SwingState.Pre, SwingType.攻击, Hand.MAIN_HAND);
        sendPacket(PlayerInteractEntityC2SPacket.attack(blocking, mc.player.isSneaking()));
        SettingUtils.swing(SwingState.Post, SwingType.攻击, Hand.MAIN_HAND);

        if (SettingUtils.shouldRotate(RotationType.攻击)) Managers.ROTATION.end(Objects.hash(name + "attacking"));

        if (attackSwing.get()) clientSwing(attackHand.get(), Hand.MAIN_HAND);

        lastAttack = System.currentTimeMillis();
    }

    private Entity getBlocking() {
        Entity crystal = null;
        double lowest = 1000;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity)) continue;
            if (mc.player.distanceTo(entity) > 5) continue;
            if (!SettingUtils.inAttackRange(entity.getBoundingBox())) continue;

            if (antiCev.get()) {
                for (BlockPos pos : surroundBlocks) {
                    if (entity.getBlockPos().equals(pos.up())) {
                        double dmg = Math.max(10, BODamageUtils.crystalDamage(mc.player, mc.player.getBoundingBox(), entity.getPos(), null, false));

                        if (dmg < lowest) {
                            lowest = dmg;
                            crystal = entity;
                        }
                    }
                }
            }

            for (BlockPos pos : alwaysAttack.get() ? surroundBlocks : valids) {
                if (!Box.from(new BlockBox(pos)).intersects(entity.getBoundingBox())) continue;

                double dmg = BODamageUtils.crystalDamage(mc.player, mc.player.getBoundingBox(), entity.getPos(), null, false);
                if (dmg < lowest) {
                    crystal = entity;
                    lowest = dmg;
                }
            }
        }
        return crystal;
    }

    private void setBB() {
        if (!centered && center.get() && mc.player.isOnGround() && (!phaseCenter.get() || !OLEPOSSUtils.inside(mc.player, mc.player.getBoundingBox().shrink(0.01, 0.01, 0.01)))) {
            double targetX, targetZ;

            if (smartCenter.get()) {
                targetX = MathHelper.clamp(mc.player.getX(), currentPos.getX() + 0.31, currentPos.getX() + 0.69);
                targetZ = MathHelper.clamp(mc.player.getZ(), currentPos.getZ() + 0.31, currentPos.getZ() + 0.69);
            } else {
                targetX = currentPos.getX() + 0.5;
                targetZ = currentPos.getZ() + 0.5;
            }

            double dist = new Vec3d(targetX, 0, targetZ).distanceTo(new Vec3d(mc.player.getX(), 0, mc.player.getZ()));

            if (dist < 0.2873) {
                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(targetX, mc.player.getY(), targetZ, Managers.ON_GROUND.isOnGround()));
            }

            double x = mc.player.getX(), z = mc.player.getZ();

            for (int i = 0; i < Math.ceil(dist / 0.2873); i++) {
                double yaw = Rotations.getYaw(new Vec3d(targetX, 0, targetZ)) + 90;

                x += Math.cos(Math.toRadians(yaw)) * 0.2873;
                z += Math.sin(Math.toRadians(yaw)) * 0.2873;

                sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, mc.player.getY(), z, Managers.ON_GROUND.isOnGround()));
            }

            mc.player.setPos(targetX, mc.player.getY(), targetZ);
            mc.player.setBoundingBox(new Box(targetX - 0.3, mc.player.getY(), targetZ - 0.3, targetX + 0.3, mc.player.getY() + (mc.player.getBoundingBox().maxY - mc.player.getBoundingBox().minY), targetZ + 0.3));

            centered = true;
        }
    }

    private boolean checkToggle() {
        if (lastPos != null) {
            if (toggleMove.get() && (currentPos.getX() != lastPos.getX() || currentPos.getZ() != lastPos.getZ())) {
                toggle();
                sendToggledMsg("moved horizontally");
                return true;
            }

            if (toggleVertical.get() == VerticalToggleMode.Up || toggleVertical.get() == VerticalToggleMode.Any) {
                if (currentPos.getY() > lastPos.getY()) {
                    toggle();
                    sendToggledMsg("moved up");
                    return true;
                }
            }
            if (toggleVertical.get() == VerticalToggleMode.Down || toggleVertical.get() == VerticalToggleMode.Any) {
                if (currentPos.getY() < lastPos.getY()) {
                    toggle();
                    sendToggledMsg("moved down");
                    return true;
                }
            }
        }

        return false;
    }

    private void placeBlocks() {
        List<BlockPos> positions = new ArrayList<>();
        setSupport();

        if (support) positions.addAll(supportPositions);
        else positions.addAll(surroundBlocks);

        valids.clear();
        valids.addAll(positions.stream().filter(this::validBlock).toList());

        updateAttack();

        updateResult();

        updatePlaces();

        blocksLeft = Math.min(placesLeft, result.count());

        hand = getHand();
        switched = false;

        valids.stream().filter(pos -> !EntityUtils.intersectsWithEntity(Box.from(new BlockBox(pos)), this::validEntity)).sorted(Comparator.comparingDouble(Rotations::getYaw)).forEach(this::place);

        if (switched && hand == null) {
            switch (switchMode.get()) {
                case Silent -> InvUtils.swapBack();
                case PickSilent -> BOInvUtils.pickSwapBack();
                case InvSwitch -> BOInvUtils.swapBack();
            }
        }
    }

    private void updatePlaces() {
        switch (placeDelayMode.get()) {
            case Ticks -> {
                if (placesLeft >= places.get() || tickTimer >= placeDelayT.get()) {
                    placesLeft = places.get();
                    tickTimer = 0;
                }
            }
            case Seconds -> {
                if (placesLeft >= places.get() || timer >= placeDelayS.get()) {
                    placesLeft = places.get();
                    timer = 0;
                }
            }
        }
    }

    private boolean validBlock(BlockPos pos) {
        if (!OLEPOSSUtils.replaceable(pos)) return false;

        PlaceData data = SettingUtils.getPlaceDataOR(pos, placed::contains);
        if (!data.valid()) return false;

        if (!SettingUtils.inPlaceRange(data.pos())) return false;

        return !placed.contains(pos);
    }

    private void place(BlockPos pos) {
        if (blocksLeft <= 0) {
            return;
        }

        PlaceData data = SettingUtils.getPlaceDataOR(pos, placed::contains);

        if (data == null || !data.valid()) {
            return;
        }

        placing = true;

        if (SettingUtils.shouldRotate(RotationType.方块放置) && !Managers.ROTATION.start(data.pos(), priority, RotationType.方块放置, Objects.hash(name + "placing"))) {return;}

        if (!switched && hand == null) {
            switch (switchMode.get()) {
                case Normal, Silent -> {
                    InvUtils.swap(result.slot(), true);
                    switched = true;
                }
                case PickSilent -> switched = BOInvUtils.pickSwitch(result.slot());
                case InvSwitch -> switched = BOInvUtils.invSwitch(result.slot());
            }
        }

        if (!switched && hand == null) {
            return;
        }

        placeBlock(hand == null ? Hand.MAIN_HAND : hand, data.pos().toCenterPos(), data.dir(), data.pos());

        if (placeSwing.get()) clientSwing(placeHand.get(), hand == null ? Hand.MAIN_HAND : hand);

        if (!packet.get()) {
            setBlock(pos);
        }

        placed.add(pos, oneMissing() ? singleCooldown.get() : cooldown.get());
        blocksLeft--;
        placesLeft--;

        if (SettingUtils.shouldRotate(RotationType.方块放置)) {
            Managers.ROTATION.end(Objects.hash(name + "placing"));
        }
    }

    private boolean oneMissing() {
        boolean alreadyFound = false;

        for (BlockPos pos : surroundBlocks) {
            if (!OLEPOSSUtils.replaceable(pos)) continue;

            if (alreadyFound) return false;
            alreadyFound = true;
        }
        return true;
    }

    private void setBlock(BlockPos pos) {
        Item item = mc.player.getInventory().getStack(result.slot()).getItem();

        if (!(item instanceof BlockItem block)) {return;}

        mc.world.setBlockState(pos, block.getBlock().getDefaultState());
        mc.world.playSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1, 1, false);
    }

    private void setSupport() {
        support = false;
        double min = 10000;

        for (BlockPos pos : surroundBlocks) {
            if (!validBlock(pos)) {continue;}

            double y = Rotations.getYaw(pos.toCenterPos());

            if (y < min) {
                support = false;
                min = y;
            }
        }

        for (BlockPos pos : supportPositions) {
            if (!validBlock(pos)) {continue;}

            double y = Rotations.getYaw(pos.toCenterPos());

            if (y < min) {
                support = true;
                min = y;
            }
        }
    }

    private boolean valid(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block && (support ? supportBlocks : blocks).get().contains(block.getBlock());
    }

    private void updateResult() {
        result = switch (switchMode.get()) {
            case Disabled -> null;
            case Normal, Silent -> InvUtils.findInHotbar(this::valid);
            case PickSilent, InvSwitch -> InvUtils.find(this::valid);
        };
    }

    private Hand getHand() {
        if (valid(Managers.HOLDING.getStack())) {
            return Hand.MAIN_HAND;
        }
        if (valid(mc.player.getOffHandStack())) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    private void updateSupport() {
        supportPositions.clear();

        surroundBlocks.forEach(this::addSupport);
    }

    private void addSupport(BlockPos pos) {
        if (!OLEPOSSUtils.replaceable(pos)) {return;}
        if (hasSupport(pos, true)) {return;}

        PlaceData data = SettingUtils.getPlaceData(pos);
        if (data.valid()) return;

        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) {
                continue;
            }

            if (surroundBlocks.contains(pos.offset(dir)) || insideBlocks.contains(pos.offset(dir))) {continue;}

            if (EntityUtils.intersectsWithEntity(Box.from(new BlockBox(pos.offset(dir))), entity -> entity instanceof PlayerEntity && !entity.isSpectator())) continue;
            if (!SettingUtils.getPlaceData(pos.offset(dir)).valid()) continue;
            if (!SettingUtils.inPlaceRange(pos.offset(dir))) continue;

            supportPositions.add(pos.offset(dir));
            return;
        }
    }

    private boolean hasSupport(BlockPos pos, boolean checkNext) {
        for (Direction dir : Direction.values()) {
            if (supportPositions.contains(pos.offset(dir)) || (checkNext && hasSupport(pos.offset(dir), false))) {
                return true;
            }
        }
        return false;
    }

    private void updateBlocks() {
        updateInsideBlocks();
        getSurroundBlocks();

        insideBlocks.forEach(pos -> surroundBlocks.add(pos.down()));
    }

    private void updateInsideBlocks() {
        insideBlocks.clear();

        addBlocks(getPos(), getSize(mc.player));

        if (extend.get()) {
            mc.world.getPlayers().stream().filter(player -> mc.player.distanceTo(player) < 5 && player != mc.player).sorted(Comparator.comparingDouble(player -> mc.player.distanceTo(player))).forEach(player -> {
                if (!intersects(player)) {
                    return;
                }

                addBlocks(player.getBlockPos(), getSize(player));
            });
        }
    }

    private boolean intersects(PlayerEntity player) {
        getSurroundBlocks();

        for (BlockPos pos : surroundBlocks) {
            if (player.getBoundingBox().intersects(Box.from(new BlockBox(pos)))) {
                return true;
            }
        }

        return false;
    }

    private void getSurroundBlocks() {
        surroundBlocks.clear();

        insideBlocks.forEach(pos -> {
            for (Direction dir : Direction.Type.HORIZONTAL) {

                if (!surroundBlocks.contains(pos.offset(dir)) && !insideBlocks.contains(pos.offset(dir))) {
                    surroundBlocks.add(pos.offset(dir));
                }
            }
        });
    }

    private void addBlocks(BlockPos pos, int[] size) {
        for (int x = size[0]; x <= size[1]; x++) {
            for (int z = size[2]; z <= size[3]; z++) {
                BlockPos p = pos.add(x, 0, z);

                if (mc.world.getBlockState(p).getBlock().getBlastResistance() > 600 && !p.equals(currentPos)) continue;

                if (!insideBlocks.contains(pos.add(x, 0, z).withY(currentPos.getY()))) {
                    insideBlocks.add(pos.add(x, 0, z).withY(currentPos.getY()));
                }
            }
        }
    }

    private boolean validEntity(Entity entity) {
        if (entity instanceof EndCrystalEntity && System.currentTimeMillis() - lastAttack < 100) {return false;}
        return !(entity instanceof ItemEntity);
    }

    private int[] getSize(PlayerEntity player) {
        int[] size = new int[4];

        double x = player.getX() - player.getBlockX();
        double z = player.getZ() - player.getBlockZ();

        if (x < 0.3) {
            size[0] = -1;
        }
        if (x > 0.7) {
            size[1] = 1;
        }
        if (z < 0.3) {
            size[2] = -1;
        }
        if (z > 0.7) {
            size[3] = 1;
        }

        return size;
    }

    public BlockPos getPos() {
        return new BlockPos(mc.player.getBlockX(), (int) Math.round(mc.player.getY()), mc.player.getBlockZ());
    }

    public record Render(BlockPos pos, long time) {}

    public enum SwitchMode {
        Disabled,
        Normal,
        Silent,
        PickSilent,
        InvSwitch
    }

    public enum VerticalToggleMode {
        Disabled,
        Up,
        Down,
        Any
    }

    public enum PlaceDelayMode {
        Ticks,
        Seconds
    }
}

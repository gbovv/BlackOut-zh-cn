package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.enums.SwingHand;
import kassuk.addon.blackout.events.PreRotationEvent;
import kassuk.addon.blackout.managers.Managers;
import kassuk.addon.blackout.timers.TimerList;
import kassuk.addon.blackout.utils.*;
import kassuk.addon.blackout.utils.meteor.BODamageUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.function.Predicate;

public class AnchorAuraRewrite extends BlackOutModule {
    public AnchorAuraRewrite() {
        super(BlackOut.BLACKOUT, "Anchor Aura Rewrite", "Automatically destroys people using anchors.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDamage = settings.createGroup("Damage");
    private final SettingGroup sgExtrapolation = settings.createGroup("Extrapolation");
    private final SettingGroup sgRender = settings.createGroup("Render");

    //--------------------General--------------------//
    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
        .name("暂停进食")
        .description("当进食时暂停模块")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("切换模式")
        .description("物品切换方式。静默模式最可靠但并非所有场景都适用")
        .defaultValue(SwitchMode.Silent)
        .build()
    );
    private final Setting<Boolean> packet = sgRender.add(new BoolSetting.Builder()
        .name("Packet")
        .description("Doesn't place blocks client side.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> placeSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("放置速度")
        .description("每秒放置锚的数量")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> explodeSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("爆炸速度")
        .description("每秒引爆锚的数量")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    //--------------------Damage--------------------//
    private final Setting<Double> minDmg = sgDamage.add(new DoubleSetting.Builder()
        .name("最小伤害")
        .description("触发放置的最低伤害值")
        .defaultValue(8)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> maxDmg = sgDamage.add(new DoubleSetting.Builder()
        .name("最大自伤")
        .description("可承受的最大自身伤害")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> minRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("最小伤害比例")
        .description("敌方伤害与自身伤害的最小比例（敌方/自身）")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    //--------------------Extrapolation--------------------//
    private final Setting<Integer> selfExt = sgExtrapolation.add(new IntSetting.Builder()
        .name("自身预测")
        .description("用于自身伤害预测的移动预测刻度数")
        .defaultValue(0)
        .range(0, 100)
        .sliderMax(20)
        .build()
    );
    private final Setting<Integer> extrapolation = sgExtrapolation.add(new IntSetting.Builder()
        .name("敌方预测")
        .description("用于敌方伤害预测的移动预测刻度数")
        .defaultValue(0)
        .range(0, 100)
        .sliderMax(20)
        .build()
    );
    private final Setting<Integer> extSmoothness = sgExtrapolation.add(new IntSetting.Builder()
        .name("预测平滑度")
        .description("预测运动平均计算所使用的历史刻度数")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    //--------------------Render--------------------//
    private final Setting<Boolean> placeSwing = sgRender.add(new BoolSetting.Builder()
        .name("放置挥动")
        .description("在放置方块时渲染挥动动画")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwingHand> placeHand = sgRender.add(new EnumSetting.Builder<SwingHand>()
        .name("挥动手部")
        .description("选择要挥动的手部")
        .defaultValue(SwingHand.真实手持)
        .visible(placeSwing::get)
        .build()
    );
    private final Setting<Boolean> interactSwing = sgRender.add(new BoolSetting.Builder()
        .name("交互挥动")
        .description("在与方块交互时渲染挥动动画")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwingHand> interactHand = sgRender.add(new EnumSetting.Builder<SwingHand>()
        .name("交互手部") 
        .description("选择要挥动的手部")
        .defaultValue(SwingHand.真实手持)
        .visible(interactSwing::get)
        .build()
    );
    public final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .description("选择要渲染的部分")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("渲染物的线条颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );
    public final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("填充颜色")
        .description("渲染物的侧面颜色")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    private final List<BlockPos> blocks = new ArrayList<>();
    private int lastIndex = 0;
    private int length = 0;
    private long tickTime = -1;
    private double bestDmg = -1;
    private long lastTime = 0;

    private BlockPos placePos = null;
    private PlaceData placeData = null;
    private BlockPos calcPos = null;
    private PlaceData calcData = null;
    private BlockPos renderPos = null;
    private final List<AbstractClientPlayerEntity> targets = new ArrayList<>();
    private final Map<AbstractClientPlayerEntity, Box> extMap = new HashMap<>();
    private final TimerList<Anchor> anchors = new TimerList<>();
    private BlockPos explodePos = null;
    private Direction explodeDir = null;

    private double dmg = 0;
    private double self = 0;
    private double friend = 0;

    private long lastPlace = 0;
    private long lastExplode = 0;

    public void onActivate() {
        anchors.clear();
        targets.clear();
        extMap.clear();
        placePos = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRotation(PreRotationEvent event) {
        calculate(length - 1);
        renderPos = calcPos;
        placePos = calcPos;
        placeData = calcData;

        getBlocks(mc.player.getEyePos(), Math.max(SettingUtils.getPlaceRange(), SettingUtils.getPlaceWallsRange()));

        // Reset stuff for next calculation
        tickTime = System.currentTimeMillis();
        length = blocks.size();
        lastIndex = 0;
        bestDmg = -1;
        calcPos = null;
        calcData = null;

        updateTargets();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        double delta = (System.currentTimeMillis() - lastTime) / 1000f;
        lastTime = System.currentTimeMillis();

        if (tickTime < 0 || mc.player == null || mc.world == null) return;

        update();

        if (renderPos != null && pauseCheck()) event.renderer.box(renderPos, color.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private boolean pauseCheck() {
        return !pauseEat.get() || !mc.player.isUsingItem();
    }

    private void updateTargets() {
        targets.clear();

        double closestDist = 1000;
        AbstractClientPlayerEntity closest;

        for (int i = 4; i > 0; i--) {
            closest = null;
            for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
                if (targets.contains(player)) continue;

                double dist = player.distanceTo(mc.player);

                if (dist > 15) continue;

                if (closest == null || dist < closestDist) {
                    closestDist = dist;
                    closest = player;
                }
            }
            if (closest != null) targets.add(closest);
        }
        ExtrapolationUtils.extrapolateMap(extMap, player -> player == mc.player ? selfExt.get() : extrapolation.get(), player -> extSmoothness.get());
    }

    private void getBlocks(Vec3d middle, double radius) {
        blocks.clear();
        int i = (int) Math.ceil(radius);

        for (int x = -i; x <= i; x++) {
            for (int y = -i; y <= i; y++) {
                for (int z = -i; z <= i; z++) {
                    BlockPos pos = new BlockPos((int) (Math.floor(middle.x) + x), (int) (Math.floor(middle.y) + y), (int) (Math.floor(middle.z) + z));

                    if (!OLEPOSSUtils.replaceable(pos) && !(mc.world.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR)) continue;
                    if (!inRangeToTargets(pos)) continue;

                    blocks.add(pos);
                }
            }
        }
    }

    private void update() {
        int index = Math.min((int) Math.ceil((System.currentTimeMillis() - tickTime) / 50f * length), length - 1);
        calculate(index);

        if (pauseEat.get() && pauseCheck()) return;

        updateExploding();
        updatePlacing();
    }

    private void updatePlacing() {
        if (System.currentTimeMillis() - lastPlace < 1000 / placeSpeed.get()) return;
        if (!placeData.valid()) return;
        if (placePos == null) return;

        Anchor a = getAnchor(placePos);
        if (a.state != AnchorState.Air) return;

        Hand hand = getHand(stack -> stack.getItem() == Items.RESPAWN_ANCHOR);
        FindItemResult result = getResult(switchMode.get(), stack -> stack.getItem() == Items.RESPAWN_ANCHOR);
        boolean present = hand != null || result.found();

        if (!present) return;

        if (SettingUtils.shouldRotate(RotationType.方块放置) && !Managers.ROTATION.start(placePos, priority, RotationType.方块放置, Objects.hash(name + "placing"))) return;

        boolean switched = false;
        if (hand == null) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    InvUtils.swap(result.slot(), true);
                    switched = true;
                }
                case PickSilent -> switched = BOInvUtils.pickSwitch(result.slot());
                case InvSwitch -> switched = BOInvUtils.invSwitch(result.slot());
            }
        }

        if (hand == null) {
            if (!switched) return;
            hand = Hand.MAIN_HAND;
        }

        placeBlock(hand, placeData.pos().toCenterPos(), placeData.dir(), placeData.pos());

        anchors.remove(t -> t.value.pos.equals(placePos));
        anchors.add(new Anchor(placePos, AnchorState.Anchor, 0), 0.5);

        lastPlace = System.currentTimeMillis();

        if (SettingUtils.shouldRotate(RotationType.方块放置)) Managers.ROTATION.end(Objects.hash(name + "placing"));
        if (placeSwing.get()) clientSwing(placeHand.get(), hand);

        if (switched) {
            switch (switchMode.get()) {
                case Silent -> InvUtils.swapBack();
                case PickSilent -> BOInvUtils.pickSwapBack();
                case InvSwitch -> BOInvUtils.swapBack();
            }
        }
    }

    private void updateExploding() {
        bestDmg = -1;

        explodePos = null;
        explodeDir = null;

        int i = (int) Math.ceil(Math.max(SettingUtils.getPlaceRange(), SettingUtils.getPlaceWallsRange()));

        for (int x = -i; x <= i; x++) {
            for (int y = -i; y <= i; y++) {
                for (int z = -i; z <= i; z++) {
                    BlockPos pos = BlockPos.ofFloored(mc.player.getEyePos());

                    Anchor anchor = get(pos);
                    if (anchor == null) return;
                    if (anchor.state != AnchorState.Anchor && anchor.state != AnchorState.Loaded) continue;

                    Direction dir = SettingUtils.getPlaceOnDirection(pos);
                    if (dir != null) return;

                    getDmg(pos);
                    if (!explodeDmgCheck()) continue;

                    bestDmg = dmg;
                    explodePos = pos;
                    explodeDir = dir;
                }
            }
        }


        explode();
    }

    private void explode() {
        Anchor anchor = getAnchor(explodePos);
        if (System.currentTimeMillis() - lastExplode < 1000 / explodeSpeed.get()) return;
        if (anchor.state == AnchorState.Air) return;

        Hand glowHand = getHand(stack -> stack.getItem() == Items.GLOWSTONE);
        Hand explodeHand = getHand(stack -> stack.getItem() != Items.GLOWSTONE);

        FindItemResult glowResult = getResult(switchMode.get(), stack -> stack.getItem() == Items.GLOWSTONE);
        FindItemResult explodeResult = getResult(switchMode.get(), stack -> stack.getItem() != Items.GLOWSTONE);

        boolean glowPresent = glowHand != null || glowResult.found();
        boolean explodePresent = explodeHand != null || explodeResult.found();

        if (!glowPresent || !explodePresent) return;

        if (SettingUtils.shouldRotate(RotationType.交互) && !Managers.ROTATION.start(placePos, priority, RotationType.交互, Objects.hash(name + "explode"))) return;

        boolean switched = false;
        if (glowHand == null) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    InvUtils.swap(glowResult.slot(), true);
                    switched = true;
                }
                case PickSilent -> switched = BOInvUtils.pickSwitch(glowResult.slot());
                case InvSwitch -> switched = BOInvUtils.invSwitch(glowResult.slot());
            }
        }

        if (glowHand == null) {
            if (!switched) return;
            glowHand = Hand.MAIN_HAND;
        }

        interactBlock(glowHand, explodePos.toCenterPos(), explodeDir, explodePos);

        Anchor a = anchors.remove(t -> t.value.pos.equals(placePos));
        anchors.add(new Anchor(placePos, AnchorState.Loaded, a.charges + 1), 0.5);

        if (switched) {
            switch (switchMode.get()) {
                case Silent -> InvUtils.swapBack();
                case PickSilent -> BOInvUtils.pickSwapBack();
                case InvSwitch -> BOInvUtils.swapBack();
            }
        }

        switched = false;

        if (explodeHand == null) {
            switch (switchMode.get()) {
                case Silent, Normal -> {
                    InvUtils.swap(explodeResult.slot(), true);
                    switched = true;
                }
                case PickSilent -> switched = BOInvUtils.pickSwitch(explodeResult.slot());
                case InvSwitch -> switched = BOInvUtils.invSwitch(explodeResult.slot());
            }
        }

        if (explodeHand == null) {
            if (!switched) return;
            explodeHand = Hand.MAIN_HAND;
        }

        interactBlock(explodeHand, explodePos.toCenterPos(), explodeDir, explodePos);

        lastExplode = System.currentTimeMillis();

        anchors.remove(t -> t.value.pos.equals(placePos));
        anchors.add(new Anchor(placePos, AnchorState.Air, 0), 0.5);
    }

    private Anchor getAnchor(BlockPos pos) {
        Anchor a = get(pos);
        if (a != null) {
            return a;
        }
        BlockState state = mc.world.getBlockState(pos);
        return new Anchor(pos, state.getBlock() == Blocks.RESPAWN_ANCHOR ? state.get(Properties.CHARGES) < 1 ? AnchorState.Anchor : AnchorState.Loaded : AnchorState.Air, state.getBlock() == Blocks.RESPAWN_ANCHOR ? state.get(Properties.CHARGES) : 0);
    }

    private Anchor get(BlockPos pos) {
        List<Anchor> list = anchors.getList();

        for (Anchor a : list) {
            if (a.pos.equals(pos)) return a;
        }
        return null;
    }

    private Hand getHand(Predicate<ItemStack> predicate) {
        if (predicate.test(Managers.HOLDING.getStack())) {
            return Hand.MAIN_HAND;
        }
        if (predicate.test(mc.player.getOffHandStack())) {
            return Hand.OFF_HAND;
        }
        return null;
    }

    private FindItemResult getResult(SwitchMode mode, Predicate<ItemStack> stackPredicate) {
        return switch (mode) {
            case Silent, Normal -> InvUtils.findInHotbar(stackPredicate);
            case PickSilent, InvSwitch -> InvUtils.find(stackPredicate);
            case Disabled -> null;
        };
    }

    private void calculate(int index) {
        for (int i = lastIndex; i < index; i++) {
            BlockPos pos = blocks.get(i);

            PlaceData data = SettingUtils.getPlaceData(pos);

            if (!data.valid()) continue;
            if (SettingUtils.inPlaceRange(data.pos())) continue;

            getDmg(pos);

            if (!placeDmgCheck()) continue;
            if (EntityUtils.intersectsWithEntity(new Box(pos), entity -> !(entity instanceof ItemEntity))) continue;

            calcData = data;
            calcPos = pos;
            bestDmg = dmg;
        }
        lastIndex = index;
    }

    private boolean inRangeToTargets(BlockPos pos) {
        for (AbstractClientPlayerEntity target : targets) {
            if (target.getPos().add(0, 1, 0).distanceTo(Vec3d.ofCenter(pos)) < 3.5) return true;
        }
        return false;
    }

    private boolean placeDmgCheck() {
        if (dmg < bestDmg) return false;
        if (dmg < minDmg.get()) return false;
        if (self > maxDmg.get()) return false;
        return dmg / self >= minRatio.get();
    }

    private boolean explodeDmgCheck() {
        if (dmg < bestDmg) return false;
        if (dmg < minDmg.get()) return false;
        if (self > maxDmg.get()) return false;
        return dmg / self >= minRatio.get();
    }

    private void getDmg(BlockPos pos) {
        dmg = -1;
        friend = -1;
        self = -1;

        targets.forEach(target -> {
            double d = BODamageUtils.anchorDamage(target, extMap.containsKey(target) ? extMap.get(target) : target.getBoundingBox(), pos.toCenterPos(), pos, true);

            if (target == mc.player) self = Math.max(self, d);
            else if (Friends.get().isFriend(target)) friend = Math.max(friend, d);
            else dmg = Math.max(dmg, d);
        });
    }

    public enum SwitchMode {
        Silent,
        Normal,
        PickSilent,
        InvSwitch,
        Disabled
    }

    public enum AnchorState {
        Air,
        Anchor,
        Loaded
    }

    private record Anchor(BlockPos pos, AnchorState state, int charges) { }
}

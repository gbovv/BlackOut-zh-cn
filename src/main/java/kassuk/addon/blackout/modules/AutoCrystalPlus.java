package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.enums.SwingHand;
import kassuk.addon.blackout.enums.SwingState;
import kassuk.addon.blackout.enums.SwingType;
import kassuk.addon.blackout.managers.Managers;
import kassuk.addon.blackout.mixins.IInteractEntityC2SPacket;
import kassuk.addon.blackout.timers.TimerList;
import kassuk.addon.blackout.utils.BOInvUtils;
import kassuk.addon.blackout.utils.ExtrapolationUtils;
import kassuk.addon.blackout.utils.OLEPOSSUtils;
import kassuk.addon.blackout.utils.SettingUtils;
import kassuk.addon.blackout.utils.meteor.BODamageUtils;
import kassuk.addon.blackout.utils.meteor.BOEntityUtils;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author OLEPOSSU
 */

public class AutoCrystalPlus extends BlackOutModule {
    public AutoCrystalPlus() {
        super(BlackOut.BLACKOUT, "自动水晶+", "自动放置并破坏水晶（优化版）.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("放置");
    private final SettingGroup sgExplode = settings.createGroup("爆炸");
    private final SettingGroup sgSwitch = settings.createGroup("切换");
    private final SettingGroup sgDamage = settings.createGroup("伤害计算");
    private final SettingGroup sgID = settings.createGroup("ID预测");
    private final SettingGroup sgExtrapolation = settings.createGroup("外推");
    private final SettingGroup sgRender = settings.createGroup("渲染");
    private final SettingGroup sgCompatibility = settings.createGroup("兼容性");
    private final SettingGroup sgDebug = settings.createGroup("调试");

    //--------------------General--------------------//
    private final Setting<Boolean> place = sgGeneral.add(new BoolSetting.Builder()
        .name("放置水晶")
        .description("自动放置末影水晶")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> explode = sgGeneral.add(new BoolSetting.Builder()
        .name("破坏水晶")
        .description("自动攻击末影水晶")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
        .name("进食暂停")
        .description("进食时暂停模块功能")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> performance = sgGeneral.add(new BoolSetting.Builder()
        .name("性能模式")
        .description("减少计算频率以提升运行效率")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> smartRot = sgGeneral.add(new BoolSetting.Builder()
        .name("智能旋转")
        .description("通过注视方块顶部提升计算速度")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> ignoreTerrain = sgGeneral.add(new BoolSetting.Builder()
        .name("无视地形")
        .description("穿透地形进行攻击以击杀敌人")
        .defaultValue(true)
        .build()
    );

    //--------------------Place--------------------//
    private final Setting<Boolean> instantPlace = sgPlace.add(new BoolSetting.Builder()
        .name("即时放置")
        .description("水晶消失后立即放置，忽略冷却时间")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> speedLimit = sgPlace.add(new DoubleSetting.Builder()
        .name("速度限制")
        .description("每秒最大放置数据包数量 (0 表示无限制)")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .visible(instantPlace::get)
        .build()
    );
    private final Setting<Double> placeSpeed = sgPlace.add(new DoubleSetting.Builder()
        .name("放置频率")
        .description("模块每秒尝试放置水晶的次数")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<DelayMode> placeDelayMode = sgPlace.add(new EnumSetting.Builder<DelayMode>()
        .name("放置延迟模式")
        .description("延迟计算方式：秒或游戏刻")
        .defaultValue(DelayMode.Seconds)
        .build()
    );
    private final Setting<Double> placeDelay = sgPlace.add(new DoubleSetting.Builder()
        .name("放置延迟")
        .description("攻击水晶后等待多少秒再进行放置")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> placeDelayMode.get() == DelayMode.Seconds)
        .build()
    );
    private final Setting<Integer> placeDelayTicks = sgPlace.add(new IntSetting.Builder()
        .name("放置延迟刻数")
        .description("水晶存在多少游戏刻后才进行攻击")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .visible(() -> placeDelayMode.get() == DelayMode.Ticks)
        .build()
    );
    private final Setting<Double> slowDamage = sgPlace.add(new DoubleSetting.Builder()
        .name("低速伤害阈值")
        .description("当目标承受伤害低于此值时切换至低速模式")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> slowSpeed = sgPlace.add(new DoubleSetting.Builder()
        .name("低速频率")
        .description("伤害低于阈值时模块的每秒放置次数")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    //--------------------Explode--------------------//
    private final Setting<Boolean> onlyOwn = sgExplode.add(new BoolSetting.Builder()
        .name("仅攻击己方")
        .description("只攻击自己放置的水晶")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> inhibit = sgExplode.add(new BoolSetting.Builder()
        .name("存在时间模式")
        .description("水晶存在时间的计算方式：秒或游戏刻")
        .defaultValue(false)
        .build()
    );
    private final Setting<DelayMode> existedMode = sgExplode.add(new EnumSetting.Builder<DelayMode>()
        .name("存在时间模式")
        .description("水晶存在时间的计算方式：秒或游戏刻")
        .defaultValue(DelayMode.Seconds)
        .build()
    );
    private final Setting<Double> existed = sgExplode.add(new DoubleSetting.Builder()
        .name("存在时间(秒)")
        .description("攻击前水晶需存在的最小秒数")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> existedMode.get() == DelayMode.Seconds)
        .build()
    );
    private final Setting<Integer> existedTicks = sgExplode.add(new IntSetting.Builder()
        .name("存在时间(刻)")
        .description("攻击前水晶需存在的最小游戏刻数")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .visible(() -> existedMode.get() == DelayMode.Ticks)
        .build()
    );
    private final Setting<SequentialMode> sequential = sgExplode.add(new EnumSetting.Builder<SequentialMode>()
        .name("顺序模式")
        .description("同一游戏刻内不进行放置和攻击操作")
        .defaultValue(SequentialMode.Disabled)
        .build()
    );
    private final Setting<Boolean> instantAttack = sgExplode.add(new BoolSetting.Builder()
        .name("即时攻击")
        .description("首次攻击不计算延迟时间")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> expSpeedLimit = sgExplode.add(new DoubleSetting.Builder()
        .name("爆炸速度限制")
        .description("每秒最多攻击次数 (0 表示无限制)")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .visible(instantAttack::get)
        .build()
    );
    private final Setting<Double> expSpeed = sgExplode.add(new DoubleSetting.Builder()
        .name("爆炸频率") 
        .description("模块每秒尝试攻击水晶的次数")
        .defaultValue(4)
        .range(0.01, 20)
        .sliderRange(0.01, 20)
        .build()
    );
    private final Setting<Boolean> setDead = sgExplode.add(new BoolSetting.Builder()
        .name("隐藏水晶")
        .description("攻击后隐藏水晶实体（通常无需启用）")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> setDeadDelay = sgExplode.add(new DoubleSetting.Builder()
        .name("隐藏延迟")
        .description("攻击后等待多少秒隐藏水晶")
        .defaultValue(0.05)
        .range(0, 1)
        .sliderRange(0, 1)
        .visible(setDead::get)
        .build()
    );

    //--------------------Switch--------------------//
    private final Setting<SwitchMode> switchMode = sgSwitch.add(new EnumSetting.Builder<SwitchMode>()
        .name("切换模式")
        .description("切换至主手水晶的模式")
        .defaultValue(SwitchMode.Disabled)
        .build()
    );
    private final Setting<Double> switchPenalty = sgSwitch.add(new DoubleSetting.Builder()
        .name("切换惩罚")
        .description("切换武器后等待多少秒再攻击水晶")
        .defaultValue(0.25)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    //--------------------Damage--------------------//
    private final Setting<DmgCheckMode> dmgCheckMode = sgDamage.add(new EnumSetting.Builder<DmgCheckMode>()
        .name("伤害检查模式")
        .description("放置安全等级检查（normal为常规模式）")
        .defaultValue(DmgCheckMode.Normal)
        .build()
    );
    private final Setting<Double> minPlace = sgDamage.add(new DoubleSetting.Builder()
        .name("最低放置伤害")
        .description("执行放置操作所需的最低伤害值")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> maxPlace = sgDamage.add(new DoubleSetting.Builder()
        .name("最大自身伤害")
        .description("允许放置时对自身造成的最大伤害")
        .defaultValue(8)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> minPlaceRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("最低伤害比率")
        .description("敌方与自身伤害比值的下限（敌方伤害/自身伤害）")
        .defaultValue(1.4)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );
    private final Setting<Double> maxFriendPlace = sgDamage.add(new DoubleSetting.Builder()
        .name("友方最大伤害")
        .description("允许放置时对友方造成的最大伤害")
        .defaultValue(8)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> minFriendPlaceRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("友方最低比率")
        .description("敌方与友方伤害比值的下限（敌方伤害/友方伤害）")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );
    private final Setting<ExplodeMode> expMode = sgDamage.add(new EnumSetting.Builder<ExplodeMode>()
        .name("爆炸伤害模式")
        .description("爆炸伤害检查的详细程度")
        .defaultValue(ExplodeMode.FullCheck)
        .build()
    );
    private final Setting<Double> minExplode = sgDamage.add(new DoubleSetting.Builder()
        .name("最低爆炸伤害")
        .description("触发水晶爆炸的最低敌方伤害值")
        .defaultValue(2.5)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> maxExp = sgDamage.add(new DoubleSetting.Builder()
        .name("最大自身爆炸伤害")
        .description("允许爆炸时对自身造成的最大伤害")
        .defaultValue(9)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> minExpRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("爆炸最低比率")
        .description("敌方与自身伤害比值的下限（敌方伤害/自身伤害）")
        .defaultValue(1.1)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );
    private final Setting<Double> maxFriendExp = sgDamage.add(new DoubleSetting.Builder()
        .name("友方最大爆炸伤害")
        .description("允许爆炸时对友方造成的最大伤害")
        .defaultValue(12)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Double> minFriendExpRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("友方爆炸最低比率")
        .description("敌方与友方伤害比值的下限（敌方伤害/友方伤害）")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );
    private final Setting<Double> forcePop = sgDamage.add(new DoubleSetting.Builder()
        .name("强制击破")
        .description("当敌方在X次攻击内可被击杀时忽略伤害检查")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> antiFriendPop = sgDamage.add(new DoubleSetting.Builder()
        .name("友方击破保护")
        .description("当友方在X次攻击内可被击杀时取消操作")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> antiSelfPop = sgDamage.add(new DoubleSetting.Builder()
        .name("自身击破保护")
        .description("当自身在X次攻击内可被击杀时取消操作")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    //--------------------ID-Predict--------------------//
    private final Setting<Boolean> idPredict = sgID.add(new BoolSetting.Builder()
        .name("ID预测")
        .description("在水晶生成前进行预攻击")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> idStartOffset = sgID.add(new IntSetting.Builder()
        .name("起始ID偏移量")
        .description("预攻击时提前多少个实体ID")
        .defaultValue(1)
        .min(0)
        .sliderMax(10)
        .build()
    );
    private final Setting<Integer> idOffset = sgID.add(new IntSetting.Builder()
        .name("数据包ID间隔")
        .description("相邻数据包之间预攻击的ID间隔数")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .build()
    );
    private final Setting<Integer> idPackets = sgID.add(new IntSetting.Builder()
        .name("预攻击数据包")
        .description("每次触发时发送的预攻击数据包数量")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .build()
    );
    private final Setting<Double> idDelay = sgID.add(new DoubleSetting.Builder()
        .name("预攻击起始延迟")
        .description("开始发送预攻击数据包前的等待时间（秒）")
        .defaultValue(0.05)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );
    private final Setting<Double> idPacketDelay = sgID.add(new DoubleSetting.Builder()
        .name("数据包间隔延迟")
        .description("相邻预攻击数据包之间的发送间隔时间（秒）")
        .defaultValue(0.05)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    //--------------------Extrapolation--------------------//
    private final Setting<Integer> selfExt = sgExtrapolation.add(new IntSetting.Builder()
        .name("自身运动预测")
        .description("用于自身伤害检查的运动预测游戏刻数")
        .defaultValue(0)
        .range(0, 100)
        .sliderMax(20)
        .build()
    );
    private final Setting<Integer> extrapolation = sgExtrapolation.add(new IntSetting.Builder()
        .name("敌方运动预测")
        .description("用于敌方伤害检查的运动预测游戏刻数")
        .defaultValue(0)
        .range(0, 100)
        .sliderMax(20)
        .build()
    );
    private final Setting<Integer> rangeExtrapolation = sgExtrapolation.add(new IntSetting.Builder()
        .name("攻击距离预测")
        .description("放置前攻击距离计算使用的运动预测游戏刻数")
        .defaultValue(0)
        .range(0, 100)
        .sliderMax(20)
        .build()
    );
    private final Setting<Integer> hitboxExtrapolation = sgExtrapolation.add(new IntSetting.Builder()
        .name("碰撞箱预测")
        .description("放置检查时碰撞箱计算的运动预测游戏刻数")
        .defaultValue(0)
        .range(0, 100)
        .sliderMax(20)
        .build()
    );
    private final Setting<Integer> extSmoothness = sgExtrapolation.add(new IntSetting.Builder()
        .name("预测平滑度")
        .description("运动预测计算中使用前N帧的平均值")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    //--------------------Render--------------------//
    private final Setting<Boolean> placeSwing = sgRender.add(new BoolSetting.Builder()
    .name("放置挥动动画")
    .description("放置水晶时显示挥动手臂的动画效果")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwingHand> placeHand = sgRender.add(new EnumSetting.Builder<SwingHand>()
    .name("放置手持方式")
    .description("选择放置水晶时挥动的手臂")
        .defaultValue(SwingHand.真实手持)
        .visible(placeSwing::get)
        .build()
    );
    private final Setting<Boolean> attackSwing = sgRender.add(new BoolSetting.Builder()
        .name("攻击挥动动画")
        .description("攻击水晶时显示挥动手臂的动画效果")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwingHand> attackHand = sgRender.add(new EnumSetting.Builder<SwingHand>()
    .name("攻击手持方式")
    .description("选择攻击水晶时挥动的手臂")
        .defaultValue(SwingHand.真实手持)
        .visible(attackSwing::get)
        .build()
    );
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
    .name("渲染显示")
    .description("是否渲染放置水晶的框体效果")
        .defaultValue(true)
        .build()
    );
    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
    .name("渲染模式")
    .description("选择框体的渲染显示模式")
        .defaultValue(RenderMode.BlackOut)
        .build()
    );
    private final Setting<Double> renderTime = sgRender.add(new DoubleSetting.Builder()
    .name("高亮时长")
    .description("框体保持完全可见的时间（秒）")
        .defaultValue(0.3)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> renderMode.get().equals(RenderMode.Earthhack) || renderMode.get().equals(RenderMode.Future))
        .build()
    );
    private final Setting<FadeMode> fadeMode = sgRender.add(new EnumSetting.Builder<FadeMode>()
    .name("渐隐模式")
    .description("选择框体的渐隐方式")
        .defaultValue(FadeMode.Normal)
        .visible(() -> renderMode.get() == RenderMode.BlackOut)
        .build()
    );
    private final Setting<EarthFadeMode> earthFadeMode = sgRender.add(new EnumSetting.Builder<EarthFadeMode>()
    .name("地影渐隐模式")
    .description("选择地影渲染模式下框体的渐隐方式")
        .defaultValue(EarthFadeMode.Normal)
        .visible(() -> renderMode.get() == RenderMode.Earthhack)
        .build()
    );
    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
    .name("渐隐时长")
    .description("框体完全渐隐所需时间（秒）")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> renderMode.get().equals(RenderMode.Earthhack) || renderMode.get().equals(RenderMode.Future))
        .build()
    );
    private final Setting<Double> animationSpeed = sgRender.add(new DoubleSetting.Builder()
    .name("动画移动速度")
    .description("黑幕模式框体的移动动画速率")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> renderMode.get().equals(RenderMode.BlackOut))
        .build()
    );
    private final Setting<Double> animationMoveExponent = sgRender.add(new DoubleSetting.Builder()
    .name("移动速率指数")
    .description("距离目标越远时移动速度越快")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> renderMode.get().equals(RenderMode.BlackOut))
        .build()
    );
    private final Setting<Double> animationExponent = sgRender.add(new DoubleSetting.Builder()
    .name("缩放速率指数")
    .description("黑幕模式框体的缩放动画速率")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> renderMode.get().equals(RenderMode.BlackOut))
        .build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
    .name("显示模式")
    .description("选择框体的渲染组成部分")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
    .name("线框颜色")
    .description("渲染框体的线框颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );
    public final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
    .name("填充颜色")
        .description("渲染框体的填充颜色")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    //--------------------Compatibility--------------------//
    private final Setting<Double> autoMineDamage = sgCompatibility.add(new DoubleSetting.Builder()
    .name("自动挖掘伤害")
    .description("优先在自动挖掘目标方块上放置水晶")
        .defaultValue(1.1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );
    private final Setting<Boolean> amPlace = sgCompatibility.add(new BoolSetting.Builder()
    .name("自动挖掘放置")
    .description("在方块实际被破坏前忽略自动挖掘目标方块")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> amProgress = sgCompatibility.add(new DoubleSetting.Builder()
        .name("Auto Mine Progress")
        .description("Ignores the block after it has reached this progress.")
        .defaultValue(0.95)
        .range(0, 1)
        .sliderRange(0, 1)
        .visible(amPlace::get)
        .build()
    );
    private final Setting<Boolean> amSpam = sgCompatibility.add(new BoolSetting.Builder()
    .name("自动挖掘连放")
    .description("在方块被破坏前连续放置水晶")
        .defaultValue(false)
        .visible(amPlace::get)
        .build()
    );
    private final Setting<AutoMineBrokenMode> amBroken = sgCompatibility.add(new EnumSetting.Builder<AutoMineBrokenMode>()
    .name("自动挖掘中断")
    .description("不在已中断的自动挖掘方块上放置")
        .defaultValue(AutoMineBrokenMode.Near)
        .build()
    );
    private final Setting<Boolean> paAttack = sgCompatibility.add(new BoolSetting.Builder()
    .name("活塞水晶攻击")
    .description("不攻击由活塞水晶放置的水晶")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> paPlace = sgCompatibility.add(new BoolSetting.Builder()
    .name("活塞水晶放置")
    .description("当活塞水晶启用时不进行放置")
        .defaultValue(true)
        .build()
    );

    //--------------------Debug--------------------//
       private final Setting<Boolean> renderExt = sgDebug.add(new BoolSetting.Builder()
       .name("运动预测渲染")
       .description("在玩家的预测位置上渲染框体")
       .defaultValue(false)
       .build()
   );
   private final Setting<Boolean> renderSelfExt = sgDebug.add(new BoolSetting.Builder()
       .name("自身运动预测渲染")
       .description("在自身预测位置上渲染框体")
       .defaultValue(false)
       .build()
   );

    private long ticksEnabled = 0;
    private double placeTimer = 0;
    private double placeLimitTimer = 0;
    private double delayTimer = 0;
    private int delayTicks = 0;

    private BlockPos placePos = null;
    private Direction placeDir = null;
    private Entity expEntity = null;
    private final TimerList<Integer> attackedList = new TimerList<>();
    private final TimerList<Integer> inhibitList = new TimerList<>();
    private final Map<BlockPos, Long> existedList = new HashMap<>();
    private final Map<BlockPos, Long> existedTicksList = new HashMap<>();
    private final Map<BlockPos, Long> own = new HashMap<>();
    private final Map<AbstractClientPlayerEntity, Box> extPos = new HashMap<>();
    private final Map<AbstractClientPlayerEntity, Box> extHitbox = new HashMap<>();
    private Vec3d rangePos = null;
    private final List<Box> blocked = new ArrayList<>();
    private final Map<BlockPos, Double[]> earthMap = new HashMap<>();
    private double attackTimer = 0;
    private double switchTimer = 0;
    private int confirmed = Integer.MIN_VALUE;
    private long lastMillis = System.currentTimeMillis();
    private boolean suicide = false;
    public static boolean placing = false;
    private long lastAttack = 0;

    private Vec3d renderTarget = null;
    private Vec3d renderPos = null;
    private double renderProgress = 0;

    private AutoMine autoMine = null;

    private int placed = 0;

    private double cps = 0;
    private final List<Long> explosions = Collections.synchronizedList(new ArrayList<>());

    private final List<Predict> predicts = new ArrayList<>();
    private final List<SetDead> setDeads = new ArrayList<>();

    @Override
    public void onActivate() {
        super.onActivate();
        ticksEnabled = 0;

        earthMap.clear();
        existedTicksList.clear();
        existedList.clear();
        blocked.clear();
        extPos.clear();
        own.clear();
        renderPos = null;
        renderProgress = 0;
        lastMillis = System.currentTimeMillis();
        attackedList.clear();
        lastAttack = 0;

        predicts.clear();
        setDeads.clear();
    }

    @Override
    public String getInfoString() {
        return String.format("%.1f", cps);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTickPost(TickEvent.Post event) {
        delayTicks++;
        ticksEnabled++;
        placed++;

        if (mc.player == null || mc.world == null) return;

        if (autoMine == null) autoMine = Modules.get().get(AutoMine.class);

        ExtrapolationUtils.extrapolateMap(extPos, player -> player == mc.player ? selfExt.get() : extrapolation.get(), player -> extSmoothness.get());
        ExtrapolationUtils.extrapolateMap(extHitbox, player -> hitboxExtrapolation.get(), player -> extSmoothness.get());

        Box rangeBox = ExtrapolationUtils.extrapolate(mc.player, rangeExtrapolation.get(), extSmoothness.get());
        if (rangeBox == null) rangePos = mc.player.getEyePos();
        else rangePos = new Vec3d((rangeBox.minX + rangeBox.maxX) / 2f, rangeBox.minY + mc.player.getEyeHeight(mc.player.getPose()), (rangeBox.minZ + rangeBox.maxZ) / 2f);

        List<BlockPos> toRemove = new ArrayList<>();
        existedList.forEach((key, val) -> {
            if (System.currentTimeMillis() - val >= 5000 + existed.get() * 1000)
                toRemove.add(key);
        });
        toRemove.forEach(existedList::remove);

        toRemove.clear();
        existedTicksList.forEach((key, val) -> {
            if (ticksEnabled - val >= 100 + existedTicks.get())
                toRemove.add(key);
        });
        toRemove.forEach(existedTicksList::remove);

        toRemove.clear();
        own.forEach((key, val) -> {
            if (System.currentTimeMillis() - val >= 5000)
                toRemove.add(key);
        });
        toRemove.forEach(own::remove);

        if (performance.get()) updatePlacement();
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onRender3D(Render3DEvent event) {
        attackedList.update();
        inhibitList.update();

        if (autoMine == null) autoMine = Modules.get().get(AutoMine.class);

        suicide = Modules.get().isActive(Suicide.class);
        double delta = (System.currentTimeMillis() - lastMillis) / 1000f;
        lastMillis = System.currentTimeMillis();

        cps = 0;
        synchronized (explosions) {
            explosions.removeIf(time -> {
                double p = (System.currentTimeMillis() - time) / 1000D;

                if (p >= 5) return true;

                double d = p <= 4 ? 1 : 1 - (p - 4);
                cps += d;
                return false;
            });
        }
        cps /= 4.5;

        attackTimer = Math.max(attackTimer - delta, 0);
        placeTimer = Math.max(placeTimer - delta * getSpeed(), 0);
        placeLimitTimer += delta;
        delayTimer += delta;
        switchTimer = Math.max(0, switchTimer - delta);

        update();
        checkDelayed();

        //Rendering
        if (render.get()) {
            switch (renderMode.get()) {
                case BlackOut -> {
                    if (placePos != null && !isPaused() && holdingCheck()) {
                        renderProgress = Math.min(1, renderProgress + delta);
                        renderTarget = new Vec3d(placePos.getX(), placePos.getY(), placePos.getZ());
                    } else {
                        renderProgress = Math.max(0, renderProgress - delta);
                    }

                    if (renderTarget != null) {
                        renderPos = smoothMove(renderPos, renderTarget, delta * animationSpeed.get() * 5);
                    }

                    if (renderPos != null) {
                        double r = 0.5 - Math.pow(1 - renderProgress, animationExponent.get()) / 2f;

                        if (r >= 0.001) {
                            double down = -0.5;
                            double up = -0.5;
                            double width = 0.5;

                            switch (fadeMode.get()) {
                                case Up -> {
                                    up = 0;
                                    down = -(r * 2);
                                }
                                case Down -> {
                                    up = -1 + r * 2;
                                    down = -1;
                                }
                                case Normal -> {
                                    up = -0.5 + r;
                                    down = -0.5 - r;
                                    width = r;
                                }
                            }
                            Box box = new Box(renderPos.getX() + 0.5 - width, renderPos.getY() + down, renderPos.getZ() + 0.5 - width,
                                renderPos.getX() + 0.5 + width, renderPos.getY() + up, renderPos.getZ() + 0.5 + width);

                            event.renderer.box(box, new Color(color.get().r, color.get().g, color.get().b, color.get().a), lineColor.get(), shapeMode.get(), 0);
                        }
                    }
                }
                case Future -> {
                    if (placePos != null && !isPaused() && holdingCheck()) {
                        renderPos = new Vec3d(placePos.getX(), placePos.getY(), placePos.getZ());
                        renderProgress = fadeTime.get() + renderTime.get();
                    } else {
                        renderProgress = Math.max(0, renderProgress - delta);
                    }

                    if (renderProgress > 0 && renderPos != null) {
                        event.renderer.box(new Box(renderPos.getX(), renderPos.getY() - 1, renderPos.getZ(),
                                renderPos.getX() + 1, renderPos.getY(), renderPos.getZ() + 1),
                            new Color(color.get().r, color.get().g, color.get().b, (int) Math.round(color.get().a * Math.min(1, renderProgress / fadeTime.get()))),
                            new Color(lineColor.get().r, lineColor.get().g, lineColor.get().b, (int) Math.round(lineColor.get().a * Math.min(1, renderProgress / fadeTime.get()))), shapeMode.get(), 0);
                    }
                }
                case Earthhack -> {
                    List<BlockPos> toRemove = new ArrayList<>();
                    for (Map.Entry<BlockPos, Double[]> entry : earthMap.entrySet()) {
                        BlockPos pos = entry.getKey();
                        Double[] alpha = entry.getValue();
                        if (alpha[0] <= delta) {
                            toRemove.add(pos);
                        } else {
                            double r = Math.min(1, alpha[0] / alpha[1]) / 2f;
                            double down = -0.5;
                            double up = -0.5;
                            double width = 0.5;

                            switch (earthFadeMode.get()) {
                                case Normal -> {
                                    up = 1;
                                    down = 0;
                                }
                                case Up -> {
                                    up = 1;
                                    down = 1 - (r * 2);
                                }
                                case Down -> {
                                    up = r * 2;
                                    down = 0;
                                }
                                case Shrink -> {
                                    up = 0.5 + r;
                                    down = 0.5 - r;
                                    width = r;
                                }
                            }

                            Box box = new Box(pos.getX() + 0.5 - width, pos.getY() + down, pos.getZ() + 0.5 - width,
                                pos.getX() + 0.5 + width, pos.getY() + up, pos.getZ() + 0.5 + width);

                            event.renderer.box(box,
                                new Color(color.get().r, color.get().g, color.get().b, (int) Math.round(color.get().a * Math.min(1, alpha[0] / alpha[1]))),
                                new Color(lineColor.get().r, lineColor.get().g, lineColor.get().b, (int) Math.round(lineColor.get().a * Math.min(1, alpha[0] / alpha[1]))), shapeMode.get(), 0);
                            entry.setValue(new Double[]{alpha[0] - delta, alpha[1]});
                        }
                    }
                    toRemove.forEach(earthMap::remove);
                }
            }
        }

        if (mc.player != null) {
            //Render extrapolation
            if (renderExt.get()) {
                extPos.forEach((name, bb) -> {
                    if (renderSelfExt.get() || !name.equals(mc.player))
                        event.renderer.box(bb, color.get(), lineColor.get(), shapeMode.get(), 0);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onEntity(EntityAddedEvent event) {
        confirmed = event.entity.getId();

        if (event.entity.getBlockPos().equals(placePos)) explosions.add(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSend(PacketEvent.Send event) {
        if (mc.player != null && mc.world != null) {
            if (event.packet instanceof UpdateSelectedSlotC2SPacket) {
                switchTimer = switchPenalty.get();
            }

            if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {

                if (!(packet.getHand() == Hand.MAIN_HAND ? Managers.HOLDING.isHolding(Items.END_CRYSTAL) : mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL))
                    return;

                if (isOwn(packet.getBlockHitResult().getBlockPos().up())) own.remove(packet.getBlockHitResult().getBlockPos().up());

                own.put(packet.getBlockHitResult().getBlockPos().up(), System.currentTimeMillis());
                blocked.add(OLEPOSSUtils.getCrystalBox(packet.getBlockHitResult().getBlockPos().up()));
                addExisted(packet.getBlockHitResult().getBlockPos().up());
            }
        }
    }

    // Other stuff
    private void update() {
        placing = false;
        expEntity = null;

        Hand hand = getHand(stack -> stack.getItem() == Items.END_CRYSTAL);

        Hand handToUse = hand;
        if (!performance.get()) updatePlacement();

        switch (switchMode.get()) {
            case Simple -> {
                int slot = InvUtils.findInHotbar(Items.END_CRYSTAL).slot();
                if (placePos != null && hand == null && slot >= 0) {
                    InvUtils.swap(slot, false);
                    handToUse = Hand.MAIN_HAND;
                }
            }
            case Gapple -> {
                int gapSlot = InvUtils.findInHotbar(OLEPOSSUtils::isGapple).slot();
                if (mc.options.useKey.isPressed() && Managers.HOLDING.isHolding(Items.END_CRYSTAL, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE) && gapSlot >= 0) {
                    if (getHand(OLEPOSSUtils::isGapple) == null)
                        InvUtils.swap(gapSlot, false);
                    handToUse = getHand(itemStack -> itemStack.getItem() == Items.END_CRYSTAL);
                } else if (Managers.HOLDING.isHolding(Items.END_CRYSTAL, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE)) {
                    int slot = InvUtils.findInHotbar(Items.END_CRYSTAL).slot();
                    if (placePos != null && hand == null && slot >= 0) {
                        InvUtils.swap(slot, false);
                        handToUse = Hand.MAIN_HAND;
                    }
                }
            }
        }

        if (placePos != null && placeDir != null) {
            if (!isPaused() && (!paPlace.get() || !Modules.get().isActive(PistonCrystal.class))) {
                int silentSlot = InvUtils.find(itemStack -> itemStack.getItem() == Items.END_CRYSTAL).slot();
                int hotbar = InvUtils.findInHotbar(Items.END_CRYSTAL).slot();
                if (handToUse != null || (switchMode.get() == SwitchMode.Silent && hotbar >= 0) || ((switchMode.get() == SwitchMode.PickSilent || switchMode.get() == SwitchMode.InvSilent) && silentSlot >= 0)) {
                    placing = true;
                    if (!SettingUtils.shouldRotate(RotationType.交互) || Managers.ROTATION.start(placePos.down(), smartRot.get() ? new Vec3d(placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5) : null, priority, RotationType.交互, Objects.hash(name + "placing"))) {
                        if (speedCheck() && delayCheck())
                            placeCrystal(placePos.down(), placeDir, handToUse, silentSlot, hotbar);
                    }
                }
            }
        }

        PistonCrystal pa = Modules.get().get(PistonCrystal.class);
        double[] value = null;

        if (!isPaused() && (hand != null || switchMode.get() == SwitchMode.Silent || switchMode.get() == SwitchMode.PickSilent || switchMode.get() == SwitchMode.InvSilent) && explode.get()) {
            for (Entity en : mc.world.getEntities()) {
                if (!(en instanceof EndCrystalEntity)) continue;
                if (paAttack.get() && pa.isActive() && en.getBlockPos().equals(pa.crystalPos)) continue;
                if (inhibitList.contains(en.getId())) continue;
                if (switchTimer > 0) continue;

                double[] dmg = getDmg(en.getPos(), true)[0];

                if (!canExplode(en.getPos())) continue;

                if ((expEntity == null || value == null) || ((dmgCheckMode.get().equals(DmgCheckMode.Normal) && dmg[0] > value[0]) || (dmgCheckMode.get().equals(DmgCheckMode.Safe) && dmg[2] / dmg[0] < value[2] / dmg[0]))) {
                    expEntity = en;
                    value = dmg;
                }
            }
        }

        if (expEntity != null) {
            if (multiTaskCheck() && !isAttacked(expEntity.getId()) && attackDelayCheck() && existedCheck(expEntity.getBlockPos())) {
                if (!SettingUtils.shouldRotate(RotationType.攻击) || startAttackRot()) {
                    explode(expEntity.getId(), expEntity.getPos());
                }
            }
        } else if (SettingUtils.shouldRotate(RotationType.攻击)) Managers.ROTATION.end(Objects.hash(name + "attacking"));
    }

    private boolean attackDelayCheck() {
        if (instantAttack.get())
            return expSpeedLimit.get() <= 0 || System.currentTimeMillis() > lastAttack + 1000 / expSpeedLimit.get();
        else
            return System.currentTimeMillis() > lastAttack + 1000 / expSpeed.get();
    }

    private boolean startAttackRot() {
        return (Managers.ROTATION.start(expEntity.getBoundingBox(), smartRot.get() ? expEntity.getPos() : null, priority + (!isAttacked(expEntity.getId()) && blocksPlacePos(expEntity) ? -0.1 : 0.1), RotationType.攻击, Objects.hash(name + "attacking")));
    }

    private boolean blocksPlacePos(Entity entity) {
        return placePos != null && entity.getBoundingBox().intersects(new Box(placePos.getX(), placePos.getY(), placePos.getZ(), placePos.getX() + 1, placePos.getY() + (SettingUtils.cc() ? 1 : 2), placePos.getZ() + 1));
    }

    private boolean isAlive(Box box) {
        if (box == null) return true;

        for (Entity en : mc.world.getEntities()) {
            if (!(en instanceof EndCrystalEntity)) continue;
            if (bbEquals(box, en.getBoundingBox())) return true;
        }
        return false;
    }

    private boolean bbEquals(Box box1, Box box2) {
        return box1.minX == box2.minX &&
            box1.minY == box2.minY &&
            box1.minZ == box2.minZ &&
            box1.maxX == box2.maxX &&
            box1.maxY == box2.maxY &&
            box1.maxZ == box2.maxZ;
    }

    private boolean speedCheck() {

        if (speedLimit.get() > 0 && placeLimitTimer < 1 / speedLimit.get())
            return false;

        if (instantPlace.get() && !shouldSlow() && !isBlocked(placePos))
            return true;

        return placeTimer <= 0;
    }

    private boolean holdingCheck() {
        return switch (switchMode.get()) {
            case Silent -> InvUtils.findInHotbar(Items.END_CRYSTAL).slot() >= 0;
            case PickSilent, InvSilent -> InvUtils.find(Items.END_CRYSTAL).slot() >= 0;
            default -> getHand(itemStack -> itemStack.getItem() == Items.END_CRYSTAL) != null;
        };
    }

    private void updatePlacement() {
        if (!place.get()) {
            placePos = null;
            placeDir = null;
            return;
        }
        placePos = getPlacePos();
    }

    private void placeCrystal(BlockPos pos, Direction dir, Hand handToUse, int sl, int hsl) {
        if (pos != null && mc.player != null) {
            if (renderMode.get().equals(RenderMode.Earthhack)) {
                if (!earthMap.containsKey(pos))
                    earthMap.put(pos, new Double[]{fadeTime.get() + renderTime.get(), fadeTime.get()});
                else
                    earthMap.replace(pos, new Double[]{fadeTime.get() + renderTime.get(), fadeTime.get()});
            }

            blocked.add(new Box(pos.getX() - 0.5, pos.getY() + 1, pos.getZ() - 0.5, pos.getX() + 1.5, pos.getY() + 2, pos.getZ() + 1.5));

            boolean switched = handToUse == null;
            if (switched) {
                switch (switchMode.get()) {
                    case PickSilent -> BOInvUtils.pickSwitch(sl);
                    case Silent -> InvUtils.swap(hsl, true);
                    case InvSilent -> BOInvUtils.invSwitch(sl);
                }
            }

            addExisted(pos.up());

            if (!isOwn(pos.up())) own.put(pos.up(), System.currentTimeMillis());
            else {
                own.remove(pos.up());
                own.put(pos.up(), System.currentTimeMillis());
            }

            placeLimitTimer = 0;
            placeTimer = 1;
            placed = 0;

            interactBlock(switched ? Hand.MAIN_HAND : handToUse, pos.toCenterPos(), dir, pos);

            if (placeSwing.get()) clientSwing(placeHand.get(), switched ? Hand.MAIN_HAND : handToUse);

            if (SettingUtils.shouldRotate(RotationType.交互))
                Managers.ROTATION.end(Objects.hash(name + "placing"));

            if (switched) {
                switch (switchMode.get()) {
                    case PickSilent -> BOInvUtils.pickSwapBack();
                    case Silent -> InvUtils.swapBack();
                    case InvSilent -> BOInvUtils.swapBack();
                }
            }
            if (idPredict.get()) {
                int highest = getHighest();

                int id = highest + idStartOffset.get();
                for (int i = 0; i < idPackets.get() * idOffset.get(); i += idOffset.get()) {
                    addPredict(id + i, new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5), idDelay.get() + idPacketDelay.get() * i);
                }
            }
        }
    }

    private boolean delayCheck() {
        if (placeDelayMode.get() == DelayMode.Seconds)
            return delayTimer >= placeDelay.get();
        return delayTicks >= placeDelayTicks.get();
    }

    private boolean multiTaskCheck() {
        return placed >= sequential.get().ticks;
    }

    private int getHighest() {
        int highest = confirmed;
        for (Entity entity : mc.world.getEntities()) {
            if (entity.getId() > highest) highest = entity.getId();
        }
        if (highest > confirmed) confirmed = highest;
        return highest;
    }

    private boolean isBlocked(BlockPos pos) {
        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        for (Box bb : blocked) {
            if (bb.intersects(box)) return true;
        }
        return false;
    }

    private boolean isAttacked(int id) {
        return attackedList.contains(id);
    }

    private void explode(int id, Vec3d vec) {
        attackEntity(id, OLEPOSSUtils.getCrystalBox(vec), vec);
    }

    private void attackEntity(int id, Box bb, Vec3d vec) {
        if (mc.player != null) {
            lastAttack = System.currentTimeMillis();
            attackedList.add(id, 1 / expSpeed.get());
            if (inhibit.get()) inhibitList.add(id, 0.5);

            delayTimer = 0;
            delayTicks = 0;

            removeExisted(BlockPos.ofFloored(vec));

            SettingUtils.registerAttack(bb);
            PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.attack(mc.player, mc.player.isSneaking());
            ((IInteractEntityC2SPacket) packet).setId(id);

            SettingUtils.swing(SwingState.Pre, SwingType.攻击, Hand.MAIN_HAND);

            sendPacket(packet);

            SettingUtils.swing(SwingState.Post, SwingType.攻击, Hand.MAIN_HAND);
            if (attackSwing.get()) clientSwing(attackHand.get(), Hand.MAIN_HAND);

            blocked.clear();
            if (setDead.get()) {
                Entity entity = mc.world.getEntityById(id);
                if (entity == null) return;

                addSetDead(entity, setDeadDelay.get());
            }
        }
    }

    private boolean existedCheck(BlockPos pos) {
        if (existedMode.get() == DelayMode.Seconds)
            return !existedList.containsKey(pos) || System.currentTimeMillis() > existedList.get(pos) + existed.get() * 1000;
        else
            return !existedTicksList.containsKey(pos) || ticksEnabled >= existedTicksList.get(pos) + existedTicks.get();
    }

    private void addExisted(BlockPos pos) {
        if (existedMode.get() == DelayMode.Seconds) {
            if (!existedList.containsKey(pos)) existedList.put(pos, System.currentTimeMillis());
        } else {
            if (!existedTicksList.containsKey(pos)) existedTicksList.put(pos, ticksEnabled);
        }
    }

    private void removeExisted(BlockPos pos) {
        if (existedMode.get() == DelayMode.Seconds) existedList.remove(pos);
        else existedTicksList.remove(pos);
    }

    private boolean canExplode(Vec3d vec) {
        if (onlyOwn.get() && !isOwn(vec)) return false;
        if (!inExplodeRange(vec)) return false;

        double[][] result = getDmg(vec, true);
        return explodeDamageCheck(result[0], result[1], isOwn(vec));
    }

    private boolean canExplodePlacing(Vec3d vec) {
        if (onlyOwn.get() && !isOwn(vec)) return false;
        if (!inExplodeRangePlacing(vec)) return false;

        double[][] result = getDmg(vec, false);
        return explodeDamageCheck(result[0], result[1], isOwn(vec));
    }

    private Hand getHand(Predicate<ItemStack> predicate) {
        return predicate.test(Managers.HOLDING.getStack()) ? Hand.MAIN_HAND :
            predicate.test(mc.player.getOffHandStack()) ? Hand.OFF_HAND : null;
    }

    private boolean isPaused() {
        return pauseEat.get() && mc.player.isUsingItem();
    }

    private void setEntityDead(Entity en) {
        mc.world.removeEntity(en.getId(), Entity.RemovalReason.KILLED);
    }

    private BlockPos getPlacePos() {

        int r = (int) Math.ceil(Math.max(SettingUtils.getPlaceRange(), SettingUtils.getPlaceWallsRange()));
        //Used in placement calculation
        BlockPos bestPos = null;
        Direction bestDir = null;
        double[] highest = null;

        BlockPos pPos = BlockPos.ofFloored(mc.player.getEyePos());

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = pPos.add(x, y, z);
                    // Checks if crystal can be placed
                    if (!air(pos) || !(!SettingUtils.oldCrystals() || air(pos.up())) || !crystalBlock(pos.down()) || blockBroken(pos.down())) continue;

                    // Checks if there is possible placing direction
                    Direction dir = SettingUtils.getPlaceOnDirection(pos.down());
                    if (dir == null) continue;

                    // Checks if the placement is in range
                    if (!inPlaceRange(pos.down()) || !inExplodeRangePlacing(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) continue;

                    // Calculates damages and healths
                    double[][] result = getDmg(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), false);

                    // Checks if damages are valid
                    if (!placeDamageCheck(result[0], result[1], highest)) continue;

                    // Checks if placement is blocked by other entities (other than players)
                    Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + (SettingUtils.cc() ? 1 : 2), pos.getZ() + 1);

                    if (BOEntityUtils.intersectsWithEntity(box, this::validForIntersect, extHitbox)) continue;

                    // Sets best pos to calculated one
                    bestDir = dir;
                    bestPos = pos;
                    highest = result[0];
                }
            }
        }

        placeDir = bestDir;
        return bestPos;
    }

    private boolean placeDamageCheck(double[] dmg, double[] health, double[] highest) {
        //  0 = enemy, 1 = friend, 2 = self

        //  Dmg Check
        if (highest != null) {
            if (dmgCheckMode.get().equals(DmgCheckMode.Normal) && dmg[0] < highest[0]) return false;
            if (dmgCheckMode.get().equals(DmgCheckMode.Safe) && dmg[2] / dmg[0] > highest[2] / highest[0]) return false;
        }

        //  Force/anti-pop check
        double playerHP = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (playerHP >= 0 && dmg[2] * antiSelfPop.get() >= playerHP) return false;
        if (health[1] >= 0 && dmg[1] * antiFriendPop.get() >= health[1]) return false;
        if (health[0] >= 0 && dmg[0] * forcePop.get() >= health[0]) return true;

        //  Min Damage
        if (dmg[0] < minPlace.get()) return false;

        //  Max Damage
        if (dmg[1] > maxFriendPlace.get()) return false;
        if (dmg[1] >= 0 && dmg[0] / dmg[1] < minFriendPlaceRatio.get()) return false;
        if (dmg[2] > maxPlace.get()) return false;
        return dmg[2] < 0 || dmg[0] / dmg[2] >= minPlaceRatio.get();
    }

    private boolean explodeDamageCheck(double[] dmg, double[] health, boolean own) {
        boolean checkOwn = expMode.get() == ExplodeMode.FullCheck
            || expMode.get() == ExplodeMode.SelfDmgCheck
            || expMode.get() == ExplodeMode.SelfDmgOwn
            || expMode.get() == ExplodeMode.AlwaysOwn;

        boolean checkDmg = expMode.get() == ExplodeMode.FullCheck
            || (expMode.get() == ExplodeMode.SelfDmgOwn && !own)
            || (expMode.get() == ExplodeMode.AlwaysOwn && !own);

        //  0 = enemy, 1 = friend, 2 = self

        //  Force/anti-pop check
        double playerHP = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (checkOwn) {
            if (playerHP >= 0 && dmg[2] * forcePop.get() >= playerHP) return false;
            if (health[1] >= 0 && dmg[1] * antiFriendPop.get() >= health[1]) return false;
        }

        if (checkDmg) {
            if (health[0] >= 0 && dmg[0] * forcePop.get() >= health[0]) return true;
            if (dmg[0] < minExplode.get()) return false;

            if (dmg[1] >= 0 && dmg[0] / dmg[1] < minFriendExpRatio.get()) return false;
            if (dmg[2] >= 0 && dmg[0] / dmg[2] < minExpRatio.get()) return false;
        }

        if (checkOwn) {
            if (dmg[1] > maxFriendExp.get()) return false;
            return dmg[2] <= maxExp.get();
        }
        return true;
    }

    private boolean isOwn(Vec3d vec) {
        return isOwn(BlockPos.ofFloored(vec));
    }

    private boolean isOwn(BlockPos pos) {
        for (Map.Entry<BlockPos, Long> entry : own.entrySet()) {
            if (entry.getKey().equals(pos)) return true;
        }
        return false;
    }

    private double[][] getDmg(Vec3d vec, boolean attack) {
        double self = BODamageUtils.crystalDamage(mc.player, extPos.containsKey(mc.player) ? extPos.get(mc.player) : mc.player.getBoundingBox(), vec, ignorePos(attack), ignoreTerrain.get());

        if (suicide) return new double[][]{new double[]{self, -1, -1}, new double[]{20, 20}};

        double highestEnemy = -1;
        double highestFriend = -1;
        double enemyHP = -1;
        double friendHP = -1;
        for (Map.Entry<AbstractClientPlayerEntity, Box> entry : extPos.entrySet()) {
            AbstractClientPlayerEntity player = entry.getKey();
            Box box = entry.getValue();
            if (player.getHealth() <= 0 || player == mc.player) continue;

            double dmg = BODamageUtils.crystalDamage(player, box, vec, ignorePos(attack), ignoreTerrain.get());
            if (BlockPos.ofFloored(vec).down().equals(autoMine.targetPos()))
                dmg *= autoMineDamage.get();
            double hp = player.getHealth() + player.getAbsorptionAmount();

            //  friend
            if (Friends.get().isFriend(player)) {
                if (dmg > highestFriend) {
                    highestFriend = dmg;
                    friendHP = hp;
                }
            }
            //  enemy
            else if (dmg > highestEnemy) {
                highestEnemy = dmg;
                enemyHP = hp;
            }
        }

        return new double[][]{new double[]{highestEnemy, highestFriend, self}, new double[]{enemyHP, friendHP}};
    }

    private boolean air(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() instanceof AirBlock;
    }

    private boolean crystalBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock().equals(Blocks.OBSIDIAN) ||
            mc.world.getBlockState(pos).getBlock().equals(Blocks.BEDROCK);
    }

    private boolean inPlaceRange(BlockPos pos) {
        return SettingUtils.inPlaceRange(pos);
    }

    private boolean inExplodeRangePlacing(Vec3d vec) {
        return SettingUtils.inAttackRange(new Box(vec.getX() - 1, vec.getY(), vec.getZ() - 1, vec.getX() + 1, vec.getY() + 2, vec.getZ() + 1), rangePos != null ? rangePos : null);
    }

    private boolean inExplodeRange(Vec3d vec) {
        return SettingUtils.inAttackRange(new Box(vec.getX() - 1, vec.getY(), vec.getZ() - 1, vec.getX() + 1, vec.getY() + 2, vec.getZ() + 1));
    }

    private double getSpeed() {
        return shouldSlow() ? slowSpeed.get() : placeSpeed.get();
    }

    private boolean shouldSlow() {
        return placePos != null && getDmg(new Vec3d(placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5), false)[0][0] <= slowDamage.get();
    }

    private Vec3d smoothMove(Vec3d current, Vec3d target, double delta) {
        if (current == null) return target;

        double absX = Math.abs(current.x - target.x);
        double absY = Math.abs(current.y - target.y);
        double absZ = Math.abs(current.z - target.z);

        double x = (absX + Math.pow(absX, animationMoveExponent.get() - 1)) * delta;
        double y = (absX + Math.pow(absY, animationMoveExponent.get() - 1)) * delta;
        double z = (absX + Math.pow(absZ, animationMoveExponent.get() - 1)) * delta;

        return new Vec3d(current.x > target.x ? Math.max(target.x, current.x - x) : Math.min(target.x, current.x + x),
            current.y > target.y ? Math.max(target.y, current.y - y) : Math.min(target.y, current.y + y),
            current.z > target.z ? Math.max(target.z, current.z - z) : Math.min(target.z, current.z + z));
    }

    private boolean validForIntersect(Entity entity) {
        if (entity instanceof EndCrystalEntity && canExplodePlacing(entity.getPos()))
            return false;

        return !(entity instanceof PlayerEntity) || !entity.isSpectator();
    }

    private BlockPos ignorePos(boolean attack) {
        if (!amPlace.get()) return null;
        if (!amSpam.get() && attack) return null;
        if (autoMine == null || !autoMine.isActive()) return null;
        if (autoMine.targetPos() == null) return null;

        return autoMine.getMineProgress() > amProgress.get() ? autoMine.targetPos() : null;
    }

    private boolean blockBroken(BlockPos pos) {
        if (!amPlace.get()) return false;

        if (autoMine == null || !autoMine.isActive()) return false;
        if (autoMine.targetPos() == null) return false;
        if (!autoMine.targetPos().equals(pos)) return false;

        double progress = autoMine.getMineProgress();

        if (progress >= 1 && !amBroken.get().broken) return true;
        if (progress >= amProgress.get() && !amBroken.get().near) return true;
        return progress < amProgress.get() && !amBroken.get().normal;
    }

    private void addPredict(int id, Vec3d pos, double delay) {
        predicts.add(new Predict(id, pos, Math.round(System.currentTimeMillis() + delay * 1000)));
    }

    private void addSetDead(Entity entity, double delay) {
        setDeads.add(new SetDead(entity, Math.round(System.currentTimeMillis() + delay * 1000)));
    }

    private void checkDelayed() {
        List<Predict> toRemove = new ArrayList<>();
        for (Predict p : predicts) {
            if (System.currentTimeMillis() >= p.time) {
                explode(p.id, p.pos);
                toRemove.add(p);
            }
        }
        toRemove.forEach(predicts::remove);

        List<SetDead> toRemove2 = new ArrayList<>();
        for (SetDead p : setDeads) {
            if (System.currentTimeMillis() >= p.time) {
                setEntityDead(p.entity);
                toRemove2.add(p);
            }
        }
        toRemove2.forEach(setDeads::remove);
    }

    public enum DmgCheckMode {
        Normal,
        Safe
    }

    public enum RenderMode {
        BlackOut,
        Future,
        Earthhack
    }

    public enum SwitchMode {
        Disabled,
        Simple,
        Gapple,
        Silent,
        InvSilent,
        PickSilent
    }

    public enum SequentialMode {
        Disabled(0),
        Weak(1),
        Strong(2),
        Strict(3);

        public final int ticks;

        SequentialMode(int ticks) {
            this.ticks = ticks;
        }
    }

    public enum ExplodeMode {
        FullCheck,
        SelfDmgCheck,
        SelfDmgOwn,
        AlwaysOwn,
        Always
    }

    public enum DelayMode {
        Seconds,
        Ticks
    }

    public enum EarthFadeMode {
        Normal,
        Up,
        Down,
        Shrink
    }

    public enum FadeMode {
        Up,
        Down,
        Normal
    }

    public enum AutoMineBrokenMode {
        Near(true, false, false),
        Broken(true, true, false),
        Never(false, false, false),
        Always(true, true, true);

        public final boolean normal;
        public final boolean near;
        public final boolean broken;

        AutoMineBrokenMode(boolean normal, boolean near, boolean broken) {
            this.normal = normal;
            this.near = near;
            this.broken = broken;
        }
    }

    private record Predict(int id, Vec3d pos, long time) {
    }

    private record SetDead(Entity entity, long time) {
    }
}

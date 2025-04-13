package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.managers.Managers;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * @author OLEPOSSU
 */

public class AntiAim extends BlackOutModule {
    public AntiAim() {
        super(BlackOut.BLACKOUT, "Anti Aim", "Funi conter stik module.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgIgnore = settings.createGroup("Ignore");

    //--------------------General--------------------//
    private final Setting<Modes> mode = sgGeneral.add(new EnumSetting.Builder<Modes>()
        .name("模式")
        .description("选择头部旋转的工作模式")
        .defaultValue(Modes.Custom)
        .build()
    );
    private final Setting<Double> enemyRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("敌人范围")
        .description("检测范围内的玩家并面向他们")
        .defaultValue(20)
        .range(0, 1000)
        .sliderMin(0)
        .visible(() -> mode.get().equals(Modes.Enemy))
        .sliderMax(1000)
        .build()
    );
    private final Setting<Double> spinSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("旋转速度")
        .description("每tick旋转的角度数")
        .defaultValue(5)
        .min(0)
        .sliderMin(0)
        .visible(() -> mode.get().equals(Modes.Spin))
        .sliderMax(100)
        .build()
    );
    private final Setting<Boolean> rYaw = sgGeneral.add(new BoolSetting.Builder()
        .name("随机偏航")
        .description("将水平角度设为随机值")
        .defaultValue(true)
        .visible(() -> mode.get().equals(Modes.CSGO))
        .build()
    );
    private final Setting<Boolean> rPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("随机俯仰")
        .description("将垂直角度设为随机值")
        .defaultValue(false)
        .visible(() -> mode.get().equals(Modes.CSGO))
        .build()
    );
    private final Setting<Integer> csgoPitch = sgGeneral.add(new IntSetting.Builder()
        .name("预设俯仰角度")
        .description("CSGO模式下使用的固定俯仰角度")
        .defaultValue(90)
        .range(-90, 90)
        .sliderMin(-90)
        .visible(() -> mode.get().equals(Modes.CSGO) && !rPitch.get())
        .sliderMax(90)
        .build()
    );
    private final Setting<Double> csDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("CSGO间隔")
        .description("CSGO模式下的角度更新间隔")
        .defaultValue(5)
        .range(0, 100)
        .sliderMin(0)
        .visible(() -> mode.get().equals(Modes.CSGO))
        .sliderMax(100)
        .build()
    );
    private final Setting<Integer> yaw = sgGeneral.add(new IntSetting.Builder()
        .name("水平角度")
        .description("自定义的水平朝向角度")
        .defaultValue(0)
        .range(-180, 180)
        .sliderMin(-180)
        .visible(() -> mode.get().equals(Modes.Custom))
        .sliderMax(180)
        .build()
    );
    private final Setting<Integer> pitch = sgGeneral.add(new IntSetting.Builder()
        .name("俯仰角度")
        .description("自定义的垂直视角角度")
        .defaultValue(90)
        .range(-90, 90)
        .sliderMin(-90)
        .sliderMax(90)
        .visible(() -> mode.get().equals(Modes.Custom))
        .build()
    );
    private final Setting<Boolean> bowMode = sgGeneral.add(new BoolSetting.Builder()
        .name("持弓上视")
        .description("手持弓时自动向上看")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> encMode = sgGeneral.add(new BoolSetting.Builder()
        .name("持经验瓶下视")
        .description("手持经验瓶时自动向下看")
        .defaultValue(true)
        .build()
    );

    //--------------------Ignore--------------------//
    private final Setting<Boolean> iYaw = sgIgnore.add(new BoolSetting.Builder()
        .name("忽略偏航")
        .description("手持特定物品时不改变水平角度")
        .defaultValue(true)
        .build()
    );
    private final Setting<List<Item>> yItems = sgIgnore.add(new ItemListSetting.Builder()
        .name("忽略偏航物品")
        .description("手持这些物品时忽略偏航角度调整")
        .defaultValue(Items.ENDER_PEARL, Items.BOW, Items.EXPERIENCE_BOTTLE)
        .build()
    );
    private final Setting<Boolean> iPitch = sgIgnore.add(new BoolSetting.Builder()
        .name("忽略俯仰")
        .description("手持特定物品时不改变垂直角度")
        .defaultValue(true)
        .build()
    );
    private final Setting<List<Item>> pItems = sgIgnore.add(new ItemListSetting.Builder()
        .name("忽略俯仰物品")
        .description("手持这些物品时忽略俯仰角度调整")
        .defaultValue(Items.ENDER_PEARL, Items.BOW, Items.EXPERIENCE_BOTTLE)
        .build()
    );

    private final Random r = new Random();
    private double spinYaw;
    private double csTick = 0;
    private double csYaw;
    private double csPitch;

    @Override
    public void onActivate() {
        super.onActivate();
        spinYaw = 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player != null && mc.world != null) {
            if (mode.get() == Modes.CSGO) {
                if (csTick <= 0) {
                    csTick += csDelay.get();
                    csYaw = rYaw.get() ? r.nextInt(-180, 180) : mc.player.getYaw();
                    csPitch = rPitch.get() ? r.nextInt(-90, 90) : csgoPitch.get();
                } else {
                    csTick--;
                }
            }

            Item item = mc.player.getMainHandStack().getItem();
            boolean ignoreYaw = yItems.get().contains(item) && iYaw.get();
            boolean ignorePitch = pItems.get().contains(item) && iPitch.get();

            double y = ignoreYaw ? mc.player.getYaw() :
                switch (mode.get()) {
                    case Enemy -> closestYaw();
                    case Spin -> getSpinYaw();
                    case CSGO -> csYaw;
                    case Custom -> yaw.get();
                };

            double p = item == Items.EXPERIENCE_BOTTLE && encMode.get() ? 90 :
                item == Items.BOW && bowMode.get() ? -90 :
                    ignorePitch ? mc.player.getPitch() :
                        switch (mode.get()) {
                            case Enemy -> closestPitch();
                            case Spin -> 0.0;
                            case CSGO -> csPitch;
                            case Custom -> pitch.get();
                        };

            Managers.ROTATION.start(y, p, priority, RotationType.Other, Objects.hash(name + "look"));
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    private double closestYaw() {
        PlayerEntity closest = getClosest();

        if (closest != null) {
            return Rotations.getYaw(closest);
        }
        return mc.player.getYaw();
    }

    private double closestPitch() {
        PlayerEntity closest = getClosest();

        if (closest != null) {
            return Rotations.getPitch(closest);
        }
        return mc.player.getPitch();
    }

    private double getSpinYaw() {
        spinYaw += spinSpeed.get();

        return spinYaw;
    }

    private PlayerEntity getClosest() {
        PlayerEntity closest = null;
        for (PlayerEntity pl : mc.world.getPlayers()) {
            if (pl == mc.player) continue;

            if (Friends.get().isFriend(pl)) continue;

            if (closest == null) closest = pl;

            double distance = mc.player.getPos().distanceTo(pl.getPos());

            if (distance > enemyRange.get()) continue;

            if (distance < closest.getPos().distanceTo(mc.player.getPos())) {
                closest = pl;
            }
        }
        return closest;
    }

    public enum Modes {
        Enemy,
        Spin,
        CSGO,
        Custom
    }
}

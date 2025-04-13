package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;

/**
 * @author KassuK
 */

public class FlightPlus extends BlackOutModule {
    public FlightPlus() {
        super(BlackOut.BLACKOUT, "飞行增强", "KasumsSoft开发的飞行模块");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<FlightMode> flyMode = sgGeneral.add(new EnumSetting.Builder<FlightMode>()
        .name("飞行模式")
        .description("选择飞行实现方式")
        .defaultValue(FlightMode.Momentum)
        .build()
    );
    private final Setting<Boolean> useTimer = sgGeneral.add(new BoolSetting.Builder()
        .name("启用计时器")
        .description("是否使用计时器加速")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> timer = sgGeneral.add(new DoubleSetting.Builder()
        .visible(useTimer::get)
        .name("计时器倍率")
        .description("数据包发送倍率")
        .defaultValue(1.088)
        .min(0)
        .sliderMax(10)
        .visible(useTimer::get)
        .build()
    );
    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("水平速度")
        .description("水平方向移动速度（块/刻）")
        .defaultValue(0.6)
        .min(0)
        .sliderMax(10)
        .visible(() -> flyMode.get() == FlightMode.Momentum)
        .build()
    );
    private final Setting<Double> ySpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("垂直速度")
        .description("Y轴移动速度（块/刻）")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(10)
        .visible(() -> flyMode.get() == FlightMode.Momentum)
        .build()
    );
    private final Setting<Double> antiKickDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("防检测间隔")
        .description("防检测间隔刻数")
        .defaultValue(10)
        .min(0)
        .sliderMax(100)
        .visible(() -> flyMode.get() == FlightMode.Momentum)
        .build()
    );
    private final Setting<Double> antiKickAmount = sgGeneral.add(new DoubleSetting.Builder()
        .name("防检测幅度")
        .description("防检测时下移距离（块）")
        .defaultValue(1)
        .min(0)
        .sliderMax(10)
        .visible(() -> flyMode.get() == FlightMode.Momentum)
        .build()
    );
    private final Setting<Boolean> keepY = sgGeneral.add(new BoolSetting.Builder()
        .name("维持高度")
        .description("跳跃飞行时保持初始高度")
        .defaultValue(true)
        .visible(() -> flyMode.get() == FlightMode.Jump)
        .build()
    );
    private final Setting<Double> glideAmount = sgGeneral.add(new DoubleSetting.Builder()
        .name("滑翔速率")
        .description("每刻下降距离（块）")
        .defaultValue(0.2)
        .min(0)
        .sliderMax(1)
        .visible(() -> flyMode.get() == FlightMode.Glide)
        .build()
    );

    private double startY = 0.0;
    private int tick = 0;

    @Override
    public void onActivate() {
        if (mc.player != null && mc.world != null){
            startY = mc.player.getY();
            Modules.get().get(Timer.class).setOverride(timer.get());
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event){
        if (mc.player != null && mc.world != null){
            double[] result = getYaw(mc.player.input.movementForward, mc.player.input.movementSideways);
            float yaw = (float) result[0] + 90;
            double x = 0, y = tick % antiKickDelay.get() == 0 ? antiKickAmount.get() * -0.04 : 0, z = 0;
            if (flyMode.get().equals(FlightMode.Momentum)){
                if (mc.options.jumpKey.isPressed() && y == 0){
                    y = ySpeed.get();
                }
                else if (mc.options.sneakKey.isPressed()){
                    y = -ySpeed.get();
                }
                if (result[1] == 1){
                    x = Math.cos(Math.toRadians(yaw)) * speed.get();
                    z = Math.sin(Math.toRadians(yaw)) * speed.get();

                }
                ((IVec3d) event.movement).set(x, y, z);
            }
            if (flyMode.get().equals(FlightMode.Jump)){
                if (mc.options.jumpKey.wasPressed()){
                    mc.player.jump();
                    startY += 0.4;
                }
                if (mc.options.sneakKey.wasPressed() && !mc.options.sneakKey.isPressed()){
                    startY = mc.player.getY();
                }

                if (keepY.get() && mc.player.getY() <= startY && !mc.options.sneakKey.isPressed())
                    mc.player.jump();
                if (result[1] == 1){
                    x = Math.cos(Math.toRadians(yaw)) * speed.get();
                    z = Math.sin(Math.toRadians(yaw)) * speed.get();

                }
                ((IVec3d) event.movement).setXZ(x, z);
            }
            if (flyMode.get().equals(FlightMode.Glide)){
                if (!mc.player.isOnGround())
                    ((IVec3d) event.movement).setY(-glideAmount.get());

            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event){
        tick++;
    }

    @Override
    public void onDeactivate(){
        if (mc.player != null && mc.world != null){
            Modules.get().get(Timer.class).setOverride(1);
        }
    }

    private double[] getYaw(double f, double s) {
        double yaw = mc.player.getYaw();
        double move;
        if (f > 0) {
            move = 1;
            yaw += s > 0 ? -45 : s < 0 ? 45 : 0;
        } else if (f < 0) {
            move = 1;
            yaw += s > 0 ? -135 : s < 0 ? 135 : 180;
        } else {
            move = s != 0 ? 1 : 0;
            yaw += s > 0 ? -90 : s < 0 ? 90 : 0;
        }
        return new double[]{yaw, move};
    }

    public enum FlightMode {
        Momentum,
        Jump,
        Glide,
    }
}

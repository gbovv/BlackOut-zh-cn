package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.utils.OLEPOSSUtils;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * @author OLEPOSSU
 */

public class PacketFly extends BlackOutModule {
    public PacketFly() {
        super(BlackOut.BLACKOUT, "数据包飞行", "使用数据包实现飞行功能");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFly = settings.createGroup("Fly");
    private final SettingGroup sgPhase = settings.createGroup("Phase");

    //--------------------General--------------------//
    private final Setting<Boolean> onGroundSpoof = sgGeneral.add(new BoolSetting.Builder()
        .name("地面欺骗")
        .description("伪造玩家着地状态")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> onGround = sgGeneral.add(new BoolSetting.Builder()
        .name("地面状态")
        .description("向服务器报告玩家着地状态")
        .defaultValue(false)
        .visible(onGroundSpoof::get)
        .build()
    );
    private final Setting<Integer> xzBound = sgGeneral.add(new IntSetting.Builder()
        .name("水平容差")
        .description("水平方向坐标偏移量（区块）")
        .defaultValue(1337)
        .sliderRange(-1337, 1337)
        .build()
    );
    private final Setting<Integer> yBound = sgGeneral.add(new IntSetting.Builder()
        .name("垂直容差")
        .description("垂直方向坐标偏移量（区块）")
        .defaultValue(0)
        .sliderRange(-1337, 1337)
        .build()
    );
    private final Setting<Boolean> strictVertical = sgGeneral.add(new BoolSetting.Builder()
        .name("垂直严格模式")
        .description("禁止在同一数据包中同时进行水平与垂直移动")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> antiKick = sgGeneral.add(new BoolSetting.Builder()
        .name("防踢出")
        .description("模拟自然下落防止被踢出")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> antiKickAmount = sgGeneral.add(new DoubleSetting.Builder()
        .name("防踢倍率")
        .description("下落速度调整倍率（0.04格/刻 * 倍率）")
        .defaultValue(1)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Integer> antiKickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("防踢间隔")
        .description("防踢数据包发送间隔（刻）")
        .defaultValue(10)
        .min(1)
        .sliderRange(0, 100)
        .build()
    );
    private final Setting<Boolean> predictID = sgGeneral.add(new BoolSetting.Builder()
        .name("ID预测")
        .description("预判下一次坐标同步包的ID")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> debugID = sgGeneral.add(new BoolSetting.Builder()
        .name("ID调试")
        .description("在聊天栏显示坐标同步包ID")
        .defaultValue(false)
        .build()
    );

    //--------------------Fly--------------------//
    private final Setting<Double> packets = sgFly.add(new DoubleSetting.Builder()
        .name("发包频率")
        .description("每移动刻发送的数据包数量（个/刻）")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> speed = sgFly.add(new DoubleSetting.Builder()
        .name("飞行速度")
        .description("单个数据包的移动距离（格/包）")
        .defaultValue(0.2873)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Boolean> fastVertical = sgFly.add(new BoolSetting.Builder()
        .name("垂直加速")
        .description("上升时每移动刻发送多个数据包")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> downSpeed = sgFly.add(new DoubleSetting.Builder()
        .name("下降速度")
        .description("垂直下降速率（格/刻）")
        .defaultValue(0.062)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> upSpeed = sgFly.add(new DoubleSetting.Builder()
        .name("上升速度")
        .description("垂直上升速率（格/刻）")
        .defaultValue(0.062)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    //--------------------Phase--------------------//
    private final Setting<Double> phasePackets = sgPhase.add(new DoubleSetting.Builder()
        .name("穿透发包频率")
        .description("穿透模式下每移动刻发送的数据包数量（个/刻）")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> phaseSpeed = sgPhase.add(new DoubleSetting.Builder()
        .name("穿透速度")
        .description("穿透模式下单个数据包的移动距离（格/包）")
        .defaultValue(0.062)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Boolean> phaseFastVertical = sgPhase.add(new BoolSetting.Builder()
        .name("垂直加速穿透")
        .description("穿透模式上升时每移动刻发送多个数据包")
        .defaultValue(false)
        .build()
    );
    private final Setting<Double> phaseDownSpeed = sgPhase.add(new DoubleSetting.Builder()
        .name("穿透下降速率")
        .description("穿透模式下垂直下降速度（格/刻）")
        .defaultValue(0.062)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Double> phaseUpSpeed = sgPhase.add(new DoubleSetting.Builder()
        .name("穿透上升速率")
        .description("穿透模式下垂直上升速度（格/刻）")
        .defaultValue(0.062)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private int ticks = 0;
    private int id = -1;
    private int sent = 0;
    private int rur = 0;
    private double packetsToSend = 0;
    private final Random random = new Random();
    private String info = null;
    private final Map<Integer, Vec3d> validPos = new HashMap<>();
    private final List<PlayerMoveC2SPacket> validPackets = new ArrayList<>();

    public boolean moving = false;

    @Override
    public void onActivate() {
        super.onActivate();
        ticks = 0;
        validPos.clear();
    }
    @Override
    public void onDeactivate() {
        validPos.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post e) {
        ticks++;
        rur++;
        if (rur % 20 == 0) {
            info = "Packets: " + sent;
            sent = 0;
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent e) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        boolean phasing = isPhasing();
        boolean semiPhasing = isSemiPhase();

        mc.player.noClip = semiPhasing;
        packetsToSend += packets(semiPhasing);

        boolean shouldAntiKick = antiKick.get() && ticks % antiKickDelay.get() == 0 && !phasing && !onGround();

        double yaw = getYaw();
        double motion = semiPhasing ? phaseSpeed.get() : speed.get();

        double x = 0, y = 0, z = 0;

        if (jumping()) {
            y = semiPhasing ? phaseUpSpeed.get() : upSpeed.get();
        } else if (sneaking()) {
            y = semiPhasing ? -phaseDownSpeed.get() : -downSpeed.get();
        }

        if (y != 0) {
            moving = false;
        }

        if (moving) {
            x = Math.cos(Math.toRadians(yaw + 90)) * motion;
            z = Math.sin(Math.toRadians(yaw + 90)) * motion;
        } else {
            if (semiPhasing && !phaseFastVertical.get()) {
                packetsToSend = Math.min(packetsToSend, 1);
            }
            if (!semiPhasing && !fastVertical.get()) {
                packetsToSend = Math.min(packetsToSend, 1);
            }
        }

        Vec3d offset = new Vec3d(0, 0, 0);
        boolean antiKickSent = false;
        for (; packetsToSend >= 1; packetsToSend -= 1) {
            double yOffset;
            if (shouldAntiKick && y >= 0 && !antiKickSent) {
                yOffset = antiKickAmount.get() * -0.04;
                antiKickSent = true;
            } else {
                yOffset = y;
            }

            offset = offset.add(strictVertical.get() && yOffset != 0 ? 0 : x, yOffset, strictVertical.get() && yOffset != 0 ? 0 : z);

            send(offset.add(mc.player.getPos()), getBounds(), getOnGround());

            if (x == 0 && z == 0 && y == 0) {
                break;
            }
        }

        ((IVec3d) e.movement).set(offset.x, offset.y, offset.z);

        packetsToSend = Math.min(packetsToSend, 1);
    }

    @EventHandler
    public void onSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket) {
            if (!validPackets.contains((PlayerMoveC2SPacket) event.packet)) {
                event.cancel();
            } else {
                sent++;
            }
        } else {
            sent++;
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive e) {
        if (e.packet instanceof PlayerPositionLookS2CPacket packet) {
            if (debugID.get()) {
                debug("id: " + packet.getTeleportId());
            }
            Vec3d vec = new Vec3d(packet.getX(), packet.getY(), packet.getZ());

            if (validPos.containsKey(packet.getTeleportId()) && validPos.get(packet.getTeleportId()).equals(vec)) {
                if (debugID.get()) {
                    debug("true");
                }
                e.cancel();
                if (!predictID.get()) {
                    sendPacket(new TeleportConfirmC2SPacket(packet.getTeleportId()));
                }
                validPos.remove(packet.getTeleportId());
                return;
            }
            if (debugID.get()) {
                debug("false");
            }

            id = packet.getTeleportId();
        }
    }

    @Override
    public String getInfoString() {
        return info;
    }

    private boolean onGround() {
        return mc.player.isOnGround() || (mc.player.getBlockY() - mc.player.getY() == 0 && OLEPOSSUtils.collidable(mc.player.getBlockPos().down()));
    }

    private double packets(boolean semiPhasing) {
        return semiPhasing ? phasePackets.get() : packets.get();
    }

    private Vec3d getBounds() {
        int yaw = random.nextInt(0, 360);
        return new Vec3d(Math.cos(Math.toRadians(yaw)) * xzBound.get(), yBound.get(), Math.sin(Math.toRadians(yaw)) * xzBound.get());
    }

    private boolean getOnGround() {
        return onGroundSpoof.get() ? onGround.get() : mc.player.isOnGround();
    }

    private boolean isPhasing() {
        return OLEPOSSUtils.inside(mc.player, mc.player.getBoundingBox().shrink(0.0625, 0, 0.0625));
    }

    private boolean isSemiPhase() {
        return OLEPOSSUtils.inside(mc.player, mc.player.getBoundingBox().expand(0.01, 0, 0.01));
    }

    private boolean jumping() {
        return mc.options.jumpKey.isPressed();
    }

    private boolean sneaking() {
        return mc.options.sneakKey.isPressed();
    }

    private void send(Vec3d pos, Vec3d bounds, boolean onGround) {
        PlayerMoveC2SPacket.PositionAndOnGround normal = new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, onGround);
        PlayerMoveC2SPacket.PositionAndOnGround bound = new PlayerMoveC2SPacket.PositionAndOnGround(pos.x + bounds.x, pos.y + bounds.y, pos.z + bounds.z, onGround);

        validPackets.add(normal);
        sendPacket(normal);
        validPos.put(id + 1, pos);

        validPackets.add(bound);
        sendPacket(bound);
        if (id < 0) {
            return;
        }

        id++;
        if (predictID.get()) {
            sendPacket(new TeleportConfirmC2SPacket(id));
        }
    }

    private double getYaw() {
        double f = mc.player.input.movementForward, s = mc.player.input.movementSideways;

        double yaw = mc.player.getYaw();
        if (f > 0) {
            moving = true;
            yaw += s > 0 ? -45 : s < 0 ? 45 : 0;
        } else if (f < 0) {
            moving = true;
            yaw += s > 0 ? -135 : s < 0 ? 135 : 180;
        } else {
            moving = s != 0;
            yaw += s > 0 ? -90 : s < 0 ? 90 : 0;
        }
        return yaw;
    }
}

package kassuk.addon.blackout.globalsettings;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.managers.RotationManager;
import kassuk.addon.blackout.utils.NCPRaytracer;
import kassuk.addon.blackout.utils.OLEPOSSUtils;
import kassuk.addon.blackout.utils.RotationUtils;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * @author OLEPOSSU
 */

public class RotationSettings extends BlackOutModule {
    public RotationSettings() {
        super(BlackOut.SETTINGS, "Rotate", "Global rotation settings for every blackout module.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInteract = settings.createGroup("交互");
    private final SettingGroup sgBlockPlace = settings.createGroup("方块放置");
    private final SettingGroup sgMining = settings.createGroup("挖掘");
    private final SettingGroup sgAttack = settings.createGroup("攻击");
    private final SettingGroup sgUse = settings.createGroup("使用");

    //--------------------General--------------------//
    public final Setting<Boolean> vanillaRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("Vanilla Rotation")
        .description("Turns your head.")
        .defaultValue(false)
        .build()
    );
    public final Setting<Double> yawStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("Yaw Step")
        .description("How many yaw degrees should be rotated each packet.")
        .defaultValue(90)
        .range(0, 180)
        .sliderRange(0, 180)
        .build()
    );
    public final Setting<Double> pitchStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("Pitch Step")
        .description("How many pitch degrees should be rotated each packet.")
        .defaultValue(45)
        .range(0, 180)
        .sliderRange(0, 180)
        .build()
    );
    public final Setting<Double> yawRandomization = sgGeneral.add(new DoubleSetting.Builder()
        .name("Yaw Randomization")
        .description(".")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    public final Setting<Double> pitchRandomization = sgGeneral.add(new DoubleSetting.Builder()
        .name("Pitch Randomization")
        .description(".")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    //--------------------Interact--------------------//
    private final Setting<Boolean> interactRotate = rotateSetting("Interact", "interacting with a block", sgInteract);
    public final Setting<Double> interactTime = timeSetting("Interact", sgInteract);
    public final Setting<RotationCheckMode> interactMode = modeSetting("Interact", sgInteract);
    public final Setting<Double> interactYawAngle = yawAngleSetting("Interact", sgInteract, () -> interactMode.get() == RotationCheckMode.Angle);
    public final Setting<Double> interactPitchAngle = pitchAngleSetting("Interact", sgInteract, () -> interactMode.get() == RotationCheckMode.Angle);
    public final Setting<Integer> interactMemory = memorySetting("Interact", sgInteract);

    //--------------------Block-Place--------------------//
    private final Setting<Boolean> blockRotate = rotateSetting("Block Place", "placing a block", sgBlockPlace);
    public final Setting<Double> blockTime = timeSetting("Block Place", sgBlockPlace);
    public final Setting<RotationCheckMode> blockMode = modeSetting("Block Place", sgBlockPlace);
    public final Setting<Double> blockYawAngle = yawAngleSetting("Block Place", sgBlockPlace, () -> blockMode.get() == RotationCheckMode.Angle);
    public final Setting<Double> blockPitchAngle = pitchAngleSetting("Block Place", sgBlockPlace, () -> blockMode.get() == RotationCheckMode.Angle);
    public final Setting<Integer> blockMemory = memorySetting("Block Place", sgBlockPlace);

    //--------------------Mining--------------------//
    private final Setting<Boolean> mineRotate = rotateSetting("Mining", "mining a block", sgMining);
    public final Setting<Double> mineTime = timeSetting("Mining", sgMining);
    public final Setting<RotationCheckMode> mineMode = modeSetting("Mining", sgMining);
    public final Setting<MiningRotMode> mineTiming = sgMining.add(new EnumSetting.Builder<MiningRotMode>().name("Mining Rotate Timing").description(".").defaultValue(MiningRotMode.End).build());
    public final Setting<Double> mineYawAngle = yawAngleSetting("Mining", sgMining, () -> mineMode.get() == RotationCheckMode.Angle);
    public final Setting<Double> minePitchAngle = pitchAngleSetting("Mining", sgMining, () -> mineMode.get() == RotationCheckMode.Angle);
    public final Setting<Integer> mineMemory = memorySetting("Mining", sgMining);

    //--------------------Attack--------------------//
    private final Setting<Boolean> attackRotate = rotateSetting("Attack", "attacking an entity", sgAttack);
    public final Setting<Double> attackTime = timeSetting("Attack", sgAttack);
    public final Setting<RotationCheckMode> attackMode = modeSetting("Attack", sgAttack);
    public final Setting<Double> attackYawAngle = yawAngleSetting("Attack", sgAttack, () -> attackMode.get() == RotationCheckMode.Angle);
    public final Setting<Double> attackPitchAngle = pitchAngleSetting("Attack", sgAttack, () -> attackMode.get() == RotationCheckMode.Angle);
    public final Setting<Integer> attackMemory = memorySetting("Attack", sgAttack);

    //--------------------Use--------------------//
    public final Setting<Double> useTime = timeSetting("Use", sgUse);

    public enum MiningRotMode {
        Start,
        End,
        Double
    }

    public enum RotationCheckMode {
        Raytrace,
        StrictRaytrace,
        Angle
    }

    private Setting<Boolean> rotateSetting(String type, String verb, SettingGroup sg) {
        return sg.add(new BoolSetting.Builder().name(type + "旋转").description("当" + verb + "时旋转视角").defaultValue(false).build());
    }

    private Setting<Double> timeSetting(String type, SettingGroup sg) {
        return sg.add(new DoubleSetting.Builder().name(type + "旋转时间").description("在动作结束后保持旋转x秒").defaultValue(0.5).min(0).sliderRange(0, 1).build());
    }

    private Setting<RotationCheckMode> modeSetting(String type, SettingGroup sg) {
        return sg.add(new EnumSetting.Builder<RotationCheckMode>().name(type + "旋转模式").description(".").defaultValue(RotationCheckMode.Raytrace).build());
    }

    private Setting<Double> yawAngleSetting(String type, SettingGroup sg, IVisible visible) {
        return sg.add(new DoubleSetting.Builder().name(type + "偏航角度").description("当偏航角度小于此值时接受旋转").defaultValue(90).range(0, 180).sliderRange(0, 180).visible(visible).build());
    }

    private Setting<Double> pitchAngleSetting(String type, SettingGroup sg, IVisible visible) {
        return sg.add(new DoubleSetting.Builder().name(type + "俯仰角度").description("当俯仰角度小于此值时接受旋转").defaultValue(45).range(0, 180).sliderRange(0, 180).visible(visible).build());
    }

    private Setting<Integer> memorySetting(String type, SettingGroup sg) {
        return sg.add(new IntSetting.Builder().name(type + "记忆").description("若在x个数据包前注视过该目标则接受旋转").defaultValue(1).range(1, 20).sliderRange(1, 20).build());
    }

    public final Vec3d vec = new Vec3d(0, 0, 0);

    public boolean rotationCheck(Box box, RotationType type) {
        List<RotationManager.Rotation> history = RotationManager.history;
        if (box == null) return false;

        switch (mode(type)) {
            case Raytrace -> {
                for (int r = 0; r < memory(type); r++) {
                    if (history.size() <= r) break;

                    RotationManager.Rotation rot = history.get(r);

                    if (raytraceCheck(rot.vec(), rot.yaw(), rot.pitch(), box)) return true;
                }
            }
            case StrictRaytrace -> {
                for (int r = 0; r < memory(type); r++) {
                    if (history.size() <= r) break;

                    RotationManager.Rotation rot = history.get(r);

                    double range = 7;

                    Vec3d end = new Vec3d(
                        range * Math.cos(Math.toRadians(rot.yaw() + 90)) * Math.abs(Math.cos(Math.toRadians(rot.pitch()))),
                        range * -Math.sin(Math.toRadians(rot.pitch())),
                        range * Math.sin(Math.toRadians(rot.yaw() + 90)) * Math.abs(Math.cos(Math.toRadians(rot.pitch()))))
                        .add(rot.vec());

                    if (NCPRaytracer.raytrace(rot.vec(), end, box)) return true;
                }
            }
            case Angle -> {
                for (int r = 0; r < memory(type); r++) {
                    if (history.size() <= r) break;
                    RotationManager.Rotation rot = history.get(r);

                    if (angleCheck(rot.vec(), rot.yaw(), rot.pitch(), box, type)) return true;
                }
            }
        }

        return false;
    }

    public boolean shouldRotate(RotationType type) {
        return switch (type) {
            case 交互 -> interactRotate.get();
            case 方块放置 -> blockRotate.get();
            case 攻击 -> attackRotate.get();
            case Mining -> mineRotate.get();
            default -> true;
        };
    }

    public RotationCheckMode mode(RotationType type) {
        return switch (type) {
            case 交互 -> interactMode.get();
            case 方块放置 -> blockMode.get();
            case 攻击 -> attackMode.get();
            case Mining -> mineMode.get();
            default -> null;
        };
    }

    public double time(RotationType type) {
        return switch (type) {
            case 交互 -> interactTime.get();
            case 方块放置 -> blockTime.get();
            case 攻击 -> attackTime.get();
            case Mining -> mineTime.get();
            case Use -> useTime.get();
            case Other -> 1.0;
        };
    }

    public int memory(RotationType type) {
        return switch (type) {
            case 交互 -> interactMemory.get();
            case 方块放置 -> blockMemory.get();
            case 攻击 -> attackMemory.get();
            case Mining -> mineMemory.get();
            default -> 1;
        };
    }

    public double yawStep(RotationType type) {
        return switch (type) {
            case Other, Use -> 42069;
            default -> yawStep.get() + (Math.random() - 0.5) * 2 * yawRandomization.get();
        };
    }

    public double pitchStep(RotationType type) {
        return switch (type) {
            case Other, Use -> 42069;
            default -> pitchStep.get() + (Math.random() - 0.5) * 2 * pitchRandomization.get();
        };
    }

    public double yawAngle(RotationType type) {
        return switch (type) {
            case 交互 -> interactYawAngle.get();
            case 方块放置 -> blockYawAngle.get();
            case 攻击 -> attackYawAngle.get();
            case Mining -> mineYawAngle.get();
            default -> 0.0;
        };
    }

    public double pitchAngle(RotationType type) {
        return switch (type) {
            case 交互 -> interactPitchAngle.get();
            case 方块放置 -> blockPitchAngle.get();
            case 攻击 -> attackPitchAngle.get();
            case Mining -> minePitchAngle.get();
            default -> 0.0;
        };
    }

    public boolean angleCheck(Vec3d pos, double y, double p, Box box, RotationType type) {
        return RotationUtils.yawAngle(y, RotationUtils.getYaw(pos, box.getCenter())) <= yawAngle(type) && Math.abs(p - RotationUtils.getPitch(pos, box.getCenter())) <= pitchAngle(type);
    }

    public boolean raytraceCheck(Vec3d pos, double y, double p, Box box) {
        double range = pos.distanceTo(OLEPOSSUtils.getMiddle(box)) + 3;

        Vec3d end = new Vec3d(range * Math.cos(Math.toRadians(y + 90)) * Math.abs(Math.cos(Math.toRadians(p))),
            range * -Math.sin(Math.toRadians(p)),
            range * Math.sin(Math.toRadians(y + 90)) * Math.abs(Math.cos(Math.toRadians(p)))).add(pos);

        for (float i = 0; i < 1; i += 0.01) {
            if (box.contains(pos.x + (end.x - pos.x) * i, pos.y + (end.y - pos.y) * i, pos.z + (end.z - pos.z) * i)) return true;
        }
        return false;
    }

    private double lerp(double from, double to, double delta) {
        return from + (to - from) * delta;
    }

    public boolean validForCheck(BlockPos pos, BlockState state) {
        if (state.isSolid()) return true;
        if (state.getBlock() instanceof FluidBlock) return false;
        if (state.getBlock() instanceof StairsBlock) return false;
        if (state.hasBlockEntity()) return false;

        return state.isFullCube(mc.world, pos);
    }

    public boolean endMineRot() {
        if (!mineRotate.get()) return false;
        return mineTiming.get() == MiningRotMode.End || mineTiming.get() == MiningRotMode.Double;
    }

    public boolean startMineRot() {
        if (!mineRotate.get()) return false;
        return mineTiming.get() == MiningRotMode.Start || mineTiming.get() == MiningRotMode.Double;
    }
}

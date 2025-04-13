package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.enums.SwingHand;
import kassuk.addon.blackout.enums.SwingState;
import kassuk.addon.blackout.enums.SwingType;
import kassuk.addon.blackout.managers.Managers;
import kassuk.addon.blackout.utils.BOInvUtils;
import kassuk.addon.blackout.utils.RotationUtils;
import kassuk.addon.blackout.utils.SettingUtils;
import kassuk.addon.blackout.utils.meteor.BODamageUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;

import java.util.Objects;

/**
 * @author OLEPOSSU
 */

public class KillAuraPlus extends BlackOutModule {
    public KillAuraPlus() {
        super(BlackOut.BLACKOUT, "杀戮光环+", "强化版攻击模块，专为水晶PVP设计");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<TargetMode> targetMode = sgGeneral.add(new EnumSetting.Builder<TargetMode>()
        .name("目标模式")
        .description("选择优先攻击的目标类型")
        .defaultValue(TargetMode.Health)
        .build()
    );
    private final Setting<Integer> maxHp = sgGeneral.add(new IntSetting.Builder()
        .name("最大生命值")
        .description("目标生命值必须低于此值")
        .defaultValue(36)
        .min(0)
        .sliderMax(36)
        .build()
    );
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("攻击间隔")
        .description("每次攻击之间的延迟（秒）")
        .defaultValue(0.500)
        .min(0)
        .sliderMax(1)
        .build()
    );
    private final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("旋转模式")
        .description("攻击时的头部旋转时机（需在旋转设置中启用攻击旋转）")
        .defaultValue(RotationMode.OnHit)
        .build()
    );
    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("切换模式")
        .description("武器切换方式（建议保持禁用）")
        .defaultValue(SwitchMode.Disabled)
        .build()
    );
    private final Setting<Boolean> onlyWeapon = sgGeneral.add(new BoolSetting.Builder()
        .name("仅限武器")
        .description("只使用剑或斧进行攻击")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("挥动动画")
        .description("攻击实体时渲染武器挥动动画")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwingHand> swingHand = sgGeneral.add(new EnumSetting.Builder<SwingHand>()
        .name("挥动手部")
        .description("选择要渲染动画的手部")
        .defaultValue(SwingHand.真实手持)
        .visible(swing::get)
        .build()
    );

    private double timer = 0;
    private PlayerEntity target = null;

    @EventHandler
    private void onRender(Render3DEvent event) {
        timer = Math.min(delay.get(), timer + event.frameTime);

        updateTarget();

        if (target == null) {
            return;
        }

        boolean switched = false;
        switch (switchMode.get()) {
            case Disabled ->
                switched = !onlyWeapon.get() || mc.player.getMainHandStack().getItem() instanceof SwordItem || mc.player.getMainHandStack().getItem() instanceof AxeItem;
            case Normal -> {
                int slot = bestSlot(false);
                if (slot >= 0) {
                    InvUtils.swap(slot, true);
                    switched = true;
                }
            }
            case InvSwitch, PickSwitch, Silent -> switched = true;
        }

        if (!switched) {
            return;
        }

        boolean rotated = rotationMode.get() != RotationMode.Constant || !SettingUtils.shouldRotate(RotationType.攻击) || Managers.ROTATION.start(target.getBoundingBox(), priority, RotationType.攻击, Objects.hash(name + "attacking"));

        if (!rotated || timer < delay.get()) {
            return;
        }

        rotated = rotationMode.get() != RotationMode.OnHit || !SettingUtils.shouldRotate(RotationType.攻击) || Managers.ROTATION.start(target.getBoundingBox(), priority, RotationType.攻击, Objects.hash(name + "attacking"));

        if (!rotated) {
            return;
        }


        switch (switchMode.get()) {
            case Silent -> {
                switched = false;
                int slot = bestSlot(false);
                if (slot >= 0) {
                    InvUtils.swap(slot, true);
                    switched = true;
                }
            }
            case PickSwitch -> {
                switched = false;
                int slot = bestSlot(true);
                if (slot >= 0) {
                    switched = BOInvUtils.pickSwitch(slot);
                }
            }
            case InvSwitch -> {
                switched = false;
                int slot = bestSlot(true);
                if (slot >= 0) {
                    switched = BOInvUtils.invSwitch(slot);
                }
            }
        }

        if (!switched) {
            return;
        }

        attackTarget();

        switch (switchMode.get()) {
            case Silent -> InvUtils.swapBack();
            case InvSwitch -> BOInvUtils.swapBack();
            case PickSwitch -> BOInvUtils.pickSwapBack();
        }

        if (rotationMode.get() == RotationMode.OnHit) {
            Managers.ROTATION.end(Objects.hash(name + "attacking"));
        }
    }

    private void attackTarget() {
        timer = 0;

        SettingUtils.swing(SwingState.Pre, SwingType.攻击, Hand.MAIN_HAND);

        sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));

        SettingUtils.swing(SwingState.Post, SwingType.攻击, Hand.MAIN_HAND);
        if (swing.get()) clientSwing(swingHand.get(), Hand.MAIN_HAND);
    }

    private int bestSlot(boolean inventory) {
        int slot = -1;
        double hDmg = -1;
        double dmg;
        for (int i = 0; i < (inventory ? mc.player.getInventory().size() + 1 : 9); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (onlyWeapon.get() && !(stack.getItem() instanceof SwordItem) && !(stack.getItem() instanceof AxeItem)) {
                continue;
            }

            dmg = DamageUtils.getAttackDamage(mc.player, target, stack);
            if (dmg > hDmg) {
                slot = i;
                hDmg = dmg;
            }
        }
        return slot;
    }

    private void updateTarget() {
        double value = 0;
        target = null;

        mc.world.getPlayers().forEach(player -> {
            if (player.getHealth() <= 0 || player.isSpectator() || player.getHealth() + player.getAbsorptionAmount() > maxHp.get() || !SettingUtils.inAttackRange(player.getBoundingBox()) || player == mc.player || Friends.get().isFriend(player)) {
                return;
            }

            double val = switch (targetMode.get()) {
                case Health -> 10000 - player.getHealth() - player.getAbsorptionAmount();
                case Angle -> 10000 - Math.abs(RotationUtils.yawAngle(mc.player.getYaw(), Rotations.getYaw(player)));
                case Distance -> 10000 - mc.player.getPos().distanceTo(player.getPos());
            };
            if (val > value) {
                target = player;
            }
        });
    }

    public enum TargetMode {
        Health,
        Angle,
        Distance
    }

    public enum RotationMode {
        OnHit,
        Constant
    }

    public enum SwitchMode {
        Disabled,
        Normal,
        Silent,
        PickSwitch,
        InvSwitch
    }
}

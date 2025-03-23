package kassuk.addon.blackout.globalsettings;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.SwingState;
import kassuk.addon.blackout.enums.SwingType;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;

/**
 * @author OLEPOSSU
 */

public class SwingSettings extends BlackOutModule {
    public SwingSettings() {
        super(BlackOut.SETTINGS, "Swing", "Global swing settings for every blackout module.");
    }

    private final SettingGroup sgInteract = settings.createGroup("交互");
    private final SettingGroup sgBlockPlace = settings.createGroup("方块放置");
    private final SettingGroup sgMining = settings.createGroup("挖掘");
    private final SettingGroup sgAttack = settings.createGroup("攻击");
    private final SettingGroup sgUse = settings.createGroup("使用");

    public final Setting<Boolean> interact = sgInteract.add(new BoolSetting.Builder()
        .name("交互挥手")
        .description("当与方块交互时挥动手部。")
        .defaultValue(true)
        .build()
    );
    public final Setting<SwingState> interactState = sgInteract.add(new EnumSetting.Builder<SwingState>()
        .name("交互状态")
        .description("在操作之前或之后挥动手部")
        .defaultValue(SwingState.Post)
        .visible(interact::get)
        .build()
    );
    public final Setting<Boolean> blockPlace = sgBlockPlace.add(new BoolSetting.Builder()
        .name("方块放置挥手")
        .description("当放置方块时挥动手部。")
        .defaultValue(true)
        .build()
    );
    public final Setting<SwingState> blockPlaceState = sgBlockPlace.add(new EnumSetting.Builder<SwingState>()
        .name("方块放置状态")
        .description("在放置方块之前或之后挥动手部")
        .defaultValue(SwingState.Post)
        .visible(blockPlace::get)
        .build()
    );
    public final Setting<MiningSwingState> mining = sgMining.add(new EnumSetting.Builder<MiningSwingState>()
        .name("挖掘挥手")
        .description("放置水晶时挥动手部")
        .defaultValue(MiningSwingState.Double)
        .build()
    );
    public final Setting<Boolean> attack = sgAttack.add(new BoolSetting.Builder()
        .name("攻击挥手")
        .description("攻击实体时挥动手部")
        .defaultValue(true)
        .build()
    );
    public final Setting<SwingState> attackState = sgAttack.add(new EnumSetting.Builder<SwingState>()
        .name("攻击状态")
        .description("在攻击之前或之后挥动手部")
        .defaultValue(SwingState.Post)
        .visible(attack::get)
        .build()
    );
    public final Setting<Boolean> use = sgUse.add(new BoolSetting.Builder()
        .name("使用挥手")
        .description("使用物品时挥动手部")
        .defaultValue(true)
        .build()
    );
    public final Setting<SwingState> useState = sgUse.add(new EnumSetting.Builder<SwingState>()
        .name("使用状态")
        .description("在使用物品之前或之后挥动手部")
        .defaultValue(SwingState.Post)
        .visible(use::get)
        .build()
    );

    public void swing(SwingState state, SwingType type, Hand hand) {
        if (mc.player == null) {
            return;
        }
        if (!state.equals(getState(type))) {
            return;
        }

        switch (type) {
            case 交互 -> swing(interact.get(), hand);
            case 放置 -> swing(blockPlace.get(), hand);
            case 攻击 -> swing(attack.get(), hand);
            case 使用 -> swing(use.get(), hand);
        }
    }

    public void mineSwing(MiningSwingState state) {
        switch (state) {
            case Start -> {
                if (mining.get() != MiningSwingState.Start) {
                    return;
                }
            }
            case End -> {
                if (mining.get() != MiningSwingState.End) {
                    return;
                }
            }
            case Disabled -> {
                return;
            }
        }
        if (mc.player == null) {
            return;
        }

        swing(true, Hand.MAIN_HAND);
    }

    private SwingState getState(SwingType type) {
        return switch (type) {
            case 交互 -> interactState.get();
            case 挖掘 -> SwingState.Post;
            case 放置 -> blockPlaceState.get();
            case 攻击 -> attackState.get();
            case 使用 -> useState.get();
        };
    }

    private void swing(boolean shouldSwing, Hand hand) {
        if (mc.player == null) {
            return;
        }

        if (shouldSwing) mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
    }

    public enum MiningSwingState {
        Disabled,
        Start,
        End,
        Double
    }
}

package kassuk.addon.blackout.globalsettings;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

/**
 * @author OLEPOSSU
 */

public class ServerSettings extends BlackOutModule {
    public ServerSettings() {
        super(BlackOut.SETTINGS, "Server", "所有Blackout模块的全局服务器设置");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> cc = sgGeneral.add(new BoolSetting.Builder()
        .name("CC碰撞箱")
        .description("新放置的水晶需要1格高空间且没有实体碰撞箱")
        .defaultValue(false)
        .build()
    );
    public final Setting<Boolean> oldVerCrystals = sgGeneral.add(new BoolSetting.Builder()
        .name("1.12.2水晶")
        .description("放置水晶需要2格高的空间")
        .defaultValue(false)
        .build()
    );
    public final Setting<Boolean> oldVerDamage = sgGeneral.add(new BoolSetting.Builder()
        .name("1.12.2伤害计算")
        .description("使用旧版伤害计算方式")
        .defaultValue(false)
        .build()
    );

}

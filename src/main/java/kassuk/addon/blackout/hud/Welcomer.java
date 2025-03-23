package kassuk.addon.blackout.hud;

import kassuk.addon.blackout.BlackOut;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * @author KassuK
 */

public class Welcomer extends HudElement {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("界面缩放比例")
        .description("调整整个界面的显示比例大小")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("文本颜色")
        .description(BlackOut.COLOR)
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("文字阴影")
        .description("在文字后方显示阴影效果")
        .defaultValue(true)
        .build()
    );

    public static final HudElementInfo<Welcomer> INFO = new HudElementInfo<>(BlackOut.HUD_BLACKOUT, "Welcomer", "Welcomes you.", Welcomer::new);

    public Welcomer() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null || mc.world == null) {return;}

        String text = "Welcome " + mc.player.getName().getString();
        setSize(renderer.textWidth(text, shadow.get(), scale.get()), renderer.textHeight(shadow.get(), scale.get()));

        renderer.text(text, x, y, textColor.get(), shadow.get(), scale.get());
    }
}

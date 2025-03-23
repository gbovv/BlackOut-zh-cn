package kassuk.addon.blackout.hud;

import kassuk.addon.blackout.BlackOut;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.MathHelper;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author KassuK
 * @author OLEPOSSU
 * @author ccetl
 */

public class BlackoutArray extends HudElement {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWave = settings.createGroup("Wave");

    //--------------------General--------------------//
    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("模块颜色")
        .description("列表使用的模块名称颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );
    private final Setting<SettingColor> infoColor = sgGeneral.add(new ColorSetting.Builder()
        .name("信息颜色")
        .description("列表使用的信息文本颜色")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
    private final Setting<Side> side = sgGeneral.add(new EnumSetting.Builder<Side>()
        .name("对齐方向")
        .description("列表在屏幕上的对齐方位")
        .defaultValue(Side.Right)
        .build()
    );
    private final Setting<From> from = sgGeneral.add(new EnumSetting.Builder<From>()
        .name("排序起点")
        .description("模块排序的起始方向")
        .defaultValue(From.Top)
        .build()
    );
    private final Setting<Boolean> infoCare = sgGeneral.add(new BoolSetting.Builder()
        .name("信息长度影响排序")
        .description("排序时是否考虑信息文本的长度")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> onlyBlackout = sgGeneral.add(new BoolSetting.Builder()
        .name("仅显示Blackout模块")
        .description("只在列表中显示Blackout模块")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("文字阴影")
        .description("在文字后方显示阴影效果")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("界面缩放")
        .description("列表渲染时的缩放比例")
        .defaultValue(1)
        .min(0)
        .sliderRange(10, 10)
        .build()
    );

    //--------------------Wave--------------------//
    private final Setting<Boolean> wave = sgWave.add(new BoolSetting.Builder()
        .name("颜色波浪")
        .description("启用动态颜色波浪效果")
        .defaultValue(false)
        .build()
    );
    private final Setting<SettingColor> waveColor = sgWave.add(new ColorSetting.Builder()
        .name("模块波浪颜色")
        .description("模块名称使用的动态颜色")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
    private final Setting<SettingColor> infoWaveColor = sgWave.add(new ColorSetting.Builder()
        .name("信息波浪颜色")
        .description("信息文本使用的动态颜色")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
    private final Setting<Double> speed = sgWave.add(new DoubleSetting.Builder()
        .name("波浪速度")
        .description("颜色波浪效果的波动速度")
        .defaultValue(1)
        .min(0)
        .sliderRange(1, 10)
        .build()
    );
    private final Setting<Double> length = sgWave.add(new DoubleSetting.Builder()
        .name("波浪长度")
        .description("颜色波浪的波动周期长度")
        .defaultValue(5)
        .min(0)
        .sliderRange(1, 10)
        .build()
    );

    public static final HudElementInfo<BlackoutArray> INFO = new HudElementInfo<>(BlackOut.HUD_BLACKOUT, "BlackoutArray", "An ArrayList for blackout features.", BlackoutArray::new);

    public BlackoutArray() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double height = height(renderer);

        List<Line> lines = getModules()
            .stream()
            .sorted(Comparator.comparing(module -> width(renderer, module.title + (infoCare.get() ? (getInfo(module).isEmpty() ? "" : getInfo(module) + " ") : ""))))
            .map(module -> new Line(module.title, getInfo(module)))
            .collect(Collectors.toList());

        if (from.get() == From.Top) {
            Collections.reverse(lines);
        }

        setSize(120 * scale.get() * scale.get(), !lines.isEmpty() ? height * lines.size() : 30 * scale.get() * scale.get());

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            double f = 0;
            if (wave.get()) {
                f = Math.sin(System.currentTimeMillis() / 1000d * speed.get() - i / length.get()) + 1;
            }
            renderer.text(line.name, side.get() == Side.Left ? x : x + getWidth() - width(renderer, line.name + (line.info.isEmpty() ? "" : " " + line.info)), y + i * height, getColor(color.get(), waveColor.get(), f), shadow.get(), scale.get());
            renderer.text(line.info, side.get() == Side.Left ? x + width(renderer, line.name + " ") : x + getWidth() - width(renderer, line.info), y + i * height, getColor(infoColor.get(), infoWaveColor.get(), f), shadow.get(), scale.get());
        }
    }

    private String getInfo(Module module) {
        return module.getInfoString() == null ? "" : module.getInfoString();
    }

    private List<Module> getModules() {
        return Modules.get().getActive()
            .stream()
            .filter(module -> !onlyBlackout.get() || module.category.equals(BlackOut.BLACKOUT))
            .collect(Collectors.toList());
    }

    private Color getColor(SettingColor color, SettingColor waveColor, double f) {
        return wave.get() ? new Color(colorVal(color.r, waveColor.r, f), colorVal(color.g, waveColor.g, f), colorVal(color.b, waveColor.b, f), color.a) : color;
    }

    private int colorVal(int original, int wave, double f) {
        return MathHelper.clamp((int) Math.floor(wave + (original - wave) * f), 0, 255);
    }

    private record Line(String name, String info) {
        @Override
        public String toString() {
            return name + (info.isEmpty() ? "" : " " + info);
        }
    }

    private double width(HudRenderer renderer, String text) {
        return renderer.textWidth(text) * scale.get() * scale.get();
    }

    private double height(HudRenderer renderer) {
        return renderer.textHeight(true) * scale.get() * scale.get();
    }

    public enum Side {
        Right,
        Left
    }

    public enum From {
        Top,
        @SuppressWarnings("unused")
        Bottom
    }
}

package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.MergeMode;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.nvg.ui.NvgButton;
import com.niuqu.pickupcard.render.nvg.ui.NvgColorChip;
import com.niuqu.pickupcard.render.nvg.ui.NvgSlider;
import com.niuqu.pickupcard.render.nvg.ui.NvgToggle;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.style.StyleOverrides;
import com.niuqu.pickupcard.text.CountFormat;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 配置项注册表：每个配置项一行声明 —— <b>归哪一页、哪个小节、控件怎么造、默认值是什么</b>。
 *
 * <p>【为什么必须有这一个类（2026-09-19 架构审计定案）】用户报的四个"功能打架"里有三个
 * 出在"同一件事的真相存了好几处"：
 * <ol>
 *   <li><b>页面归属</b>从前写在五个 buildX 方法里，漂了：入场的「展开方式」落在位置页，
 *       玩家在动画页找不到它（第一问）。现在归谁全在这一张表里。</li>
 *   <li><b>默认值</b>从前在「恢复本页默认」里手抄了三十多个字面量，漂了：exitMode 抄的是
 *       FADE，正本已改成 TRAIN —— 点"恢复默认"反而改掉设置（第四问）。现在恢复一律
 *       {@code value.set(value.getDefault())}：默认值只有一个出处，就是 TOML 键定义自己。</li>
 *   <li>行是<b>纯数据</b>（控件供给器是懒的），页面归属与恢复值因此第一次能离线单测
 *       （{@code ConfigPageSpecTest}）。</li>
 * </ol>
 *
 * <p>【界面自己不存值】控件的读写直接打到 Forge 配置上（"界面显示 5、实际画 9"的老坑），
 * 这条纪律原样搬过来：每个工厂最后一行都是 {@link #changed()}。
 */
public final class ConfigPageSpec {

    /** 配置界面的一页。标签与页说明（底部那行）也归这里 —— 从前在界面的 Section 枚举里。 */
    public enum Page {
        GENERAL("通用", "弹不弹卡、要不要显示名字、什么算同一样东西"),
        ANIM("动画", "卡片出现和消失的快慢，以及数量变化怎么动"),
        LAYOUT("位置与堆叠", "卡片停在哪、同时最多几张（位置整屏拖拽调）"),
        LOOK("外观", "卡片的长相：形状一节、颜色一节"),
        /** 【为什么单独一页】三张名单是可增删的列表，行数会涨，跟固定八行的"外观"不是一回事。 */
        FILTER("过滤", "哪些东西不弹卡、一定要弹、或者弹了不出声");

        final String label;
        final String hint;

        Page(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        public String label() {
            return label;
        }

        public String hint() {
            return hint;
        }
    }

    /**
     * 一行。{@code widget == null} 的是小节头（占同样的行距、画暗色小字、不接悬停）。
     * <p>【供给器为什么是懒的】{@link #rows} 在测试里也会被调用，此时 Forge 配置还没加载、
     * Minecraft 还没启动 —— 控件只有在游戏里重建界面时才真正构造。
     */
    public record Row(Page page, String label, String hint,
                      Supplier<NvgWidget> widget, Runnable restore) {

        public boolean isHeader() {
            return widget == null;
        }

        public boolean restorable() {
            return restore != null;
        }
    }

    /** 「位置」那行要回调界面两件事：显示当前值、打开编辑场。界面自己实现。 */
    public interface AnchorBridge {

        /** 「位置」钮上的一句话（自动还是自定义、自定义在哪儿）。 */
        String anchorValueText();

        /** 点开整屏拖拽编辑场。 */
        void openEditor();
    }

    private ConfigPageSpec() {
    }

    /**
     * 全部配置行。顺序 = 玩家看到的顺序（页内小节头夹在中间）。
     *
     * @param style  当前生效的样式（主题 + 玩家改动）—— 外观/动画行的"生效值"显示用
     * @param eff    当前生效的行为设置 —— 行为行的"生效值"显示用
     * @param bridge 「位置」行的回调（只被那一行的控件供给器捕获，不会立刻调用）
     */
    public static List<Row> rows(StyleModel style, PickupCardSettings eff, AnchorBridge bridge) {
        List<Row> rows = new ArrayList<>();
        PickupCardConfig.Values v = PickupCardConfig.VALUES;
        addGeneral(rows, v, eff);
        addAnim(rows, v, style, eff);
        addLayout(rows, v, eff, bridge);
        addLook(rows, v, style);
        return List.copyOf(rows);
    }

    // ------------------------------------------------------------------
    // 通用：总开关置顶，按「显示什么」「行为与合并」分两节
    // ------------------------------------------------------------------

    private static void addGeneral(List<Row> rows, PickupCardConfig.Values v, PickupCardSettings eff) {
        rows.add(new Row(Page.GENERAL, "总开关",
                "关掉之后捡东西不再弹卡；重开时屏上不会涌出积压的旧卡",
                () -> bool(v.enabled, eff.enabled()), restore(v.enabled)));
        rows.add(new Row(Page.GENERAL, "显示什么", null, null, null));
        rows.add(new Row(Page.GENERAL, "显示物品名",
                "关掉后卡片只剩竖条、图标和数量，会明显变窄",
                () -> bool(v.showItemName, eff.showItemName()), restore(v.showItemName)));
        rows.add(new Row(Page.GENERAL, "显示物品ID",
                "名字一栏显示 minecraft:stone 这样的注册 ID，而不是它的中文名",
                () -> bool(v.showItemId, eff.showItemId()), restore(v.showItemId)));
        rows.add(new Row(Page.GENERAL, "名字最大宽度",
                "名字超过这个宽度就截断加省略号；0 = 按屏宽自动",
                () -> number(v.nameMaxWidth, eff.nameMaxWidth(), 0, 400, 1, "px"),
                restore(v.nameMaxWidth)));
        rows.add(new Row(Page.GENERAL, "数量写法",
                "数量的书写方式，点一下换下一种：+64 → ×64 → 64 → +1.2K",
                () -> cycle(v.countFormat, CountFormat.values(), ConfigPageSpec::countName),
                restore(v.countFormat)));
        rows.add(new Row(Page.GENERAL, "行为与合并", null, null, null));
        // 间距没有"-1=没改"的哨兵（范围 0..32），shown 永远不会用到 —— 传默认值即可
        rows.add(new Row(Page.GENERAL, "卡片间距",
                "两张卡上下之间的空隙。卡内「框与框」的距离是另一回事，归主题 JSON",
                () -> decimal(v.separation, LayoutSettings.DEFAULT_SEPARATION, 0, 16),
                restore(v.separation)));
        rows.add(new Row(Page.GENERAL, "合并粒度",
                "「同一种」的判定宽度，点一下换下一种：同名同附魔才并 → 同名就并 → 同名就并但改名不并 → 从不合并",
                () -> cycle(v.mergeMode, MergeMode.values(), ConfigPageSpec::mergeName),
                restore(v.mergeMode)));
    }

    // ------------------------------------------------------------------
    // 动画：入场（含展开方式）/ 停留与消失 / 合并与跳动
    // ------------------------------------------------------------------

    private static void addAnim(List<Row> rows, PickupCardConfig.Values v,
                                StyleModel style, PickupCardSettings eff) {
        rows.add(new Row(Page.ANIM, "入场", null, null, null));
        rows.add(new Row(Page.ANIM, "入场时长",
                "新卡从竖条后面滑出来到就位一共多久；调它预览会当场重演一遍",
                () -> styleTime(v.stEnterMs, style.enterMs(), 0, 2_000, 40), restore(v.stEnterMs)));
        rows.add(new Row(Page.ANIM, "入场动画",
                "只关新卡滑出这一段：关掉后新卡直接出现，跳动和淡出不受影响",
                () -> styleSwitch(v.stEnterEnabled, style.enterEnabled()),
                restore(v.stEnterEnabled)));
        // 【2026-09-19 搬家】展开方式 = 入场的形态（火车/拉幕），从前错放在位置页 ——
        // 玩家在动画页找不到它（审计第一问）。它跟入场时长是一伙的。
        rows.add(new Row(Page.ANIM, "展开方式",
                "新卡入场怎么从竖条右侧出现，点一下换下一种：火车（内容整块平移出来）→ 拉幕（可见范围从左往右展开）",
                () -> cycle(v.appearMode, LayoutSettings.Appear.values(), ConfigPageSpec::appearName),
                restore(v.appearMode)));
        rows.add(new Row(Page.ANIM, "停留与消失", null, null, null));
        rows.add(new Row(Page.ANIM, "停留时长",
                "一张卡从就位到开始淡出，在屏上待多久；连捡同一件会不断刷新这个计时",
                () -> time(v.holdMs, eff.holdMs(), 500, 10_000, 250), restore(v.holdMs)));
        rows.add(new Row(Page.ANIM, "消失方式",
                "卡片怎么消失，点一下换下一种：火车退回（平移回竖条后，默认）→ 淡出（原地变透明）→ 拉幕收拢（可见范围从右往左收）。三种都叠加透明度下降",
                () -> cycle(v.exitMode, LayoutSettings.Exit.values(), ConfigPageSpec::exitName),
                restore(v.exitMode)));
        rows.add(new Row(Page.ANIM, "消失时长",
                "上面那个消失动作用多久；0 = 到点立刻消失",
                () -> time(v.exitMs, eff.exitMs(), 0, 2_000, 20), restore(v.exitMs)));
        rows.add(new Row(Page.ANIM, "合并与跳动", null, null, null));
        rows.add(new Row(Page.ANIM, "数字跳动",
                "连续捡同一种东西时，整张卡向外鼓一下、数字从旧值滚到新值",
                () -> styleSwitch(v.stBumpEnabled, style.bumpEnabled()), restore(v.stBumpEnabled)));
        rows.add(new Row(Page.ANIM, "跳动时长",
                "上面那下「鼓」持续多久",
                () -> styleTime(v.stBumpMs, style.bumpMs(), 0, 1_000, 20), restore(v.stBumpMs)));
        rows.add(new Row(Page.ANIM, "淡回时长",
                "卡快淡完时又捡到同一种东西，多久补回不透明（已淡到快看不见时改按新卡重播）",
                () -> styleTime(v.stReviveMs, style.reviveMs(), 0, 1_000, 20), restore(v.stReviveMs)));
    }

    // ------------------------------------------------------------------
    // 位置与堆叠：锚在哪（拖拽调整）、怎么对齐、同屏几张
    // ------------------------------------------------------------------

    private static void addLayout(List<Row> rows, PickupCardConfig.Values v,
                                  PickupCardSettings eff, AnchorBridge bridge) {
        rows.add(new Row(Page.LAYOUT, "位置", null, null, null));
        rows.add(new Row(Page.LAYOUT, "位置",
                "整摞卡停在哪儿。点开整屏编辑场，按住那摞卡拖到想要的位置，"
                        + "全屏幕随便拖、拖到哪儿就是哪儿；新卡永远贴着锚线、旧的向上顶。"
                        + "位置按屏幕比例记忆，换缩放档不错位；锚线上放不下几张，"
                        + "放不下的自己去排队",
                () -> new NvgButton("", bridge::anchorValueText, bridge::openEditor), null));
        rows.add(new Row(Page.LAYOUT, "水平对齐",
                "锚线管卡的哪条边，点一下换下一种：竖条左缘锚定（竖条成一条线）→ 右缘对齐（右缘齐、左缘参差）。自动锚线跟着这一档各自解析",
                () -> cycle(v.align, LayoutSettings.Side.values(), ConfigPageSpec::sideName),
                restore(v.align)));
        rows.add(new Row(Page.LAYOUT, "卡片缩放",
                "100% 原样。「自动」= 锚线上快放不下整摞时按比例缩小，最小 60%",
                () -> percent(v.scalePercent, PickupCardConfig.layoutSnapshot().scalePercent()),
                restore(v.scalePercent)));
        rows.add(new Row(Page.LAYOUT, "数量", null, null, null));
        rows.add(new Row(Page.LAYOUT, "同屏上限",
                "同时在屏最多几张。锚线上实在放不下时，放不下的先回队列等位子，不会硬消失",
                () -> number(v.maxOnScreen, eff.maxOnScreen(), 1, 16, 1, " 张"),
                restore(v.maxOnScreen)));
        rows.add(new Row(Page.LAYOUT, "排队上限",
                "屏上满了就先排队（先来先上屏）；0 = 不排队，屏满之后的拾取直接丢掉。"
                        + "排不上的会并进「还有 N 项」那张溢出卡",
                () -> number(v.queueSize, eff.queueSize(), 0, 32, 1, " 张"),
                restore(v.queueSize)));
    }

    // ------------------------------------------------------------------
    // 外观：形状一节、颜色一节（全部"-1/空串 = 跟随主题"）
    // ------------------------------------------------------------------

    private static void addLook(List<Row> rows, PickupCardConfig.Values v, StyleModel style) {
        rows.add(new Row(Page.LOOK, "形状", null, null, null));
        rows.add(new Row(Page.LOOK, "竖条宽度",
                "最左那根稀有度颜色条的横向粗细；写回 -1 = 跟随主题（默认 2px）",
                () -> styleNumber(v.stBarWidth, style.barWidth(), 1, 8, 1, "px"),
                restore(v.stBarWidth)));
        rows.add(new Row(Page.LOOK, "图标内边距",
                "图标距卡顶、卡底各留多少；调大卡片变高，图标大小不变",
                () -> styleNumber(v.stPaddingV, style.paddingV(), 0, 8, 1, ""),
                restore(v.stPaddingV)));
        rows.add(new Row(Page.LOOK, "图标大小",
                "原版物品图标是 16 —— 取 16 或它的整数倍最清晰",
                () -> styleNumber(v.stIconSize, style.iconSize(), 8, 64, 1, "px"),
                restore(v.stIconSize)));
        rows.add(new Row(Page.LOOK, "圆角",
                "三个框的圆角半径；调到很大就变成胶囊",
                () -> styleNumber(v.stCornerRadius, style.cornerRadius(), 0, 16, 1, "px"),
                restore(v.stCornerRadius)));
        rows.add(new Row(Page.LOOK, "描边粗细",
                "框描边的粗细；0 = 不描边",
                () -> styleNumber(v.stBorderWidth, style.borderWidth(), 0, 4, 1, "px"),
                restore(v.stBorderWidth)));
        rows.add(new Row(Page.LOOK, "颜色", null, null, null));
        rows.add(new Row(Page.LOOK, "底色（上）",
                "卡面渐变的上端。点色块换一个（第一下回到主题色）；要精确色值写 TOML 的 fillTop，如 #7DE38B",
                () -> color(v.stFillTop, style.fillTop()), restore(v.stFillTop)));
        rows.add(new Row(Page.LOOK, "底色（下）",
                "渐变的下端。和上面写成一样就是纯色；精确色值写 TOML 的 fillBottom",
                () -> color(v.stFillBottom, style.fillBottom()), restore(v.stFillBottom)));
        rows.add(new Row(Page.LOOK, "描边颜色",
                "框描边的颜色；精确色值写 TOML 的 border",
                () -> color(v.stBorder, style.border()), restore(v.stBorder)));
        rows.add(new Row(Page.LOOK, "物品名颜色",
                "名字的颜色；精确色值写 TOML 的 nameColor",
                () -> color(v.stNameColor, style.nameColor()), restore(v.stNameColor)));
    }

    // ------------------------------------------------------------------
    // 恢复默认：默认值只有一个出处 —— TOML 键定义自己
    // ------------------------------------------------------------------

    /** 「恢复本页默认」那行的说明：项数现算，杜绝"九项"那种手抄漂移。 */
    public static String restoreHint(Page page, int restorable) {
        if (page == Page.FILTER) {
            return "清空三张名单 —— 误点会把你自己加的规则全删掉";
        }
        String base = "重置本页 " + restorable + " 项（其他页不动）";
        if (page == Page.LAYOUT) {
            return base + "；锚点不在这里 —— 它归编辑场的「回到默认」";
        }
        return base;
    }

    /** 恢复 = 写回键定义时的默认值。手抄字面量的那条路（漂出过 FADE）到此封死。 */
    private static <T> Runnable restore(ForgeConfigSpec.ConfigValue<T> value) {
        return () -> value.set(value.getDefault());
    }

    // ------------------------------------------------------------------
    // 控件工厂：配置读写都从这里进出（原样搬自界面，静态化）
    // ------------------------------------------------------------------

    static void changed() {
        CardStage.INSTANCE.refreshStyle();
    }

    private static NvgToggle bool(ForgeConfigSpec.BooleanValue config, boolean shown) {
        return new NvgToggle("", config::get, on -> {
            config.set(on);
            changed();
        });
    }

    /** 外观开关：配置里是 -1/0/1，界面只显示"开/关"，点了就写死 1/0。 */
    private static NvgToggle styleSwitch(ForgeConfigSpec.IntValue config, boolean shown) {
        return new NvgToggle("", () -> config.get() < 0 ? shown : config.get() == 1, on -> {
            config.set(on ? 1 : 0);
            changed();
        });
    }

    private static NvgSlider number(ForgeConfigSpec.IntValue config, int shown, int min, int max, int step,
                                    String suffix) {
        return new NvgSlider("", min, max, step,
                () -> (double) currentInt(config, shown), value -> {
            config.set((int) Math.round(value));
            changed();
        },
                value -> Integer.toString((int) Math.round(value)) + suffix);
    }

    private static NvgSlider styleNumber(ForgeConfigSpec.IntValue config, int shown, int min, int max,
                                         int step, String suffix) {
        return number(config, shown, min, max, step, suffix);
    }

    private static NvgSlider decimal(ForgeConfigSpec.DoubleValue config, double shown, double min, double max) {
        return new NvgSlider("", min, max, 1,
                () -> currentDouble(config, shown),
                value -> {
                    config.set((double) Math.round(value));
                    changed();
                },
                value -> Math.round(value) + "px");
    }

    private static NvgSlider time(ForgeConfigSpec.LongValue config, long shown, long min, long max, long step) {
        return new NvgSlider("", min, max, step,
                () -> (double) currentLong(config, shown), value -> {
            config.set(Math.round(value));
            changed();
        },
                value -> Math.round(value) + "ms");
    }

    private static NvgSlider styleTime(ForgeConfigSpec.LongValue config, long shown, long min, long max, long step) {
        return time(config, shown, min, max, step);
    }

    /**
     * 卡片缩放：0 是个真值（「自动」）。
     * <p>滑条上 0 是"自动"，1..49 是空档 —— 不夹的话界面会显示 "10%" 而生效的是 50%
     * （sanitized 会夹），那就是"设了等于没设"。
     */
    private static NvgSlider percent(ForgeConfigSpec.IntValue config, int shown) {
        return new NvgSlider("", LayoutSettings.AUTO_SCALE, LayoutSettings.MAX_SCALE_PERCENT, 5,
                () -> (double) config.get(),
                value -> {
                    int pct = (int) Math.round(value);
                    config.set(pct <= LayoutSettings.AUTO_SCALE ? LayoutSettings.AUTO_SCALE
                            : Math.max(LayoutSettings.MIN_SCALE_PERCENT, pct));
                    changed();
                },
                value -> value <= LayoutSettings.AUTO_SCALE ? "自动" : Math.round(value) + "%");
    }

    private static <E extends Enum<E>> NvgButton cycle(ForgeConfigSpec.EnumValue<E> config, E[] values,
                                                       Function<E, String> name) {
        return new NvgButton("", () -> name.apply(config.get()), () -> {
            E current = config.get();
            int i = 0;
            for (int k = 0; k < values.length; k++) {
                if (values[k] == current) {
                    i = k;
                }
            }
            config.set(values[(i + 1) % values.length]);
            changed();
        });
    }

    /** 颜色 = 色块循环；精确色值的出路在 TOML，行说明里有写。 */
    private static NvgColorChip color(ForgeConfigSpec.ConfigValue<String> config, int effectiveArgb) {
        return new NvgColorChip("", () -> effectiveArgb(config, effectiveArgb), config::get, text -> {
            config.set(text);
            changed();
        });
    }

    /** 色块画的生效色：配置里解析得动就用解析值（含手写 hex），否则回主题生效值。 */
    private static int effectiveArgb(ForgeConfigSpec.ConfigValue<String> config, int effective) {
        return StyleOverrides.parseArgb(config.get()).orElseGet(() -> effective);
    }

    /** 显示用的当前值：配置里"没改过"（-1）时给生效值，界面因此永远不显示第三态。 */
    private static int currentInt(ForgeConfigSpec.IntValue config, int effective) {
        return config.get() < 0 ? effective : config.get();
    }

    private static long currentLong(ForgeConfigSpec.LongValue config, long effective) {
        return config.get() < 0 ? effective : config.get();
    }

    private static double currentDouble(ForgeConfigSpec.DoubleValue config, double effective) {
        return config.get() < 0 ? effective : config.get();
    }

    // ------------------------------------------------------------------
    // 枚举值的界面说法（要能在同一行控件里读完，完整语义在悬停说明里）
    // ------------------------------------------------------------------

    private static String sideName(LayoutSettings.Side side) {
        return side == LayoutSettings.Side.RIGHT ? "右缘对齐" : "竖条左缘锚定";
    }

    /** 草稿（design/animation.html）自己的叫法：火车＝平移，拉幕＝展开可见范围。 */
    private static String appearName(LayoutSettings.Appear appear) {
        return appear == LayoutSettings.Appear.CLIP ? "拉幕" : "火车";
    }

    /** 与入场对称的那一半：淡出 / 火车退回 / 拉幕收拢。 */
    private static String exitName(LayoutSettings.Exit exit) {
        return switch (exit) {
            case TRAIN -> "火车退回";
            case WIPE -> "拉幕收拢";
            default -> "淡出";
        };
    }

    private static String countName(CountFormat format) {
        return switch (format) {
            case X_PREFIX -> "×64";
            case PLAIN -> "64";
            case ABBREVIATED -> "+1.2K";
            default -> "+64";
        };
    }

    private static String mergeName(MergeMode mode) {
        return switch (mode) {
            case SAME_ITEM -> "同名就并";
            case SAME_ITEM_KEEP_NAMED -> "改名不并";
            case NEVER -> "从不合并";
            default -> "同名同附魔才并";
        };
    }
}

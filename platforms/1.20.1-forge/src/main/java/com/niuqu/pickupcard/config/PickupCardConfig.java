package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.filter.FilterSettings;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.style.StyleOverrides;
import com.niuqu.pickupcard.style.Theme;
import com.niuqu.pickupcard.text.CountFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 玩家可见的设置。
 * <p>
 * 【外观在这里，但只是"改动"】卡面的默认值仍然只有一处真源：主题文件
 * （{@code styles/*.json}，资源包可覆盖）。{@code [style]} 段存的是**玩家改过的项**，
 * 每一项都能取 -1 = 跟随主题 —— 所以不存在"两处真源"：主题给默认值，配置只覆盖改过的。
 * 配置界面写的就是这一段，界面自己不另存一份。
 * <p>
 * 另一部分是"行为偏好"：停留多久、怎么合并、过滤谁。
 * <p>
 * 【配置项的名字与注释是玩家唯一会直接读到的文字】一律说人话，不许用比喻或代号 ——
 * 这条是硬规则（见 docs/plan-ui.md）。
 */
public final class PickupCardConfig {

    private static final ForgeConfigSpec SPEC;
    /** 包级可见：配置界面在同一个包里直接读写它 —— 界面不另存一份值，否则迟早出现
     *  "界面显示 5、实际画 9"这种两处都"对"的 bug。 */
    static final Values VALUES;

    static {
        Pair<Values, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Values::new);
        VALUES = pair.getLeft();
        SPEC = pair.getRight();
    }

    private PickupCardConfig() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    /** 采样一份当前值。热路径上每次读几个内存字段，代价可忽略，换来的是改配置不用重启。 */
    public static PickupCardSettings snapshot() {
        return new PickupCardSettings(
                VALUES.holdMs.get(),
                VALUES.exitMs.get(),
                VALUES.mergeEnabled.get(),
                VALUES.mergeWindowMs.get(),
                VALUES.maxOnScreen.get(),
                VALUES.countFormat.get()).sanitized();
    }

    /**
     * 采样布局设置。下面那一组键的注释一律按"人话"写：配置项是玩家唯一会直接读到的文字，
     * 比喻、内部代号、黑话在那里就是 bug。
     */
    public static LayoutSettings layoutSnapshot() {
        return new LayoutSettings(
                VALUES.stickTo.get(),
                VALUES.leftEdge.get(),
                VALUES.appearMode.get()).sanitized();
    }

    /** 采样过滤三表。列表元素不做校验——坏规则由 FilterRule.parse 静默跳过。 */
    public static FilterSettings filterSnapshot() {
        return new FilterSettings(
                List.copyOf(VALUES.blacklist.get()),
                List.copyOf(VALUES.whitelist.get()),
                List.copyOf(VALUES.muteList.get()));
    }

    /** 当前主题。 */
    public static Theme theme() {
        return VALUES.theme.get();
    }

    /**
     * 玩家改过的外观项。<b>-1 一律表示"没改过"，不覆盖主题</b> ——
     * 所以这里不会把主题的值抄一份进来，换主题依旧生效。
     */
    public static StyleOverrides styleOverrides() {
        StyleOverrides.Builder b = StyleOverrides.builder();
        int radius = VALUES.stCornerRadius.get();
        if (radius >= 0) {
            b.cornerRadius(radius);
        }
        int padH = VALUES.stPaddingH.get();
        if (padH >= 0) {
            b.paddingH(padH);
        }
        int padV = VALUES.stPaddingV.get();
        if (padV >= 0) {
            b.paddingV(padV);
        }
        int gap = VALUES.stGap.get();
        if (gap >= 0) {
            b.gap(gap);
        }
        int icon = VALUES.stIconSize.get();
        if (icon >= 0) {
            b.iconSize(icon);
        }
        int bar = VALUES.stBarWidth.get();
        if (bar >= 0) {
            b.barWidth(bar);
        }
        int barInset = VALUES.stBarInsetY.get();
        if (barInset >= 0) {
            b.barInsetY(barInset);
        }
        int shadowDy = VALUES.stShadowOffsetY.get();
        if (shadowDy >= 0) {
            b.shadowOffsetY(shadowDy);
        }
        int blur = VALUES.stShadowBlur.get();
        if (blur >= 0) {
            b.shadowBlur(blur);
        }
        int alpha = VALUES.stShadowAlpha.get();
        if (alpha >= 0) {
            b.shadowAlpha(alpha);
        }
        int highlight = VALUES.stTopHighlight.get();
        if (highlight >= 0) {
            b.topHighlight(highlight == 1);
        }
        long enterMs = VALUES.stEnterMs.get();
        if (enterMs >= 0) {
            b.enterMs(enterMs);
        }
        long bumpMs = VALUES.stBumpMs.get();
        if (bumpMs >= 0) {
            b.bumpMs(bumpMs);
        }
        int enterOn = VALUES.stEnterEnabled.get();
        if (enterOn >= 0) {
            b.enterEnabled(enterOn == 1);
        }
        int bumpOn = VALUES.stBumpEnabled.get();
        if (bumpOn >= 0) {
            b.bumpEnabled(bumpOn == 1);
        }
        return b.build();
    }

    /** 外观项一律"−1 = 跟随主题"，所以范围从 -1 起。 */
    private static ForgeConfigSpec.IntValue styleInt(ForgeConfigSpec.Builder builder, String name,
                                                    String comment, int max) {
        return builder.comment(comment, "-1 = 跟随主题（默认）。")
                .defineInRange(name, -1, -1, max);
    }

    /** 配置项的定义。行为偏好与 {@link PickupCardSettings}/{@link FilterSettings} 对应。 */
    static final class Values {

        final ForgeConfigSpec.LongValue holdMs;
        final ForgeConfigSpec.LongValue exitMs;
        final ForgeConfigSpec.BooleanValue mergeEnabled;
        final ForgeConfigSpec.LongValue mergeWindowMs;
        final ForgeConfigSpec.IntValue maxOnScreen;
        final ForgeConfigSpec.EnumValue<CountFormat> countFormat;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> blacklist;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> whitelist;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> muteList;
        final ForgeConfigSpec.EnumValue<LayoutSettings.Side> stickTo;
        final ForgeConfigSpec.IntValue leftEdge;
        final ForgeConfigSpec.EnumValue<LayoutSettings.Appear> appearMode;

        // ---- [style] 外观：全部用 -1 表示"跟随主题" ----
        final ForgeConfigSpec.EnumValue<Theme> theme;
        final ForgeConfigSpec.IntValue stCornerRadius;
        final ForgeConfigSpec.IntValue stPaddingH;
        final ForgeConfigSpec.IntValue stPaddingV;
        final ForgeConfigSpec.IntValue stGap;
        final ForgeConfigSpec.IntValue stIconSize;
        final ForgeConfigSpec.IntValue stBarWidth;
        final ForgeConfigSpec.IntValue stBarInsetY;
        final ForgeConfigSpec.IntValue stShadowOffsetY;
        final ForgeConfigSpec.IntValue stShadowBlur;
        final ForgeConfigSpec.IntValue stShadowAlpha;
        final ForgeConfigSpec.IntValue stTopHighlight;
        final ForgeConfigSpec.LongValue stEnterMs;
        final ForgeConfigSpec.LongValue stBumpMs;
        final ForgeConfigSpec.IntValue stEnterEnabled;
        final ForgeConfigSpec.IntValue stBumpEnabled;

        Values(ForgeConfigSpec.Builder builder) {
            builder.comment("Pickup Card —— 拾取卡片提示（纯客户端）").push("notice");

            holdMs = builder
                    .comment("一张卡在屏上停留多久（毫秒），从最近一次被刷新算起。",
                            "连捡同一件东西会不断刷新这个计时，所以连捡时不会闪。")
                    .defineInRange("holdMs", 2_600L, 200L, 60_000L);

            exitMs = builder
                    .comment("退场动画时长（毫秒）。渲染层按它决定退场动画播多久。")
                    .defineInRange("exitMs", 320L, 0L, 5_000L);

            builder.pop();

            builder.comment("合并：短时间内连续捡同一种东西，并成一张卡而不是弹一堆").push("merge");
            mergeEnabled = builder
                    .comment("开：2 秒内连捡 64 个钻石是一张卡的数字在滚。",
                            "关：捡几次弹几张。")
                    .define("enabled", true);

            mergeWindowMs = builder
                    .comment("合并窗口（毫秒）。超过这个间隔就单开一张新卡。")
                    .defineInRange("windowMs", 1_200L, 0L, 10_000L);
            builder.pop();

            builder.comment("布局").push("layout");

            maxOnScreen = builder
                    .comment("同时在屏最多几张。超出的会挤掉最久没被碰过的那张。")
                    .defineInRange("maxOnScreen", 5, 1, 16);

            stickTo = builder
                    .comment("卡片靠屏幕哪一边停。同时出现多张时，它们自上而下排列。",
                            "  LEFT  = 卡片左边尽量停在下面 leftEdge 的位置，内容往右伸展。推荐。",
                            "          一摞卡排下来，左边的稀有度竖条成一条竖线。",
                            "  RIGHT = 卡片右边固定不动，左边随内容长短伸缩。",
                            "          内容再长也只是往左伸，永远不会超出屏幕右边。")
                    .defineEnum("stickTo", LayoutSettings.Side.LEFT);

            leftEdge = builder
                    .comment("只有上面选了 LEFT 才有用：卡片左边想停在离屏幕左边多少像素的地方。",
                            "注意这是「想停在这儿」，不是「一定停在这儿」——",
                            "内容太长、右边放不下时，卡片会自动往左让，不会把内容挤出屏幕。",
                            "屏幕宽度会随玩家的界面缩放大小变化，所以别设得太靠右；",
                            "设得比屏幕还宽，这一项就等于没设。")
                    .defineInRange("leftEdge", 16, 0, 4000);

            appearMode = builder
                    .comment("卡片出现时怎么展开。",
                            "  SLIDE = 内容保持原样，从左往右平移到最终位置；先看到最右端，再逐渐看到全部。",
                            "  CLIP  = 内容位置不动，可见范围从左往右慢慢扩大；先看到最左端。")
                    .defineEnum("appearMode", LayoutSettings.Appear.SLIDE);

            builder.pop();

            builder.comment("数字").push("count");
            countFormat = builder
                    .comment("数量的写法。PLUS = +64（默认），X_PREFIX = ×64，",
                            "PLAIN = 64，ABBREVIATED = +1.2K。")
                    .defineEnum("format", CountFormat.PLUS);
            builder.pop();

            builder.comment("外观：这里只放玩家改过的项。-1 = 跟随主题（默认），此时该项用主题里的值。",
                            "游戏内配置界面写的就是这一段；手改 TOML 也行。")
                    .push("style");
            theme = builder
                    .comment("主题：DARK = 深色（默认），LIGHT = 浅色。",
                            "主题给的是整套默认值，下面那些改动只覆盖你改过的项 —— 换主题不会把你的改动丢掉。")
                    .defineEnum("theme", Theme.DARK);
            stCornerRadius = styleInt(builder, "cornerRadius", "三个框的圆角半径（像素）", 16);
            stPaddingH = styleInt(builder, "paddingH", "框内水平内边距（像素）", 16);
            stPaddingV = styleInt(builder, "paddingV", "框内垂直内边距（像素）。卡高 = 图标边长 + 上下各一份它", 16);
            stGap = styleInt(builder, "gap", "框与框之间、名字与数量之间的间隙（像素）", 16);
            stIconSize = styleInt(builder, "iconSize", "物品图标边长（像素）。原版贴图是 16，取 16 = 不缩放最清晰", 64);
            stBarWidth = styleInt(builder, "barWidth", "稀有度竖条宽度（像素）", 24);
            stBarInsetY = styleInt(builder, "barInsetY", "竖条上下各内缩多少（像素）。0 = 与卡片齐平", 16);
            stShadowOffsetY = styleInt(builder, "shadowOffsetY", "投影下移量（像素）", 16);
            stShadowBlur = styleInt(builder, "shadowBlur", "投影模糊半径（像素）。0 = 硬边，有效区间约 0-8", 16);
            stShadowAlpha = styleInt(builder, "shadowAlpha", "投影不透明度 0-255。0 = 不画投影", 255);
            stTopHighlight = styleInt(builder, "topHighlight", "顶部那条 1px 高光。0 = 不画，1 = 画", 1);
            stEnterMs = builder
                    .comment("入场动画总时长（毫秒）。竖条占前 30%，内容从 18% 起跑。", "-1 = 跟随主题（默认）。")
                    .defineInRange("enterMs", -1L, -1L, 5_000L);
            stBumpMs = builder
                    .comment("合并时数字跳动时长（毫秒）。", "-1 = 跟随主题（默认）。")
                    .defineInRange("bumpMs", -1L, -1L, 5_000L);
            stEnterEnabled = styleInt(builder, "enterEnabled", "入场动画开关。0 = 关（动画敏感玩家可以关掉，卡片直接出现）", 1);
            stBumpEnabled = styleInt(builder, "bumpEnabled", "合并时数字跳动开关。0 = 关", 1);
            builder.pop();

            builder.comment("过滤。规则写法：minecraft:stone = 物品，#forge:ores = tag，@somebotania = 整个 mod")
                    .push("filter");
            blacklist = builder
                    .comment("黑名单：命中则不弹卡。默认是空的 —— 也就是说默认每一次拾取都会弹卡，",
                            "包括泥土、圆石、沙子。嫌刷屏就往这里加（例如 minecraft:cobblestone）。")
                    .defineList("blacklist", List.of(), o -> o instanceof String);

            whitelist = builder
                    .comment("白名单：命中则永远弹卡并强调。优先级最高，压过黑名单。")
                    .defineList("whitelist", List.of(), o -> o instanceof String);

            muteList = builder
                    .comment("静音名单：命中照常弹卡，但没有稀有提示音，原版拾取音也压掉。")
                    .defineList("muteList", List.of(), o -> o instanceof String);
            builder.pop();
        }
    }
}

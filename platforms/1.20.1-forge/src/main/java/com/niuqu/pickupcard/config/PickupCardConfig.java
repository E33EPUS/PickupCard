package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.filter.FilterSettings;
import com.niuqu.pickupcard.notice.PickupCardSettings;
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
 * 【外观为什么不在这里】卡面的颜色/圆角/动画参数有唯一真源（styles/default.json，
 * 资源包可覆盖），放进配置就变成两处真源——改了配置不生效、改了主题又被配置盖掉，
 * 都比没有这个配置项更糟。这里只放"行为偏好"：停留多久、怎么合并、过滤谁、要不要音。
 */
public final class PickupCardConfig {

    private static final ForgeConfigSpec SPEC;
    private static final Values VALUES;

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

    /** 采样过滤三表。列表元素不做校验——坏规则由 FilterRule.parse 静默跳过。 */
    public static FilterSettings filterSnapshot() {
        return new FilterSettings(
                List.copyOf(VALUES.blacklist.get()),
                List.copyOf(VALUES.whitelist.get()),
                List.copyOf(VALUES.muteList.get()),
                VALUES.useDefaultIgnoreList.get());
    }

    /** 配置项的定义。行为偏好与 {@link PickupCardSettings}/{@link FilterSettings} 对应。 */
    static final class Values {

        final ForgeConfigSpec.LongValue holdMs;
        final ForgeConfigSpec.LongValue exitMs;
        final ForgeConfigSpec.BooleanValue mergeEnabled;
        final ForgeConfigSpec.LongValue mergeWindowMs;
        final ForgeConfigSpec.IntValue maxOnScreen;
        final ForgeConfigSpec.EnumValue<CountFormat> countFormat;
        final ForgeConfigSpec.BooleanValue useDefaultIgnoreList;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> blacklist;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> whitelist;
        final ForgeConfigSpec.ConfigValue<List<? extends String>> muteList;

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
            builder.pop();

            builder.comment("数字").push("count");
            countFormat = builder
                    .comment("数量的写法。PLUS = +64（默认），X_PREFIX = ×64，",
                            "PLAIN = 64，ABBREVIATED = +1.2K。")
                    .defineEnum("format", CountFormat.PLUS);
            builder.pop();

            builder.comment("过滤。规则写法：minecraft:stone = 物品，#forge:ores = tag，@somebotania = 整个 mod")
                    .push("filter");
            useDefaultIgnoreList = builder
                    .comment("内置默认忽略表（泥土/圆石/沙子类刷屏物品），整体开关。",
                            "白名单永远压过黑名单与内置表。")
                    .define("useDefaultIgnoreList", true);

            blacklist = builder
                    .comment("黑名单：命中则不弹卡。")
                    .defineList("blacklist", List.of(), o -> o instanceof String);

            whitelist = builder
                    .comment("白名单：命中则永远弹卡并强调（含内置表忽略的物品）。")
                    .defineList("whitelist", List.of(), o -> o instanceof String);

            muteList = builder
                    .comment("静音名单：命中照常弹卡，但没有稀有提示音，原版拾取音也压掉。")
                    .defineList("muteList", List.of(), o -> o instanceof String);
            builder.pop();
        }
    }
}

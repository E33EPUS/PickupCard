package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.text.CountFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 玩家可见的设置。
 * <p>
 * 【为什么项这么少】上一版有一整套"背景色 / 描边厚度 / 边框厚度 / 圆角半径"——那些是
 * 自己写渲染层时不得不暴露的参数，因为改外观要重新编译。现在外观是 CSS，玩家（和作者）
 * 直接改页面文件就行，配置里只留"真的属于玩家偏好"的东西：停留多久、要不要合并、
 * 同时最多几张、数字怎么写。
 * <p>
 * 【刻意没做的】外观相关的都不放配置：它们有唯一真源（HTML/CSS），放进配置就又变成两处了。
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

    /** 配置项的定义。与 {@link PickupCardSettings} 一一对应。 */
    static final class Values {

        final ForgeConfigSpec.LongValue holdMs;
        final ForgeConfigSpec.LongValue exitMs;
        final ForgeConfigSpec.BooleanValue mergeEnabled;
        final ForgeConfigSpec.LongValue mergeWindowMs;
        final ForgeConfigSpec.IntValue maxOnScreen;
        final ForgeConfigSpec.EnumValue<CountFormat> countFormat;

        Values(ForgeConfigSpec.Builder builder) {
            builder.comment("Pickup Card —— 拾取卡片提示（纯客户端）").push("notice");

            holdMs = builder
                    .comment("一张卡在屏上停留多久（毫秒），从最近一次被刷新算起。",
                            "连捡同一件东西会不断刷新这个计时，所以连捡时不会闪。")
                    .defineInRange("holdMs", 2_600L, 200L, 60_000L);

            exitMs = builder
                    .comment("退场动画时长（毫秒）。必须和 CSS 里退场动画的时长一致，",
                            "否则节点会在动画播完之前被摘掉，看起来就是\"卡突然消失\"。")
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
        }
    }
}

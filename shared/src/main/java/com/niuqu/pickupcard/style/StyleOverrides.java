package com.niuqu.pickupcard.style;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * 玩家在配置界面里改的"外观参数"。
 *
 * <p>【它和主题 JSON 是什么关系】主题（{@code assets/pickupcard/styles/*.json}）是**一套设计**，
 * 配置是**玩家在这套设计上的个人改动**。<b>主题给默认值，配置只覆盖它改过的项</b> ——
 * 所以每个字段都是可选的：没设过 = "这项没动，用主题的"。这样"换主题"和
 * "我只把圆角调大一点"两件事不会互相覆盖。
 *
 * <p>【为什么用 builder 而不是 15 个位置参数】这一排参数里有一半是同类型的 Optional，
 * 写错位置编译器不会报错、单测也很难发现（表现是"改阴影结果圆角变了"）。
 *
 * <p>【一个键只有一个家】配置界面写的就是这份值（落盘在 TOML 的 {@code [style]} 段），
 * 界面不另存一份。主题 JSON 里同名键仍然有效，只是会被这里压过去。
 */
public record StyleOverrides(OptionalInt cornerRadius,
                             OptionalInt paddingH,
                             OptionalInt paddingV,
                             OptionalInt gap,
                             OptionalInt iconSize,
                             OptionalInt barWidth,
                             OptionalInt barInsetY,
                             OptionalLong enterMs,
                             OptionalLong bumpMs,
                             Optional<Boolean> enterEnabled,
                             Optional<Boolean> bumpEnabled) {

    /** 一项都没覆盖 = 完全用主题。 */
    public static StyleOverrides none() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 把覆盖盖到主题上。
     * <p>
     * 【顺序不能反】先覆盖、再 {@link StyleModel#sanitized()} —— 玩家可以在 TOML 里手写任何
     * 数字，夹逼必须是最后一道。反过来等于给玩家开了一个能把渲染打崩的口子。
     */
    public StyleModel apply(StyleModel theme) {
        return new StyleModel(
                cornerRadius.orElse(theme.cornerRadius()),
                paddingH.orElse(theme.paddingH()),
                paddingV.orElse(theme.paddingV()),
                gap.orElse(theme.gap()),
                iconSize.orElse(theme.iconSize()),
                barWidth.orElse(theme.barWidth()),
                barInsetY.orElse(theme.barInsetY()),
                theme.fillTop(),
                theme.fillBottom(),
                theme.border(),
                theme.glowAlpha(),
                theme.nameColor(),
                enterMs.orElse(theme.enterMs()),
                bumpMs.orElse(theme.bumpMs()),
                enterEnabled.orElse(theme.enterEnabled()),
                bumpEnabled.orElse(theme.bumpEnabled()),
                theme.glowPulseEnabled()).sanitized();
    }

    /** 可变构建器：每设一项都显式命名，杜绝位置错。 */
    public static final class Builder {
        private OptionalInt cornerRadius = OptionalInt.empty();
        private OptionalInt paddingH = OptionalInt.empty();
        private OptionalInt paddingV = OptionalInt.empty();
        private OptionalInt gap = OptionalInt.empty();
        private OptionalInt iconSize = OptionalInt.empty();
        private OptionalInt barWidth = OptionalInt.empty();
        private OptionalInt barInsetY = OptionalInt.empty();
        private OptionalLong enterMs = OptionalLong.empty();
        private OptionalLong bumpMs = OptionalLong.empty();
        private Optional<Boolean> enterEnabled = Optional.empty();
        private Optional<Boolean> bumpEnabled = Optional.empty();

        public Builder cornerRadius(int value) {
            cornerRadius = OptionalInt.of(value);
            return this;
        }

        public Builder paddingH(int value) {
            paddingH = OptionalInt.of(value);
            return this;
        }

        public Builder paddingV(int value) {
            paddingV = OptionalInt.of(value);
            return this;
        }

        public Builder gap(int value) {
            gap = OptionalInt.of(value);
            return this;
        }

        public Builder iconSize(int value) {
            iconSize = OptionalInt.of(value);
            return this;
        }

        public Builder barWidth(int value) {
            barWidth = OptionalInt.of(value);
            return this;
        }

        public Builder barInsetY(int value) {
            barInsetY = OptionalInt.of(value);
            return this;
        }

        public Builder enterMs(long value) {
            enterMs = OptionalLong.of(value);
            return this;
        }

        public Builder bumpMs(long value) {
            bumpMs = OptionalLong.of(value);
            return this;
        }

        public Builder enterEnabled(boolean value) {
            enterEnabled = Optional.of(value);
            return this;
        }

        public Builder bumpEnabled(boolean value) {
            bumpEnabled = Optional.of(value);
            return this;
        }

        public StyleOverrides build() {
            return new StyleOverrides(cornerRadius, paddingH, paddingV, gap, iconSize, barWidth,
                    barInsetY, enterMs, bumpMs, enterEnabled, bumpEnabled);
        }
    }
}

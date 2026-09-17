package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.painter.TrioCardPainter;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.style.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.LongConsumer;
import java.util.function.IntConsumer;

/**
 * 游戏内的外观配置界面。
 *
 * <p>【为什么现在才有】v1 明确不做完整配置 GUI（{@code docs/design.md}）。但拖得越久，
 * "配置项"就越像一堆只有手改 TOML 才能用的暗号 —— 而草稿页上那排开关本来就是
 * 给人拨的。这个界面只做一件事：<b>让那排开关在游戏里也能拨</b>。
 *
 * <p>【它改的是配置，不是渲染】每次拨动 → 写 TOML 的值 → 通知渲染立刻重读。
 * 界面自己不存一份：否则"界面显示 5、实际画 9"这种 bug 迟早会出现，
 * 而且它属于最难查的那类（两个地方都"对"）。
 *
 * <p>【为什么在 config 包里】因为它要读写的就是 {@link PickupCardConfig} 的同一份
 * 配置值。放到别的包里就得再开一层转发接口，而那层接口除了满足包里没别人之外没有价值。
 *
 * <p>【每个键都能单独生效】左侧是外观、右侧是动画。上面那张卡是**实时预览**，
 * 而且它走的就是真卡那套绘制代码（见 {@code TrioCardPainter.paintPreview}）——
 * 预览自己画一遍的话，它迟早会和真卡不一样，那时"所见即所得"就是假的。
 */
public final class PickupCardConfigScreen extends Screen {

    private static final int W = 172;
    /** 行高与步进是量出来的，不是拍脑袋：MC 的逻辑画布只有 240 高（1280x720 下 guiScale 3），
     *  8 行 × 22px 会溢出屏幕（第一版就是这样，截图里右列只数到 6 行）。 */
    private static final int ROW_H = 16;
    private static final int STEP = 18;
    private static final int PREVIEW_Y = 30;
    private static final int COL_TOP = 92;

    private final Screen parent;

    public PickupCardConfigScreen(Screen parent) {
        super(Component.literal("拾取卡片 · 外观与动画"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PickupCardConfig.Values v = PickupCardConfig.VALUES;
        int col1 = 16;
        int col2 = Math.max(col1 + W + 12, this.width / 2 + 6);

        int y = COL_TOP;
        addRenderableWidget(themeButton(v, col1, y));
        y += STEP;
        addRenderableWidget(intSlider("阴影偏移", v.stShadowOffsetY, 0, 8, col1, y));
        y += STEP;
        addRenderableWidget(intSlider("阴影模糊", v.stShadowBlur, 0, 16, col1, y));
        y += STEP;
        addRenderableWidget(intSlider("阴影浓度", v.stShadowAlpha, 0, 255, col1, y));
        y += STEP;
        addRenderableWidget(triToggle("顶部高光", v.stTopHighlight, col1, y));
        y += STEP;
        addRenderableWidget(intSlider("圆角", v.stCornerRadius, 0, 16, col1, y));
        y += STEP;
        addRenderableWidget(intSlider("框间距", v.stGap, 0, 16, col1, y));
        y += STEP;
        addRenderableWidget(intSlider("横向内边距", v.stPaddingH, 0, 16, col1, y));

        y = COL_TOP;
        addRenderableWidget(intSlider("图标边长", v.stIconSize, 8, 64, col2, y));
        y += STEP;
        addRenderableWidget(intSlider("竖条宽度", v.stBarWidth, 1, 24, col2, y));
        y += STEP;
        addRenderableWidget(intSlider("竖条内缩", v.stBarInsetY, 0, 16, col2, y));
        y += STEP;
        addRenderableWidget(intSlider("纵向内边距", v.stPaddingV, 0, 16, col2, y));
        y += STEP;
        addRenderableWidget(longSlider("入场时长", v.stEnterMs, 0, 2_000, col2, y));
        y += STEP;
        addRenderableWidget(triToggle("入场动画", v.stEnterEnabled, col2, y));
        y += STEP;
        addRenderableWidget(longSlider("跳动时长", v.stBumpMs, 0, 2_000, col2, y));
        y += STEP;
        addRenderableWidget(triToggle("数字跳动", v.stBumpEnabled, col2, y));

        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        // 实时预览：真卡的绘制代码 + 当前样式
        StyleModel style = CardStage.INSTANCE.previewStyle();
        TrioCardPainter.paintPreview(gui, style, 20f, PREVIEW_Y, 150f,
                new ItemStack(Items.NETHER_STAR), "经验", "+137", 0xFF7DFF8A);

        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, Component.literal(
                        "改完立刻生效；-1 / 跟随主题 = 用主题里的值"),
                this.width / 2, COL_TOP - 24, 0xFF9AA4AD);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private Button themeButton(PickupCardConfig.Values v, int x, int y) {
        Button[] holder = new Button[1];
        Button button = Button.builder(Component.literal("主题: " + themeName(v.theme.get())), b -> {
            Theme next = v.theme.get() == Theme.DARK ? Theme.LIGHT : Theme.DARK;
            v.theme.set(next);
            changed();
            b.setMessage(Component.literal("主题: " + themeName(next)));
        }).bounds(x, y, W, ROW_H).build();
        holder[0] = button;
        return button;
    }

    private Button triToggle(String label, ForgeConfigSpec.IntValue config, int x, int y) {
        return Button.builder(Component.literal(label + ": " + triName(config.get())), b -> {
            int next = config.get() < 0 ? 1 : (config.get() == 1 ? 0 : -1);
            config.set(next);
            changed();
            b.setMessage(Component.literal(label + ": " + triName(next)));
        }).bounds(x, y, W, ROW_H).build();
    }

    /**
     * 数值滑条。
     * <p>
     * 【参数为什么不叫 value】{@link AbstractSliderButton} 自己有一个 {@code protected double value}，
     * 匿名内部类里那个名字会被它遮住 —— 写 {@code value.set(...)} 编译报"double 不能被解引用"。
     * 名字冲突编译器会说话，但这种"看着对、报错却看不懂"的最费时间，所以直接改名叫 config。
     */
    private AbstractSliderButton intSlider(String label, ForgeConfigSpec.IntValue config, int min, int max,
                                           int x, int y) {
        return new AbstractSliderButton(x, y, W, ROW_H, Component.empty(), sliderPos(config.get(), min, max)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(label + ": " + valueName(toValue(this.value, min, max))));
            }

            @Override
            protected void applyValue() {
                config.set(toValue(this.value, min, max));
                changed();
            }
        };
    }

    private AbstractSliderButton longSlider(String label, ForgeConfigSpec.LongValue config, long min, long max,
                                            int x, int y) {
        return new AbstractSliderButton(x, y, W, ROW_H, Component.empty(),
                (config.get() - min) / (double) (max - min)) {
            @Override
            protected void updateMessage() {
                long ms = min + Math.round(this.value * (max - min));
                setMessage(Component.literal(label + ": " + (ms < 0 ? "跟随主题" : ms + "ms")));
            }

            @Override
            protected void applyValue() {
                config.set(min + Math.round(this.value * (max - min)));
                changed();
            }
        };
    }

    /** 滑条位置：-1（跟随主题）留给最左边，其余值线性铺开。 */
    private static double sliderPos(int value, int min, int max) {
        int lo = Math.max(0, min);
        if (value < 0) {
            return 0.0;
        }
        return (value - lo + 1) / (double) (max - lo + 1);
    }

    private static int toValue(double pos, int min, int max) {
        int lo = Math.max(0, min);
        int span = max - lo + 1;
        int index = (int) Math.round(pos * span) - 1;
        return index < 0 ? -1 : Math.min(max, lo + index);
    }

    private static String themeName(Theme theme) {
        return theme == Theme.LIGHT ? "浅色" : "深色";
    }

    private static String triName(int value) {
        return value < 0 ? "跟随主题" : (value == 1 ? "开" : "关");
    }

    private static String valueName(int value) {
        return value < 0 ? "跟随主题" : Integer.toString(value);
    }

    /** 每次改动都让渲染立刻重读，不等那一秒的重读间隔。 */
    private static void changed() {
        CardStage.INSTANCE.refreshStyle();
    }
}

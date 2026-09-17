package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.layout.StackLayout;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.text.CountFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 游戏内配置界面：<b>左侧一列分类标签，右侧是实时预览 + 这个分类的配置项</b>。
 *
 * <p>【为什么是"标签页 + 预览"这个形状】2026-09-17 用户的原话："以前那张界面是有明确分类的
 * （动画、外观等），左边一列标签，右边放实时预览和配置项 —— 那个效果很好；现在这个我
 * 不知道都是些什么东西。" 上一版把 25 个控件平铺成两列、全是长得一样的滑条、标签还是
 * 实现词（"纵向内边距""跟随主题"）—— 那不是"信息密度高"，是<b>没有分类、没有解释</b>。
 *
 * <p>【两条硬规矩】
 * <ol>
 *   <li><b>每个控件悬停时出一句人话</b>（{@link #hints}）。标签只能写短词，短词一定有歧义；
 *       把"改了会怎样"挂在旁边，是这个界面唯一能自我解释的办法。</li>
 *   <li><b>预览必须和当前分类相关</b>：调外观/动画看一张卡，调布局/行为看一摞卡 ——
 *       贴边、锚点、同时几张这些键，单张卡根本看不出来。</li>
 * </ol>
 *
 * <p>【它改的是配置，不是渲染】拨动 → 写配置 → 通知渲染立刻重读。界面自己不存一份，
 * 否则"界面显示 5、实际画 9"迟早出现。
 *
 * <p>【主题那项去哪了】从界面上撤了。用户原话"现在这个主题配置我不知道有什么意义" ——
 * 深浅色主题是给资源包作者和整套换肤用的，不是日常会拨的开关，占一格反而稀释了别的项。
 * 配置键还在（{@code [style] theme}），手改 TOML 一样有效。
 */
public final class PickupCardConfigScreen extends Screen {

    // ---- 左侧标签列 ----
    private static final int TAB_X = 8;
    private static final int TAB_TOP = 30;
    private static final int TAB_W = 74;
    private static final int TAB_H = 18;
    private static final int TAB_STEP = 20;

    // ---- 右侧：预览 + 配置项 ----
    private static final int ITEM_W = 150;
    private static final int ITEM_STEP = 16;
    private static final int ITEM_TOP = 62;

    /** 分类。名字用大白话，不用 Left/Right 这种实现词。 */
    private enum Tab {
        LOOK("外观"),
        ANIM("动画"),
        LAYOUT("布局"),
        BEHAVIOR("行为");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private Tab tab = Tab.LOOK;
    /** 悬停提示：控件 → 一句人话。标签只能写短词，短词一定会有歧义。 */
    private final Map<AbstractWidget, String> hints = new LinkedHashMap<>();

    public PickupCardConfigScreen(Screen parent) {
        super(Component.literal("拾取卡片 · 设置"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------
    // 搭界面
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        hints.clear();
        PickupCardConfig.Values v = PickupCardConfig.VALUES;

        int y = TAB_TOP;
        for (Tab t : Tab.values()) {
            Tab target = t;
            addRenderableWidget(Button.builder(
                            Component.literal((t == tab ? "▸ " : "  ") + t.label),
                            b -> {
                                tab = target;
                                rebuildWidgets();
                            })
                    .bounds(TAB_X, y, TAB_W, TAB_H).build());
            y += TAB_STEP;
        }

        switch (tab) {
            case LOOK -> buildLook(v);
            case ANIM -> buildAnim(v);
            case LAYOUT -> buildLayout(v);
            case BEHAVIOR -> buildBehavior(v);
        }

        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(TAB_X, this.height - 24, TAB_W, 18).build());
    }

    private void buildLook(PickupCardConfig.Values v) {
        int c1 = itemX1();
        int c2 = itemX2();
        int y = ITEM_TOP;
        hint(addRenderableWidget(intSlider("卡片高低", v.stPaddingV, 0, 8, c1, y)),
                "卡片厚多少。调大卡片变厚，图标大小不变");
        hint(addRenderableWidget(intSlider("图标大小", v.stIconSize, 8, 64, c2, y)),
                "原版物品图标是 16 —— 取 16 或它的整数倍最清晰");
        y += itemStep();
        hint(addRenderableWidget(intSlider("竖条宽度", v.stBarWidth, 1, 24, c1, y)),
                "左边那根稀有度竖条的粗细");
        hint(addRenderableWidget(intSlider("竖条内缩", v.stBarInsetY, 0, 16, c2, y)),
                "竖条上下各缩进去多少；0 = 和卡片一样高");
        y += itemStep();
        hint(addRenderableWidget(intSlider("圆角", v.stCornerRadius, 0, 16, c1, y)),
                "三个框的圆角半径；调到很大就变成胶囊");
        hint(addRenderableWidget(intSlider("框间距", v.stGap, 0, 16, c2, y)),
                "图标格、名字框之间的缝");
        y += itemStep();
        hint(addRenderableWidget(intSlider("左右内边距", v.stPaddingH, 0, 16, c1, y)),
                "名字和框边之间的距离");
        hint(addRenderableWidget(triToggle("顶部高光", v.stTopHighlight, c2, y)),
                "框顶那条 1px 亮线；关了就没玻璃感");
        y += itemStep();
        hint(addRenderableWidget(intSlider("阴影浓度", v.stShadowAlpha, 0, 255, c1, y)),
                "卡片投影的浓淡；0 = 不画投影");
        hint(addRenderableWidget(intSlider("阴影模糊", v.stShadowBlur, 0, 16, c2, y)),
                "投影边缘的软硬；0 = 硬边");
        y += itemStep();
        hint(addRenderableWidget(intSlider("阴影下移", v.stShadowOffsetY, 0, 8, c1, y)),
                "投影往下偏几个像素");
    }

    private void buildAnim(PickupCardConfig.Values v) {
        int c1 = itemX1();
        int c2 = itemX2();
        int y = ITEM_TOP;
        hint(addRenderableWidget(longSlider("入场时长", v.stEnterMs, 0, 2_000, c1, y)),
                "卡片从竖条后面滑出来的总时间");
        hint(addRenderableWidget(triToggle("入场动画", v.stEnterEnabled, c2, y)),
                "关掉就是直接出现（晕动症友好）");
        y += itemStep();
        hint(addRenderableWidget(longSlider("跳动时长", v.stBumpMs, 0, 2_000, c1, y)),
                "连续捡同一种东西时，数字弹一下的时间");
        hint(addRenderableWidget(triToggle("数字跳动", v.stBumpEnabled, c2, y)),
                "关掉数字就不弹");
    }

    private void buildLayout(PickupCardConfig.Values v) {
        int c1 = itemX1();
        int c2 = itemX2();
        int y = ITEM_TOP;
        hint(addRenderableWidget(sideButton(v, c1, y)),
                "左 = 竖条成一条竖线；右 = 卡的右缘齐、竖条参差");
        hint(addRenderableWidget(anchorSlider(v, c2, y)),
                "竖条左缘停在哪；自动 = 跟着画布宽度算");
        y += itemStep();
        hint(addRenderableWidget(enumButton("展开方式", v.appearMode, LayoutSettings.Appear.values(),
                        PickupCardConfigScreen::appearName, c1, y)),
                "火车 = 整块滑出来；拉幕 = 可见范围一点点变宽（先露图标）");
        hint(addRenderableWidget(maxCardsSlider(v, c2, y)),
                "同时在屏最多几张。GUI 缩放越大、画布越小，放得下的越少");
    }

    private void buildBehavior(PickupCardConfig.Values v) {
        int c1 = itemX1();
        int c2 = itemX2();
        int y = ITEM_TOP;
        hint(addRenderableWidget(steppedLongSlider("停留多久", v.holdMs, 500, 10_000, 250, c1, y)),
                "一张卡在屏幕上待多久");
        hint(addRenderableWidget(steppedLongSlider("淡出多久", v.exitMs, 0, 2_000, 20, c2, y)),
                "消失时淡出多长时间；0 = 直接消失");
        y += itemStep();
        hint(addRenderableWidget(boolButton("合并同种", v.mergeEnabled, c1, y)),
                "短时间内连捡同一种东西，并成一张卡（数字在滚）");
        hint(addRenderableWidget(steppedLongSlider("合并窗口", v.mergeWindowMs, 0, 5_000, 100, c2, y)),
                "两次拾取间隔小于它就算同一次");
        y += itemStep();
        hint(addRenderableWidget(enumButton("数量写法", v.countFormat, CountFormat.values(),
                        PickupCardConfigScreen::countName, c1, y)),
                "数量怎么显示：+64 / ×64 / 64 / +1.2K");
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        if (showPreview()) {
            if (tab == Tab.LAYOUT || tab == Tab.BEHAVIOR) {
                renderStackPreview(gui);
            } else {
                renderCardPreview(gui);
            }
        }
        super.render(gui, mouseX, mouseY, partialTick);

        gui.drawString(this.font, hintText(mouseX, mouseY), itemX1(), this.height - 20, 0xFF9AA4AD);
    }

    /** 悬停谁就说谁 —— 这是这个界面唯一能自我解释的地方。 */
    private Component hintText(int mouseX, int mouseY) {
        for (Map.Entry<AbstractWidget, String> e : hints.entrySet()) {
            if (e.getKey().isMouseOver(mouseX, mouseY)) {
                return Component.literal(e.getValue());
            }
        }
        return Component.literal(switch (tab) {
            case LOOK -> "这些改的是卡片长什么样（右上是预览）";
            case ANIM -> "这些改的是卡片怎么出现、数字怎么跳";
            case LAYOUT -> "这些改的是卡片停在哪、同时显示几张（右边一摞就是预览）";
            case BEHAVIOR -> "这些改的是待多久、怎么合并、数量怎么写";
        });
    }

    private void renderCardPreview(GuiGraphics gui) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        NvgCardPainter.paintPreview(gui, style, itemX1(), 30f, 150f,
                new ItemStack(Items.NETHER_STAR), "经验", "+137", 0xFF7DFF8A, true);
    }

    /**
     * 布局/行为页的预览：<b>真的排布函数 + 真的屏宽</b>画一摞不同宽度的卡。
     * <p>
     * 【为什么三张、而且宽度不同】"贴边/锚点"的效果只在多张不同宽度的卡之间才看得出来：
     * 左对齐时三条竖条成一条竖线，右对齐时右缘齐、竖条参差。单张卡两种设置长得一模一样。
     */
    private void renderStackPreview(GuiGraphics gui) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        LayoutSettings layout = PickupCardConfig.layoutSnapshot();
        String[][] samples = {{"经验", "+137"}, {"信标", "+1"}, {"石头", "+64"}};
        int[] accents = {0xFF7DFF8A, 0xFF55EBFF, 0xFF9AA4AD};
        float[] widths = {132f, 106f, 84f};
        ItemStack[] icons = {
                new ItemStack(Items.NETHER_STAR), new ItemStack(Items.BEACON), new ItemStack(Items.STONE)};

        List<StackLayout.Size> sizes = new ArrayList<>();
        for (int i = widths.length - 1; i >= 0; i--) {      // 最新的排第一（StackLayout 的约定）
            sizes.add(new StackLayout.Size(widths[i], style.boxHeight()));
        }
        // 虚拟下边距：卡是贴底锚定的，用真实高度会压在左下那颗"完成"上
        float virtualBottom = this.height - 26f;
        for (StackLayout.Slot slot : StackLayout.stack(sizes, this.width, virtualBottom, layout,
                CardStage.MARGIN_X, CardStage.MARGIN_Y, CardStage.STACK_GAP)) {
            int i = widths.length - 1 - slot.index();
            NvgCardPainter.paintPreview(gui, style, slot.x(), slot.y(), slot.width(),
                    icons[i], samples[i][0], samples[i][1], accents[i], i == 0);
        }
    }

    // ------------------------------------------------------------------
    // 尺寸与位置（按画布算，不写死）
    // ------------------------------------------------------------------

    private int itemX1() {
        return TAB_X + TAB_W + 14;
    }

    private int itemX2() {
        return itemX1() + ITEM_W + 8;
    }

    private int itemStep() {
        return Math.max(13, Math.min(ITEM_STEP, (this.height - ITEM_TOP - 40) / 6));
    }

    private int rowH() {
        return itemStep() - 2;
    }

    /** 画布太矮就不画预览 —— 预览是锦上添花，控件点不到是功能没了（guiScale 5 只有 144 高）。 */
    private boolean showPreview() {
        return this.height >= 190;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    // ------------------------------------------------------------------
    // 控件
    // ------------------------------------------------------------------

    private void hint(AbstractWidget w, String text) {
        hints.put(w, text);
    }

    /** 贴边：按钮上把<b>后果</b>写出来，而不是只写 LEFT/RIGHT。 */
    private Button sideButton(PickupCardConfig.Values v, int x, int y) {
        return Button.builder(Component.literal(sideName(v.stickTo.get())), b -> {
            LayoutSettings.Side next = v.stickTo.get() == LayoutSettings.Side.LEFT
                    ? LayoutSettings.Side.RIGHT : LayoutSettings.Side.LEFT;
            v.stickTo.set(next);
            changed();
            b.setMessage(Component.literal(sideName(next)));
        }).bounds(x, y, ITEM_W, rowH()).build();
    }

    /** 锚点 x：最左边是"自动"（跟着画布算），其余是绝对像素。 */
    private AbstractSliderButton anchorSlider(PickupCardConfig.Values v, int x, int y) {
        final int max = 600;
        AbstractSliderButton w = new AbstractSliderButton(x, y, ITEM_W, rowH(), Component.empty(),
                (v.leftEdge.get() + 1) / (double) (max + 1)) {
            @Override
            protected void updateMessage() {
                int value = (int) Math.round(this.value * (max + 1)) - 1;
                setMessage(Component.literal("锚点: " + anchorName(value)));
            }

            @Override
            protected void applyValue() {
                v.leftEdge.set((int) Math.round(this.value * (max + 1)) - 1);
                changed();
            }
        };
        // 【构造完必须自己补一次标签】AbstractSliderButton 构造期间不跑 updateMessage，
        // 而且 1.20.1 里它没有公开的 getValue() —— 所以初值只能从配置取。
        w.setMessage(Component.literal("锚点: " + anchorName(v.leftEdge.get())));
        return w;
    }

    private AbstractSliderButton maxCardsSlider(PickupCardConfig.Values v, int x, int y) {
        AbstractSliderButton w = new AbstractSliderButton(x, y, ITEM_W, rowH(), Component.empty(),
                (v.maxOnScreen.get() - 1) / 15.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("最多几张: " + (1 + (int) Math.round(this.value * 15))));
            }

            @Override
            protected void applyValue() {
                v.maxOnScreen.set(1 + (int) Math.round(this.value * 15));
                changed();
            }
        };
        w.setMessage(Component.literal("最多几张: " + v.maxOnScreen.get()));
        return w;
    }

    private Button triToggle(String label, ForgeConfigSpec.IntValue config, int x, int y) {
        return Button.builder(Component.literal(label + ": " + triName(config.get())), b -> {
            int next = config.get() < 0 ? 1 : (config.get() == 1 ? 0 : -1);
            config.set(next);
            changed();
            b.setMessage(Component.literal(label + ": " + triName(next)));
        }).bounds(x, y, ITEM_W, rowH()).build();
    }

    private Button boolButton(String label, ForgeConfigSpec.BooleanValue config, int x, int y) {
        return Button.builder(Component.literal(label + ": " + (config.get() ? "开" : "关")), b -> {
            boolean next = !config.get();
            config.set(next);
            changed();
            b.setMessage(Component.literal(label + ": " + (next ? "开" : "关")));
        }).bounds(x, y, ITEM_W, rowH()).build();
    }

    private <E extends Enum<E>> Button enumButton(String label, ForgeConfigSpec.EnumValue<E> config,
                                                  E[] values, Function<E, String> name, int x, int y) {
        return Button.builder(Component.literal(label + ": " + name.apply(config.get())), b -> {
            E current = config.get();
            int i = 0;
            for (int k = 0; k < values.length; k++) {
                if (values[k] == current) i = k;
            }
            E next = values[(i + 1) % values.length];
            config.set(next);
            changed();
            b.setMessage(Component.literal(label + ": " + name.apply(next)));
        }).bounds(x, y, ITEM_W, rowH()).build();
    }

    /**
     * 数值滑条。
     * <p>
     * 【参数为什么不叫 value】{@link AbstractSliderButton} 自带 {@code protected double value}，
     * 匿名内部类里那个名字会被遮住 —— 写 {@code value.set(...)} 编译报"double 不能被解引用"。
     */
    private AbstractSliderButton intSlider(String label, ForgeConfigSpec.IntValue config, int min, int max,
                                           int x, int y) {
        AbstractSliderButton w = new AbstractSliderButton(x, y, ITEM_W, rowH(), Component.empty(),
                sliderPos(config.get(), min, max)) {
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
        w.setMessage(Component.literal(label + ": " + valueName(config.get())));
        return w;
    }

    private AbstractSliderButton longSlider(String label, ForgeConfigSpec.LongValue config, long min, long max,
                                            int x, int y) {
        AbstractSliderButton w = new AbstractSliderButton(x, y, ITEM_W, rowH(), Component.empty(),
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
        w.setMessage(Component.literal(label + ": "
                + (config.get() < 0 ? "跟随主题" : config.get() + "ms")));
        return w;
    }

    /**
     * 带步进的时长滑条。
     * <p>
     * 【为什么必须量化】停留范围 500~10000ms 铺在 150px 宽的滑条上，一像素 ≈ 63ms ——
     * 想调 4000 拖出 3987，玩家会觉得这界面调不准。按 250ms 吸附，每一格都是整得好看的数。
     */
    private AbstractSliderButton steppedLongSlider(String label, ForgeConfigSpec.LongValue config,
                                                   long min, long max, long step, int x, int y) {
        AbstractSliderButton w = new AbstractSliderButton(x, y, ITEM_W, rowH(), Component.empty(),
                (config.get() - min) / (double) (max - min)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(label + ": " + plain(this.value, min, max, step) + "ms"));
            }

            @Override
            protected void applyValue() {
                config.set(plain(this.value, min, max, step));
                changed();
            }
        };
        w.setMessage(Component.literal(label + ": " + config.get() + "ms"));
        return w;
    }

    /** 位置 → 值：线性铺开并按 step 吸附。 */
    private static long plain(double pos, long min, long max, long step) {
        long raw = min + Math.round(pos * (max - min));
        long snapped = Math.round(raw / (double) step) * step;
        return Math.max(min, Math.min(max, snapped));
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

    private static String sideName(LayoutSettings.Side side) {
        return side == LayoutSettings.Side.RIGHT ? "贴边: 右（右缘齐）" : "贴边: 左（竖条成线）";
    }

    private static String appearName(LayoutSettings.Appear appear) {
        return appear == LayoutSettings.Appear.CLIP ? "拉幕（先露图标）" : "火车（数字端先出）";
    }

    private static String countName(CountFormat format) {
        return switch (format) {
            case X_PREFIX -> "×64";
            case PLAIN -> "64";
            case ABBREVIATED -> "+1.2K";
            default -> "+64";
        };
    }

    private static String anchorName(int value) {
        return value < 0 ? "自动" : Integer.toString(value);
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

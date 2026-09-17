package com.niuqu.pickupcard.render.nvg.ui;

/**
 * 自绘控件的地基：<b>一块矩形 + 悬停/按下/焦点三个状态 + 一次激活</b>。
 *
 * <p>【为什么不做事件树/布局引擎】这个界面只有二十来个控件、都是同一行里等宽等高的格子，
 * 排布由配置界面按"第几行第几列"算一次就够。旧版那条注释写得很准：
 * <b>不许长成第二个 UI 框架</b> —— 我们不需要 View 树、焦点漫游、布局约束这些东西，
 * 它们解决的问题这个界面一个都没有。
 *
 * <p>【为什么形状与文字分两处提交】见 {@link NvgUi}：形状走 NanoVG、文字走原版批次，
 * 但控件不用管这件事 —— 它调 {@code ui.text(...)} 时只是登记，顺序由 {@link NvgUi#close()} 保证。
 */
public abstract class NvgWidget {

    private final String label;
    protected float x;
    protected float y;
    protected float w;
    protected float h;
    /** 鼠标悬停 / 被按下 / 持有键盘焦点。三个状态下控件长得不一样，这是"能操作"的唯一提示。 */
    protected boolean hovered;
    protected boolean pressed;
    protected boolean focused;

    protected NvgWidget(String label) {
        this.label = label;
    }

    /** 玩家看到的那一行名字（也是 harness 按名字找控件的键）。 */
    public final String label() {
        return label;
    }

    public final NvgWidget at(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public final float x() {
        return x;
    }

    public final float y() {
        return y;
    }

    public final float width() {
        return w;
    }

    public final float height() {
        return h;
    }

    public final boolean hit(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 控件里显示的那行值（滑条的数字、开关的开/关）。日志与 harness 读它。 */
    public String value() {
        return "";
    }

    /** 控件底的配色：按下 > 悬停 > 常态。 */
    protected final int wellColor(NvgPalette palette) {
        return pressed ? palette.wellPressed : (hovered || focused) ? palette.wellHover : palette.well;
    }

    /** 画自己（形状 + 登记文字，都在 {@code ui} 上）。 */
    public abstract void draw(NvgUi ui);

    // ------------------------------------------------------------------
    // 事件：屏幕把真实鼠标事件转给控件，命中就吃掉
    // ------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !hit(mouseX, mouseY)) {
            return false;
        }
        pressed = true;
        focused = true;
        return true;
    }

    public void mouseReleased(double mouseX, double mouseY) {
        boolean wasPressed = pressed;
        pressed = false;
        if (wasPressed && hit(mouseX, mouseY)) {
            onActivate();
        }
    }

    /** 拖拽：滑条要靠它才有手感。默认什么都不做。 */
    public void mouseDragged(double mouseX, double mouseY) {
    }

    public void mouseMoved(double mouseX, double mouseY) {
        hovered = hit(mouseX, mouseY);
        if (!hovered && !pressed) {
            focused = false;
        }
    }

    /** 敲键盘（只有文本框这类需要）。 */
    public boolean keyPressed(int keyCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char c) {
        return false;
    }

    /** 点在别处：交还焦点。 */
    public void blur() {
        focused = false;
        pressed = false;
    }

    /** 点一下释放时触发（按钮/开关/循环都用这个语义）。 */
    protected void onActivate() {
    }
}

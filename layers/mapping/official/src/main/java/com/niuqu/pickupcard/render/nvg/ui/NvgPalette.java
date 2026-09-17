package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.style.StyleModel;

/**
 * 自绘界面的配色与尺寸 —— <b>一个地方改，所有控件跟着变</b>。
 *
 * <p>【为什么单独成类】旧版（v0.1.0）就有一份 {@code UiPalette}，颜色散在六个控件文件里。
 * 散着写的代价不是"难改"，是<b>改不干净</b>：调一次底色，六个控件的悬停色就不一致了，
 * 而那种不一致眼睛看得出来、名字叫不出来。
 *
 * <p>【颜色为什么是 ARGB int 而不是 NVGColor】{@code NVGColor} 要挂在 MemoryStack 上、
 * 有生命周期；控件只在画的那一刻才需要它。存 int，画的瞬间再转 —— 这样调色板是纯数据，
 * 也就不用担心"这一帧的 MemoryStack 已经弹掉了"。
 */
public final class NvgPalette {

    /** 界面底色（不透明，压在游戏画面上）。 */
    public final int backdrop;
    /** 面板/侧栏底。 */
    public final int panel;
    /** 控件底（未交互）。 */
    public final int well;
    /** 控件底（悬停）。 */
    public final int wellHover;
    /** 控件底（按下）。 */
    public final int wellPressed;
    /** 填充条 / 选中态。 */
    public final int accent;
    /** 描边。 */
    public final int outline;
    /** 正文。 */
    public final int text;
    /** 次要文字（标签、说明）。 */
    public final int textDim;

    // ---- 尺寸（逻辑像素）----
    /** 控件圆角。 */
    public float radius = 4f;
    /** 控件高度。 */
    public float rowHeight = 18f;
    /** 描边粗细。 */
    public float outlineWidth = 1f;
    /** 滑条轨道高度 / 开关高度。 */
    public float trackHeight = 6f;
    /** 滑块（圆）半径。 */
    public float knobRadius = 5f;

    /** 深色界面。默认就是它 —— 游戏里九成时间在暗环境，浅色面板会晃眼。 */
    public static NvgPalette dark() {
        return new NvgPalette(0xF0101218, 0xC0202836, 0x80202836, 0xB0364152, 0xC04A5871,
                0xFF7DFF8A, 0x40FFFFFF, 0xFFEBEFF6, 0xFF9AA4AD);
    }

    /** 浅色：跟着主题走（主题是浅色时用这套）。 */
    public static NvgPalette light() {
        return new NvgPalette(0xF0E9ECF3, 0xC0FFFFFF, 0x60D5DAE5, 0xA0C3CAD8, 0xC0A9B2C4,
                0xFF2E9E45, 0x40000000, 0xFF1B1F27, 0xFF5A6272);
    }

    /** 按卡面主题选一套界面配色 —— 界面跟卡面同族，不然像两个 mod 拼在一起。 */
    public static NvgPalette of(StyleModel style) {
        // 卡面底色偏亮（fillTop 的 alpha/亮度高）就当作浅色主题
        int rgb = style.fillTop() & 0xFFFFFF;
        int brightness = ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
        return brightness > 3 * 128 ? light() : dark();
    }

    private NvgPalette(int backdrop, int panel, int well, int wellHover, int wellPressed,
                       int accent, int outline, int text, int textDim) {
        this.backdrop = backdrop;
        this.panel = panel;
        this.well = well;
        this.wellHover = wellHover;
        this.wellPressed = wellPressed;
        this.accent = accent;
        this.outline = outline;
        this.text = text;
        this.textDim = textDim;
    }
}

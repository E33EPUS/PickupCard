package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.render.nvg.NvgCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgCircle;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.lwjgl.nanovg.NanoVG.nvgRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgScissor;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;

/**
 * 自绘界面的一帧：<b>形状走 NanoVG，文字走原版字形</b>，一个 {@code AutoCloseable} 收尾。
 *
 * <p>【为什么要包一层】NanoVG 直接调 GL，而原版文字走的是延迟批次：
 * <ul>
 *   <li>形状必须在 NanoVG 的帧里画（它就是 GL）；</li>
 *   <li>文字必须在那帧<b>之后</b>才提交，否则会被 NanoVG 的状态改动作废（症状是文字忽有忽无）；</li>
 * </ul>
 * 所以这里把文字调用<b>先登记、在 {@link #end()} 里统一补画</b>：控件只管"我要在这儿写一行字"，
 * 顺序由这一处保证。让每个控件自己记住"我的文字要晚一帧"迟早会有人漏。
 *
 * <p>【文字为什么还借原版字形】中文在一个 TTF 里不好办（要么塞 10MB 字体，要么把字形图集
 * 当贴图降级成位图）。卡面内容当初就是这么定的（见 {@code NvgCardPainter}）：形状自绘、
 * 文字交给 MC 自己的字形图集。界面沿同一条边界。
 */
public final class NvgUi implements AutoCloseable {

    private final GuiGraphics gui;
    private final Font font;
    private final NvgCanvas canvas;
    private final MemoryStack stack;
    /** 登记的一条文字：内容 + **登记时所在的裁剪框**。 */
    private record Text(Runnable draw, Clip clip) {
    }

    /**
     * 一个裁剪框（屏幕逻辑坐标）。
     * <p>
     * 【为什么文字也要记它】形状在 NanoVG 的帧里当场画掉，文字是 {@link #close()} 里才提交的 ——
     * 提交时当前裁剪早就变了。不记住登记时那个框，滚出视口的行会在裁剪失效之后才画出来。
     */
    record Clip(float x, float y, float w, float h) {
    }

    private final List<Text> texts = new ArrayList<>();

    /** 当前的裁剪框（null = 没裁）；形状那一套由 NanoVG 的 save/restore 管。 */
    private Clip clip;
    private final List<Clip> clipStack = new ArrayList<>();

    /** 界面配色。 */
    public final NvgPalette palette;
    /** 本帧鼠标位置（逻辑坐标）与时刻。 */
    public final float mouseX;
    public final float mouseY;
    public final long now;

    private NvgUi(GuiGraphics gui, NvgCanvas canvas, MemoryStack stack, NvgPalette palette,
                  float mouseX, float mouseY, long now) {
        this.gui = gui;
        this.font = Minecraft.getInstance().font;
        this.canvas = canvas;
        this.stack = stack;
        this.palette = palette;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.now = now;
    }

    /**
     * 开一帧。<b>调用方必须用 try-with-resources</b>：形状画在 {@link #end()} 里才提交文字，
     * 中间抛异常也要把 GL 状态还回去（{@link NvgCanvas#end()} 自己保证）。
     *
     * @return 上下文不可用时返回 null —— 调用方当作"这一帧没界面"
     */
    public static NvgUi begin(GuiGraphics gui, NvgPalette palette, float mouseX, float mouseY, long now) {
        NvgCanvas nvg = NvgCanvas.shared();
        if (nvg == null || !nvg.valid()) {
            PickupCard.LOGGER.error("NanoVG 不可用：自绘界面这一帧画不出来（上面的 [nvg] 日志有原因）");
            return null;
        }
        gui.flush();
        MemoryStack stack = MemoryStack.stackPush();
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        nvg.begin(gui.guiWidth(), gui.guiHeight(), scale);
        return new NvgUi(gui, nvg, stack, palette, mouseX, mouseY, now);
    }

    /** NanoVG 句柄，交给要直接调 NanoVG 的地方（比如卡面预览）。 */
    public long vg() {
        return canvas.handle();
    }

    public MemoryStack stack() {
        return stack;
    }

    public Font font() {
        return font;
    }

    // ------------------------------------------------------------------
    // 形状
    // ------------------------------------------------------------------

    /** 圆角矩形填充。 */
    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        nvgBeginPath(vg());
        nvgRoundedRect(vg(), x, y, w, h, Math.max(0f, Math.min(radius, Math.min(w, h) / 2f)));
        nvgFillColor(vg(), color(argb));
        nvgFill(vg());
    }

    /** 一条竖渐变（面板、进度条那种"有厚度"的底）。 */
    public void fillGradient(float x, float y, float w, float h, int topArgb, int bottomArgb) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        nvgBeginPath(vg());
        nvgRoundedRect(vg(), x, y, w, h, palette.radius);
        NVGPaint paint = nvgLinearGradient(vg(), x, y, x, y + h,
                color(topArgb), color(bottomArgb), NVGPaint.mallocStack(stack));
        nvgFillPaint(vg(), paint);
        nvgFill(vg());
    }

    /** 圆角矩形描边（路径内缩半个线宽，整条线才落在框内）。 */
    public void strokeRoundRect(float x, float y, float w, float h, float radius, int argb) {
        float half = palette.outlineWidth / 2f;
        if (w <= palette.outlineWidth || h <= palette.outlineWidth) {
            return;
        }
        nvgBeginPath(vg());
        nvgRoundedRect(vg(), x + half, y + half, w - palette.outlineWidth, h - palette.outlineWidth,
                Math.max(0f, radius - half));
        nvgStrokeWidth(vg(), palette.outlineWidth);
        nvgStrokeColor(vg(), color(argb));
        nvgStroke(vg());
    }

    /** 正圆填充（滑块、开关的钮）。 */
    public void circle(float cx, float cy, float r, int argb) {
        if (r <= 0f) {
            return;
        }
        nvgBeginPath(vg());
        nvgCircle(vg(), cx, cy, r);
        nvgFillColor(vg(), color(argb));
        nvgFill(vg());
    }

    /** 一块面板：底 + 描边，界面上的"容器"都是它。 */
    public void panel(float x, float y, float w, float h) {
        fillRoundRect(x, y, w, h, palette.radius, palette.panel);
        strokeRoundRect(x, y, w, h, palette.radius, palette.outline);
    }

    /** 一块"井"：控件的底（悬停/pressed 各一档颜色，由调用方给）。 */
    public void well(float x, float y, float w, float h, int argb) {
        fillRoundRect(x, y, w, h, palette.radius, argb);
        strokeRoundRect(x, y, w, h, palette.radius, palette.outline);
    }

    // ------------------------------------------------------------------
    // 文字（先登记，end() 之后统一提交）
    // ------------------------------------------------------------------

    public void text(String s, float x, float y, int argb) {
        register(() -> gui.drawString(font, s, Math.round(x), Math.round(y), argb, true));
    }

    /** 居中写一行（x 给中心）。 */
    public void textCentered(String s, float centerX, float y, int argb) {
        register(() -> gui.drawString(font, s, Math.round(centerX - font.width(s) / 2f),
                Math.round(y), argb, true));
    }

    /** 右对齐写一行（x 给右缘）。 */
    public void textRight(String s, float rightX, float y, int argb) {
        register(() -> gui.drawString(font, s, Math.round(rightX - font.width(s)),
                Math.round(y), argb, true));
    }

    /** 登记一条文字，连同它此刻所在的裁剪框。 */
    private void register(Runnable draw) {
        texts.add(new Text(draw, clip));
    }

    // ------------------------------------------------------------------
    // 裁剪（滚动的列表、预览面板用）
    // ------------------------------------------------------------------

    /**
     * 开一个裁剪框。<b>必须配对 {@link #popClip()}</b>。
     * <p>
     * 【为什么要一次设两套】形状是 NanoVG 画的（{@code nvgScissor}），文字是原版批次画的
     * （{@code gui.enableScissor}）—— 只设一套的症状是"形状被裁了、文字糊在外面"，
     * 而那种半对的样子最难查。所以这里一次把两边都设上。
     */
    public void pushClip(float x, float y, float w, float h) {
        clipStack.add(clip);
        clip = new Clip(x, y, Math.max(0f, w), Math.max(0f, h));
        nvgSave(vg());
        nvgScissor(vg(), clip.x(), clip.y(), clip.w(), clip.h());
    }

    /** 收掉最近一次 {@link #pushClip}。 */
    public void popClip() {
        if (clipStack.isEmpty()) {
            return;
        }
        nvgRestore(vg());
        clip = clipStack.remove(clipStack.size() - 1);
    }

    public int textWidth(String s) {
        return font.width(s);
    }

    // ------------------------------------------------------------------

    /** 收帧：先关 NanoVG，再把登记的文字交给原版批次。 */
    @Override
    public void close() {
        try {
            canvas.end();
        } finally {
            stack.close();
        }
        // 文字按"登记时的裁剪框"分组提交：换框前先把上一段的批次冲掉，否则裁剪会被套到
        // 先登记的那些行上（原版的 enableScissor 只影响之后提交的东西）
        Clip active = null;
        for (Text t : texts) {
            Clip want = t.clip();
            boolean same = want == null ? active == null : want.equals(active);
            if (!same) {
                if (active != null) {
                    gui.disableScissor();
                }
                active = want;
                if (active != null) {
                    gui.enableScissor(Math.round(active.x()), Math.round(active.y()),
                            Math.round(active.x() + active.w()), Math.round(active.y() + active.h()));
                }
            }
            t.draw().run();
        }
        if (active != null) {
            gui.disableScissor();
        }
    }

    /** ARGB -> NanoVG 要的 RGBA 分量。 */
    private NVGColor color(int argb) {
        return nvgRGBA((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24),
                NVGColor.mallocStack(stack));
    }
}

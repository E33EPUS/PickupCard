package com.niuqu.pickupcard.render.shape;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.niuqu.pickupcard.render.shape.ShapeRenderType;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * 形状层的唯一入口：所有矢量图元都从这里提交。
 * <p>
 * 【坐标语义】<b>与 {@code GuiGraphics.fill} 完全一致</b>——坐标处于"当前 pose 空间"，
 * 提交时把当前 pose 烘进顶点。也就是说调用方 {@code pose().translate(卡X, 卡Y)} 之后，
 * 这里收的就应该是卡内局部坐标。
 * <p>
 * 【唯一真正要守的纪律】<b>一个 painter 的一趟绘制里只能有一种坐标空间。</b>
 * 上一版"每张卡的装饰都在屏幕左边出现幽灵分身"，根因就是一半用屏幕绝对坐标、
 * 一半用卡内局部坐标。这里跟随 {@code GuiGraphics} 的语义，是因为那是最不会搞错的一种：
 * 它跟原版所有绘制代码一致，不需要额外记规则。
 * <p>
 * 【关于"批处理"】1.20.1 上 uniform 是 <b>per-draw</b> 的（{@code ShaderInstance.apply()}
 * 在 flush 那一刻把所有 uniform 上传给当前程序），所以形状参数一变就必须 flush。
 * 能做的是：把 uniform 组合完全相同的<b>连续</b>形状合进同一个 draw。卡上的强调色条、
 * 同尺寸的圆徽章之类都能白拿到合并——不需要碰自定义顶点属性（那个在 1.20.1 上是坑）。
 * <p>
 * 【为什么不手搓 GL 状态】开关 blend/cull/depth 全交给 {@link ShapeRenderType} 里那份
 * 声明式的 {@code RenderStateShard} 清单，由框架成对 setup/clear。上一版手写的那套
 * 没配对还原，真机表现为整层不可见且无日志——手写状态没有"成对"这个保证。
 */
public final class ShapeBatch {

    /** 与 gui_shape.fsh 里的模式常量一一对应。 */
    private static final int MODE_RECT_FILL = 0;
    private static final int MODE_RECT_RING = 1;
    private static final int MODE_CIRCLE_FILL = 2;
    private static final int MODE_CIRCLE_RING = 3;
    private static final int MODE_CRESCENT = 4;

    /** 复用临时向量：只在客户端主线程用，避免每顶点分配。 */
    private static final Vector3f POS = new Vector3f();

    /** 一次绘制的统计，给 harness 读——让"提交了多少形状、花了多少次 draw"变成可查的事实。 */
    public record Stats(int shapes, int flushes, int merges) {
    }

    /** uniform 组合。完全相同的连续形状合并进同一个 draw。 */
    private record Key(float halfW, float halfH, float radius, float stroke,
                       float centerX, float centerY, float radius2, int mode, float soft) {
    }

    private final GuiGraphics gui;
    private Key pending;
    private int shapes;
    private int flushes;
    private int merges;

    public ShapeBatch(GuiGraphics gui) {
        this.gui = gui;
    }

    // ------------------------------------------------------------------
    // 提交
    // ------------------------------------------------------------------

    /** 圆角矩形/胶囊填充。{@code radius >= min(w,h)/2} 即为胶囊。 */
    public void roundRect(float x, float y, float w, float h, float radius, int color) {
        roundRectGradient(x, y, w, h, radius, color, color);
    }

    /** 圆角矩形/胶囊填充，支持上下两色渐变（玻璃卡面用）。 */
    public void roundRectGradient(float x, float y, float w, float h, float radius, int top, int bottom) {
        submit(rectKey(w, h, radius, 0f, 0f, MODE_RECT_FILL), x, y, x + w, y + h, top, bottom);
    }

    /**
     * 一团柔和的影子：同一个圆角矩形，但边缘按 {@code soft} 像素软化。
     * <p>
     * 【为什么不是真高斯】1.20.1 上没有能白拿的模糊。SDF 的距离场可以直接把边缘
     * 过渡带加宽，小半径下和真高斯几乎看不出差别，而代价只是<b>同一个 quad 换一个 uniform</b>
     * ——不新增 pass、不新增渲染目标。{@code soft} 约取"想要的模糊半径的一半"。
     * 半径拉到 8px 以上会开始失真（真高斯在拐角糊得更圆），那时才值得考虑离屏模糊。
     */
    public void shadow(float x, float y, float w, float h, float radius, float soft, int color) {
        submit(rectKey(w, h, radius, 0f, soft, MODE_RECT_FILL), x, y, x + w, y + h, color, color);
    }

    /** 圆角矩形/胶囊描边环，{@code thickness} 为线宽（向两侧各半）。 */
    public void roundRectStroked(float x, float y, float w, float h, float radius, float thickness, int color) {
        submit(rectKey(w, h, radius, thickness, 0f, MODE_RECT_RING), x, y, x + w, y + h, color, color);
    }

    /** 圆填充。 */
    public void circle(float cx, float cy, float r, int color) {
        submit(circleKey(r, 0f, r, MODE_CIRCLE_FILL), cx - r, cy - r, cx + r, cy + r, color, color);
    }

    /** 圆环。 */
    public void ring(float cx, float cy, float r, float thickness, int color) {
        submit(circleKey(r, thickness, r, MODE_CIRCLE_RING), cx - r, cy - r, cx + r, cy + r, color, color);
    }

    /**
     * 月牙：大圆（圆心在 cx,cy，半径 r）减去切割圆（圆心偏移 cutDx,cutDy，半径 cutR）。
     * 卡左外侧那枚装饰就是它。
     */
    public void crescent(float cx, float cy, float r, float cutDx, float cutDy, float cutR, int color) {
        submit(new Key(r, r, r, 0f, cutDx, cutDy, cutR, MODE_CRESCENT, 0f),
                cx - r, cy - r, cx + r, cy + r, color, color);
    }

    /** 一趟绘制结束时必须调用（painter 的收尾）。 */
    public void flush() {
        flushPending();
    }

    public Stats stats() {
        return new Stats(shapes, flushes, merges);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void submit(Key key, float x0, float y0, float x1, float y1, int top, int bottom) {
        if (!ShapeShaders.ready()) {
            return;
        }
        if (pending == null) {
            applyUniforms(key);
            pending = key;
        } else if (pending.equals(key)) {
            merges++;
        } else {
            flushPending();
            applyUniforms(key);
            pending = key;
        }
        emit(x0, y0, x1, y1, top, bottom);
        shapes++;
    }

    private void flushPending() {
        if (pending == null) {
            return;
        }
        gui.bufferSource().endBatch(ShapeRenderType.SHAPE);
        flushes++;
        pending = null;
    }

    private static Key rectKey(float w, float h, float radius, float stroke, float soft, int mode) {
        return new Key(w / 2f, h / 2f, Math.min(radius, Math.min(w, h) / 2f), stroke, 0f, 0f, 0f, mode, soft);
    }

    private static Key circleKey(float r, float stroke, float radius2, int mode) {
        return new Key(r, r, r, stroke, 0f, 0f, radius2, mode, 0f);
    }

    /** uniform 只在 flush 时才真正上传，所以这里只是写缓冲区；换组合前必须先 flush。 */
    private static void applyUniforms(Key k) {
        set("u_halfSize", u -> u.set(k.halfW(), k.halfH()));
        set("u_radius", u -> u.set(k.radius()));
        set("u_stroke", u -> u.set(k.stroke()));
        set("u_center", u -> u.set(k.centerX(), k.centerY()));
        set("u_radius2", u -> u.set(k.radius2()));
        set("u_mode", u -> u.set(k.mode()));
        set("u_soft", u -> u.set(k.soft()));
    }

    /** 缺失的 uniform 由 {@link ShapeShaders#uniform} 负责尖叫，这里安静跳过。 */
    private static void set(String name, Consumer<Uniform> apply) {
        Uniform uniform = ShapeShaders.uniform(name);
        if (uniform != null) {
            apply.accept(uniform);
        }
    }

    private void emit(float x0, float y0, float x1, float y1, int top, int bottom) {
        VertexConsumer vc = gui.bufferSource().getBuffer(ShapeRenderType.SHAPE);
        Matrix4f pose = gui.pose().last().pose();
        // UV (0,0)-(1,1) 让片段端着色器重建局部像素坐标
        vertex(vc, pose, x0, y1, 0f, 1f, bottom);
        vertex(vc, pose, x1, y1, 1f, 1f, bottom);
        vertex(vc, pose, x1, y0, 1f, 0f, top);
        vertex(vc, pose, x0, y0, 0f, 0f, top);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float u, float v, int argb) {
        POS.set(x, y, 0f);
        pose.transformPosition(POS);
        vc.vertex(POS.x, POS.y, 0f)
                .uv(u, v)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }
}

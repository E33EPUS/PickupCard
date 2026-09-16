package com.niuqu.pickupcard.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * 形状批处理：每个形状一个四边形、一次 draw，SDF 在片段着色器里算
 * （圆角/描边/圆/月牙），解析抗锯齿，与分辨率无关。坐标一律屏幕绝对值
 * ——v2 的"幽灵分身"就是绝对/局部坐标混用造成的，这里从接口上杜绝：
 * 所有方法只收最终屏幕坐标，调用方不再做任何坐标换算。
 * <p>
 * 【逐形状 draw 的代价】每张卡约 8 个形状、同屏上限 6 张 ≈ 50 次 draw，
 * 对 HUD 而言可忽略（原版 HUD 本身就有数百次）；换来的是 uniform 语义
 * 清晰，不需要自定义顶点属性（GENERIC 绑定在 1.20.1 上是坑）。
 */
public final class ShapeBatch {

    /** 模式常量，与 gui_shape.fsh 一致。 */
    private static final int MODE_RECT_FILL = 0;
    private static final int MODE_RECT_RING = 1;
    private static final int MODE_CIRCLE_FILL = 2;
    private static final int MODE_CIRCLE_RING = 3;
    private static final int MODE_CRESCENT = 4;

    /** 共享临时向量：只在客户端主线程用，避免每顶点分配。 */
    private static final org.joml.Vector3f POS = new org.joml.Vector3f();

    private ShapeBatch() {
    }

    private static boolean begin(@Nullable Matrix4f m, float x0, float y0, float x1, float y1,
                                 int color0, int color1) {
        ShaderInstance shader = PickupShaders.guiShape();
        if (shader == null) return false;
        RenderSystem.setShader(() -> shader);
        Tesselator tess = Tesselator.getInstance();
        var buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(buf, m, x0, y1, 0f, 1f, color1);
        vertex(buf, m, x1, y1, 1f, 1f, color1);
        vertex(buf, m, x1, y0, 1f, 0f, color0);
        vertex(buf, m, x0, y0, 0f, 0f, color0);
        tess.end();
        return true;
    }

    private static void vertex(com.mojang.blaze3d.vertex.BufferBuilder buf, @Nullable Matrix4f m,
                               float x, float y, float u, float v, int argb) {
        float px = x, py = y;
        if (m != null) {
            // 把 GUI pose（含入场缩放/位移）烘进顶点；JOML transformPosition 拒绝手搓行列
            POS.set(x, y, 0f);
            m.transformPosition(POS);
            px = POS.x;
            py = POS.y;
        }
        buf.vertex(px, py, 0f)
                .uv(u, v)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }

    private static void setCommon(ShaderInstance shader, float halfW, float halfH, float radius, int mode) {
        shader.safeGetUniform("u_halfSize").set(halfW, halfH);
        shader.safeGetUniform("u_radius").set(radius);
        shader.safeGetUniform("u_mode").set(mode);
    }

    /** 给颜色乘一个 alpha 系数（动画淡入淡出用），上限 1。 */
    public static int fade(int argb, float factor) {
        int a = Math.min(255, Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** 圆角矩形/胶囊填充。radius >= min(w,h)/2 即胶囊。colorTop/colorBottom 支持竖直渐变。 */
    public static void pillFill(@Nullable Matrix4f m, float x, float y, float w, float h,
                                float radius, int colorTop, int colorBottom) {
        ShaderInstance shader = PickupShaders.guiShape();
        if (shader == null) return;
        setCommon(shader, w / 2f, h / 2f, Math.min(radius, Math.min(w, h) / 2f), MODE_RECT_FILL);
        shader.safeGetUniform("u_stroke").set(0f);
        begin(m, x, y, x + w, y + h, colorTop, colorBottom);
    }

    /** 圆角矩形/胶囊描边环（thickness 为线宽，向两侧各半）。 */
    public static void pillRing(@Nullable Matrix4f m, float x, float y, float w, float h,
                                float radius, float thickness, int color) {
        ShaderInstance shader = PickupShaders.guiShape();
        if (shader == null) return;
        setCommon(shader, w / 2f, h / 2f, Math.min(radius, Math.min(w, h) / 2f), MODE_RECT_RING);
        shader.safeGetUniform("u_stroke").set(thickness);
        begin(m, x, y, x + w, y + h, color, color);
    }

    /** 圆填充。 */
    public static void circleFill(@Nullable Matrix4f m, float cx, float cy, float r, int color) {
        ShaderInstance shader = PickupShaders.guiShape();
        if (shader == null) return;
        setCommon(shader, r, r, r, MODE_CIRCLE_FILL);
        shader.safeGetUniform("u_stroke").set(0f);
        shader.safeGetUniform("u_center").set(0f, 0f);
        shader.safeGetUniform("u_radius2").set(r);
        begin(m, cx - r, cy - r, cx + r, cy + r, color, color);
    }

    /** 圆环。 */
    public static void circleRing(@Nullable Matrix4f m, float cx, float cy, float r, float thickness, int color) {
        ShaderInstance shader = PickupShaders.guiShape();
        if (shader == null) return;
        setCommon(shader, r, r, r, MODE_CIRCLE_RING);
        shader.safeGetUniform("u_stroke").set(thickness);
        shader.safeGetUniform("u_center").set(0f, 0f);
        shader.safeGetUniform("u_radius2").set(r);
        begin(m, cx - r, cy - r, cx + r, cy + r, color, color);
    }

    /**
     * 月牙（开口朝右）：大圆（圆心 = quad 中心）减去切割圆。
     * cutD/ cutR 相对 r 归一：值越小月牙越细。
     */
    public static void crescent(@Nullable Matrix4f m, float cx, float cy, float r,
                                float cutD, float cutR, int color) {
        ShaderInstance shader = PickupShaders.guiShape();
        if (shader == null) return;
        setCommon(shader, r, r, r, MODE_CRESCENT);
        shader.safeGetUniform("u_stroke").set(0f);
        shader.safeGetUniform("u_center").set(cutD, 0f);
        shader.safeGetUniform("u_radius2").set(cutR);
        begin(m, cx - r, cy - r, cx + r, cy + r, color, color);
    }
}

package com.niuqu.pickupcard.render.shape;

import com.mojang.blaze3d.shaders.Uniform;
import com.niuqu.pickupcard.PickupCard;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * 形状着色器的持有者，以及一个<b>把静默失败变成尖叫</b>的 uniform 取用口。
 * <p>
 * 【为什么必须自己包一层取 uniform】1.20.1 的 {@code ShaderInstance.safeGetUniform(name)}
 * 在名字不存在时返回的是一个 {@code AbstractUniform} <b>空实现</b>（字节码就是一条
 * {@code return}），而它<b>不报错、不打日志</b>。也就是说打错一个字母、或者某个 uniform
 * 被 GLSL 编译器优化掉，都会变成"set 全部被丢弃、画面什么都不显示、日志一片干净"。
 * <p>
 * 上一版"外壳整层不可见、无日志可查"就长这个样子。所以这里一律走
 * {@link ShaderInstance#getUniform(String)}（拿不到返回 {@code null}），并在缺失时
 * <b>每个名字只尖叫一次</b>（避免每帧刷屏），然后把这条错误留在日志里。
 * <p>
 * 注册本身在平台层（{@code RegisterShadersEvent} 是 Forge API），这里只存引用，
 * 这样形状层不依赖任何加载器。
 */
public final class ShapeShaders {

    @Nullable
    private static volatile ShaderInstance shape;

    /** 已经报过缺失的 uniform 名，保证只尖叫一次。 */
    private static final Set<String> REPORTED = new HashSet<>();

    private ShapeShaders() {
    }

    public static void bind(@Nullable ShaderInstance instance) {
        shape = instance;
    }

    @Nullable
    public static ShaderInstance shape() {
        return shape;
    }

    /** 着色器是不是已经就绪。渲染前先问一句，比拿到 null 再猜要好。 */
    public static boolean ready() {
        return shape != null;
    }

    /**
     * 取一个 uniform；不存在就返回 {@code null} 并<b>报错一次</b>。
     * <p>
     * 调用方必须处理 {@code null}（跳过这次设置），不要退回 {@code safeGetUniform}。
     */
    @Nullable
    public static Uniform uniform(String name) {
        ShaderInstance shader = shape;
        if (shader == null) {
            return null;
        }
        Uniform uniform = shader.getUniform(name);
        if (uniform == null) {
            boolean first;
            synchronized (REPORTED) {
                first = REPORTED.add(name);
            }
            if (first) {
                PickupCard.LOGGER.error(
                        "着色器 gui_shape 里找不到 uniform '{}' —— 这次设置整个会被丢弃，画面不会有任何变化。"
                                + "检查 assets/pickupcard/shaders/core/gui_shape.json 的 uniforms 数组，"
                                + "以及 GLSL 里这个名字是否真的被用到（未被用到的 uniform 会被编译器优化掉）。",
                        name);
            }
        }
        return uniform;
    }

    /** 换世界/重载时清掉"已报过"的记录，让重载后的问题能再报一次。 */
    public static void resetReports() {
        synchronized (REPORTED) {
            REPORTED.clear();
        }
    }
}

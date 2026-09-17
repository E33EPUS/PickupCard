package com.niuqu.pickupcard.render.shape;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * 形状层自己的 {@link RenderType}。
 * <p>
 * 【为什么是继承而不是 {@code RenderType.create}】1.20.1 的 {@code create} 是
 * <b>包级私有</b>的，模组在包外根本调不到。继承 {@link RenderType} 用它的 protected
 * 构造器是绕开这一点的正规做法（ModernUI 的 {@code GuiRenderType} 就是这么做的），
 * 不需要 access transformer。
 * <p>
 * 【为什么必须走 RenderType 而不是自己开关 GL 状态】上一版就是在
 * {@code RenderGuiEvent.Post} 里手写
 * {@code RenderSystem.enableBlend/disableCull/disableDepthTest} 然后忘了配对还原，
 * 真机表现为"整层不可见、没有任何日志可查"，最后只能整条路径重写。
 * {@link RenderStateShard} 把"进入时设什么、离开时还原什么"变成一份<b>声明</b>，
 * 两侧由框架成对调用 —— 手写的开关没有这个保证。
 * <p>
 * 【状态清单从哪抄的】照 ModernUI 的 {@code ROUND_RECT_STATES}：无纹理、半透明混合、
 * LEQUAL 深度测试、关闭面剔除、主渲染目标、深度写入。半透明卡面必须走
 * TRANSLUCENT 混合，否则 alpha 会被当成二值。
 */
public final class ShapeRenderType extends RenderType {

    private static final ShaderStateShard SHAPE_SHADER = new ShaderStateShard(ShapeShaders::shape);

    private static final ImmutableList<RenderStateShard> STATES = ImmutableList.of(
            SHAPE_SHADER,
            NO_TEXTURE,
            TRANSLUCENT_TRANSPARENCY,
            LEQUAL_DEPTH_TEST,
            NO_CULL,
            LIGHTMAP,
            NO_OVERLAY,
            NO_LAYERING,
            MAIN_TARGET,
            DEFAULT_TEXTURING,
            COLOR_DEPTH_WRITE,
            DEFAULT_LINE,
            NO_COLOR_LOGIC);

    public static final RenderType SHAPE = new ShapeRenderType(
            "pickupcard_shape",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            1536,
            () -> STATES.forEach(RenderStateShard::setupRenderState),
            () -> STATES.forEach(RenderStateShard::clearRenderState));

    private ShapeRenderType(String name, VertexFormat format, int bufferSize,
                            Runnable setupState, Runnable clearState) {
        super(name, format, VertexFormat.Mode.QUADS, bufferSize, false, false, setupState, clearState);
    }
}

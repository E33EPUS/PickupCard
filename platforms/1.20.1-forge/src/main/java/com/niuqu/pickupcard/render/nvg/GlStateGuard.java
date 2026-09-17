package com.niuqu.pickupcard.render.nvg;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * 把 MC 的 GL 状态借出去，再原样还回来。
 *
 * <p>【为什么必须要有这个类】NanoVG 是直接调 GL 的第三方程，它不认识 MC 的状态缓存：
 * 它会在 flush 末尾 {@code glUseProgram(0)}、改混合模式、解绑 VAO 和缓冲区、
 * 甚至挪 viewport。而 MC 那些"记得自己设过什么"的地方<b>不会知道</b>有人动过手：
 * 典型后果是之后所有绘制都绑不到着色器程序上（画面直接空），或者混合模式错一档。
 *
 * <p>【做法：存-放，而不是猜】这里记下 NanoVG 会碰的每一项，画完照原值放回。
 * 之所以"放回原值"就够，是因为有一条不变式：进入这里之前，<b>真实 GL 状态 == MC 以为的状态</b>
 * （两边都是 MC 自己设的）。把真实状态恢复成原值，等于把这条不变式恢复回来，
 * 所以不需要去猜 MC 缓存里存了什么、也不需要用反射去清缓存。
 *
 * <p>【为什么连 scissor 和 viewport 都要存】它们看着与画卡无关，但 NanoVG 的
 * {@code nvgBeginFrame} 会按它自己的窗口尺寸设 viewport，而 MC 的 GUI 层外面
 * 可能正开着裁剪（我们自己入场动画那段就用了 scissor）。不存的话，
 * 症状是"卡片画完之后，别的东西被裁错了位置"，而且只在特定帧出现。
 */
final class GlStateGuard {

    private final int program;
    private final boolean blendEnabled;
    private final int srcRgb;
    private final int dstRgb;
    private final int srcAlpha;
    private final int dstAlpha;
    private final int equationRgb;
    private final int equationAlpha;
    private final boolean depthEnabled;
    private final boolean depthWrite;
    private final boolean cullEnabled;
    private final boolean scissorEnabled;
    private final int[] scissorBox = new int[4];
    private final int[] viewport = new int[4];
    private final int vertexArray;
    private final int arrayBuffer;
    private final int elementBuffer;
    private final int activeTexture;
    private final int texture2d;

    private GlStateGuard() {
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        texture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer box = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
            box.get(scissorBox);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, box.clear());
            box.get(viewport);
        }
    }

    static GlStateGuard save() {
        return new GlStateGuard();
    }

    void restore() {
        GL20.glUseProgram(program);
        if (blendEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        if (depthEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glDepthMask(depthWrite);
        if (cullEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        if (scissorEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        GL13.glActiveTexture(activeTexture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2d);
    }
}

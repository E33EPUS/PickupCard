package com.niuqu.pickupcard.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.niuqu.pickupcard.PickupCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Matrix4f;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 物品图标的离屏贴图缓存：<b>物品渲进一张透明底的小渲染目标，读回像素，转成 NanoVG
 * 图像</b> —— 从此图标是一张"带 alpha 的贴图"，而不是一次原版 renderItem 调用。
 *
 * <p>【为什么必须存在（2026-09-19 hunt 根因）】方块/mod 物品走 Forge
 * {@code ItemBlockRenderTypes} 映射的 entitySolid/entityCutout 渲染层（NO_BLEND：
 * 帧缓冲不混合 fragment alpha），{@code RenderSystem.setShaderColor} 的 alpha 分量对它们
 * 无效 —— 在"原版 renderItem 直接画"这条路上，图标在数学上<b>不存在淡出的可能</b>：
 * 只能满亮到瞬间消失，或向某个颜色渐变（用户看到的"变黑然后消失"）。变成贴图之后
 * alpha 回来了：淡出、缩放、任何调制都由 NanoVG 的一条管线统一管（与卡壳同一个
 * 全局 alpha），变黑曲线整条作废。
 *
 * <p>【为什么这等价于 loot journal 的做法】lj 的图标本来就是自绘贴图（主题
 * {@code "type": "simple"}），它的"全局调制色栈"对自己的贴图天然有效 —— 它不是解决了
 * NO_BLEND 问题，是绕开了。本类把同一条思路搬到物品图标上：先离屏渲成贴图，再走
 * 可调制的绘制路径。
 *
 * <p>【流程】{@link #ensure} 在 NanoVG 帧<b>外</b>调用（它要切 FBO、渲物品、读回像素）；
 * {@link #image} 在帧<b>内</b>调用（{@code nvgCreateImageRGBA} 首次上传，之后命中缓存）。
 * 缓存按"物品 + NBT + 附魔光"识别；上限 {@value #MAX_ENTRIES} 条，超了整表重建
 * （ NanoVG 图像属于当时的 context，逐条删要记 context，整表清最简单也最稳）。
 */
public final class ItemIconCache {

    /** 渲染目标的边长（图标本体占中心 16，四周留白防等距投影裁边）。 */
    private static final int SIZE = 32;
    /** 缓存上限。一张 32×32 贴图约 4 KB，128 张 ≈ 512 KB，客户端会话内足够。 */
    private static final int MAX_ENTRIES = 128;
    /** 离屏投影的半窗（逻辑 px）：物品 16px 在 RT 中心占 16 显示 px，四周各 4px 余量。 */
    private static final float HALF = 12f;

    private record Entry(long vg, int image, ByteBuffer pixels, long lastUse) {
    }

    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static RenderTarget target;

    private ItemIconCache() {
    }

    /**
     * 确保这个物品的像素已就绪。<b>必须在 NanoVG 帧外调用</b>（切 FBO、渲物品、读回）。
     * 已就绪时是空操作。
     *
     * @param guiCenterX 逻辑画布中心 x（物品画在"虚拟 GUI 坐标"的这个位置——见下）
     * @param guiCenterY 逻辑画布中心 y
     *
     * <p>【为什么不自己 setOrtho 投影】首轮实测：自造正交（z 序列按 GuiGraphics 的
     * MAX/MIN_GUI_Z 推算）渲出来全透明——离屏渲染的坑全在投影与 z 的配合上，考据不如
     * 实参。现在的做法：<b>投影保持 HUD 当前的真实矩阵不动</b>，把物品画在"虚拟 GUI
     * 坐标"的画布中心（viewport 只有 32px，套住的正是 NDC 中心）——原版 renderItem
     * 在这套投影下画得出来，这条路就画得出来。
     */
    public static void ensure(ItemStack stack, int guiCenterX, int guiCenterY) {
        if (stack.isEmpty() || CACHE.containsKey(key(stack))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (target == null) {
            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            target.setClearColor(0f, 0f, 0f, 0f);
        }
        var main = mc.getMainRenderTarget();
        try {
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            // 【投影自己构造（实证数据在手）】HUD 投影实测 m22=-0.0002, m32=-1.2 →
            // z=+150 的 zc=-1.23，在 [-1,1] 之外 = 全透明（三轮仪表逐帧钉死）。
            // 现在直接给 RT 一个自洽的正交：xy 窗口 = 画布中心 ±12（物品 16px 居中
            // 占 21px，2× 超采样），z 行把 150±8 干净地映进 [-1,1]。
            Matrix4f projection = new Matrix4f();
            // 【JOML 字段名是 m<列><行>：平移在 m30/m31/m32，m03 是 w 行的 x 系数】
            // 上一版把平移写进 m03/m13 —— x 被加进 w，NDC 被推到 ±17 全裁
            // （四轮"全透明"的真因）。正确字段：xy 窗口 = 画布中心 ±12，
            // z 行把 150±8 映进 [-1,1]。
            projection.m00(2f / (HALF * 2f));
            projection.m11(2f / (HALF * 2f));
            projection.m22(0.004f);
            projection.m30(-(2f * guiCenterX) / (HALF * 2f));
            projection.m31(-(2f * guiCenterY) / (HALF * 2f));
            projection.m33(1f);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
            // 【ModelView 必须置 identity】HUD 阶段的 ModelView 是世界相机的 view 矩阵
            // —— 顶点先过 ModelView 再过投影，不置 identity 的话几何被相机搬到世界某处，
            // 再被小窗口正交裁掉 = 全透明（上一轮 quad 探针全 0 的真因）。
            var modelView = RenderSystem.getModelViewStack();
            modelView.pushPose();
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            // renderFakeItem 内部 translate(x+8, y+8, 150) 并走原版 renderItem 的完整链
            //（模型变换、光照、flush 全含）—— 传画布中心减半格，物品正落在 RT 中央。
            new GuiGraphics(mc, buffers).renderFakeItem(stack, guiCenterX - 8, guiCenterY - 8);
            buffers.endBatch();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();

            ByteBuffer pixels = readPixels();
            int lit = 0;
            for (int i = 3; i < SIZE * SIZE * 4; i += 4) {
                if ((pixels.get(i) & 0xFF) > 8) {
                    lit++;
                }
            }
            if (lit == 0) {
                // 全透明 = 这张贴图没画上（画面表现为该卡图标整帧缺失，不能静默）
                PickupCard.LOGGER.error("[图标缓存] {} 离屏渲染读回全透明——图标该帧会缺失",
                        key(stack));
            }

            CACHE.put(key(stack), new Entry(0L, 0, pixels, System.currentTimeMillis()));
        } catch (Exception e) {
            PickupCard.LOGGER.error("[图标缓存] 物品渲到离屏贴图失败（{}）—— 这张卡的图标该帧不画",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()), e);
        } finally {
            main.bindWrite(true);
        }
    }

    /**
     * 拿这个物品的 NanoVG 图像 handle（0 = 没有，调用方该帧跳过图标）。
     * <b>必须在 NanoVG 帧内调用</b>：首帧把已就绪的像素上传成 context 图像。
     */
    public static int image(long vg, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Entry entry = CACHE.get(key(stack));
        if (entry == null || entry.pixels() == null) {
            return 0;
        }
        if (entry.vg() != vg || entry.image() == 0) {
            if (entry.image() != 0) {
                NanoVG.nvgDeleteImage(entry.vg(), entry.image());
            }
            entry.pixels().rewind();
            int image = NanoVG.nvgCreateImageRGBA(vg, SIZE, SIZE, 0, entry.pixels());
            CACHE.put(key(stack), new Entry(vg, image, entry.pixels(), entry.lastUse()));
            return image;
        }
        return entry.image();
    }

    /** 诊断计数：缓存里有多少个物品贴图（harness 读数用）。 */
    public static int size() {
        return CACHE.size();
    }

    /** 清空（换世界时随渲染层一起重置；NanoVG context 销毁后旧 handle 全部作废）。 */
    public static void clear() {
        CACHE.clear();
    }

    private static String key(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id + "#" + (stack.hasTag() ? stack.getTag().hashCode() : 0)
                + "#" + (stack.hasFoil() ? 1 : 0);
    }

    /**
     * 从渲染目标读回像素：<b>行序翻转</b>（GL 帧缓冲原点在左下，图像原点在左上）+
     * <b>预乘 alpha</b>（NanoVG 的混合按预乘语义，straight 像素直接喂会在半透明边缘发亮）。
     */
    private static ByteBuffer readPixels() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer raw = stack.malloc(SIZE * SIZE * 4);
            // 1.20.1 的 RenderTarget 没有 readPixels —— bindRead + glReadPixels 从
            // GL_READ_FRAMEBUFFER 目标读（读回后行序是"底部在前"，见下面翻转）。
            target.bindRead();
            GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                    MemoryUtil.memAddress(raw));
            target.unbindRead();
            ByteBuffer out = ByteBuffer.allocateDirect(SIZE * SIZE * 4);
            for (int y = 0; y < SIZE; y++) {
                int srcRow = (SIZE - 1 - y) * SIZE;
                for (int x = 0; x < SIZE; x++) {
                    int i = (srcRow + x) * 4;
                    int r = raw.get(i) & 0xFF;
                    int g = raw.get(i + 1) & 0xFF;
                    int b = raw.get(i + 2) & 0xFF;
                    int a = raw.get(i + 3) & 0xFF;
                    out.put((byte) (r * a / 255));
                    out.put((byte) (g * a / 255));
                    out.put((byte) (b * a / 255));
                    out.put((byte) a);
                }
            }
            return out;
        }
    }
}

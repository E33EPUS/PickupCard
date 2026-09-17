package com.niuqu.pickupcard.render.nvg;

import org.junit.jupiter.api.Test;
import org.lwjgl.nanovg.NSVGImage;
import org.lwjgl.nanovg.NSVGPath;
import org.lwjgl.nanovg.NSVGShape;
import org.lwjgl.nanovg.NanoSVG;
import org.lwjgl.system.Pointer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "引擎进来了吗"的第一道门：不开游戏、不需要 GL 上下文，只验三件事 ——
 * 依赖能解析、native 能加载、SVG 能被真的解析成路径。
 *
 * 【为什么这条测试值得存在】前两件事失败时的报错都是 UnsatisfiedLinkError，
 * 而它只在"第一次真的去画"的那一刻发生。如果那个时刻发生在玩家机器上，
 * 症状就是 HUD 整个不画；放进单元测试，失败就变得又早又响。
 *
 * 【踩过的坑：链表结尾不是 Java 的 null】
 * LWJGL 给 NanoSVG 生成的指针工厂**不做 null 检查**——
 * {@code NSVGPath.create(long)} 的字节码就是 {@code new NSVGPath(address, null)}，
 * 没有 {@code address == 0 ? null : ...} 那一行。
 * 于是链表最后一个元素的 {@code next()} 返回的是**包着 0 地址的非 null 对象**，
 * 对它调一次 {@code npts()} 就是 SIGSEGV（实测：JVM 直接崩，Gradle 只报
 * "Test Executor finished with non-zero exit value"，连测试名字都不给你）。
 * 所以下面两个循环都用 {@link #alive} 判断，而不是 {@code != null}。
 *
 * NanoSVG 的 nsvgParse 是纯 CPU 的（不需要 OpenGL 上下文），所以它能在这里跑 ——
 * 真正需要显卡的那部分（nvgCreate / nvgBeginFrame）只能进游戏验。
 */
class NanoSvgSmokeTest {

    /** 把三段式卡片的外壳写成 SVG：竖条 + 名字框，两个圆角矩形，颜色取主题里的两个键。 */
    private static final String SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="32" viewBox="0 0 24 32">
              <rect x="0" y="2" width="5" height="28" rx="2.5" fill="#55EBFF"/>
              <rect x="9" y="0" width="15" height="32" rx="6" fill="#262B38"/>
            </svg>
            """;

    /** 指针还活着吗——不是"对象非 null"，见类注释。 */
    private static boolean alive(Pointer node) {
        return node != null && node.address() != 0L;
    }

    @Test
    void nativeLoadsAndSvgParsesToCubicPaths() {
        NSVGImage image = NanoSVG.nsvgParse(SVG, "px", 96f);
        assertNotNull(image, "nsvgParse 返回 null：native 没加载成功");
        assertTrue(alive(image), "nsvgParse 返回了空指针");
        try {
            assertEquals(24f, image.width(), 0.01f, "SVG 宽度");
            assertEquals(32f, image.height(), 0.01f, "SVG 高度");

            int shapes = 0;
            int paths = 0;
            int segments = 0;
            int[] fills = new int[8];
            for (NSVGShape shape = image.shapes(); alive(shape); shape = shape.next()) {
                shapes++;
                assertTrue(shape.bounds(0) >= -0.5f && shape.bounds(1) >= -0.5f
                                && shape.bounds(2) <= 24.5f && shape.bounds(3) <= 32.5f,
                        "形状越界: " + shape.bounds(0) + "," + shape.bounds(1)
                                + "," + shape.bounds(2) + "," + shape.bounds(3));
                if (shapes <= fills.length) {
                    fills[shapes - 1] = shape.fill().color();
                }
                for (NSVGPath path = shape.paths(); alive(path); path = path.next()) {
                    paths++;
                    assertTrue(path.npts() >= 4, "一个形状至少要有 1 段立方（4 个点）");
                    assertEquals(0, (path.npts() - 1) % 3, "nanosvg 输出三次贝塞尔：点数 = 1 + 3n");
                    assertEquals(1, path.closed(), "闭合图形");
                    segments += (path.npts() - 1) / 3;
                }
            }
            assertEquals(2, shapes, "两个 rect 应该是两个 shape");
            assertEquals(2, paths, "每个 rect 一条 path");
            assertTrue(segments >= 8, "圆角矩形至少 8 段立方，实际 " + segments);
            assertTrue(fills[0] != 0 && fills[1] != 0, "两个形状都该有填充色");
            assertTrue(fills[0] != fills[1], "两个 rect 的颜色不同，解析不该把颜色搞成同一个");
            System.out.printf("NanoSVG: %d shapes / %d paths / %d cubic segments / colors %08X %08X%n",
                    shapes, paths, segments, fills[0], fills[1]);
        } finally {
            NanoSVG.nsvgDelete(image);
        }
    }
}

package com.niuqu.pickupcard.render;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * 「一张卡怎么画」的插槽 —— 本 mod 里<b>唯一</b>允许换掉渲染方案的地方。
 * <p>
 * 【为什么要留这个接口】渲染是需求变得最快的一层（换风格、换引擎、加特效），而拾取管线
 * （嗅探包 → 过滤 → 合并账本）几乎不动。把画法收口成一个接口之后，「要不要引进渲染引擎 /
 * 前置库」就不再是一个一旦选了很难回头的架构决定，而是一个可以随时替换的实现。
 * 这也是本仓对 v0.1.0 那个教训的回应：当时界面直接长在 ApricityUI 的 DOM 上，
 * 想换画法等于重写整个渲染层。
 * <p>
 * 【为什么按批给卡而不是一张一张回调】按批给，painter 才能自己决定绘制顺序：想逐卡画就
 * 逐卡画；想把所有卡的外壳先合成一遍、再统一画内容也行（上一版就是这么做的，为的是
 * 避免混合状态在一帧里反复切换）。逐卡回调会把这种优化从接口层堵死。
 * <p>
 * 【坐标约定】{@link CardSlot} 给的是最终屏幕坐标（GUI 缩放后的像素），painter 不需要
 * 再算锚点或堆叠位置。卡内局部坐标由各 painter 自己选，但一旦选了就别跟屏幕坐标混用
 * ——上一版的「幽灵分身」bug（装饰画到了屏幕左边）就是绝对/局部坐标混用造成的。
 */
public interface CardPainter {

    /**
     * 画这一帧的所有卡。
     *
     * @param gui    绘制入口，坐标原点在屏幕左上角
     * @param canvas 这一帧的共享上下文（时刻、时间轴、主题、配置）
     * @param slots  要画的卡，按从老到新排列；位置已经算好
     */
    void paint(GuiGraphics gui, CardCanvas canvas, List<CardSlot> slots);
}

package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.style.Easing;

/**
 * 一条补间：一个标量在给定毫秒数内从 {@code from} 走到 {@code to}，取值时按 easeOutCubic 缓动。
 *
 * <p>【为什么要有它】界面上那几处短动画（换页淡入、行悬停、标签强调条、预览换样例）要的是同一件事：
 * "记住上一个值、记住什么时候开始的、按曲线取此刻的值"。抄第二遍就会出现两套时长、两种曲线，
 * 而"哪一处手感不一样"是用眼睛说不清的。这里只做这件最小的事 —— 没有状态机、没有队列，
 * 也不管"谁该在什么时候播放"，那些是调用方的语义。
 *
 * <p>【为什么 {@link #retarget} 必须"已经在目标上就什么都不做"】悬停每一帧都会被问一次
 * "鼠标还在这一行吗"。每次都重置起始时刻的话，动画永远停在第一帧 —— 症状是"高亮亮不起来"，
 * 而原因（每帧重开）从画面上完全看不出来。
 *
 * <p>纯函数 + 一个值，不碰 GL，也不碰 MC，所以能离线钉住（{@code TweenTest}）。
 */
public final class Tween {

    private float from;
    private float to;
    private long startMs;
    private long durMs;

    private Tween(float value, long now) {
        this.from = value;
        this.to = value;
        this.startMs = now;
        this.durMs = 0L;
    }

    /** 一个已经到位的值（没有动画在跑）。{@code now} 只是起点，随便给。 */
    public static Tween at(float value, long now) {
        return new Tween(value, now);
    }

    public float target() {
        return to;
    }

    /** 此刻的值：{@code durMs <= 0} 或已经到点 → 目标值。 */
    public float at(long now) {
        if (durMs <= 0L) {
            return to;
        }
        long elapsed = now - startMs;
        if (elapsed <= 0L) {
            return from;
        }
        if (elapsed >= durMs) {
            return to;
        }
        return from + (to - from) * Easing.easeOutCubic((float) elapsed / (float) durMs);
    }

    /** 还没走到目标。调用方靠它决定"这一帧要不要重画界面"。 */
    public boolean moving(long now) {
        return durMs > 0L && now - startMs < durMs;
    }

    /**
     * 换目标：<b>从此刻的值出发</b>，所以中途改主意不会跳变（鼠标快速划过一行时最明显）。
     *
     * @param durMs 走完要多久；0 = 立刻到位
     */
    public void retarget(float target, long now, long durMs) {
        if (target == to) {
            return;
        }
        from = at(now);
        to = target;
        startMs = now;
        this.durMs = Math.max(0L, durMs);
    }

    /**
     * 立刻到位。
     * <p>重建一页时用：新控件不该继承上一页的动画进度（否则换页那一帧会看到"半亮的一行"）。
     */
    public void snap(float value) {
        from = value;
        to = value;
        durMs = 0L;
    }
}

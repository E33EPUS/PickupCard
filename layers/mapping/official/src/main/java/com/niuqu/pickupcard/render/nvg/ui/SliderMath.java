package com.niuqu.pickupcard.render.nvg.ui;

/**
 * 滑条/开关那点数学。<b>纯函数，能离线单测</b> —— 拖拽手感全靠它，而手感是"看不见的 bug"：
 * 手感坏了没人报错，只会有人觉得"这界面调不准"。
 */
public final class SliderMath {

    private SliderMath() {
    }

    /** 鼠标 x 落在轨道上的比例（夹到 0..1）。拖拽时每一帧都调它。 */
    public static double ratio(double mouseX, float trackX, float trackW) {
        if (trackW <= 0f) {
            return 0.0;
        }
        double r = (mouseX - trackX) / (double) trackW;
        return Math.max(0.0, Math.min(1.0, r));
    }

    /** 比例 → 整数（min..max 线性铺开，没有"哨兵格"）。 */
    public static int toInt(double ratio, int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return (int) Math.max(lo, Math.min(hi, Math.round(lo + ratio * (hi - lo))));
    }

    /** 比例 → 值（按 step 吸附，并夹进 min..max）。 */
    public static long snap(double ratio, long min, long max, long step) {
        long raw = min + Math.round(ratio * (max - min));
        if (step > 1) {
            raw = Math.round(raw / (double) step) * step;
        }
        return Math.max(min, Math.min(max, raw));
    }

    /** 值 → 比例（画滑条时用；越界也夹住）。 */
    public static double positionOf(double value, double min, double max) {
        if (max <= min) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }
}

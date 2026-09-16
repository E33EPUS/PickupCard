package com.niuqu.pickupcard.notice;

import com.niuqu.pickupcard.text.CountFormat;

/**
 * 一次会话里所有卡共用的参数。
 * <p>
 * 【为什么是 record 而不是直接读配置】渲染与队列都只认它，于是这两层可以在没有 Forge、
 * 没有配置文件的情况下跑单测；接配置只在平台侧做一次采样。上一版把 config 直接读进渲染
 * 路径，结果每个测试都得先起一个 Forge 环境。
 *
 * @param holdMs        一张卡在屏上停留多久（从最近一次被刷新算起）
 * @param exitMs        退场动画时长；DOM 要等它播完才移除节点
 * @param mergeEnabled  是否把连续拾取并成一张卡
 * @param mergeWindowMs 合并窗口
 * @param maxOnScreen   同时在屏上限，超出的淘汰最久没被碰过的
 * @param countFormat   数量怎么写
 */
public record PickupCardSettings(long holdMs,
                                 long exitMs,
                                 boolean mergeEnabled,
                                 long mergeWindowMs,
                                 int maxOnScreen,
                                 CountFormat countFormat) {

    public static PickupCardSettings defaults() {
        return new PickupCardSettings(2_600L, 320L, true, 1_200L, 5, CountFormat.PLUS);
    }

    /** 把外部来的值夹到合法区间：config 是玩家可改的，非法值不该变成崩溃或永不离场。 */
    public PickupCardSettings sanitized() {
        return new PickupCardSettings(
                Math.max(200L, holdMs),
                Math.max(0L, exitMs),
                mergeEnabled,
                Math.max(0L, mergeWindowMs),
                Math.max(1, maxOnScreen),
                countFormat == null ? CountFormat.PLUS : countFormat);
    }
}

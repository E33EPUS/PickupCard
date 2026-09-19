package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.MergeMode;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.text.CountFormat;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置项注册表的钉子：页面归属、行完整性、恢复默认值。
 * <p>
 * 【为什么测它】2026-09-19 审计出的四个"功能打架"里有两个是<b>纯数据漂移</b>：
 * 「展开方式」被手写进了位置页（玩家在动画页找不到入场动画）、恢复默认手抄的
 * exitMode 是 FADE 而正本已是 TRAIN（点恢复反而改错设置）。注册表把它们变成
 * 单一出处之后，这两类漂移第一次能在没有游戏的情况下被钉住。
 */
class ConfigPageSpecTest {

    private static List<ConfigPageSpec.Row> rows() {
        PickupCardSettings eff = new PickupCardSettings(4_000L, 480L, MergeMode.SAME_NBT,
                5, 9, CountFormat.PLUS, true, true, false, 0).sanitized();
        return ConfigPageSpec.rows(StyleModel.defaults(), eff, new ConfigPageSpec.AnchorBridge() {
            @Override
            public String anchorValueText() {
                return "";
            }

            @Override
            public void openEditor() {
            }
        });
    }

    private static ConfigPageSpec.Row row(List<ConfigPageSpec.Row> rows, String label) {
        return rows.stream().filter(r -> label.equals(r.label())).findFirst()
                .orElseThrow(() -> new AssertionError("注册表里没有这一行: " + label));
    }

    /** 动画的键归动画页 —— 「展开方式」是从前漂到位置页的那一个。 */
    @Test
    void animationKeysLiveOnTheAnimPage() {
        List<ConfigPageSpec.Row> rows = rows();
        assertEquals(ConfigPageSpec.Page.ANIM, row(rows, "展开方式").page());
        assertEquals(ConfigPageSpec.Page.ANIM, row(rows, "入场时长").page());
        assertEquals(ConfigPageSpec.Page.ANIM, row(rows, "入场动画").page());
        assertEquals(ConfigPageSpec.Page.ANIM, row(rows, "消失方式").page());
        assertEquals(ConfigPageSpec.Page.ANIM, row(rows, "消失时长").page());
        // 位置页还剩它该剩的
        assertEquals(ConfigPageSpec.Page.LAYOUT, row(rows, "水平对齐").page());
        assertEquals(ConfigPageSpec.Page.LAYOUT, row(rows, "卡片缩放").page());
        assertEquals(ConfigPageSpec.Page.LAYOUT, row(rows, "同屏上限").page());
    }

    /** 同一页里标签不许重名 —— harness 按标签找控件点，重名就会点错行。 */
    @Test
    void labelsAreUniqueWithinEachPage() {
        for (ConfigPageSpec.Page page : ConfigPageSpec.Page.values()) {
            Set<String> seen = new HashSet<>();
            for (ConfigPageSpec.Row r : rows()) {
                if (r.page() == page && !r.isHeader()) {
                    assertTrue(seen.add(r.label()),
                            "页 " + page + " 里标签重名: " + r.label());
                }
            }
        }
    }

    /** 行完整性：小节头没有控件；值行必须有控件、有说明、有恢复动作（「位置」除外）。 */
    @Test
    void rowsAreWellFormed() {
        for (ConfigPageSpec.Row r : rows()) {
            if (r.isHeader()) {
                assertFalse(r.restorable(), "小节头不该有恢复动作: " + r.label());
                continue;
            }
            assertNotNull(r.widget(), "值行缺控件: " + r.label());
            assertNotNull(r.hint(), "值行缺悬停说明: " + r.label());
            assertFalse(r.hint().isBlank(), "值行悬停说明是空白: " + r.label());
            if (!"位置".equals(r.label())) {
                assertTrue(r.restorable(), "值行缺恢复动作: " + r.label());
            }
        }
    }

    /** 动画页恢复 = 九项（含搬进来的「展开方式」）—— 界面上那句"重置本页 N 项"的 N 是现算的。 */
    @Test
    void animPageRestoresNineItems() {
        long n = rows().stream()
                .filter(r -> r.page() == ConfigPageSpec.Page.ANIM && r.restorable())
                .count();
        assertEquals(9L, n);
        assertTrue(ConfigPageSpec.restoreHint(ConfigPageSpec.Page.ANIM, (int) n).contains("9 项"));
    }

    /** 过滤页是动态行，注册表里不该有它 —— 恢复走 FilterPageBuilder 自己那条路。 */
    @Test
    void filterPageIsDynamicAndNotInTheRegistry() {
        assertTrue(rows().stream().noneMatch(r -> r.page() == ConfigPageSpec.Page.FILTER));
    }

    /**
     * 最容易被手抄漂掉的几个出厂值（恢复动作 = set(getDefault())，这里钉住 getDefault 本身）。
     * exitMode 曾被抄成 FADE，正本 2026-09-19 起是 TRAIN —— 与入场的火车对称。
     */
    @Test
    void factoryDefaultsComeFromTheKeyDefinitions() {
        assertEquals(LayoutSettings.Exit.TRAIN, PickupCardConfig.VALUES.exitMode.getDefault());
        assertEquals(LayoutSettings.Appear.SLIDE, PickupCardConfig.VALUES.appearMode.getDefault());
        assertEquals(LayoutSettings.Side.LEFT, PickupCardConfig.VALUES.align.getDefault());
        assertEquals(0, PickupCardConfig.VALUES.scalePercent.getDefault());
        assertEquals(4_000L, PickupCardConfig.VALUES.holdMs.getDefault());
        assertEquals(480L, PickupCardConfig.VALUES.exitMs.getDefault());
        assertEquals(5, PickupCardConfig.VALUES.maxOnScreen.getDefault());
        assertEquals(9, PickupCardConfig.VALUES.queueSize.getDefault());
    }
}

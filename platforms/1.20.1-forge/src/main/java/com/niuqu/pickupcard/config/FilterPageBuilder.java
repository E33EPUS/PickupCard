package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.filter.RuleListEdit;
import com.niuqu.pickupcard.render.nvg.ui.NvgButton;
import com.niuqu.pickupcard.render.nvg.ui.NvgTextField;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 「过滤」页的三张名单：一条规则一行、末尾一行输入框。
 *
 * <p>【为什么从界面里搬出来（2026-09-19 架构审计）】三张名单不是"一个值"，是<b>可增删的
 * 列表</b> —— 行数随玩家加多少条涨，注定进不了 {@link ConfigPageSpec} 那张静态注册表。
 * 把它单独成一个类，界面类里就不再有"列表编辑"这摊事；判定规则本身仍然只有一处
 * （{@code shared} 的 {@code FilterRules}），这里只管摆行与回写。
 *
 * <p>【为什么拒绝必须说出来】"打字 → 回车 → 什么都没发生"是这类输入框最常见的失败方式，
 * 而底部那行是界面唯一能自我解释的地方 —— 拒绝原因经 {@link Host#rejectNote} 交回去。
 */
public final class FilterPageBuilder {

    /** 「恢复本页默认」清掉几张名单（恢复说明那句要用）。 */
    public static final int RESTORE_COUNT = 3;

    /** 界面要替本类做的三件事：摆一行、说一句话、请求重建。 */
    public interface Host {

        void cell(String label, NvgWidget widget, String hint);

        /** 加规则被拒时说的一句话，短时间内在底部那行顶掉悬停说明。 */
        void rejectNote(String message);

        /** 名单变了 = 行数变了，必须重建（preserveScroll = 同页重建保留滚动位置）。 */
        void requestRebuild(boolean preserveScroll);
    }

    private FilterPageBuilder() {
    }

    /** 摆出三张名单。每张 = 一行表头（右侧只读钮报条数）+ 每条规则一行 + 一行输入框。 */
    public static void build(PickupCardConfig.Values v, Host host) {
        filterList("黑名单", v.blacklist,
                "命中就不弹卡。挖一片沙滩不想刷屏时写这里",
                "加进黑名单：物品 minecraft:cobblestone / tag #forge:ores / 整个 mod @modid，回车加",
                host);
        filterList("白名单", v.whitelist,
                "命中就一定弹卡并且强调；它压过黑名单",
                "加进白名单：写法同上；白名单 + 静音名单 = 强调地静音弹卡",
                host);
        filterList("静音名单", v.muteList,
                "照常弹卡，但稀有提示音和原版拾取音都被压掉",
                "加进静音名单：写法同上",
                host);
    }

    /** 「恢复本页默认」：清空三张名单。 */
    public static void restoreDefaults(PickupCardConfig.Values v) {
        v.blacklist.set(List.of());
        v.whitelist.set(List.of());
        v.muteList.set(List.of());
        ConfigPageSpec.changed();
    }

    private static void filterList(String title, ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                                   String what, String inputHint, Host host) {
        List<String> rules = rules(config);
        // 表头这一行：标签是名单名，右边那颗只读钮报"现在几条"——只读控件的底更暗、不画描边，
        // 一眼能看出它点不动（见 NvgButton 的 action == null）
        host.cell(title, new NvgButton("", () -> rules.size() + " 条", null), what);
        for (int i = 0; i < rules.size(); i++) {
            String rule = rules.get(i);
            int index = i;
            host.cell(rule, new NvgButton("", () -> "删除", () -> writeRules(config,
                    RuleListEdit.remove(rules(config), index), host)),
                    // 【为什么把规则原文放在最前】标签那一格只有几十像素宽，长规则在屏上就是
                    // "minecraft:cobb" —— 底部这行是唯一能看全的地方
                    rule + " —— 点「删除」把它从「" + title + "」里去掉");
        }
        host.cell("加一条", NvgTextField
                .rule("", () -> "", RuleListEdit.MAX_RULE_LENGTH, text -> addRule(config, title, text, host))
                .placeholder("写一条再回车"), inputHint);
    }

    private static List<String> rules(ForgeConfigSpec.ConfigValue<List<? extends String>> config) {
        List<? extends String> raw = config.get();
        return raw == null ? List.of() : List.copyOf(raw);
    }

    private static void writeRules(ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                                   List<String> rules, Host host) {
        config.set(List.copyOf(rules));
        ConfigPageSpec.changed();
        // 【必须重建】名单变了 = 行数变了：不重建的话删掉的那一行会留在屏上继续可点，
        // 而它背后的下标已经指向别人了。同页重建保留滚动位置。
        host.requestRebuild(true);
    }

    private static void addRule(ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                                String title, String text, Host host) {
        RuleListEdit.Result r = RuleListEdit.add(rules(config), text);
        if (r.ok()) {
            writeRules(config, r.rules(), host);
            return;
        }
        host.rejectNote(title + "：" + RuleListEdit.message(r.reject()));
    }
}

package com.niuqu.pickupcard.render.nvg.ui;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 颜色控件：<b>当前色的色块 + 点一下在色板里循环</b>（2026-09-19 定案，取代裸 hex 输入框）。
 * <p>
 * 【为什么不再让玩家手打 #RRGGBB】颜色代码是程序员的方言 —— 填错还静默不生效（解析失败
 * 就当没改过回主题），玩家看到的是"填了没反应"。色块把"现在是什么颜色"画出来，
 * 点一下换一个、第一档永远是「跟随主题」—— 改错色到不了比"再点一下"更远的代价。
 * <p>
 * 【精确色值怎么办】TOML 里写 {@code #RRGGBB} 仍然是完整出路（界面行说明里写了这句）；
 * 配置里若已有手写的值，色块会照实显示它、并在下一次点击后回到色板循环里。
 * 手写值解析失败（写坏了）时显示「无效」：生效色按主题画，但标签明说这项没生效 ——
 * 不再是安静的假象。
 */
public final class NvgColorChip extends NvgWidget {

    /** 色板：第一格是「跟随主题」哨兵（空串），后面 12 个覆盖黑白灰与常用彩色的明暗两端。 */
    private static final String[] PRESETS = {
            "",
            "#F2F5F9", "#C7CEDB", "#8A93A6", "#4A5262", "#20242E", "#0B0D12",
            "#FF6B6B", "#FFB35C", "#FFE066", "#7DE38B", "#55EBFF", "#D78BFF",
    };

    private final Supplier<Integer> effective;
    private final Supplier<String> raw;
    private final Consumer<String> write;

    /**
     * @param effective 生效色（主题 + 已解析的覆盖）—— 色块画的是它，永远诚实
     * @param raw       配置原文（空串 = 跟随主题；写坏的串 = 无效）
     * @param write     写回配置：空串回主题，否则 {@code #RRGGBB…}
     */
    public NvgColorChip(String label, Supplier<Integer> effective, Supplier<String> raw,
                        Consumer<String> write) {
        super(label);
        this.effective = effective;
        this.raw = raw;
        this.write = write;
    }

    @Override
    public String value() {
        String current = raw.get();
        if (current == null || current.isBlank()) {
            return "主题";
        }
        return com.niuqu.pickupcard.style.StyleOverrides.parseArgb(current).isPresent()
                ? normalize(current) : "无效";
    }

    private static String normalize(String hex) {
        String trimmed = hex.trim();
        return "#" + (trimmed.startsWith("#") ? trimmed.substring(1) : trimmed).toUpperCase(Locale.ROOT);
    }

    @Override
    protected void onActivate() {
        String current = raw.get();
        int index = 0;
        if (current != null && !current.isBlank()
                && com.niuqu.pickupcard.style.StyleOverrides.parseArgb(current).isPresent()) {
            String want = normalize(current);
            for (int i = 1; i < PRESETS.length; i++) {
                if (normalize(PRESETS[i]).equals(want)) {
                    index = i;
                    break;
                }
            }
        }
        // 不在色板上的手写色：下一次点击回到「主题」，再点才进色板 —— 循环没有死角
        String next = PRESETS[(index + 1) % PRESETS.length];
        write.accept(next);
    }

    @Override
    protected void paint(NvgUi ui) {
        NvgPalette p = ui.palette;
        String label = value();
        boolean invalid = label.equals("无效");

        // 色块画生效色：无论配置里写没写，玩家看到的就是卡上现在的颜色
        float swatch = h - 6f;
        float swatchY = y + 3f;
        ui.well(x + 2f, swatchY, swatch, swatch, invalid ? p.well : effective.get());
        float textX = x + 2f + swatch + 5f;
        ui.text(label, textX, y + (h - ui.font().lineHeight) / 2f,
                invalid ? p.textDim : p.text);
        if (hovered || focused) {
            ui.strokeRoundRect(x, y, w, h, p.radius, p.outline);
        }
    }
}

package com.niuqu.pickupcard.render.nvg.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 开关：左边一句「开 / 关」，右边一颗滑到两头的<b>圆钮</b>。
 *
 * <p>【为什么不用按钮显示"开/关"】按钮的语义是"点一下做一件事"，开关的语义是"现在是什么状态"。
 * 两者画得一样的话，玩家得先读字才知道能不能点；圆钮一摆，状态和可点性一眼都在。
 * 这也是这一层存在的意义：原版按钮画不出圆钮（它是九宫格贴图），NanoVG 一行就够。
 */
public final class NvgToggle extends NvgWidget {

    private final Supplier<Boolean> state;
    private final Consumer<Boolean> onChange;

    public NvgToggle(String label, Supplier<Boolean> state, Consumer<Boolean> onChange) {
        super(label);
        this.state = state;
        this.onChange = onChange;
    }

    @Override
    public String value() {
        return state.get() ? "开" : "关";
    }

    @Override
    protected void onActivate() {
        onChange.accept(!state.get());
    }

    @Override
    protected void paint(NvgUi ui) {
        NvgPalette p = ui.palette;
        boolean on = state.get();
        float pillW = Math.min(26f, w * 0.32f);
        float pillH = Math.max(8f, h - 8f);
        float pillX = x + w - pillW - 2f;
        float pillY = y + (h - pillH) / 2f;
        float r = pillH / 2f;

        ui.text(value(), x + 2f, y + (h - ui.font().lineHeight) / 2f, p.text);

        // 轨道
        ui.fillRoundRect(pillX, pillY, pillW, pillH, r, on ? p.accent : p.well);
        if (!on) {
            ui.strokeRoundRect(pillX, pillY, pillW, pillH, r, p.outline);
        }
        // 钮：开在右边、关在左边（圆钮位置本身就是状态）
        float knobX = on ? pillX + pillW - r : pillX + r;
        ui.circle(knobX, pillY + r, r - 1f, on ? 0xFFFFFFFF : 0xFFD5DAE5);
        if (hovered || focused) {
            ui.strokeRoundRect(x, y, w, h, p.radius, p.outline);
        }
    }
}

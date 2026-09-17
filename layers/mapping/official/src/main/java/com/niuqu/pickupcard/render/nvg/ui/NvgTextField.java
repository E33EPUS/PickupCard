package com.niuqu.pickupcard.render.nvg.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;

/**
 * 文本框（现在只有颜色项用它）：<b>点一下拿焦点，光标闪，退格能删，回车放焦点。</b>
 *
 * <p>【为什么只有这一个控件要键盘】这个界面别的控件都是"点一下就有结果"，只有颜色必须让玩家
 * 打出 {@code #AARRGGBB}。旧版那套里它叫 {@code TextWidget}，是唯一一个需要焦点的控件 ——
 * 焦点这件事一旦扩散到全界面（Tab 漫游、快捷键、拖选），就真长成第二个 UI 框架了。
 *
 * <p>【为什么每敲一个字就上报】颜色没有"提交"这个动作（回车只是放焦点），
 * 打了合法值就该立刻在预览里看见 —— 这也是这个界面唯一的即时反馈。
 */
public final class NvgTextField extends NvgWidget {

    private final Supplier<String> value;
    private final Consumer<String> onChange;
    private String draft;
    private boolean editing;

    public NvgTextField(String label, Supplier<String> value, Consumer<String> onChange) {
        super(label);
        this.value = value;
        this.onChange = onChange;
    }

    @Override
    public String value() {
        return value.get();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean hit = super.mouseClicked(mouseX, mouseY, button);
        if (hit) {
            draft = value.get();
            editing = true;
        }
        return hit;
    }

    @Override
    public void blur() {
        super.blur();
        editing = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int modifiers) {
        if (!editing) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !draft.isEmpty()) {
            draft = draft.substring(0, draft.length() - 1);
            onChange.accept(draft);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c) {
        if (!editing || draft.length() >= 9) {
            return false;
        }
        if (!isColorChar(c)) {
            return true;      // 吃掉非法字符：颜色框里只该出现 0-9 a-f A-F #
        }
        draft = draft + c;
        onChange.accept(draft);
        return true;
    }

    private static boolean isColorChar(char c) {
        return c == '#' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    @Override
    protected void paint(NvgUi ui) {
        NvgPalette p = ui.palette;
        ui.well(x, y, w, h, wellColor(p));
        String shown = editing ? draft : value.get();
        ui.text(shown, x + 4f, y + (h - ui.font().lineHeight) / 2f, p.text);
        if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
            // 光标：闪，且跟在文字后面 —— 不闪的话玩家分不清"在编辑"还是"只是显示"
            float caret = x + 5f + ui.textWidth(shown);
            ui.fillRoundRect(caret, y + 3f, 1f, h - 6f, 0.5f, p.text);
        }
    }
}

package com.niuqu.pickupcard.dev;

import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * 开发用调试屏：让"改一行 → 看一眼"的循环从分钟级压到秒级。
 * <p>
 * 【它为什么是这个项目里最重要的工具】上一版死于三个真机 bug + 一次美术定不了稿，
 * 这四个问题的共同点是：<b>它们的反馈周期太长</b>。"编译 25 秒 → 起游戏 → 进世界 →
 * 捡东西 → 看"一轮好几分钟，所以一次只能验一个假设；而美术需要的是几十轮快速试错。
 * <p>
 * 【它不自己画卡】卡的绘制完全走 {@link CardStage#renderInto}，与 HUD 同一条路径。
 * 这里只多做三件事：铺可控的背景、画几何辅助线、显示只读读数。
 * <p>
 * 【它为什么不算"第二个 UI 框架"】没有控件系统、没有布局、没有事件分发——按键是硬编码的
 * switch。等形状层做完，滑条会用我们自己的形状层画，那既是工具也是"吃狗粮"。
 */
public final class DevCardScreen extends Screen {

    private static final int BG_CHESS = 0;
    private static final int BG_FLAT = 1;
    private static final int BG_COUNT = 2;

    /** 棋盘格边长。半透明卡面必须在有纹理的背景上才判断得出来。 */
    private static final int CELL = 12;

    private final List<CardFixtures.Fixture> fixtures = CardFixtures.all();
    private int background = BG_CHESS;
    /** 辅助线默认开：上一版两个坐标 bug 都靠它一眼看穿。 */
    private boolean guides = true;
    private boolean stats = true;

    public DevCardScreen() {
        super(Component.literal("PickupCard Harness"));
    }

    /** 世界继续跑——动画、Tick、拾取都要活着，否则看到的不是真的。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // 刻意不加任何 widget：这个屏不是给玩家用的界面
    }

    /** 盖掉原版背景（默认是模糊+暗化），换成我们能控制的底。 */
    @Override
    public void renderBackground(GuiGraphics gui) {
        // 故意空白：背景由本类自己铺，见 render
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        paintBackground(gui);
        CardStage.INSTANCE.renderInto(gui, Minecraft.getInstance());
        if (guides) paintGuides(gui);
        if (stats) paintStats(gui);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------------
    // 三层叠加：背景 / 辅助线 / 读数
    // ------------------------------------------------------------------

    private void paintBackground(GuiGraphics gui) {
        if (background == BG_FLAT) {
            gui.fill(0, 0, width, height, 0xFF23232B);
            return;
        }
        for (int y = 0; y < height; y += CELL) {
            for (int x = 0; x < width; x += CELL) {
                boolean alt = ((x / CELL) + (y / CELL)) % 2 == 0;
                gui.fill(x, y, x + CELL, y + CELL, alt ? 0xFF202028 : 0xFF2C2C36);
            }
        }
    }

    /**
     * 几何辅助线：卡身外框 + 中心十字。
     * <p>
     * 这是上一版最贵的一课——"图标飞到屏幕左下角"和"y 被加了两次"在真机上只表现为
     * "有点歪"，肉眼根本定不了位；有了外框和十字，错位是<b>看得见</b>的。
     */
    private void paintGuides(GuiGraphics gui) {
        for (CardSlot slot : CardStage.INSTANCE.lastSlots()) {
            int x = Math.round(slot.x());
            int y = Math.round(slot.y());
            int w = Math.round(slot.width());
            int h = Math.round(slot.height());
            gui.renderOutline(x, y, w, h, 0xFFFF4D6D);
            int cx = Math.round(slot.centerX());
            int cy = Math.round(slot.centerY());
            gui.fill(cx - 3, cy, cx + 3, cy + 1, 0xFF4DFF88);
            gui.fill(cx, cy - 3, cx + 1, cy + 3, 0xFF4DFF88);
        }
    }

    private void paintStats(GuiGraphics gui) {
        CardStage.Stats s = CardStage.INSTANCE.stats();
        int y = 6;
        y = line(gui, y, "bg=" + bgName() + "  guides=" + (guides ? "on" : "off"), 0xFFBFC6D4);
        y = line(gui, y, "cards=" + s.live() + "  painted=" + s.painted()
                + "  layout=" + s.layoutMicros() + "us", 0xFFBFC6D4);
        y = line(gui, y, "[1-9] inject   [A] all   [C] clear   [G] guides   [B] bg", 0xFF7D8695);
        y = line(gui, y, "[F] stats   [ESC] close", 0xFF7D8695);

        for (int i = 0; i < fixtures.size(); i++) {
            CardFixtures.Fixture f = fixtures.get(i);
            line(gui, y, (i + 1) + " = " + f.label() + " x" + f.amount(), 0xFF6E7686);
        }
    }

    private int line(GuiGraphics gui, int y, String text, int color) {
        gui.drawString(font, text, 8, y, color, true);
        return y + font.lineHeight + 1;
    }

    private String bgName() {
        return switch (background) {
            case BG_FLAT -> "flat";
            default -> "chess";
        };
    }

    // ------------------------------------------------------------------
    // 按键（硬编码；等形状层到位再换成自绘的滑条/开关）
    // ------------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < fixtures.size()) {
                CardFixtures.clear();
                CardFixtures.inject(fixtures.get(index));
                return true;
            }
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_A -> {
                CardFixtures.clear();
                CardFixtures.injectAll();
                return true;
            }
            case GLFW.GLFW_KEY_C -> {
                CardFixtures.clear();
                return true;
            }
            case GLFW.GLFW_KEY_G -> {
                guides = !guides;
                return true;
            }
            case GLFW.GLFW_KEY_F -> {
                stats = !stats;
                return true;
            }
            case GLFW.GLFW_KEY_B -> {
                background = (background + 1) % BG_COUNT;
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "DevCardScreen(fixtures=%d)", fixtures.size());
    }
}

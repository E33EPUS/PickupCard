package com.niuqu.pickupcard.style;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

/**
 * 卡面样式的唯一真源：从 {@code assets/pickupcard/styles/<name>.json} 读入的参数集。
 * 颜色一律 {@code #RRGGBBAA}，缺哪个键就用默认值——主题文件永远允许只写想改的几行。
 * <p>
 * 【结构长什么样】三段式，从左到右：<b>稀有度竖条 | 物品图标格 | 名字+数量框</b>。
 * 三个图形各自是独立的圆角矩形，不是一张大卡身——所以这里没有"卡身圆角""卡身渐变"，
 * 有的是每个框自己的圆角、内边距和底色。
 * <p>
 * 【为什么不是 Forge config】外观有且只有一个真源，资源包可以整体覆盖它；塞进 config
 * 就成了第二真源。改外观的正确姿势：改 CSS 草稿看效果 → 同步 {@code default.json}。
 * <p>
 * 【参数只留用得上的】月份牌式的老字段（月牙/徽章倍率/名字胶囊/端点/角标底色）随旧画法
 * 一起删了。留着不用的参数会变成"改了没反应"的陷阱，比没有更糟。
 *
 * @param cornerRadius   三个框的圆角半径（px）；夹逼到不超过框高一半
 * @param paddingH       框内水平内边距
 * @param paddingV       框内垂直内边距（决定框高：图标 + 上下各一份）
 * @param gap            框与框之间、名字与数量之间的间隙
 * @param iconSize       物品图标边长（px）。原版物品贴图是 16px，卡上再放大。
 * @param barWidth       稀有度竖条的宽度（px）
 * @param barInsetY      竖条上下各内缩多少（px）。0 = 与卡片齐平；大于 0 时竖条比卡片矮一截。
 * @param fillTop        框底色（玻璃拟态的深色半透明）
 * @param fillBottom     框底色下端（两色相同 = 纯色）
 * @param border         框描边色
 * @param glowAlpha      稀有度微光不透明度（0 = 不画）
 *
 * <p>【2026-09-17 删掉的两组参数】投影（浓度 / 下移 / 软边）与顶部那条 1px 高光。
 * 用户的原话是"直接把影子和高光删了" —— 在草地这种有纹理的底上，投影读起来就是一圈脏边；
 * 20px 高的框上也读不出什么"玻璃感"。删的是<b>参数本身</b>，不是"默认设成 0"：
 * 留着能调、却一眼看不出差别的键，就是"改了没反应"的陷阱。
 * @param nameColor      物品名颜色
 * @param enterMs        入场动画时长
 * @param bumpMs         数字跳动时长
 * @param enterEnabled   入场动画开关
 * @param bumpEnabled    数字跳动开关
 * @param glowPulseEnabled 稀有度微光呼吸开关
 */
public record StyleModel(int cornerRadius,
                         int paddingH,
                         int paddingV,
                         int gap,
                         int iconSize,
                         int barWidth,
                         int barInsetY,
                         int fillTop,
                         int fillBottom,
                         int border,
                         int glowAlpha,
                         int nameColor,
                         long enterMs,
                         long bumpMs,
                         boolean enterEnabled,
                         boolean bumpEnabled,
                         boolean glowPulseEnabled) {

    public static StyleModel defaults() {
        return new StyleModel(
                4, 4, 3, 3, 16, 4, 1,
                0xD1262B38, 0xDB161A22, 0x2EFFFFFF,
                46,
                0xF0EBEFF6,
                560L, 300L, true, true, true);
    }

    /** 主题里的数值全部过一遍夹逼：玩家手写的 JSON 不该能把渲染打崩。 */
    public StyleModel sanitized() {
        return new StyleModel(
                Math.max(0, cornerRadius),
                Math.max(2, paddingH),
                Math.max(2, paddingV),
                Math.max(0, gap),
                Math.max(8, Math.min(64, iconSize)),
                Math.max(1, Math.min(24, barWidth)),
                Math.max(0, Math.min(16, barInsetY)),
                fillTop, fillBottom, border,
                Math.max(0, Math.min(255, glowAlpha)),
                nameColor,
                Math.max(0, enterMs),
                Math.max(0, bumpMs),
                enterEnabled, bumpEnabled, glowPulseEnabled);
    }

    // iconSize() 是 record 自带的存取器，不要再定义一遍
    /** 三个框的统一高度。图标装得下，名字也就装得下。 */
    public float boxHeight() {
        return iconSize() + paddingV * 2f;
    }

    /** 从 JSON 文本解析，缺键补默认，坏文本回退整套默认——主题坏了不该拖垮拾取提示。 */
    public static StyleModel parse(@Nullable String json) {
        if (json == null || json.isBlank()) return defaults();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject geo = obj(root, "geometry");
            JsonObject mat = obj(root, "material");
            JsonObject tex = obj(root, "text");
            JsonObject anim = obj(root, "animation");
            return new StyleModel(
                    i(geo, "cornerRadius", 4),
                    i(geo, "paddingH", 4),
                    i(geo, "paddingV", 3),
                    i(geo, "gap", 3),
                    i(geo, "iconSize", 16),
                    i(geo, "barWidth", 4),
                    i(geo, "barInsetY", 1),
                    color(mat, "fillTop", 0xD1262B38),
                    color(mat, "fillBottom", 0xDB161A22),
                    color(mat, "border", 0x2EFFFFFF),
                    i(mat, "glowAlpha", 46),
                    color(tex, "nameColor", 0xF0EBEFF6),
                    i(anim, "enterMs", 560),
                    i(anim, "bumpMs", 300),
                    b(anim, "enterEnabled", true),
                    b(anim, "bumpEnabled", true),
                    b(anim, "glowPulseEnabled", true)).sanitized();
        } catch (Exception e) {
            return defaults();
        }
    }

    private static JsonObject obj(JsonObject root, String name) {
        return root.has(name) && root.get(name).isJsonObject()
                ? root.getAsJsonObject(name) : new JsonObject();
    }

    private static int i(JsonObject o, String key, int def) {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }

    private static float f(JsonObject o, String key, float def) {
        return o.has(key) ? o.get(key).getAsFloat() : def;
    }

    private static boolean b(JsonObject o, String key, boolean def) {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }

    /** {@code #RRGGBB} 或 {@code #RRGGBBAA} → ARGB int。不带 alpha 的补 FF。 */
    private static int color(JsonObject o, String key, int def) {
        if (!o.has(key)) return def;
        String s = o.get(key).getAsString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            long v = Long.parseLong(s, 16);
            if (s.length() == 6) return (int) (v | 0xFF000000L);
            if (s.length() == 8) {
                // 网页习惯 RRGGBBAA：alpha 在最后一个字节，内部统一转 ARGB
                long a = v & 0xFFL;
                long rgb = v >>> 8;
                return (int) ((a << 24) | rgb);
            }
        } catch (NumberFormatException ignored) {
        }
        return def;
    }
}

package com.niuqu.pickupcard.style;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

/**
 * 卡面样式的唯一真源：从 {@code assets/pickupcard/styles/<name>.json} 读入的参数集。
 * 颜色一律 {@code #RRGGBBAA}，缺哪个键就用默认值——主题文件永远允许只写想改的几行。
 * <p>
 * 【为什么不是 Forge config】外观有且只有一个真源（design.md §8），资源包可以整体覆盖
 * 它；塞进 config 就成了第二真源。数值默认值与 docs/art/mockup.html 的 CSS 变量同步，
 * 改外观的正确姿势：改 mockup 看效果 → 同步这里和 default.json。
 *
 * @param cornerRadius   圆角半径（px）
 * @param paddingH       水平内边距
 * @param paddingV       垂直内边距
 * @param gap            卡与卡的间距
 * @param iconScale      物品图标倍率（原版物品是 16px，2 = 32px）
 * @param fillTop        卡面渐变上端（玻璃拟态的深色半透明）
 * @param fillBottom     卡面渐变下端
 * @param border         描边色
 * @param highlight      顶部高光线颜色
 * @param shadowAlpha    投影不透明度（0 = 不画投影）
 * @param shadowOffsetY  投影下移量
 * @param glowAlpha      稀有度微光不透明度（0 = 不画）
 * @param nameColor      物品名颜色
 * @param newBadgeBg     NEW 角标底色
 * @param newBadgeText   NEW 角标文字色
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
                         float iconScale,
                         int fillTop,
                         int fillBottom,
                         int border,
                         int highlight,
                         int shadowAlpha,
                         int shadowOffsetY,
                         int glowAlpha,
                         int nameColor,
                         int newBadgeBg,
                         int newBadgeText,
                         long enterMs,
                         long bumpMs,
                         boolean enterEnabled,
                         boolean bumpEnabled,
                         boolean glowPulseEnabled,
                         float badgeScale,
                         float namePillScale,
                         float endDotScale,
                         float crescentScale,
                         int borderInset) {

    public static StyleModel defaults() {
        return new StyleModel(
                999, 12, 7, 8, 2.0f,
                0xD1262B38, 0xDB161A22, 0x2EFFFFFF, 0x3CFFFFFF,
                90, 3, 46,
                0xF0EBEFF6,
                0xFF55EBFF, 0xFF0D1117,
                320L, 300L, true, true, true,
                1.9f, 0.62f, 0.40f, 0.52f, 3);
    }

    /** 主题里的数值全部过一遍夹逼：玩家手写的 JSON 不该能把渲染打崩。 */
    public StyleModel sanitized() {
        return new StyleModel(
                Math.max(0, cornerRadius),
                Math.max(2, paddingH),
                Math.max(2, paddingV),
                Math.max(0, gap),
                Math.max(1f, Math.min(4f, iconScale)),
                fillTop, fillBottom, border, highlight,
                Math.max(0, Math.min(255, shadowAlpha)),
                Math.max(0, Math.min(16, shadowOffsetY)),
                Math.max(0, Math.min(255, glowAlpha)),
                nameColor, newBadgeBg, newBadgeText,
                Math.max(0, enterMs),
                Math.max(0, bumpMs),
                enterEnabled, bumpEnabled, glowPulseEnabled,
                Math.max(1f, Math.min(3f, badgeScale)),
                Math.max(0.4f, Math.min(0.9f, namePillScale)),
                Math.max(0.2f, Math.min(0.8f, endDotScale)),
                Math.max(0f, Math.min(1.2f, crescentScale)),
                Math.max(1, Math.min(8, borderInset)));
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
                    i(geo, "cornerRadius", 999),
                    i(geo, "paddingH", 12),
                    i(geo, "paddingV", 7),
                    i(geo, "gap", 8),
                    f(geo, "iconScale", 2.0f),
                    color(mat, "fillTop", 0xD1262B38),
                    color(mat, "fillBottom", 0xDB161A22),
                    color(mat, "border", 0x2EFFFFFF),
                    color(mat, "highlight", 0x3CFFFFFF),
                    i(mat, "shadowAlpha", 90),
                    i(mat, "shadowOffsetY", 3),
                    i(mat, "glowAlpha", 46),
                    color(tex, "nameColor", 0xF0EBEFF6),
                    color(tex, "newBadgeBg", 0xFF55EBFF),
                    color(tex, "newBadgeText", 0xFF0D1117),
                    i(anim, "enterMs", 320),
                    i(anim, "bumpMs", 300),
                    b(anim, "enterEnabled", true),
                    b(anim, "bumpEnabled", true),
                    b(anim, "glowPulseEnabled", true),
                    f(geo, "badgeScale", 1.9f),
                    f(geo, "namePillScale", 0.62f),
                    f(geo, "endDotScale", 0.40f),
                    f(geo, "crescentScale", 0.52f),
                    i(geo, "borderInset", 3)).sanitized();
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

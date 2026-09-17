package com.niuqu.pickupcard.style;

/**
 * 主题：一套完整设计（颜色 + 全部默认值）。
 *
 * <p>【它是"默认值"不是"设置"】玩家的个人改动（{@link StyleOverrides}）叠在主题之上，
 * 只覆盖改过的项 —— 所以换主题不会被个人设置卡住，个人设置也不会把主题弄丢。
 *
 * <p>路径放在这里而不是渲染层：主题是一份数据，值本身不该知道 Minecraft 的存在。
 */
public enum Theme {

    /** 深色（默认）。 */
    DARK("styles/default.json"),
    /** 浅色。强调色压深过一档，否则黄/青会糊在白底上。 */
    LIGHT("styles/light.json");

    private final String assetPath;

    Theme(String assetPath) {
        this.assetPath = assetPath;
    }

    /** 相对 {@code assets/pickupcard/} 的资源路径。 */
    public String assetPath() {
        return assetPath;
    }
}

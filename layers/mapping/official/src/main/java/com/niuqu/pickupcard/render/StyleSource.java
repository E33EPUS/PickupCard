package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * 主题文件的读取与热重读。
 * <p>
 * 【为什么单独成类】它是渲染层里唯一碰资源系统的地方。把它摘出去之后，
 * {@link CardStage} 与 painter 都只认 {@link StyleModel} 这个值，测试与替换都不需要
 * 一个跑着的 Minecraft —— 上一版把这段 try/catch 塞在 436 行的渲染器末尾，
 * 结果是"主题读坏了"与"画错了"混在同一个类的日志里，分不出是谁的锅。
 * <p>
 * 【坏了不抛】主题是玩家可以手改的文件，写坏了不该让拾取提示整个消失 ——
 * 解析失败一律回退整套默认值。
 */
public final class StyleSource {

    private static final ResourceLocation PATH =
            new ResourceLocation("pickupcard", "styles/default.json");

    /** 重读间隔：改 JSON 一秒内生效，又不用每帧都去问资源管理器。 */
    private static final long RECHECK_MS = 1_000L;

    private StyleModel cached;
    private long nextCheckAt;

    /** 拿当前主题。未到重读点时直接返回缓存。 */
    public StyleModel current(long now) {
        if (cached != null && now < nextCheckAt) {
            return cached;
        }
        nextCheckAt = now + RECHECK_MS;
        cached = read().sanitized();
        return cached;
    }

    /** 强制下次访问时重读（换世界、资源包重载后调用）。 */
    public void invalidate() {
        cached = null;
        nextCheckAt = 0L;
    }

    private static StyleModel read() {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(PATH);
            if (resource.isEmpty()) {
                return StyleModel.defaults();
            }
            try (var in = resource.get().open()) {
                return StyleModel.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            return StyleModel.defaults();
        }
    }
}

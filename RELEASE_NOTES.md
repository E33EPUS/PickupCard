# Release Notes

<!--
发版约定：正文 = 本文件里的版本段。
- 中文摘要在前，英文块放在【段尾】（商店 changelog 取段尾那一块）
- 段标题必须是 `## vX.Y.Z`（带 v），与 git tag 一致
-->

## v0.2.1

首个自绘版本：**不再需要 ApricityUI**，零必需依赖，纯客户端 —— 服务端不用装。

捡起任何东西都会在 HUD 上弹出一张卡，物品和经验球都算。同一样东西连着捡会并成一张，
数字滚上去；卡片从一条竖条后面滑出来，退场是淡出。四档稀有度各有一套强调色，
主题 JSON 可以整套换掉（几何、材质、文字、颜色、动画时长都在里面，资源包就能覆盖）。

这一版新增**过滤页**：黑名单 / 白名单 / 静音名单，每条支持物品 `minecraft:cobblestone`、
标签 `#forge:ores`、整个 mod `@modid`。这三张表此前只能手改配置文件。

**不需要任何前置模组。**

First self-drawn release: **ApricityUI is no longer required** — zero required
dependencies, client-side only, no server install needed.

Every pickup pops a card on your HUD, items and XP alike. Grabbing the same thing
again merges into the existing card with a rolling count; cards slide out from
behind a bar and fade on exit, with an accent colour per rarity tier. A theme JSON
carries the geometry, materials, text colours and animation timings, so a resource
pack can restyle the whole thing.

This version adds the **filter page**: a blacklist, a whitelist and a mute list,
each accepting item ids (`minecraft:cobblestone`), tags (`#forge:ores`) or whole
mods (`@modid`). Those three lists used to be config-file only.

**No dependencies required.**

## v0.1.0

首个版本：拾取卡片提示的完整形态。

纯客户端 —— 服务端不用装。四档稀有度卡面（木牌 / 铜牌 / 蓝银 / 暗紫鎏金），
两秒内连捡同类物品合并成一张卡（数字滚动），本局首次拾取的物品带 NEW 角标。
界面由 ApricityUI 渲染，卡面与动画用 CSS 写成，改外观不用重编译。

**需要 ApricityUI 1.2.0+ 作为前置。**

First release. Client-side only, so no server install needed. Four rarity card
styles, merge-on-repeat pickups with a rolling count, and a NEW tag the first
time you ever grab an item in a session. The card look and animation are written
in CSS and rendered by ApricityUI, so restyling needs no rebuild.

**Requires ApricityUI 1.2.0+ as a dependency.**

# Release Notes

<!--
发版约定：正文 = 本文件里的版本段。
- 中文摘要在前，英文块放在【段尾】（商店 changelog 取段尾那一块）
- 段标题必须是 `## vX.Y.Z`（带 v），与 git tag 一致
-->

## v0.2.3

0.2.2 验收打回五连修：**切页不再有动画**；配置行文字与悬停带、小节头刺全部对齐；
预览静止卡在面板里居中、点它不再播动画；**位置编辑场全屏幕随便拖**（拖到哪儿就是
哪儿，不再有拖不动的边角），示例堆和区域框跟着「水平对齐」走。

---

Five quick fixes from 0.2.2 acceptance: no more page-switch animation; config rows,
hover bands and header ticks all line up; the static preview card is centered and
plays no animation; the anchor editor drags anywhere on screen with alignment-aware
samples and region brackets.

## v0.2.2

**卡片搬家了：新卡现在贴着物品栏上缘出现，旧的向上顶。** 常见分辨率终于放得下
同屏上限的张数 —— 854×480 窗口（最常见的 427×240 画布）实测 5 张全部原尺寸在屏，
放不下的拾取改排队等位子，不再无声消失。"动画结束图标回弹"（退场播完整摞卡突然
放大一圈）也根治了：缩放和卡宽现在有 340ms 的过渡。

配置界面这一版大整了一次排版：行距恒定、页内分小节、每页有「恢复本页默认」、
颜色改成色块点选（第一档是「跟随主题」）、预览回到"非动画页一张静止完整卡、
动画页自动演"的分工。消失方式有了三档（淡出 / 火车退回 / 拉幕收拢），可以和
入场方式自由组合；经验卡的微光会呼吸了；淡出最后一帧图标闪回的毛病修了。

纯客户端，服务端不用装；不依赖 ApricityUI。从 0.2.1 直接覆盖即可，
配置文件兼容（旧的贴边/竖条位置键会被忽略，锚点在配置界面里拖）。

---

Cards now stack upward from a fixed line just above the hotbar. On the common
427x240 canvas all five cards fit at full size; pickups that do not fit queue up
instead of vanishing, and the end-of-animation scale pop is gone (340ms transitions
on scale and card width). The config screen got a layout pass: constant row rhythm,
section headers, a per-page "restore defaults" button, color swatches with
"follow theme" first, and a per-page preview (static card elsewhere, live stage on
the animation page). Exits now have three modes freely combinable with entrances,
the rarity glow breathes, and the last-frame icon flash is fixed. Client-side only,
no hard dependencies; drop-in upgrade from 0.2.1.

## v0.2.1

首个自绘版本：**不再需要 ApricityUI**，零必需依赖，纯客户端 —— 服务端不用装。

捡起任何东西都会在 HUD 上弹出一张卡，物品和经验球都算。同一样东西连着捡会并成一张，
数字滚上去；卡片从一条竖条后面滑出来，退场是淡出。四档稀有度各有一套强调色，
主题 JSON 可以整套换掉（几何、材质、文字、颜色、动画时长都在里面，资源包就能覆盖）。

这一版新增**过滤页**：黑名单 / 白名单 / 静音名单，每条支持物品 `minecraft:cobblestone`、
标签 `#forge:ores`、整个 mod `@modid`。这三张表此前只能手改配置文件。

**卡片挪到了物品栏右边那条区域**（物品栏与屏幕右缘之间），并一路下到屏幕底、与物品栏同层。
**同一样东西再次拾起**现在真的会动：整张卡鼓一下、数字从旧值滚上去。

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

Cards now sit in the region between the hotbar and the right edge of the screen,
dropping to the bottom so they share the hotbar's row. Picking the same item up
again finally animates: the whole card pulses and the count rolls from the old
value to the new one.

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

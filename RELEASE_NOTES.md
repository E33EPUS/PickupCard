# Release Notes

<!--
发版约定：正文 = 本文件里的版本段。
- 中文摘要在前，英文块放在【段尾】（商店 changelog 取段尾那一块）
- 段标题必须是 `## vX.Y.Z`（带 v），与 git tag 一致
-->

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

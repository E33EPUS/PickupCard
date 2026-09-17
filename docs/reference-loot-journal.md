# 参照物：Loot Journal: Pickup Notifier（同赛道最成熟的那一版）

用户 2026-09-17 让我们"学它的思路架构"。本文件只记录**读过源码之后确认的事实**，
以及我们从它身上搬了什么、明确没搬什么 —— 免得下次再从 jar 反编译一遍。

- 源码：`C:\Users\NIUQU\AppData\Local\Temp\lj`（1.21.1 的 6.2.1 clone，**只读参照，不当依赖**）
- 与我们同版本的产物：`D:\Myworld\.minecraft\versions\IA\mods\loot_journal-forge-1.20.1-6.1.2.jar`
  （6.1.2 是 1.20.1 那一支；clone 是 1.21.1 —— 看架构够用，看 NBT/组件细节要留意版本差）
- 规模：81 个 Java 文件；多加载器（`common/` + `fabric/` + `neoforge/` + `buildSrc`）

## 它的骨架（三条主脉）

1. **主题是数据，不是代码**：`client/registry/*`（`ThemeRegistry`/`ResourceRegistry`/
   `ResourceKind`/`PickupResourceManager`/`CompactDispatchCodec`）→ 主题由资源包加载，
   带 codec。主题的选择靠 **匹配 DSL**：`client/themes/match/` 13 个类
   （`IsItemMatch`/`ItemTagMatch`/`ModMatch`/`RarityMatch`/`IsXPMatch`/`ItemStackMatch` +
   `AllOf/AnyOf/NoneOf/Always`）。外观由可替换部件拼出：面板
   （`FillPanel`/`NineSlicedPanel`/`NonePanel`）、横幅（`TextureBanner`/`NoneBanner`）、
   图标（`SimpleIcon`/`PickupIcon`）、图标特效（`RayGlowEffect`/`NoneEffect`）+ 变量系统
   （`ColorVariable`/`BooleanVariable`/`Var`/`VarCodec`）。
2. **排版是 token 列表**：`client/renderer/layout/tokens/*`（`IconToken`/`NameToken`/
   `CountToken`/`GapToken`/`TotalToken`，统一接口 `LayoutToken`）+ `LayoutParser`
   （解析配置里的 `ELEMENT_ORDER` 字符串）+ `PickupLayout`/`LayoutResult`/`LayoutEntry`。
   "卡里放什么、什么顺序"是**数据**。
3. **拾取是带类型的事件对象**：`PickupEvent` 把 `renderIcon(GuiGraphics, PickupRenderer)`、
   `bind(PickupStyle)`、`maybeMerge(other)`、`supportsTotalCount()`、`displayName()`、
   `count()`、`total()` 交给实现（`ItemPickupEvent`/`XpPickupEvent`/`OverflowPickupEvent`）。
   只有**一个 mixin**（`MixinClientPacketListener`）。

## 它没有的东西（我们的 HudSafeZone 是它没有的）

全仓库与"避让 HUD"有关的代码只有 `ScreenAnchor` 里两行 `getGuiScaledHeight() - offset
- entryHeight()`。**它不躲快捷栏、不躲计分板、不躲状态效果图标、不做底部安全区计算** ——
它的答案是一整套玩家旋钮：`SCREEN_ANCHOR`（四个角）+ `GROWTH_DIRECTION` +
`ANCHOR_X_OFFSET`/`ANCHOR_Y_OFFSET` + `SCALE`（0.1–3.0，界面按百分比显示）。

## 它暴露的配置面（`config/Config.java`，供对照）

`ENABLE_LOOT_JOURNAL`、`SHOW_ITEM_PICKUPS`/`SHOW_XP_PICKUPS`/`SHOW_OVERFLOW_PICKUPS`、
`ABBREVIATE_NUMBERS`、`MERGE_MODE`、`STACKING_MODE`(`FIXED_SLOTS`/`SMOOTH_FLOW`)、
`MAX_NAME_WIDTH`、`DISPLAY_TIME`、`DISPLAY_CAPACITY`(默认 9, 1..64)、`QUEUE_SIZE`(默认 9, 0..256)、
`THEME`、`SCREEN_ANCHOR`、`GROWTH_DIRECTION`、`ANCHOR_X_OFFSET`、`ANCHOR_Y_OFFSET`、
`SEPARATION`、`SCALE`、`ELEMENT_ORDER`、`ELEMENT_PADDING_{LEFT,RIGHT,TOP,BOTTOM}`、
`FADE_IN_TIME`/`FADE_OUT_TIME`/`FADE_IN_EASING`/`FADE_OUT_EASING`、
`PULSE_STRENGTH`/`PULSE_TIME`/`PULSE_PEAK`/`PULSE_EASE_IN`/`PULSE_EASE_OUT`、
`RAY_GLOW_ENABLED`、`TRACK_ITEM_PICKUPS`/`TRACK_XP_PICKUPS`、`ENABLE_PLAYER_FILTERING`/
`PLAYER_WHITELIST`、`ENABLE_SOUNDS`/`SOUND_ID`。
**配置界面用 YACL**（`dev.isxander.yacl3`）+ Fabric 侧 ModMenu —— 它不自绘配置界面。

## 溢出卡（`OverflowPickupEvent`，值得抄的那一招）

排队也满了之后的那些拾取不再各弹一张，而是并进**一张「还有 N 项」的卡**：
`renderIcon` 里按 `max(0.2s, 1.0 - 0.05 * 张数)` 的间隔轮播其中的图标
（`stacks.get((int)(renderer.timeInSeconds() / interval % stacks.size()))`）。
`ItemCountResolver` 还会**递归数容器里的东西**（潜影盒）。

## 我们搬了什么 / 没搬什么

- ✅ **搬**：`MergeMode` 四档谓词（我们原来的"合并窗口（毫秒）"已删，见
  `shared/.../notice/MergeMode.java`）。
- ✅ **搬（已批准，待做）**：`DISPLAY_CAPACITY` + `QUEUE_SIZE` + 溢出卡；`SCALE`（卡片缩放，
  与"屏幕放不下就丢最老的"配合）。
- 🕓 **记为方向、本轮不做**：主题数据化（资源包 + 匹配 DSL + 可替换部件）。
- ❌ **不搬**：`ELEMENT_ORDER` 字符串（我们的 token 管线已经能表达，多个键反而是第二份真源）；
  缓动曲线做成枚举（我们的节奏是定死的，多一个旋钮就多一种"手感不对"）；
  YACL（我们选了自绘三列 + 预览，代价自己扛）。

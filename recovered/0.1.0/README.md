# 还原出来的 0.1.0（= 用户说的"以前的效果"）

## 这份东西从哪来

2026-09-17：用户指出 `D:\Myworld\.minecraft\versions\1.20.1-main\mods\pickupcard-Forge-1.20.1-0.1.0.jar`
还在。那是"以前那版"的**编译产物**——源码早就删了（`D:\pickupnotice` 全盘搜过，无备份无提交）。

- `original-jar.zip` —— 原 jar 原件（不要动，这是唯一的原始证据）
- `src/` —— 用 Vineflower 反编译出来的 Java 源码（44 个文件 / 2742 行，mojmap）
- `assets/` —— jar 里的资源：4 张手绘卡面（`textures/gui/card_*.png`，640×184）+ `sdf_rect` 着色器 + `lang/zh_cn.json`（**连界面文案都在**）

## 它长什么样（= 要还原的目标）

| 包 | 内容 |
| --- | --- |
| `configui/` | **左侧 4 个分类标签**（general / animation / layout / appearance）+ **右侧头部实时预览**（`CardPreview` 170×40）+ 滚动列表 + 每项悬停提示 + 圆角面板（`UiPalette`）+ 四种控件（Slider / Cycle / Toggle / Text） |
| `anim/` | `Timeline` + `Motion` + `Easing` + `AnimationSpec` + `AnimationStyle`（动画是**独立的规格对象**，不是散在渲染里的魔数） |
| `layout/` | `NoticeLayout` + `LayoutSpec` + `ScreenAnchor` + **`GrowthDirection`**（堆叠往哪长是可配的） |
| `queue/` | `NoticeQueue` + `NoticeMerger` + **`StackSmoother`**（"旧的被顶上去"的平滑在这里） |
| `render/` | `CardNoticeRenderer` + `CardSkin`（烘好的皮肤）+ `NoticeMetrics` + `PickupOverlay` |


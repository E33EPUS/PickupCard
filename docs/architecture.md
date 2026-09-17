# 架构

单分支多目标：**代码按"能被哪些目标共用"分层，工程按"发到哪个加载器"分目录。**
声明全部在 `versions/*.json`，由 `tools/verify_targets.py` 与
`gradle/pickupcard-layers.gradle` 两处读同一份声明。

```
gradle.properties          ← 仓库身份唯一来源（mod_version / mod_id / …）
versions/targets.json      ← 有哪些目标、各挂哪些层、能不能发
versions/layers.json       ← 层的定义（每层是一个谓词）
gradle/pickupcard-layers.gradle  ← 按声明把 shared/ 与层接进源集
shared/                    ← 与平台、映射、加载器都无关的纯逻辑
layers/mapping/official/   ← 写给 Mojang 官方名的那一份
platforms/<目标>/          ← 各自的 Gradle 工程（注入配置、事件总线、入口）
tools/verify_targets.py    ← 结构守卫（CI 第一道闸）
```

## 代码放哪一层

| 想加的代码 | 放哪 | 判据 |
| --- | --- | --- |
| 队列规则、合并窗口、数量格式 | `shared/` | 不 import `net.minecraft` 就能编过 |
| Mixin 进原版类、读写 ItemStack | `layers/mapping/official/` | 用到官方名（Yarn 里名字不同） |
| 配置定义、事件总线、mod 入口 | `platforms/<目标>/` | 绑加载器，且目前量少不够成层 |

层名对应的是**类别**而不是具体版本：`official` 是所有用官方名的目标共用的，
`1.21+` 是所有 1.21 以上的目标共用的。所以 1.21.1 与将来的 1.21.x 都挂 `1.21+`。

## 为什么拾取逻辑与物品类型分开

`NoticeQueue` 的载荷是泛型 `T`，队列本身只认 `key` 与 `lookKey` 两个字符串。这样：

- 合并规则的**全部测试都不需要启动游戏** —— 载荷用 `String` 就够了（见 `NoticeQueueTest`）；
- 物品类型只出现在映射层，将来 Fabric 那份 Yarn 名副本不必重写队列；
- "队列说该有哪些卡"与"卡片怎么画"彻底解耦。

## 渲染层：画法是一个插槽

渲染是需求变得最快的一层（换风格、换引擎、加特效），而拾取管线几乎不动。所以"怎么画"
被收口成一个接口，其余各管一件事——上一版把这五件事连同绘制全塞进一个 436 行的类里，
改任意一处都要先读懂全部：

| 问题 | 答案在哪 | 性质 |
| --- | --- | --- |
| 一张卡怎么画 | `render/CardPainter` | 插槽，可整体替换 |
| 卡多大 | `render/CardMetrics` | 要字体，所以量文字宽度 |
| 卡在哪 | `shared/layout/StackLayout` | 纯数学，有单测 |
| 主题从哪来 | `render/StyleSource` | 懒加载 + 一秒热重读 |
| 动画进度 | `shared/style/CardTimeline` + `render/CardCanvas` | 纯函数 + 每帧上下文 |
| 事件 → 屏上的卡 | `render/CardStage` | 只调度，**不画一笔** |

骨架期挂的是 `render/painter/BaselineCardPainter`：一块卡面 + 图标 + 名字 + 数量。
它故意长得朴素 —— 职责只是证明管线是通的，不带任何"已经定下来的风格"。

**渲染方案本身尚未定案**，取舍与推荐见 [`decision-rendering.md`](decision-rendering.md)。

## 已知约束

- **纯客户端**：任何原版/别的 mod 的服务器都能用。代价是拿不到只有服务端知道的信息
  （物品实体上的改名与 NBT 例外——注入点在实体移除之前，能捞到真身）。
- **卡宽跟名字走**：`CardMetrics` 现量现算，所以画法必须适配可变宽度。
  "定宽贴图横拉"是上一版走不通的路（见 `decision-rendering.md`）。
- **同屏上限由账本管**：`maxOnScreen` 满员时淘汰最久没被碰过的那张，渲染层不参与取舍。

## 渲染路径：哪条是生产、哪条是回退、哪条是死的

画一张卡要过几套代码，是接手时最容易走错的地方（"有个 SDF 还有个 NanoVG，到底留哪个？"）。
所以列成一张表，**改卡面前先读它**：

| 路径 | 状态 | 在哪 | 改卡面时要不要动 |
| --- | --- | --- | --- |
| 原版两趟（物品图标 + 中文文字） | **活的，不可替代** | `TrioCardPainter.body()` / `contentOnly()` | 要（文字用哪段、图标画多大只在这里） |
| DOM 草稿 | **活的，视觉真源** | `design/theme.css` + `measure.html` | 要，**而且先动它** —— 它是真源，游戏跟它 |
| NanoVG 外壳 | **生产** | `platforms/*/render/nvg/NvgCardPainter.java` | 要 |
| SDF 整卡回退 | 活的，**第二份实现** | `TrioCardPainter` 的 `paint()`+`chrome()`+`barShapes()` | 要（NanoVG native 起不来时走它） |
| SDF 形状层 | **活的、承重** | `render/shape/ShapeBatch` + `assets/*/shaders/core/gui_shape.*` | 要：**两条路径的影子与微光都靠它**；harness 的滑条/辅助线也用它 |
| ~~`BaselineCardPainter`~~ | **已删**（2026-09-17） | — | 不 |

### 这张表存在的理由（一次真实的账）

同一条卡几何有 **3 个实现**（DOM / NanoVG / SDF 回退），于是每次改卡面都要改三处。
2026-09-17 修"内容穿透竖条"那个 bug 时，裁剪补进了 3 条绘制路径、**漏了第 4 处**
（NanoVG 的影子批没有窗口），用户第二遍才报回来；同一轮还发现 SDF 回退里**竖条被自己的
裁剪吃掉**。**同一个几何写 N 遍，就一定会有 N-1 遍是错的** —— 后面要收口的话，方向是把
"三个框"抽成一份纯数据（bar/icon/info 的 x/w/r），两个后端各自只负责"把盒子画出来"，
那时 `compare.py` 那条"两边必须逐项对齐"的税才会消失。

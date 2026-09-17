# 交接：从这里继续

> 给"压缩上下文之后重开的我"看。读完这份 + `docs/plan-ui.md` + `docs/decision-rendering.md`，
> 就能接着做，不需要重读历史对话。

## 一句话现状

三段式卡片（`稀有度竖条 ｜ 图标格 ｜ 名字+数量框`）已能在游戏里画出来，带入场动画与
SDF 柔和阴影；参数由 `design/tokens.css` 单源生成；有一个像素对照工具但输入还不够干净。

## 跑起来 / 常用命令

```bash
cd platforms/1.20.1-forge

# 编译 + 单测（55 个用例）
./gradlew build

# 无人值守截图：开屏 → 注入样例 → 每页拍两张（展开中途 + 就位）→ 退出
./gradlew runClient -PharnessAuto=shot
#   产物在 run/screenshots/pickupcard-harness-p{1,2,3}[-mid]
#   页1 = 五档强调色+经验   页2 = 内容边界   页3 = 形状层 spike

# 交互调试（自己进去按）
./gradlew runClient        # F9 或 /pickupcarddev
#   A=推全部样例 1-9=单张 C=清空 G=辅助线 B=背景 F=读数

# 主题：改 design/tokens.css 后必须重新生成，否则游戏里读的是旧 JSON
python tools/css_tokens.py            # 生成
python tools/css_tokens.py --check    # 只校验（CI 用）

# 像素对照（v1，见下方已知缺陷）
python design/compare.py 设计图.png 游戏截图.png
```

## 参数的真源在哪（重要）

```
design/tokens.css   ← 唯一真源。所有 --pc-* 参数只在这里定义，HTML 和 Java 都读它
        │
        └─ tools/css_tokens.py ─▶ assets/pickupcard/styles/default.json（深色）
                               └▶ assets/pickupcard/styles/light.json（浅色）
                                        └─▶ Java: StyleModel
```

**不要手改生成出来的 JSON**——下次跑脚本就被覆盖。加新参数必须同时登记进
`tools/css_tokens.py` 的 `SCHEMA`，否则抽取器直接报错退出（这是刻意的，见下）。

## 已知缺陷（都没修完，别当成已完成）

### 1. 形状层"本批第一次 draw 不透明"

**症状**：每批**第一次**提交的形状渲染成不透明（顶点 alpha 被丢），第二次之后正常。
触发条件是"这一次 draw 之前发生过一次别的 RenderType 的绘制"。卡片页每张卡的第一个形状
是阴影，所以五张阴影一度全是黑带。

**已排除**：SDF 软化宽度、未冲刷的原版缓冲、直角/圆角（顺序实验推翻了）、
`aastep` 里 `smoothstep(0,0,dis)` 的 0/0（顺手加了 `max(fw, 0.5)` 下限，不是本症状的原因）。

**当前**：`ShapeBatch.warmUp()` 往屏幕外丢一个退化矩形，让坏掉的那次自己去坏。
**这是权宜之计，真修好后必须删掉**。

**下一步查法**：在 RENDER 层核 `RenderType.end()` 前后 `setupRenderState` /
`clearRenderState` 的配对与执行顺序，特别是非形状层 RenderType 的 `clearRenderState`
之后哪个 GL 状态没还原。

### 2. `design/compare.py` 检出不可靠

- `竖条左缘极差`：设计侧应为 0，实测 1.594（5 张里误判了 1 张的竖条）
- `条-卡体间距`：两边都 ≈0.01，因为**阴影把间隙填住了**，判据立刻触发

**根因不在算法，在输入**：喂进去的是"任意截图"，有阴影、辅助线、棋盘底。
**正确做法是控制测量环境**——让 harness 出一张纯色底、无辅助线、无阴影的"测量页"，
HTML 侧也出同口径的。**不要靠继续堆启发式规则**。

### 3. 还没做的功能

- `appearMode = CLIP`（可见范围展开）没有真机对照截图
- 超长名字截断（Q8 的 70% 屏宽上限）没有真机验收
- 退场动画、合并跳动
- 主题里的 `accent` 段已生成进 JSON，但 Java 的 `RarityAccent` 还在用硬编码色值

## 下一步（按优先级）

1. **建"测量页"**（harness 一张 + HTML 一张，纯几何、无装饰）→ `compare.py` 立刻可信
   → 把设计与实现的全部差异列成表给用户确认
2. 修 `ShapeBatch.warmUp()` 背后的真根因
3. CLIP 模式对照 / 超长名字验收 / 退场动画

## 方法论：这轮踩过、别重踩

- **同时改两个变量再下结论 = 没做实验。** 我曾判定"是 `u_soft` 的锅"，又判定"是 radius=0 的锅"，
  都被**顺序对调**那个受控实验推翻。真规律是"第一个 draw"。
- **测量会被调试叠加层污染。** 红色辅助线画在 slot 边界上，被"最右非背景像素"当成了卡片内容，
  一度让我以为动画完全没生效。**用辅助线做像素测量前先关掉它。**
- **换算系数要写清楚。** 游戏截图 854×480 是帧缓冲（÷2 得逻辑 px）；HTML 截图还叠了一层
  CSS 缩放（÷4）。我因此误读过一次。
- **Qwen VL 只管语义，不管几何。** 它读对了"文字被切成 ragon Egg"这种事实（正是隧道效果），
  但把"不重叠"说成"互相重叠"、5 个绿十字只看到 1 个。**数字的事一律数值扫描。**
- **静默失败最毒。** `css_tokens.py` 的"清单外报错退出"在写它的过程中就抓出了三个真 bug。

## 环境备注

- 本会话里 `hindsight_*` 工具**调不到**（不在工具表里），知识页建不了；`mcp_load`
  这条路也走不通（工具名对不上）。要建页得从别的 harness 走。
- 窗口大小会影响 MC 的 GUI 缩放，进而影响截图里卡片的像素尺寸——**测量脚本不要硬编码分辨率**。

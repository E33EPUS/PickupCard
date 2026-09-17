# 交接：从这里继续

> 给"压缩上下文之后重开的我"看。读完这份 + `docs/plan-ui.md` + `docs/decision-rendering.md`，
> 就能接着做，不需要重读历史对话。

## 一句话现状

三段式卡片（`稀有度竖条 ｜ 图标格 ｜ 名字+数量框`）能在游戏里画出来，带入场动画与 SDF 柔和阴影；
参数由 `design/tokens.css` 单源生成；**像素对照这条链路已经打通并且可信**（见下），
设计↔实现的**结构项现在全部一致**，差的地方都是内容项（字体）与几处已知未做。

## 跑起来 / 常用命令

```bash
cd platforms/1.20.1-forge

# 编译 + 单测（Java 55 个用例）
./gradlew build

# 无人值守截图：开屏 → 注入样例 → 每页拍两张 → 退出
./gradlew runClient -PharnessAuto=shot
#   产物在 run/screenshots/：
#     pickupcard-harness-p1[-mid]  五档强调色+经验   页2 = 内容边界
#     pickupcard-harness-spike[-mid] 形状层 spike
#     pickupcard-measure           测量页（纯黑底/无辅助线/无读数）← 对照用的就是这张
#   交互调试：./gradlew runClient 然后 F9 或 /pickupcarddev
#     A=推全部样例 1-9=单张 C=清空 G=辅助线 B=背景 F=读数

# 主题：改 design/tokens.css 后必须重新生成，否则游戏里读的是旧 JSON
python tools/css_tokens.py            # 生成
python tools/css_tokens.py --check    # 只校验（CI 用）

# 像素对照（三个入口，从下往上用）
python -m pytest design/test_measure.py -q          # 先证明"尺子"是准的
python design/shot.py design/measure.html --geometry # 设计侧测量页 + 浏览器自己的 DOM 矩形
python design/compare.py design/measure.png platforms/1.20.1-forge/run/screenshots/pickupcard-measure --expect 4
```

## 参数的真源在哪（重要）

```
design/tokens.css          ← 唯一真源。--pc-* 只在这里定义
        │                     design/measure.html 与 card.html 直接 <link> 它
        └─ tools/css_tokens.py ─▶ assets/pickupcard/styles/default.json（深色）
                               └▶ assets/pickupcard/styles/light.json（浅色）
                                        └─▶ Java: StyleModel
```

**不要手改生成出来的 JSON**——下次跑脚本就被覆盖。加新参数必须同时登记进
`tools/css_tokens.py` 的 `SCHEMA`，否则抽取器直接报错退出（刻意的）。

**`design/theme.css` 里不许再定义 `--pc-*`**。v1 两边各存一份、重名 18 个键，
抽取器只管 `tokens.css`，所以改了真源草稿页面纹丝不动 —— 这正是那套"清单外报错"
要防的病，只不过病人是设计稿自己。已修。

## 对照工具怎么才算可信（这一节是这轮的主要产出）

三层，各管一件事：

| 层 | 文件 | 它证明什么 |
| --- | --- | --- |
| 算法 | `design/measure.py` | 看图算数。纯函数，不读命令行不打印 |
| 算法对不对 | `design/test_measure.py` | 拿**已知答案的合成图**量，断言量回来就是真值；另有"像素扫描 vs 浏览器 DOM 矩形"的端到端自检 |
| 输入对不对 | `design/compare.py` | 纯底占比 < 40% 直接拒收；卡数不符直接拒收；只把结构项当客观门 |

**v1 的 bug 与它的错误诊断（都记在这里，别重犯）**：
v1 的"条-卡体间距"判据是"从竖条右缘往右，找第一个比竖条暗 75% 的像素"。
竖条比卡体**亮**，所以条件在第一个像素就不成立 → 指标**恒等于 1px**，与真实间距无关。
当时把它归因于"投影把间隙填住了" —— **那个诊断是错的**，间隙是纯背景时它照样返回 1。
真相是拿合成图测出来的（`python -m pytest design/test_measure.py`），不是看出来的。

**抗锯齿偏差的教训**：游戏里 5px 宽的竖条量出来是 4px，因为两侧各有一个 84% 覆盖的
抗锯齿像素被固定容差排掉了。而浏览器按整数 CSS px 布局、**根本没有抗锯齿**，
于是这个偏差两边不一样，"差得多"的旗子会插在一个纯粹由抗锯齿造成的差异上。
现在改成"像素更像强调色还是更像底色"，并且调色板分类要**一次算全**
（只比"离强调色比离底色近"的话，每个强调色都会把别的强调色的竖条也吞掉，4 张卡量出 16 张）。

## 已知缺陷 / 没做的事（都没修完，别当成已完成）

### 1. 形状层"本批第一次 draw 不透明"

**症状**：每批**第一次**提交的形状渲染成不透明（顶点 alpha 被丢），第二次之后正常。
触发条件是"这一次 draw 之前发生过一次别的 RenderType 的绘制"。

**已排除**：SDF 软化宽度、未冲刷的原版缓冲、直角/圆角（顺序实验推翻了）、
`aastep` 里 `smoothstep(0,0,dis)` 的 0/0。

**当前**：`ShapeBatch.warmUp()` 往屏幕外丢一个退化矩形，让坏掉的那次自己去坏。
**这是权宜之计，真修好后必须删掉。**

**下一步查法**：在 RENDER 层核 `RenderType.end()` 前后 `setupRenderState` /
`clearRenderState` 的配对与执行顺序，特别是非形状层 RenderType 的 `clearRenderState`
之后哪个 GL 状态没还原。

### 2. 设计稿 vs 实现：还差这几处（客观门之外）

| 差异 | 现状 |
| --- | --- |
| **顶部高光线** | 设计稿 `.box` 有一条 `--pc-highlight` 顶边高光；**Java 侧从来没画过**（`style.highlight()` 在 render 层零引用）。参数白生成了 |
| **经验卡的图标** | 设计稿用 `nether_star.png`；实现画一块 8×8 强调色方块（`TrioCardPainter` 里的占位）。设计意图是"星"，实现是"色块" |
| **强调色是硬编码的** | `RarityAccent` 里写死 `0xFFFFD83D` 等，与 `tokens.css` 的 `--pc-accent-*` 目前**值相同**但各存一份。改主题的强调色不会影响游戏 —— 第二真源 |
| **字体的语言不同** | 游戏客户端是 `en_us`（显示 Stone/Elytra/Beacon/Dragon Egg），草稿写的是中文名。卡宽的差异有一部分来自这里 |
| **`BaselineCardPainter` 是死代码** | 没有任何地方引用它，而且 `CardMetrics.ICON_PX * style.iconSize()` 是 `iconScale` 时代的写法（现在是像素，会算出 384px）。建议删掉 |
| 超长名字截断 | Q8 的 70% 屏宽上限没有真机验收 |
| 退场动画、合并跳动 | 没做 |
| `appearMode = CLIP` | 没有真机对照截图 |

### 3. 环境坑（踩过一次，别再踩）

- **全屏 + 2560×1600 时截图是坏的**：`Screenshot.grab` 出来的四张图互相不同、
  但都不含任何卡（guiScale=6）。窗口固定成 854×480（guiScale=2）就正常。
  `run/options.txt` 里现在是 `fullscreen:false`。**改窗口大小前先确认截图里真有卡。**
- 窗口大小会影响 GUI 缩放，进而影响卡片的像素尺寸 —— **测量脚本不要硬编码分辨率**。

## 下一步（按优先级）

1. 把上面那张差异表拿给用户逐条确认（尤其：高光线要不要真画、经验卡用什么图标、
   强调色要不要收进主题）。**这是唯一的主观验收，别人替不了。**
2. 修 `ShapeBatch.warmUp()` 背后的真根因
3. 退场动画 / 合并跳动 / CLIP 模式对照 / 超长名字验收

## 方法论：这轮踩过、别重踩

- **拿尺子去量真东西之前，先量一个我知道多长的东西。** v1 的指标恒等于 1px 却能骗过所有人
  （数字长得都很正常）。合成图一跑就现形。
- **别急着把锅扣在输入上。** "投影填住了间隙"听起来很合理，所以没人去查判据本身。
- **同时改两个变量再下结论 = 没做实验。** 我曾判定"是 `u_soft` 的锅"、又判定"是 radius=0 的锅"，
  都被**顺序对调**那个受控实验推翻。真规律是"第一个 draw"。
- **测量会被调试叠加层污染。** 红色辅助线画在 slot 边界上，被"最右非背景像素"当成了卡片内容。
  **用辅助线做像素测量前先关掉它。**
- **换算系数要写清楚。** 游戏截图是帧缓冲像素（本环境 guiScale=2，÷2 得逻辑 px）；
  HTML 截图还叠了一层设备像素比（`shot.py --dsf`）。
- **Qwen VL 只管语义，不管几何。** 它读对了"文字被切成 ragon Egg"这种事实，但把"不重叠"
  说成"互相重叠"、5 个绿十字只看到 1 个。**数字的事一律数值扫描。**
- **静默失败最毒。** `css_tokens.py` 的"清单外报错退出"在写它的过程中就抓出了三个真 bug；
  这一轮 `~bgmask[:, a:b].any(1)` 的括号坑也是当场被测出来的。

## 环境备注

- 本会话里 `hindsight_*` 工具**调不到**，知识页建不了；`mcp_load` 也走不通（工具名对不上）。
- 无头浏览器用系统 Edge（`shot.py` 会自动退回 `channel="msedge"`，因为 Playwright
  自带 chromium 没装、装它要联网）。

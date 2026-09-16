# UI 方案定案（2026-09-16 八问）

> 这份文档是 **UI 部分的决策正本**。八条都是用户拍板的，不是我的建议。
> 相关：`decision-rendering.md`（渲染引擎取舍）、`design.md`（美术参数）、`architecture.md`（分层）

## 一句话

**路线 B：程序化 SDF 自绘。参考 ModernUI 的"自己写渲染引擎"做法，只取所需、不做前置。
画法收口成插槽，参数真源在 CSS token，靠一个游戏内 harness 迭代。**

## 八条定案

| # | 问题 | 定案 | 关键约束 |
|---|---|---|---|
| 1 | 自研边界 | **形状层 + 自己的批处理器**，不要 ModernUI 的框架层（View 树/生命周期/UI 线程/Markflow/Resources） | 我们的是 5~6 张不响应点击的卡，View 树解决的问题我们一个都没有 |
| 2 | 视觉参数真源 | **CSS token 是持续真源**：HTML 顶部只用 `--pc-*` 写标量，薄脚本抽成 `styles/default.json`，Java 读同一份 | 只搬标量（颜色/半径/描边/内边距/间隙/时长/缓动名）；**几何与文本相关的一律不进转换**；harness 滑条只读不回写 |
| 3 | harness 形态 | **完整调试屏**：样例卡集 + 几何辅助线 + 动画时间控制 + 背景切换 + GUI scale 验证 + 只读滑条 + 截图导出 | dev-only（`FMLEnvironment.isDevelopment()`）门控；**不许长成第二个 UI 框架**（滑条用我们自己的形状层画＝吃狗粮）；**不许当第二真源** |
| 4 | 设计保真 | **限定 CSS 子集，清单外报错、绝不静默**；`tools/css_tokens.py` 遇到清单外属性直接失败退出 | 静默失败是最毒的（上一版 AUI 的 `radial-gradient` 不解析且不报错） |
| 5 | 实施顺序 | **harness 壳 → 形状层 → 卡片画法 → 美术迭代**；`BaselineCardPainter` 从占位升格为**对照组** | 先建测试台，材料用最糙的；需求从工具里长出来，不先设计 API |
| 6 | 批处理批什么 | **统一入口 + 同 uniform 自动合并 + `stats()`**；不碰自定义顶点属性 | **坐标一律屏幕绝对值**；**形状层整体先于内容层提交**（`MultiBufferSource` 按 RenderType 分组，不是按插入序） |
| 7 | 谁判"好看" | **客观门 + 候选制 + 3 轮断路器** | 客观项（错位=0/任意 scale 锐利/5 卡不重叠/超长名不越界/60fps）不过就不聊视觉；视觉我出 3 版候选你选；3 轮不行＝方向问题，停下重定，不继续磨 |
| 8 | 卡宽边界 | 上限 `min(屏宽 × 0.45, 240px)`；`Font.plainSubstrByWidth` 像素级截断 + `…`；名字区加下限 ~40px；**NEW 改为入场时的一次性光晕脉冲**（不占布局） | 全名看不到了——HUD 卡不接受悬停，tooltip 兜不了，这是**认下的信息损失** |

## 实施顺序（里程碑）

| | 做什么 | 验收（不许跳） |
|---|---|---|
| **M0** | harness 壳：dev 调试屏 + 假卡注入 + 数字读数，卡片用现有 `BaselineCardPainter` 顶着 | 进游戏按键 → 屏上有卡、读数对。**把真机循环压到秒级** |
| **M1** | 形状层：core shader + 统一入口批处理器 + `stats()` | 五个形状逐个开关看；任意 GUI scale 边缘锐利 |
| **M2** | 卡片画法 v1：照草图五样（左月牙→数量→徽章→名称胶囊→端点） | 辅助线下无错位；与 `BaselineCardPainter` 并排对照 |
| **M3** | 美术迭代：HTML 草稿 ↔ harness 对照，按允许子集走 | **你点头**（唯一主观验收） |
| **M4** | 收尾：入场/合并/退场曲线、XP 卡形、超长名字截断、NEW 闪光、性能 | 逐项真机截图 |

## 已知风险（M1 必须先解决）

**uniform 驱动的 SDF 与 `MultiBufferSource` 不兼容。** `BufferSource` 按 RenderType 分组、到
`endBatch()` 才画，而 uniform 是 per-draw 的 —— 所以"参数放 uniform"必然推出"每形状自己
flush"。这解释了归档版 `ShapeBatch` 为什么用 `Tesselator`（**不是疏忽，是被方案逼的**），
而上一版 v1 的"整层不可见"恰恰发生在手搓路径上（错题本原话：GL 状态/时序类问题无法盲调）。

**处理：M1 第一个动作是最小可见性 spike** —— 一个圆角矩形、坐标写死、画在 HUD 正中。
一个形状不可见，五十个也不用试。同时对着 1.20.1 源码核三件事：

1. `ShaderInstance.apply()` 会不会重置我设的自定义 uniform
2. `BufferUploader.drawWithShader` 的入口契约
3. `BufferSource.endBatch` 的分组与顺序语义

然后二选一定案：**(a)** uniform + 每形状 flush（成熟，ModernUI 自己也是每次 flush），
或 **(b)** 参数搬进顶点属性换真合批。**由 spike 的数据决定，不由讨论决定。**

## 与上一版的关系

上一版 `archive/selfdraw-v2` 的 shader **技术是对的**（`sdRoundBox` + `aastep`，与 ModernUI
同款算法），它死在**美术没定稿 + 手算坐标 + 没做真机验收**。所以这次复用它的着色器思路，
但补上它缺的三样：参数真源、辅助线、快速真机循环。

两条前车之鉴写在 `decision-rendering.md` 的"死路"一节：**HTML 当贴图**、**绝对/局部坐标混用**。

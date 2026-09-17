# 交接：从这里继续

> 给"压缩上下文之后重开的我"看。读完这份 + `docs/plan-ui.md` + `docs/decision-rendering.md`，
> 就能接着做，不需要重读历史对话。

## 一句话现状

卡片的**外壳已经换成 NanoVG 矢量渲染**（`org.lwjgl:lwjgl-nanovg:3.3.1`，与 MC 实际加载的
LWJGL 同版本，绑定 + 四平台 native 自包含打进 jar），物品图标与中文文字仍走原版两趟合成；
**SDF 那条路径整条保留作回退**（引擎不可用时自动降级，卡难看可以接受，HUD 不画不行）。

尺寸、动画、配置界面这一轮都动过：

| | 现状 |
| --- | --- |
| 卡片尺寸 | 卡高 **22 逻辑px**（图标 16 原生 1:1、上下内边距各 3）。原来 32：MC 字体固定 8px，框比字大 → "又大又空"，字/卡从 25% 提到 32% |
| 堆叠 | **顶锚**（`StackLayout` 从上往下）、**最新的一张在最上面**、旧的往下挤 |
| 入场 | 卡片**原地出现**（不再有缩放/上升/`easeOutBack` 过冲）；竖条纵向展开 168ms、内容从竖条后面横向滑出（延迟 101ms，459ms 走完）。曲线是草稿那条 `cubic-bezier(.22,.9,.28,1)`（`CubicBezier` 真解，不是近似） |
| 换位过渡 | `CardMove`（纯逻辑，5 条测试钉着）：起跑那一帧不许跳、落点精确、走草稿曲线 |
| 阴影 | **按轮廓**逐元素投（竖条 + 图标格 + 名字框三个影子），不再是包住整卡的大矩形 |
| 配置界面 | 键位 **K** 打开：两列 16 项（主题/阴影三项/高光/圆角/间距/内边距/图标/竖条/入场/跳动）+ **实时预览**（走真卡绘制代码） |
| 配置存储 | `[style]` 段每项可 `-1` = 跟随主题；主题给默认值，配置只覆盖改过的项（`StyleOverrides`，5 条测试） |

**像素对照这条链路仍然可信**：设计↔实现的结构项全部一致（客观门），且设计稿现在也由
同一份 token 重新产出（`design/shot.py`）。

**已部署**：`D:\Myworld\.minecraft\versions\1.20.1-main\mods\pickupcard-Forge-1.20.1-0.2.0.jar`
（自包含：51 个绑定类 + 四平台原生 + `THIRD_PARTY_NOTICES.md`）。改完代码必须重新
`build` 再覆盖过去，**运行中的实例不会热加载**。

## 跑起来 / 常用命令

```bash
cd platforms/1.20.1-forge

# 编译 + 单测（Java 69 个用例）
./gradlew build

# 无人值守截图
./gradlew runClient -PharnessAuto=shot     # 开调试屏 → 每页拍两张 → 退出
./gradlew runClient -PharnessAuto=hud      # 不开调试屏，走玩家真正走的那条路（最能说明问题的一条）
./gradlew runClient -PharnessAuto=config   # 开配置界面 → 截图 → 退出
#   产物在 run/screenshots/：
#     pickupcard-harness-p1[-mid]      五档强调色+经验（-mid 是入场途中那一帧）
#     pickupcard-harness-spike[-mid]   形状层 spike
#     pickupcard-measure               测量页（纯黑底/无辅助线/无读数）← 像素对照用的就是这张
#     pickupcard-config                配置界面
#   交互调试：./gradlew runClient 然后 F9；游戏里按 K 开配置界面
#     A=推全部样例 1-9=单张 C=清空 G=辅助线 B=背景 F=读数

# 主题：改 design/tokens.css 后必须重新生成，否则游戏里读的是旧 JSON
python tools/css_tokens.py            # 生成
python tools/css_tokens.py --check    # 只校验（CI 用）

# 像素对照（三个入口，从下往上用）
python -m pytest design/test_measure.py -q           # 先证明"尺子"是准的
python design/shot.py design/measure.html --geometry # 设计侧测量页 + 浏览器自己的 DOM 矩形（token 改了必须重跑）
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

## 已知缺陷 / 没做的事（含已修记录 —— 别重复劳动）

### 0. 已修：形状层"本批第一次 draw 不透明"

**症状**：每批第一次提交的形状渲染成不透明（顶点 alpha 被丢），第二次之后正常。

**真根因**：`assets/pickupcard/shaders/core/gui_shape.json` 里**没有 `"blend"` 块**。
`ShaderInstance.apply()` 会调 `this.blend.apply()`，缺失时构造出来的 `BlendMode` 默认
opaque=true → `RenderSystem.disableBlend()`；而 `BlendMode.lastApplied` 是 **static** 缓存，
所以只有"这一次与缓存不同"的那次 apply 真正生效 —— 表现就是"本批第一次、且之前画过别的东西"。

**修法**：照原版所有半透明 GUI 着色器补上 blend 块；`ShapeBatch.warmUp()` **已删除**。
**已验**：棋盘底上四个探针，0 个纯黑像素。**别再去找 RenderType 的配对顺序，那条路是错的。**

### 1. 阴影从来没被量过（盲点）

测量页是**纯黑底** —— 阴影叠在黑上等于什么都没叠，所以它永远不会出现在对照里。
现在的阴影还是 SDF 的 smoothstep 软化，**不是** CSS 的高斯模糊。要动它必须先给它一把尺子：
浅灰底 + 沿一条竖线扫灰度剖面，草稿与游戏并排比。**先做尺子再调，别靠感觉。**

### 2. 门禁的盲区：锚点与顺序（这一条是被用户截图打出来的）

`compare.py` 逐张比卡片结构（竖条宽/高、间距、圆角、级差），**不比对卡堆的锚点和"谁在最上面"**。
所以"底锚 + 老的在上面"这种**完全没照草稿**的排版，门禁一路绿灯。要补两条零成本指标：
**首卡上缘 / 卡高**（抓锚点）、**强调色自上而下的顺序**（抓谁在最上面）——
`measure.py` 本来就把每张卡的 accent 认出来了。

### 3. 还没做的

| 差异 | 现状 |
| --- | --- |
| 退场淡出 | 草稿是 opacity 280ms 淡出；现在只有 12px 下沉，**没有淡出** |
| 行为类配置没进界面 | 停留/退场/合并/最多几张/数量写法/三张过滤名单/贴边与左边距仍只在 TOML 里，需要第二页 |
| 配置落盘未验 | 界面改值立刻生效（内存 + `refreshStyle`），但 TOML **落盘**依赖 Forge 自动保存，没验过 |
| 配置界面没点过 | 只验到"能画出来"（截图 `pickupcard-config`）；写回 + 立刻生效这条路径没有自动化点击验证 |
| `RarityAccent` 硬编码 | 写死 `0xFFFFD83D` 等，与 `tokens.css` 的 `--pc-accent-*` 值相同但**各存一份** —— 第二真源 |
| `BaselineCardPainter` 是死代码 | 无人引用，且用的是 `iconScale` 时代的写法（现在是像素，会算出 384px）。建议删 |
| 超长名字截断 | Q8 的 70% 屏宽上限没有真机验收 |
| `appearMode = CLIP` | 没有真机对照截图 |
| 级差这个值有三份 | `CardStage.STACK_GAP` / `measure.html` / `animation.html`，目前靠门禁的"级差"项看住漂移 |

### 4. 参考图（`design/reference/paste.png`）与尺寸变更的关系

那张参考图是我们的主题放大 3 倍，对应的是**旧的 32 逻辑px 卡片**（卡 96 设备px、竖条 16）。
当时的量法结论仍然有效：**对齐 = 左缘固定**（竖条左缘极差 0）、竖条高 / 上下内缩 / 两处间距 /
图标格 / 级差**全部一致**；顶光与经验卡图标是按它补的。

后来用户说"太大"，改的是**另一件事**：MC 字体固定 8px，32 高的框里只装 8px 的字（占 25%），
而草稿同样框里是 13px（占 41%）→ 又大又空。于是把框缩到 22（字/卡 32%），
**其余长度按同一比例缩**，目的是保住参考图量出来的那些**比例** —— 那些比例正是门禁逐项判的。

### 5. 环境坑（踩过一次，别再踩）

- **全屏 + 2560×1600 时截图是坏的**：`Screenshot.grab` 出的四张图互不相同、但都不含任何卡
  （guiScale=6）。`run/options.txt` 里现在是 `fullscreen:false`。**改窗口大小前先确认截图里真有卡。**
- **GUI 缩放会被窗口尺寸夹住**：854×480 上 `guiScale:3` 不生效，MC 夹到 2。跑测固定
  `--width 1280 --height 720`（`build.gradle`）。**测量脚本不要硬编码分辨率。**

## 下一步（按优先级）

1. **门禁补两条指标**（零成本）：`首卡上缘 / 卡高`（抓锚点）、`强调色自上而下的顺序`（抓谁在最上面）。
   这次"底锚 + 老的在上面"完全没照草稿，是用户截图才发现的；补完之后这类错误在门禁上就会红。
2. **配置界面第二页**：行为类（停留/退场/合并窗口/最多几张/数量写法）+ 三张过滤名单（要列表编辑）。
   顺带两件验证：**自动点击**（harness 发真实点击 → 断言配置值变了、预览跟着变）把"改了就生效"
   钉死；**落盘验证**（改完重启看 TOML 里有没有留下）。
3. **退场淡出 280ms**（草稿有，现在只有 12px 下沉）。
4. **阴影的尺子**：浅灰底 + 竖直灰度剖面，草稿与游戏并排 —— 尺子做完再谈调参。
5. **清理第二真源与死代码**：`RarityAccent` 改成从主题取强调色；删 `BaselineCardPainter`；
   级差收进 token（现在三份）。
6. **真机验收尾巴**：超长名字截断（Q8 的 70% 屏宽）、`appearMode = CLIP` 对照、多分辨率（guiScale 2/4）。
7. **提醒**：`run/config/*.toml` 在 `run/` 里被 gitignore，且**优先于代码默认值**。
   改了 `PickupCardConfig` 的默认值之后要么删掉让它重新生成、要么手动改。

## 方法论：踩过、别重踩

- **拿尺子去量真东西之前，先量一个我知道多长的东西。** v1 的指标恒等于 1px 却能骗过所有人。
- **别急着把锅扣在输入上。** "投影填住了间隙"听起来很合理，所以没人去查判据本身。
- **同时改两个变量再下结论 = 没做实验。**
- **测量会被调试叠加层污染。** 用辅助线做像素测量前先关掉它。
- **换算系数要写清楚。** 截图是帧缓冲像素（guiScale 3 → ÷3 得逻辑 px）；HTML 侧还叠了 dsf。
- **Qwen VL 只管语义，不管几何。** 数字的事一律数值扫描。
- **过滤默认什么都不丢**（2026-09-17 定案）：内置忽略表会把泥土/圆石/沙子静默丢掉，
  玩家观感就是"mod 坏了"。现在默认每次拾取都弹卡，想安静自己写黑名单。
  **排查"某件东西没弹卡"时先看日志里有没有"被过滤器丢弃"。**
- **改了代码默认值，别忘了跑测目录里那份配置**（`run/config/*.toml` 优先于代码默认值）。
- **说不清来源的图，先读它的指纹**（底色/强调色/格子周期/间隙）。
- **静默失败最毒。** `css_tokens.py` 的"清单外报错退出"在写它时就抓出三个真 bug。
- **工具层本身有两个坑（这轮每个都踩了）**：单条命令超过约 6KB 会被**截断**（heredoc 直接读到 EOF，
  文件写一半）→ **文件按小块写**；**反斜杠会被吃掉**（`'
'` 变成真换行，连 `//` 注释里也会把
  注释截断成语法错误）→ 生成代码时用 `System.lineSeparator()`，别写转义。
- **断言要精确，宽断言会误报**：`"HashMap" in "LinkedHashMap"`、`", rise)"`（别的函数也用 rise）、
  `"jarJar"`（注释里就有"JarJar"）—— 三次都是断言太宽，白跑三轮。
- **API 要对"钉死的那一版"查**：3.3.1 的 `org.lwjgl.system.Struct` 不是泛型，3.3.3 才是；
  照本地缓存写出来的签名在真正依赖上编译不过。
- **LWJGL 生成的指针工厂不做 null 检查**：`NSVGPath.create(long)` 就是 `new NSVGPath(address, null)`，
  链表最后一项的 `next()` 返回**包着 0 地址的非 null 对象**，对它调一次 `npts()` 直接 SIGSEGV
  （JVM 崩，Gradle 只报 `Test Executor finished with non-zero exit value 1`，连测试名都不给）。
  遍历链表用 `address() != 0`，不要用 `!= null`。
- **匿名内部类会遮蔽外层同名局部变量**：`AbstractSliderButton` 自带 `protected double value`，
  参数也叫 `value` 时 `value.set(...)` 报"double 不能被解引用"。
- **布局要用截图量**：配置界面第一版 8 行 × 22px 在 240 逻辑px 画布上溢出（截图里只数到 6 行）。
  **MC 的逻辑画布只有 426×240**（1280×720 @ guiScale 3，本仓库固定这个窗口）。
- **部署路径与开发路径的类加载不是一回事**：Forge dev 的类加载器只认 legacyClassPath 文件 +
  mod 自己的 classes 根，`implementation` 依赖在 runClient 里**看不见**。三条路都撞过：
  往生成的文件追加会被 runClient 覆盖、覆盖 `-DlegacyClassPath.file` 让 BootstrapLauncher 崩、
  JarJar 要 Maven 风格版本区间。最后靠**把绑定和四平台 native 摊进自己的 classes 根**（= shade）走通。

## 环境备注

- `hindsight_*` 工具这一轮**可用**（先用 `dev_tool_search` 解锁）。本项目的倡议页：
  `kp-947e3b704b70414fb9b3390e287a0802`（引入 NanoVG 作为卡片渲染引擎）。
- 无头浏览器用系统 Edge（`shot.py` 自动退回 `channel="msedge"`）。
- 部署实例：`D:\Myworld\.minecraft\versions\1.20.1-main\`（Forge 1.20.1-47.4.1，147 个 mod）。
  该目录里**没有**第二个带 NanoVG 的 mod（整目录扫过），ModernUI 也不带（它 25MB 里 `org/lwjgl` 零条）。
- 本会话无图像输入能力：`read_image`/`describe_image` 走不通，所有"看"都是数值扫描。

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

## 渲染：为什么是 ApricityUI 而不是自绘

老版本（`D:\pickupnotice`）自己写 SDF 着色器 + 用 Edge 把 CSS 烘成位图贴图。两个问题：

1. **位图会被拉伸。** 卡宽跟物品名走，而贴图是固定宽度的，中间段只能横拉 —— 圆角、
   铆钉、符文虚线全都被双线性重采样糊掉，非整倍 GUI scale 下尤其明显；
2. **两套真源。** 卡面归 PNG（改它要跑烘焙脚本）、描边与扫光归 shader，状态职责被割开，
   于是出现了"混合状态泄漏"这类补丁（SDF 要记住进入时的混合状态再还原）。

现在外观是 CSS，卡宽定死，装饰是画出来的而不是拉出来的。**"糊"这个问题的类别不再存在。**
代价是玩家侧必须装 ApricityUI（`mods.toml` 里声明为 mandatory 前置）。

## 一个 AUI 的坑（写在代码注释里，也记在这）

`Element#append` / `insertBefore` 内部会调 `Element.init` 把通用 `Element` 换成注册类
（`SPAN`→`Span`、`ITEM`→`Item`…），换出来的是**另一个实例**。所以凡是插进 DOM 之后还要
继续操作的节点，必须先自己 `Element.init(...)` 拿到最终实例 —— 否则你手里的引用指向一个
不在树上的对象，改它没有任何效果，而且**不报错**。

## 已知约束

- **AUI 的 CSS 不解析 `radial-gradient`**，用了会静默不画（不报错）。四角铆钉因此改用
  `box-shadow` 点出来。CSS 注释里记了这条。
- **`transform` 只支持 translate/rotate/scale**，没有 skew/matrix/perspective。
- **页面的资源根是 AUI 的全局命名空间**：页面必须放在
  `assets/apricityui/apricity/<路径>`，或被玩家放到实例的 `<游戏目录>/apricity/<路径>`。
- **`transform` 不走 `calc()` 的乘除**（`calc` 只支持加减）。

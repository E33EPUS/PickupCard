# Pickup Card

**Every item you pick up is dealt into your HUD as a card.**

纯客户端 mod：不需要服务端安装，任何服务器、任何整合包都能用。拾取物品或经验时，
屏幕右下角弹出一张玻璃拟态风格的动画卡片（图标 + 名字 + 数量），自绘渲染、零前置。

## 它长什么样

一张卡 = 玻璃卡面 + 物品图标 + 名字 + 数量读数；稀有度决定强调色（vanilla 四档：
白/黄/青/紫，装了 RarityCore 则自动换它的七档配色）。

行为上：

- **合并** —— 合并窗口内连捡同一种东西，数字弹一下往上滚，而不是弹一屏卡；
- **NEW 角标** —— 这一局头一次见到的物品会亮一下（刻意不落盘：换世界就重置）；
- **附魔 / 耐久** —— 由原版渲染白拿：附魔光效在模型里，耐久条走 renderItemDecorations；
- **弹性动画** —— 入场从下方回弹转正、合并时数字鼓一下、退场下沉淡出；每种动画可独立关；
- **经验卡** —— 经验球拾取同样弹卡（绿色独立样式），与物品卡共用合并规则；
- **只弹你的** —— 信号源头按拾取者 UUID 过滤，其他玩家和捡装备的僵尸都不会给你弹卡。

## 前置

- **Minecraft 1.20.1 + Forge 47+**
- 无其他必需前置。（[RarityCore](https://modrinth.com/mod/raritycore) 是可选联动：装了
  就用它的七档稀有度与自定义配色，没装回退 vanilla 四档。）

## 装法

把 jar 丢进 `mods/` 即可。

## 配置

`config/pickupcard-client.toml`：

- `notice.holdMs` / `notice.exitMs` —— 停留多久、退场动画多长
- `merge.enabled` / `merge.windowMs` —— 要不要合并、窗口多长
- `layout.maxOnScreen` —— 同时最多几张
- `count.format` —— 数字怎么写（`+64` / `×64` / `64` / `+1.2K`）
- `filter.*` —— 过滤三表：黑名单（不弹）、白名单（永远弹并强调）、静音名单
  （弹但不响），规则写法 `minecraft:stone` / `#forge:ores` / `@somebotania`；
  内置默认忽略表（泥土/圆石类刷屏物品）可整体关掉

**外观不在配置里。** 卡面的配色、圆角、动画参数在
`assets/pickupcard/styles/default.json` —— 那是唯一真源，改完一秒内生效，不用重启。

## 自己做一套卡面

外观是数据（JSON），改外观 = 改一个文件：

1. 从 jar 里取出 `assets/pickupcard/styles/default.json`；
2. 放进资源包同路径覆盖（或直接改 jar 外的实例目录）；
3. 存盘，一秒内游戏里生效。

设计文档见 [docs/design.md](docs/design.md)；美术参数与 CSS 变量对照的可视化预览在
[docs/art/mockup.html](docs/art/mockup.html)（浏览器直接打开，可切换三种卡面语言与
三种动画性格）。

## 构建

本仓库是**单分支多目标**结构：`shared/` 放与平台无关的逻辑，`layers/` 放按映射/加载器/
版本切分的代码，`platforms/<目标>/` 是各自的 Gradle 工程。

```bash
cd platforms/1.20.1-forge && ./gradlew build
```

产物在 `platforms/1.20.1-forge/build/libs/`。结构自洽性由 `tools/verify_targets.py` 把关：

```bash
python tools/verify_targets.py
```

## 许可

MIT。

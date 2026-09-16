# Pickup Card

**Every item you pick up is dealt into your HUD as a collectible card.**

纯客户端 mod：不需要服务端安装，任何服务器、任何整合包都能用。界面由
[ApricityUI](https://github.com/Tower-of-Sighs/AUI) 渲染 —— 卡片的外观与动画是用
HTML/CSS 写的，不是烘成贴图再拉伸。

## 它长什么样

一张卡 = 稀有度面板 + 物品图标 + 名字 + `+N` 读数，四档稀有度各自一套材质：

| 稀有度 | 材质 | 装饰 |
| --- | --- | --- |
| 普通 | 木牌 | 素面 |
| 罕见 | 铜牌 | 内圈细线 + 四角铆钉 |
| 稀有 | 蓝银 | L 形角饰 |
| 史诗 | 暗紫鎏金 | 外发光 + 底部符文虚线 |

行为上：

- **合并** —— 两秒内连捡同一种东西，数字滚上去，而不是弹一屏卡；
- **NEW 角标** —— 这一局头一次见到的物品会亮一下（刻意不落盘：换世界就重置）；
- **附魔 / 耐久** —— 附魔物品带光效，有损耗的工具带耐久条（都由原版渲染，我们不重画）；
- **发牌入场** —— 新卡带一点倾角转正滑入，而不是淡入。

## 前置

- **Minecraft 1.20.1 + Forge 47+**
- **[ApricityUI](https://modrinth.com/mod/apricityui) 1.2.0+**（硬前置，必须装）

## 装法

把 jar 丢进 `mods/` 即可。ApricityUI 没装的话，加载器会在启动时就告诉你缺前置，
而不是进游戏后默默什么都不显示。

## 配置

`config/pickupcard-client.toml`：

- `holdMs` —— 一张卡停留多久
- `merge.enabled` / `merge.windowMs` —— 要不要合并、窗口多长
- `layout.maxOnScreen` —— 同时最多几张
- `count.format` —— 数字怎么写（`+64` / `×64` / `64` / `+1.2K`）

**外观不在配置里。** 卡片的配色、装饰、动画全部在
`assets/apricityui/apricity/overlays/pickupcard/card.css` —— 那是唯一真源，改它就行。

## 自己做一套卡面

因为外观是 CSS，改外观 = 改一个文件：

1. 从 jar 里取出 `assets/apricityui/apricity/overlays/pickupcard/card.css` 与 `card.html`；
2. 放进实例的 `<游戏目录>/apricity/overlays/pickupcard/`（本地目录优先级高于 jar，会覆盖）；
3. 改完回游戏按 **END** 重载资源。改 CSS 只重挂样式，不会丢状态。

四档配色与装饰都收在 `.pc-rarity-*` 里，动那几行的变量即可。动画在 `@keyframes` 段。

## 构建

本仓库是**单分支多目标**结构：`shared/` 放与平台无关的逻辑，`layers/` 放按映射/加载器/
版本切分的代码，`platforms/<目标>/` 是各自的 Gradle 工程。

```bash
cd platforms/1.20.1-forge && ./gradlew build
```

产物在 `platforms/1.20.1-forge/build/libs/`。

结构自洽性由 `tools/verify_targets.py` 把关（矩阵规则、身份唯一、层谓词与挂载一致…）：

```bash
python tools/verify_targets.py
```

## 许可

MIT。ApricityUI 是独立的第三方 mod，按它自己的许可（LGPL-2.1）分发，本仓库不包含它的代码。

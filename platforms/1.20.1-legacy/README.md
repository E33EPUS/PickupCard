# platforms/1.20.1-legacy —— 参照实现，**不编译、不部署**

这是 2026-09-17 从发行 jar（`recovered/0.1.0/original-jar.zip`）反编译恢复、并修到能编过的
**v0.1.0 快照**：烘焙皮肤 PNG + SDF 圆角着色器 + 左侧标签页 configui + 动画预设。

**它为什么在这儿**：用户后来对着四条并存的画法说了一句"我们的项目不干净"，于是卡面路线
定案为 **NanoVG 矢量一条路**（见 `docs/decision-rendering.md` 的"最终决定"）。但 v0.1.0 是
**唯一被玩家真机接受过**的那一版模样/手感，所以它留在这儿当"以前长什么样"的正本参照，
而不是当备份分支。

**别做的事**：

- ❌ 不要 `./gradlew build` 之后把产物丢进 `mods/` —— 它的 modid 也是 `pickupcard`，
  和主工程一起放会让"现在跑的是哪一版"变得不可知（这个坑踩过）；
- ❌ 不要在这儿改代码来"顺手修个小问题" —— 改动请提给主工程
  （`platforms/1.20.1-forge` + `shared/` + `layers/mapping/official`）；
- ❌ 不要把它加进 `versions/targets.json` 的矩阵 —— 它已经在 `_reference.projects` 里
  （参照不等于目标，那条声明由 `tools/verify_targets.py` 盯着）。

**可以做的事**：查"以前的某个数值/某段逻辑当时怎么写的"。这类问题先在
`recovered/0.1.0/`（原始反编译源码 + 资源，未改名）里找，那份是逐字节对应的原件。

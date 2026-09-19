# ⚠️ 紧急包装修复交接：nanovg 摊平副本会 JPMS 炸掉与 UI Deck 的同装环境

> 2026-09-19 由 UI Deck 会话写。**下一个版本（0.2.2）必须带上这个修复**，否则与 UI Deck 0.1.0+（任何版本）同装时游戏启动即炸。

## 现象（实测，2026-09-19 15:20 实例启动失败）

PickupCard 0.2.1 与 UI Deck ≥0.1.0 同装时，游戏在 mod 解析阶段直接退出：

```
java.lang.module.ResolutionException: Modules pickupcard and uideck export package org.lwjgl.nanovg to module ferritecore
```

**根因**：两个 mod 都把 `org.lwjgl:lwjgl-nanovg` 的类**摊平**在 jar 根（dev 类路径问题的同一个解法），Forge 47.4.x 的 JPMS 模块系统不允许两个模块导出同一个包。148 mod 真实实例（1.20.1-main，Forge 47.4.1）实测必炸。

## 修法（已在 UI Deck 0.1.1 上验证通过，照抄即可）

参照 `D:\UIDeck\platforms\1.20.1-forge\build.gradle` 的「prod：nanovg 以 JarJar 嵌套」一节。要点五条：

1. **unpackNvg 保留**，但只挂 `runClient`（dev 类路径照旧）；`jar`/`jarJar`/`reobfJar` **不再依赖它**。
2. **jar 任务排除类文件**：`exclude 'org/lwjgl/nanovg/**'`（排除后 Gradle 校验仍要求 `dependsOn 'unpackNvg'`——它写 classes 目录，显式声明即可）。
3. **native 资源目录留在主 jar**（`windows/x64/...` 等）——它们是 .dll/.so 资源，没有类文件就不产生 JPMS 包导出，LWJGL 按资源路径自取。
4. **prod 由 JarJar 嵌套供应类**：`jarJar.enable()` + `jarJar(implementation("org.lwjgl:lwjgl-nanovg:[3.3.1]"))`。版本必须**钉死单一版本 `[3.3.1]`**：
   - 开区间 `[3.3.1,3.4)` 会解析到 3.3.6，与 MC 运行时 LWJGL 3.3.1 错版本 = 首次调用 UnsatisfiedLinkError；
   - 还会连带把第二份 LWJGL 核心嵌进 jar（实测）。
   - jarJar 任务同样 `exclude 'org/lwjgl/nanovg/**'` + `dependsOn 'unpackNvg'`；manifest（Implementation-Version 等）移到 jarJar 任务上；jar 任务 `archiveClassifier = 'slim'` 让位。
5. **产物核验三条**（改完跑一遍）：
   - 主 jar 无 `org/lwjgl/nanovg/*.class` 残留；
   - `META-INF/jarjar/` 里有 `lwjgl-3.3.1.jar` + `lwjgl-nanovg-3.3.1.jar`（成对同版）；
   - `windows/x64/.../lwjgl_nanovg.dll` 仍在主 jar。

## 版本与部署

- 建议 `mod_version` → **0.2.2**，正常走发布流程。
- 实例 `D:\Myworld\.minecraft\versions\1.20.1-main\mods\` 里现在已有 `uideck-0.1.1.jar`（已修复侧）；**在 PickupCard 0.2.2 出来之前，这个实例带着两个 mod 是起不来的**——想先玩 UI Deck 就把 PickupCard 的 jar 临时 `.disabled`，想玩 PickupCard 就把 uideck `.disabled`。修复版部署后都改回来。

## 为什么不是 UI Deck 单方面能修的

JPMS 冲突的双方是「pickupcard 模块的摊平副本」和「uideck 侧的 nanovg（无论摊平还是嵌套）」。uideck 改成 JarJar 嵌套后，冲突对象变成「嵌套的 lwjgl-nanovg 模块 vs pickupcard 摊平副本」——**只要 PickupCard 还在摊平，冲突就在**。两个 mod（同一位作者）都切到 JarJar 后，嵌套库按版本去重（实例日志里的 architectury 就是这么解析的），冲突消失。

## 结案（2026-09-19，PickupCard 侧已执行）

配方照 UI Deck 0.1.1 落地进 `platforms/1.20.1-forge/build.gradle`，并修了参考实现
没覆盖的两处 PickupCard 特有差异：

1. **reobf**：FG6 造了 `reobfJarJar` 任务但**不自动挂链**（uideck 全项目零 reobf 配置、
   产物却有 72 个 SRG 引用 —— 它漏挂只是因为 UI Deck 没人手动接错链，本仓库首轮构建
   实测主产物 SRG 引用=0）。已在 jarJar 任务上加 `finalizedBy 'reobfJarJar'`，重验
   SRG 引用=160。⚠️ 漏挂的症状：编译过、测试绿，上线第一次画卡 NoSuchMethodError。
2. **manifest**：UI Deck 没有 mixin，它的 jarJar manifest 只有 4 个属性；PickupCard
   的最终产物 manifest 必须整份（含 `MixinConfigs`，缺了 mixin 不加载、拾取事件哑掉）。

产物核验（`build/libs/pickupcard-Forge-1.20.1-0.2.2.jar`，sha256 部署时另记）：

- `org/lwjgl/nanovg/*.class` 残留 = 0 ✓
- `META-INF/jarjar/` 里 `lwjgl-3.3.1.jar` + `lwjgl-nanovg-3.3.1.jar` 成对同版 ✓
- `windows/x64/.../lwjgl_nanovg.dll` 等四平台 native 仍在主 jar ✓
- mod 类 SRG 引用 = 160（reobf 生效）；`MixinConfigs` 与 refmap 都在主 jar ✓

**版本就地进 0.2.2**（第三次就地替换，用户铁律：没发话不动版本号）。实例 mods 目录
里那个 `pickupcard-Forge-1.20.1-0.2.2.jar.disabled`（= 修复前的 `9ef6a724…` 构建布，
当年为玩 UI Deck 禁用的）已删 —— 修复版启用后两个 mod 同装即为本交接的终态。

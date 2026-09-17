# 第三方组件声明 / Third-party notices

本 mod 的 jar 里**内嵌**了下面两个库的编译产物（不是依赖前置，是打进本 jar，玩家无需另装）。
之所以内嵌而不是当依赖：Forge 的开发/发布两条类加载路径不一样，内嵌是唯一两边都验过的做法，
而 HUD mod 的 native 加载失败意味着 HUD 整个不画。

## LWJGL — NanoVG 绑定（Java 侧）
- 文件：`org/lwjgl/nanovg/**`（约 60 个 class）
- 版本：3.3.1（与 Minecraft 1.20.1 实际加载的 `LWJGL version 3.3.1 build 7` 同版本）
- 许可：BSD-3-Clause — https://www.lwjgl.org/license

## NanoVG / NanoSVG（C 侧，已编译进 native）
- 文件：`windows/x64/.../lwjgl_nanovg.dll`、`linux/.../liblwjgl_nanovg.so`、
  `macos/.../liblwjgl_nanovg.dylib`
- 上游：NanoVG 与 NanoSVG，作者 Mikko Mononen
- 许可：zlib — https://github.com/memononen/nanovg/blob/master/LICENSE.txt

两个许可都是宽松许可，允许以二进制形式随本 mod 分发，条件是保留上述声明。

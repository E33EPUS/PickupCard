# Changelog

本文件记录版本变更。发版时在 `RELEASE_NOTES.md` 写版本段（中文在前、英文在段尾），
商店文案取段尾的英文块。

## 0.1.0

首个版本：拾取卡片提示的完整形态。

### Added

- 纯客户端嗅探原版拾取包（Mixin `ClientPacketListener#handleTakeItemEntity`），服务端无需安装。
  注入点选在 `ensureRunningOnSameThread` 之后 —— 此时物品实体尚未移除，能拿到含改名与 NBT 的真实 ItemStack。
- 四档稀有度卡面（木牌 / 铜牌 / 蓝银 / 暗紫鎏金），装饰层数递增。
- 界面由 ApricityUI 渲染，外观与动画全部写在 CSS 里（改外观不用重编译）。
- 合并：窗口内连续拾取同类物品累加数量并触发数字跳动，而不是重复弹出。
- NEW 角标：本局首次遇到的物品亮一下。只记内存、不落盘。
- 附魔光效与耐久条由原版物品渲染承担。
- 配置：停留时长、合并开关与窗口、同时在屏上限、数量写法。
- 结构守卫 `tools/verify_targets.py`：矩阵规则 / 身份唯一 / 层谓词与挂载一致 / 工程与条目互存。

### 与 0.1.0 之前的关系

仓库重建于 2026-09-16。更早的 `D:\pickupnotice` 是自己写 SDF 着色器 + 烘焙位图贴图的路线，
因"烘焙贴图被三段拉伸导致细节糊、且设计稿与实现漂移"而整体重做，改为 ApricityUI + CSS。
老仓库以 `archive/terminal-card-wip` 标签留档，不再维护。

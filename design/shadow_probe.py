"""影子探针：棋盘底截图里，"卡片左缘外那一小条必须比背景暗，而且是软边"。

【为什么单开一个探针】`compare.py` 的输入是测量页，而测量页是**纯黑底** —— 影子叠在纯黑上
等于什么都没叠。那条链对影子永远是绿灯。2026-09-17 把渲染收口成 NanoVG 时，影子被画到了
屏幕左上角（局部坐标当成屏幕坐标用了），测量页全绿、棋盘底的 harness 截图一眼就看出来。

所以这一条量的是**只有棋盘底才看得见的东西**：影子本身。

判据（都按设备像素，guiScale=3）：
  1. 存在：竖条所在的那些行，卡左缘外 1~6 px 的平均亮度比同行的远处背景低 >= 12；
  2. 是软的：从卡缘往外亮度回升，且最外面 2 列已经回到背景 ±6 以内（不是一整块实心黑）；
  3. 没乱跑：竖条顶往上 20 px 的同一列必须是背景（影子跟内容走，不是一条贯穿全屏的带）。

用法：
  python design/shadow_probe.py platforms/1.20.1-forge/run/screenshots/pickupcard-harness-p1
  python design/shadow_probe.py <图> --selftest     # 负例对照：把采样窗挪进卡里，必须判 FAIL

退出码 0 = 过，1 = 不过。
"""

import sys

import numpy as np
from PIL import Image

# 五档强调色（与 assets/pickupcard/styles/default.json 的 accent 段一致）
ACCENTS = [(0x9A, 0xA4, 0xAD), (0xFF, 0xD8, 0x3D), (0x55, 0xEB, 0xFF),
           (0xD7, 0x8B, 0xFF), (0x7D, 0xFF, 0x8A)]

TOL = 26          # 认竖条的颜色容差
DROP = 12.0       # "比背景暗多少"才算影子
FLAT = 6.0        # 离远了必须回到背景的这个范围内


def load(path):
    return np.asarray(Image.open(path).convert("RGB"), dtype=float).mean(axis=2)


def bar_column(lum_img, rgb_img):
    """返回 (竖条左缘 x, 竖条所在行集合)。取最左边那个有强调色的列 —— 卡片是左对齐的。"""
    mask = np.zeros(rgb_img.shape[:2], dtype=bool)
    for c in ACCENTS:
        mask |= np.abs(rgb_img - np.asarray(c, dtype=float)).sum(axis=2) < TOL
    cols = np.nonzero(mask.any(axis=0))[0]
    if cols.size == 0:
        return None, None
    x0 = int(cols[0])
    # 竖条 = 这一列上连续成段的匹配行；只取最长的一段，避开同色的文字
    rows = np.nonzero(mask[:, x0])[0]
    if rows.size == 0:
        return None, None
    splits = np.split(rows, np.nonzero(np.diff(rows) > 1)[0] + 1)
    band = max(splits, key=len)
    return x0, set(band.tolist())


def probe(path, selftest=False):
    rgb = np.asarray(Image.open(path).convert("RGB"), dtype=float)
    lum = rgb.mean(axis=2)
    x0, band = bar_column(lum, rgb)
    if x0 is None:
        return False, "找不到强调色竖条 —— 这张图里没有卡（或者整帧没画出来）"
    if selftest:
        x0 += 20          # 负例：采样窗挪进卡片内部，"卡外"的判据必须塌掉

    rows = sorted(band)
    inner = lum[rows][:, x0 - 6:x0]        # 卡缘外 1~6 px
    outer = lum[rows][:, x0 - 20:x0 - 13]  # 更外侧的背景
    far = lum[rows][:, x0 - 12:x0 - 10]    # 最外面那两列（判"软不软"用）
    drop = float((outer.mean(axis=1) - inner.mean(axis=1)).mean())
    if drop < DROP:
        return False, f"卡左缘外没有影子：比背景只暗 {drop:.1f}（要求 >= {DROP}）"
    resid = float(np.abs(far.mean(axis=1) - outer.mean(axis=1)).mean())
    if resid > FLAT:
        return False, f"影子不是软边：最外侧离背景还差 {resid:.1f}（要求 <= {FLAT}）"
    above = lum[rows[0] - 20:rows[0] - 14, x0 - 6:x0]
    ref = lum[rows[0] - 20:rows[0] - 14, x0 - 20:x0 - 13]
    stray = float((ref.mean() - above.mean()))
    if stray > FLAT:
        return False, f"影子跑到了竖条上头（高 {rows[0]} 再往上 20px 也暗 {stray:.1f}）"
    return True, f"竖条左缘 x={x0}，{len(rows)} 行；卡外比背景暗 {drop:.1f}，外侧回到背景 ±{resid:.1f}"


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    selftest = "--selftest" in argv
    paths = [a for a in argv if not a.startswith("--")]
    ok = True
    for p in paths:
        passed, why = probe(p, selftest=selftest)
        if selftest:
            passed = not passed          # 负例：必须判不过
            why += "（负例对照）"
        print(("PASS  " if passed else "FAIL  ") + p + "  " + why)
        ok = ok and passed
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

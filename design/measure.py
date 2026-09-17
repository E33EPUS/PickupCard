#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把一张「三段式卡面图」量成数字。纯函数：不读命令行、不打印、不碰文件。

    [稀有度竖条] [物品图标格] [名字+数量框]

【为什么单独一个模块】compare.py 是"打印对照表"的 CLI，这里只负责"看图算数"。
分开是为了能对它做**已知答案的测试** —— 一个测量工具在拿去量真东西之前，必须先
能量准一张"我知道答案的图"。否则它给出的差异里有多少是设计差异、多少是它自己看错了，
永远分不清。

【为什么推翻 v1 的算法】v1 用"从竖条右缘往右扫，第一个比竖条暗 75% 的像素就是卡体"
来找卡体起点。实测这条规则**永远**返回竖条右缘 + 1 —— 因为竖条比卡体亮，
条件在第一个像素就不成立。于是"条-框间距"这个指标恒等于 1px，与实际间距无关。
当时把它归因于"阴影把间隙填住了"，那个诊断也是错的：间隙是纯背景时它照样返回 1。
（这个 bug 是拿合成图测出来的，不是看出来的 —— 见 design/test_measure.py。）

现在的判据是**按背景色切段**，段与段之间的空白就是间隙：

  1. 竖条用强调色直接找（它是唯一一块又高又窄的强调色）；
  2. 竖条给出一个"一定在卡片内部"的纵向带；
  3. 在这个带里，**只要某一列有任何像素不是背景**，这一列就属于某个框 ——
     这样即使物品贴图在卡片中线是纯黑的（龙蛋就是），也不会把框切成两半。
"""

from __future__ import annotations

import numpy as np

# 背景匹配容差：三通道绝对差之和的上限。测量页的底是纯色，可以卡得很紧。
BG_TOL = 30
# 判定"这一列属于某个框"时，带内至少要有的非背景像素数。
# 取 2 而不是 1：单个游离像素（抖动、贴图边缘）不该把两个框之间的间隙吃掉。
COLUMN_MIN_HITS = 2


class MeasureError(Exception):
    """图里找不到符合三段式结构的卡 —— 宁可报错，也不要返回一个看着像数的错数。"""


def _near(img: np.ndarray, rgb, tol: int) -> np.ndarray:
    return _dist(img, rgb) <= tol


def _dist(img: np.ndarray, rgb) -> np.ndarray:
    """每个像素到某个颜色的三通道绝对差之和。"""
    return np.abs(img - np.asarray(rgb, dtype=np.int64)).sum(axis=-1)


def _palette_index(img: np.ndarray, palette: list) -> np.ndarray:
    """
    把每个像素归给调色板里**最近**的那个颜色，返回下标。

    【为什么不是"逐个颜色按容差筛"】见下。为什么不是"离强调色比离底色近就行"：
    那样每个强调色都会把**别的**强调色的竖条也吞进去 —— 五个强调色都亮，
    彼此的色距（实测 265）小于它们到纯黑的距离（532），于是每种强调色都筛出 5 根竖条，
    一张 4 卡的图量出 16 张卡。
    归给"最近的调色板项"就没有这个洞：每个像素只能属于一家。
    """
    best = None
    idx = None
    for i, rgb in enumerate(palette):
        d = _dist(img, rgb).astype(np.int32)
        if best is None:
            best, idx = d, np.zeros(img.shape[:2], dtype=np.int16)
        else:
            closer = d < best
            best = np.where(closer, d, best)
            idx = np.where(closer, i, idx)
    return idx


def _accent_mask(img: np.ndarray, rgb, bg) -> np.ndarray:
    """
    "这个像素更像强调色，还是更像背景" —— 按**谁更近**判，而不是按固定容差判。

    【为什么不能用固定容差】竖条只有 5 个逻辑像素宽，两边各有一个**半覆盖**的
    抗锯齿像素。实测游戏里 5px 的竖条：中间 8 个满像素 + 两侧各一个 84% 覆盖的
    (130,138,146)，而容差判据把它们都排掉了 —— 量出来 8px，而不是 10px，
    误差 1 个逻辑像素，比对照表自己的报警阈值还大。
    更要命的是这个偏差**两边不一样**：浏览器按整数 CSS px 布局，边缘落在整像素上，
    根本没有抗锯齿；SDF 着色器则永远有。于是"差得多"的旗子会插在一个纯粹由
    抗锯齿造成的差异上。
    按"离谁近"判就没有这个问题：84% 覆盖的像素离强调色近，算数；两边的判据也统一了。
    """
    return _dist(img, rgb) < _dist(img, bg)


def _runs(flags: np.ndarray) -> list[tuple[int, int]]:
    """把一维布尔数组切成 True 的连续段，返回闭区间 [(start, end), ...]。"""
    out: list[tuple[int, int]] = []
    start = None
    for i, on in enumerate(flags):
        if on and start is None:
            start = i
        elif not on and start is not None:
            out.append((start, i - 1))
            start = None
    if start is not None:
        out.append((start, len(flags) - 1))
    return out


# 竖条至少要这么高才算数吧？不——用宽高比就够了，见 _pick_bar。这里只挡掉单像素噪声。
MIN_BAR_PX = 6


def _pick_bar(mask: np.ndarray, bgmask: np.ndarray):
    """
    在一个行带里挑出竖条，返回 (x0, x1, y0, y1) 闭区间；这个带里没有竖条就返回 None。

    同一张卡上强调色会出现两次（竖条 + 数量文字），所以按 x 切成簇之后要挑：
    竖条是**又高又窄**的那个。不用"最高的簇"就完事 —— 数量文字里的"1"这种窄字形
    也能堆出 2.9 的宽高比（实测），所以判据是宽高比 ≥ 1.5 且高度够。

    【为什么返回 None 而不是抛错】强调色也会出现在**物品贴图**里：默认的 common
    是灰色 #9AA4AD，而石头/信标/龙蛋的贴图全是灰的。于是"common"这个掩码里
    有好几个行带 —— 只有一个是真的卡，别的都是贴图。把这些带当成错误会让整张图
    测不了，当成卡又会让卡数凭空多出来。正确做法是：**没有竖条的带就不是卡，跳过并记账**。
    """
    ys, xs = np.where(mask)
    if len(xs) == 0:
        return None
    order = np.argsort(xs)
    xs, ys = xs[order], ys[order]
    clusters: list[tuple[int, int]] = []
    start = 0
    for i in range(1, len(xs)):
        if xs[i] - xs[i - 1] > 4:            # 间距 > 4px 就当断成两簇
            clusters.append((start, i - 1))
            start = i
    clusters.append((start, len(xs) - 1))

    best = None
    for a, b in clusters:
        cx0, cx1 = int(xs[a]), int(xs[b])
        cy0, cy1 = int(ys[a:b + 1].min()), int(ys[a:b + 1].max())
        w, h = cx1 - cx0 + 1, cy1 - cy0 + 1
        # 竖条是大致竖直的；文字是横的。给宽高比留 1.5 的余量。
        if h / max(1, w) < 1.5 or h < MIN_BAR_PX:
            continue
        # 竖条贴在卡片左缘，所以它左边那一列必须是背景。这条把"贴在物品贴图里
        # 的竖条状同色块"挡掉 —— 那种块左右都是别的东西，不是底。
        if cx0 > 0 and not bgmask[cy0:cy1 + 1, cx0 - 1].any():
            continue
        if best is None or h > best[3] - best[2] + 1:
            best = (cx0, cx1, cy0, cy1)
    return best


def _colorize(img: np.ndarray) -> np.ndarray:
    return np.asarray(img, dtype=np.int64)


def measure_cards(img: np.ndarray, accents: list[tuple[str, tuple[int, int, int]]],
                  bg=None, bg_tol: int = BG_TOL) -> dict:
    """
    量图。返回 {"bg", "size", "cards": [...]}，每张卡带：
      accent 强调色名 / bar 竖条 bbox / icon 图标格左右缘 / name 名字框左右缘 / card 上下缘
    """
    a = _colorize(img)
    if bg is None:
        bg = _dominant_bg(a)
    bg = tuple(int(v) for v in bg)
    bgmask = _near(a, bg, bg_tol)

    # 调色板分类只做一次：每个像素归给"底色 / 某个强调色"里最近的那个。
    palette = [bg] + [rgb for _, rgb in accents]
    idx = _palette_index(a, palette)

    cards = []
    notes: list[str] = []
    for i, (name, rgb) in enumerate(accents, start=1):
        mask = idx == i
        if not mask.any():
            notes.append(f"{name}: 整张图里没有这个强调色")
            continue
        # 先按行把各张卡分开（卡之间有 6px 间隙，强调色块不会跨卡连起来）
        for y0, y1 in _runs(mask.any(axis=1)):
            bar = _pick_bar(mask[y0:y1 + 1], bgmask[y0:y1 + 1])
            if bar is None:
                notes.append(f"{name}: 行 {y0}..{y1} 里没有竖条（只有横着的块），"
                             f"跳过 —— 多半是物品贴图用了同一个颜色")
                continue
            bx0, bx1, by0, by1 = bar
            bx0, bx1, by0, by1 = bx0, bx1, y0 + by0, y0 + by1

            # 竖条一定在卡片内部 → 拿它当"纵向带"，在带里按列找框
            strip = bgmask[by0:by1 + 1]
            hits = (~strip).sum(axis=0)
            cols = hits >= COLUMN_MIN_HITS
            boxes = [(s, e) for s, e in _runs(cols) if e - s + 1 >= 2]
            # 竖条自己那一列也算"非背景"，把与竖条重叠的段去掉
            boxes = [(s, e) for s, e in boxes if not (s <= bx1 and e >= bx0)]
            if len(boxes) < 2:
                raise MeasureError(
                    f"{name}: 竖条右侧只找到 {len(boxes)} 个框，三段式结构不成立\n"
                    f"      （竖条 x={bx0}..{bx1}，带 y={by0}..{by1}，"
                    f"请检查背景色是否与卡面太接近）")
            icon, namebox = boxes[0], boxes[1]

            # 卡高：取"卡身 x 范围内任意一列有非背景像素"的那些行。
            # 【为什么不能只竖着扫一列】圆角！离左缘 2px 的那一列只在圆角以内存在，
            # 量出来会比真卡矮一截；而离左缘够远（≥ 半径）的列上又有物品贴图。
            # 半径(6) > 内边距(4)，所以"既躲开圆角又躲开贴图"的列根本不存在。
            left = min(bx0, icon[0])
            right = max(namebox[1], icon[1])
            # 【括号不能省】`~a[:, x].any(1)` 会先算 any 再取反 = "一个非背景像素都没有"，
            # 正好是想要的反面。这个坑当场就被 test_measure 抓到了。
            cardrows = (~bgmask[:, left:right + 1]).any(axis=1)
            top, bottom = _vertical_extent(cardrows, (by0 + by1) // 2)

            cards.append({
                "accent": name,
                "bar": (bx0, bx1, by0, by1),
                "icon": icon,
                "namebox": namebox,
                "card": (min(bx0, icon[0]), max(namebox[1], icon[1]), top, bottom),
                "row": (by0 + by1) // 2,
            })
    cards.sort(key=lambda c: c["row"])
    return {"bg": bg, "size": tuple(a.shape[:2]), "cards": cards, "notes": notes}


def _vertical_extent(rowmask: np.ndarray, y: int) -> tuple[int, int]:
    """从 y 往上/下走，直到离开 rowmask 的 True 区段。"""
    if not rowmask[y]:
        raise MeasureError(f"第 {y} 行不在卡片上，卡高量不出来")
    top = y
    while top > 0 and rowmask[top - 1]:
        top -= 1
    bottom = y
    n = len(rowmask)
    while bottom < n - 1 and rowmask[bottom + 1]:
        bottom += 1
    return top, bottom


def _dominant_bg(a: np.ndarray):
    """出现最多的颜色。测量页是纯底，所以这就是背景。"""
    colors, counts = np.unique(a.reshape(-1, 3), axis=0, return_counts=True)
    return colors[counts.argmax()]


def flatness(a: np.ndarray, bg, tol: int = BG_TOL) -> float:
    """
    背景占比。测量页必须是纯底 —— 棋盘格、世界画面、辅助线都会让"按背景切段"失效。
    低于 0.4 就直接拒收：宁可不出数，也不要出一个看不懂来源的数。
    """
    return float(_near(a, bg, tol).mean())


def check_card_count(m: dict, expect: int) -> list[str]:
    """
    卡数对不上就说明有带被跳过了（或被重复计入），这份度量不能直接用。
    设计侧与实现侧的卡数必须一致，否则"逐项对比"是在比不同的东西。
    """
    got = len(m["cards"])
    if got == expect:
        return []
    return [f"卡数 {got} != 期望 {expect}，这份度量不可信"]


def count_by_accent(m: dict) -> dict:
    """每种强调色各量到几张卡。用来确认"五个强调色都还在"。"""
    out: dict[str, int] = {}
    for c in m["cards"]:
        out[c["accent"]] = out.get(c["accent"], 0) + 1
    return out


# 指标的分类与报告顺序。
#   结构 = 由排版与主题决定，两边**必须一致**，不一致就是"没还原"；
#   参考 = 绝对值，跟着分辨率走，不做比较；
#   内容 = 跟着字体走（浏览器和游戏不是一个字体），差异是必然的，只报不判。
REF, STRUCT, CONTENT = "参考", "结构", "内容"
METRICS = [
    (REF, "卡高"),
    (STRUCT, "竖条宽 / 卡高"),
    (STRUCT, "竖条高 / 卡高"),
    (STRUCT, "条-图标格间距 / 卡高"),
    (STRUCT, "图标格-名字框间距 / 卡高"),
    (STRUCT, "图标格宽 / 卡高"),
    (STRUCT, "级差 / 卡高"),
    # 【为什么两条"极差"是内容项】右对齐时，左缘极差**完全**由卡宽分布决定，
    # 而卡宽跟着名字文字的宽度走 —— 浏览器和游戏不是一个字体，这个数永远不可能相等。
    # 真正属于结构的判决是"固定的是哪条边"（fixed_edge），它单列一行、必须一致。
    (CONTENT, "左缘极差 / 卡高"),
    (CONTENT, "右缘极差 / 卡高"),
    (CONTENT, "卡宽中位 / 卡高"),
    (CONTENT, "卡宽极差 / 卡高"),
]


def metrics(m: dict) -> dict:
    """按卡高归一。返回扁平字典，键就是报告里的那一行。"""
    cards = m["cards"]
    if not cards:
        raise MeasureError("没量到任何卡")
    h = _median([c["card"][3] - c["card"][2] + 1 for c in cards])
    bars = [c["bar"] for c in cards]
    bar_h = _median([b[3] - b[2] + 1 for b in bars])
    bar_w = _median([b[1] - b[0] + 1 for b in bars])
    gap1 = _median([c["icon"][0] - c["bar"][1] - 1 for c in cards])
    gap2 = _median([c["namebox"][0] - c["icon"][1] - 1 for c in cards])
    icon_w = _median([c["icon"][1] - c["icon"][0] + 1 for c in cards])
    lefts = [c["card"][0] for c in cards]
    rights = [c["card"][1] for c in cards]
    widths = [c["card"][1] - c["card"][0] + 1 for c in cards]
    tops = [c["card"][2] for c in cards]
    pitch = _median(np.diff(tops)) if len(tops) > 1 else float("nan")
    return {
        "卡高": h,
        "竖条宽 / 卡高": bar_w / h,
        "竖条高 / 卡高": bar_h / h,
        "条-图标格间距 / 卡高": gap1 / h,
        "图标格-名字框间距 / 卡高": gap2 / h,
        "图标格宽 / 卡高": icon_w / h,
        "级差 / 卡高": pitch / h,
        "左缘极差 / 卡高": (max(lefts) - min(lefts)) / h,
        "右缘极差 / 卡高": (max(rights) - min(rights)) / h,
        "卡宽中位 / 卡高": _median(widths) / h,
        "卡宽极差 / 卡高": (max(widths) - min(widths)) / h,
    }


def fixed_edge(mm: dict) -> str:
    """这张图上的卡固定的是哪条边。用来判"该左对齐还是右对齐"。"""
    return "右对齐" if mm["右缘极差 / 卡高"] < 0.04 else (
        "左对齐" if mm["左缘极差 / 卡高"] < 0.04 else "没对齐")


def _median(values) -> float:
    return float(np.median([v for v in values]))

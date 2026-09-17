#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
探针：对**任意一张图**报出它的结构 —— 尺寸、底色、内容分布、粗略地图。

【为什么要有它】对照工具要的是"测量页"（纯底、只有卡、无装饰），而参考图多半
是带装饰的渲染图或截图，`compare.py` 会直接拒收。在被拒收和"能测"之间需要一步：
先看清楚这张图里到底是什么、卡片占哪一块、底色干不干净 —— 再决定是直接量、
裁一刀量，还是得换个做法。

【它不是测量器】这里一个几何数字都不产出。产出几何的是 `measure.py`，
而它只吃干净输入。探针只回答"这张图长什么样"。

用法：
    python design/probe.py 某张图.png
    python design/probe.py 某张图.png --bg 1A1A1F     # 手动指定底色
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROWS, COLS = 32, 64
TOP_COLORS = 8
MAX_RUNS = 24


def load(path: Path):
    im = Image.open(path)
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        bg = Image.new("RGBA", im.size, (0, 0, 0, 255))
        im = Image.alpha_composite(bg, im)
    return im.convert("RGB")


def runs_of(mask: np.ndarray):
    out = []
    start = None
    for i, on in enumerate(mask):
        if on and start is None:
            start = i
        elif not on and start is not None:
            out.append((start, i - 1))
            start = None
    if start is not None:
        out.append((start, len(mask) - 1))
    return out


def show_runs(label: str, mask: np.ndarray, total: int) -> None:
    rs = runs_of(mask)
    print(f"\n{label}（共 {len(rs)} 段）")
    if not rs:
        print("  （整张图都是底色）")
        return
    for i, (a, b) in enumerate(rs[:MAX_RUNS]):
        width = 40
        bar = "#" * max(1, round((b - a + 1) / total * width * 2))
        print(f"  {i:>2}  {a:>5}..{b:<5}  {b - a + 1:>5}  {bar}")
    if len(rs) > MAX_RUNS:
        print(f"  …… 还有 {len(rs) - MAX_RUNS} 段")


def ascii_map(nonbg: np.ndarray) -> None:
    h, w = nonbg.shape
    print(f"\n粗略地图（█ 有内容，· 底色），{COLS} 列 x {ROWS} 行：")
    for r in range(ROWS):
        y0, y1 = r * h // ROWS, max(r * h // ROWS + 1, (r + 1) * h // ROWS)
        line = ""
        for c in range(COLS):
            x0, x1 = c * w // COLS, max(c * w // COLS + 1, (c + 1) * w // COLS)
            frac = nonbg[y0:y1, x0:x1].mean()
            line += "█" if frac > 0.5 else ("▓" if frac > 0.2 else ("▒" if frac > 0.05 else "·"))
        print("  " + line)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("image", type=Path)
    ap.add_argument("--bg", default=None, help="手动指定底色，如 1A1A1F；默认取出现最多的颜色")
    ap.add_argument("--tol", type=int, default=30, help="判定「等于底色」的容差（三通道差之和）")
    args = ap.parse_args()

    if not args.image.is_file():
        print(f"找不到 {args.image}", file=sys.stderr)
        return 1

    im = load(args.image)
    a = np.array(im).astype(np.int64)
    h, w = a.shape[:2]
    print(f"文件  {args.image}   {w}x{h}   原图 {args.image.stat().st_size} 字节")

    flat = a.reshape(-1, 3)
    colors, counts = np.unique(flat, axis=0, return_counts=True)
    order = np.argsort(-counts)
    print(f"\n颜色分布（前 {TOP_COLORS} 名，共 {len(colors)} 种）")
    for i in order[:TOP_COLORS]:
        c = colors[i]
        print(f"  #{c[0]:02X}{c[1]:02X}{c[2]:02X}  {counts[i] / len(flat):>7.2%}"
              f"   rgb{tuple(int(v) for v in c)}")

    bg = (tuple(int(v) for v in colors[order[0]]) if args.bg is None
          else tuple(int(args.bg[i:i + 2], 16) for i in (0, 2, 4)))
    nonbg = np.abs(a - np.array(bg)).sum(axis=2) > args.tol * 3
    share = nonbg.mean()
    print(f"\n假定底色 #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}"
          f"（{'自动取最高频色' if args.bg is None else '命令行指定'}）")
    print(f"  非底色像素占 {share:.2%}，纯底占比 {1 - share:.2%}")
    if 1 - share < 0.4:
        print("  ⚠ 纯底不足 40%：这张图**不能**直接喂给 compare.py。"
              "要么底色不是这个（试试 --bg），要么它是装饰过的页面。")
    if not nonbg.any():
        print("  整张图都是这个颜色。")
        return 0

    ys, xs = np.where(nonbg)
    print(f"  内容外接框 x {xs.min()}..{xs.max()}   y {ys.min()}..{ys.max()}")

    show_runs("行方向：哪些行有内容", nonbg.any(axis=1), h)
    show_runs("列方向：哪些列有内容", nonbg.any(axis=0), w)
    ascii_map(nonbg)
    print("\n下一步：如果内容分成几条整齐的横带、底色又够干净，"
          "\n就把其中一条卡裁剪出来再量；否则先告诉我这张图是什么，我们换个做法。")
    return 0


if __name__ == "__main__":
    sys.exit(main())

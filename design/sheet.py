#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把几张图里的卡裁出来、同一尺度拼成一张表，一眼看完差异。

【为什么不能直接用 compare.py 的 --out】那个要求两边都是"测量页"；参考图不是
（它铺着棋盘格，卡面底色又和格子几乎一样，所以框的左右缘量不准）。
这里换一条不依赖框的裁法：竖条左缘 -> 该行带里最右边那个非底色像素。
参考图的卡面底色虽然吃掉了框的内部，但**框的描边还在**，所以右缘照样拿得到。

用法：
    python design/sheet.py 参考图.png --bg 202028,2C2C36 --impl 游戏测量页 --out 对照表.png
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import measure  # noqa: E402

ACCENTS = [("common", (154, 164, 173)), ("uncommon", (255, 216, 61)),
           ("rare", (85, 235, 255)), ("xp", (125, 255, 138)), ("epic", (215, 139, 255))]


def cards_of(path: Path, bg, margin: int):
    """返回 [(emphasis名, 裁好的图)]，从竖条左缘裁到该行带最右的非底色。"""
    a = np.array(Image.open(path).convert("RGB")).astype(np.int64)
    m = measure.measure_cards(a, ACCENTS, bg=bg)
    d = measure._bg_dist(a, measure.bg_list(bg))
    out = []
    for c in m["cards"]:
        bx0, _, by0, by1 = c["bar"]
        rows = d[by0:by1 + 1] > measure.BG_TOL
        xs = np.where(rows.any(axis=0))[0]
        right = int(xs.max())
        left = max(0, bx0 - margin)
        top = max(0, by0 - margin)
        bot = min(a.shape[0], by1 + margin + 1)
        crop = Image.open(path).convert("RGB").crop((left, top, min(a.shape[1], right + margin + 1), bot))
        out.append((c["accent"], crop))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("ref", type=Path, help="参考图（或任意一张）")
    ap.add_argument("--bg", default=None, help="参考图的底色，棋盘格用逗号分隔两个")
    ap.add_argument("--impl", type=Path, default=None, help="第二张图（通常是游戏测量页）")
    ap.add_argument("--impl-bg", default=None, help="第二张图的底色，默认纯黑")
    ap.add_argument("-o", "--out", type=Path, default=Path("design/sheet.png"))
    args = ap.parse_args()

    def parse(s):
        return None if s is None else [tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))
                                       for c in s.split(",")]

    margin = 6
    groups = [(args.ref.name, cards_of(args.ref, parse(args.bg), margin))]
    if args.impl is not None:
        ibg = parse(args.impl_bg) if args.impl_bg else [(0, 0, 0)]
        groups.append((args.impl.name, cards_of(args.impl, ibg, margin)))

    maxw = max(img.width for _, cards in groups for _, img in cards)
    lab = 150
    width = lab + maxw
    height = 8 + sum(sum(i.height + 6 for _, i in cards) + 26 for _, cards in groups)
    canvas = Image.new("RGB", (width, height), (0, 0, 0))
    from PIL import ImageDraw
    draw = ImageDraw.Draw(canvas)

    y = 6
    for title, cards in groups:
        draw.text((6, y), f"== {title[:22]} ==", fill=(120, 200, 255))
        y += 20
        for accent, img in cards:
            draw.text((6, y + img.height // 2 - 5), accent, fill=(180, 190, 210))
            canvas.paste(img, (lab, y))
            y += img.height + 6
        y += 6
    canvas.save(args.out)
    print(f"{args.out}  ({canvas.width}x{canvas.height})")
    print("  同一尺度直接比：左列是强调色名，每组第一行是标题。")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
像素对照：把「设计稿」和「游戏截图」用同一套算法量一遍，按卡高归一，输出差异表。

【为什么需要它】在这之前，"还原得对不对"只能靠人眼看、靠嘴说。实测吃过一次亏：
草稿和游戏里差了"竖条该不该上下内缩""该左对齐还是右对齐"两条**结构差异**，
而当时两边没有任何可比对的依据 —— 我看不到用户看到的，用户也说不清我看到的是什么。

同一套算法跑两张图，测量口径就一致了；归一化后不同分辨率也能比。
这张表就是 Q7 里说的"客观门"：先把能客观判定的量出来，再谈好看不好看。

用法：
    python design/compare.py 设计图.png 游戏截图.png
    python design/compare.py --solo 某张图.png          # 只量一张
"""

import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TOKENS = ROOT / "design" / "tokens.css"

# 要从 tokens.css 读的强调色（对照工具跟着真源走，不自己另存一份）
ACCENT_NAMES = ["common", "uncommon", "rare", "epic", "xp"]


def load_accents():
    """从 tokens.css 读出 :root 里的强调色，返回 [(名字, (r,g,b))]。"""
    text = re.sub(r"/\*.*?\*/", " ", TOKENS.read_text(encoding="utf-8"), flags=re.S)
    root = re.search(r":root\s*\{(.*?)\}", text, flags=re.S)
    body = root.group(1) if root else ""
    out = []
    for name in ACCENT_NAMES:
        m = re.search(rf"--pc-accent-{name}\s*:\s*#([0-9a-fA-F]{{6}})", body)
        if m:
            v = m.group(1)
            out.append((name, (int(v[0:2], 16), int(v[2:4], 16), int(v[4:6], 16))))
    return out


def background_color(a):
    """图像里出现最多的颜色 —— 在对照稿和截图里都是背景。"""
    flat = a.reshape(-1, 3)
    colors, counts = np.unique(flat, axis=0, return_counts=True)
    return colors[counts.argmax()]


def is_bg(px, bg, tol=14):
    return abs(int(px[0]) - int(bg[0])) + abs(int(px[1]) - int(bg[1])) + abs(int(px[2]) - int(bg[2])) <= tol * 3


def find_bars(a, accents):
    """
    找出每张卡的稀有度竖条。
    强调色同时出现在竖条和数量文字上，所以按 x 聚类后取「最高的那一簇」= 竖条。
    """
    bars = []
    for name, rgb in accents:
        m = (np.abs(a - np.array(rgb)).sum(axis=2) <= 45)
        ys, xs = np.where(m)
        if len(ys) == 0:
            continue
        # 按 x 聚类，簇间隔 > 8px 就断开
        order = np.argsort(xs)
        clusters, cur = [], [order[0]]
        for i in order[1:]:
            if xs[i] - xs[cur[-1]] <= 8:
                cur.append(i)
            else:
                clusters.append(cur)
                cur = [i]
        clusters.append(cur)
        # 只要"够高"的簇：强调色同时出现在数量文字上，文字那一簇矮而宽，必须排掉。
        # 这也顺手排掉了竖条被裁一半之类的异常情况。
        tall = [c for c in clusters if ys[c].max() - ys[c].min() >= 0.5 * (ys.max() - ys.min())]
        if not tall:
            tall = clusters
        best = max(tall, key=lambda c: ys[c].max() - ys[c].min())
        cy, cx = ys[best], xs[best]
        bars.append({
            "name": name,
            "x0": int(cx.min()), "x1": int(cx.max()),
            "y0": int(cy.min()), "y1": int(cy.max()),
        })
    bars.sort(key=lambda b: b["y0"])
    return bars


def measure(path):
    a = np.array(Image.open(path).convert("RGB")).astype(int)
    bg = background_color(a)
    bars = find_bars(a, load_accents())
    if not bars:
        return None

    # 卡体：从竖条右缘往右扫竖条中线那一行，找第一段"非背景"
    for b in bars:
        row = (b["y0"] + b["y1"]) // 2
        # 竖条右缘往右，找第一个"明显比竖条暗"的像素 = 卡体起点。
        # 不用"回到背景色"是因为阴影可能把间隙填住，那样会量成 0。
        bar_px = a[row, b["x0"]:b["x1"] + 1].mean(axis=0)
        x = b["x1"] + 1
        limit = min(a.shape[1], b["x1"] + 200)
        while x < limit and a[row, x].sum() > bar_px.sum() * 0.75:
            x += 1
        b["body_x0"] = x
        # 卡体右缘：从 body_x0 往后，最后一个非背景像素（允许中间有背景，比如两个框之间的缝）
        x2 = x
        last = x
        gap = 0
        while x2 < a.shape[1] and gap < 40:
            if is_bg(a[row, x2], bg):
                gap += 1
            else:
                last = x2
                gap = 0
            x2 += 1
        b["body_x1"] = last
        # 卡体高度：在卡体中部一列，量非背景的垂直跨度
        col = (b["body_x0"] + b["body_x1"]) // 2
        y = b["y0"]
        while y > 0 and not is_bg(a[y, col], bg):
            y -= 1
        top = y + 1
        y = b["y1"]
        while y < a.shape[0] - 1 and not is_bg(a[y, col], bg):
            y += 1
        b["card_y0"], b["card_y1"] = top, y - 1
    return {"bars": bars, "bg": bg, "size": a.shape[:2]}


def report(label, m):
    if m is None:
        print(f"  {label}: 没检出卡片")
        return None
    bars = m["bars"]
    h = np.median([b["card_y1"] - b["card_y0"] + 1 for b in bars])
    pitch = np.median(np.diff([b["y0"] for b in bars])) if len(bars) > 1 else float("nan")
    bar_h = np.median([b["y1"] - b["y0"] + 1 for b in bars])
    bar_w = np.median([b["x1"] - b["x0"] + 1 for b in bars])
    gap_bar = np.median([b["body_x0"] - b["x1"] for b in bars])
    lefts = [b["x0"] for b in bars]
    rights = [b["body_x1"] for b in bars]
    print(f"  {label}  ({m['size'][1]}x{m['size'][0]}, {len(bars)} 张卡, 卡高 {h:.0f}px)")
    return {
        "卡高": h,
        "竖条宽 / 卡高": bar_w / h,
        "竖条高 / 卡高": bar_h / h,
        "条-卡体间距 / 卡高": gap_bar / h,
        "级差 / 卡高": pitch / h,
        "竖条左缘极差 / 卡高": (max(lefts) - min(lefts)) / h,
        "卡体右缘极差 / 卡高": (max(rights) - min(rights)) / h,
        "卡总宽 / 卡高": np.median([b["body_x1"] - b["x0"] + 1 for b in bars]) / h,
    }


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--solo" in sys.argv:
        if not args:
            print(__doc__)
            return 1
        print(f"单张测量：{args[0]}")
        report("实测", measure(args[0]))
        return 0
    if len(args) != 2:
        print(__doc__)
        return 1

    print("按卡高归一后的对照（同一个算法跑两张图，口径一致）\n")
    design = report(f"设计 {Path(args[0]).name}", measure(args[0]))
    impl = report(f"实现 {Path(args[1]).name}", measure(args[1]))
    if not design or not impl:
        return 1

    print(f"\n  {'指标':<22}{'设计':>10}{'实现':>10}{'差异':>10}")
    print("  " + "-" * 52)
    for k in design:
        d, i = design[k], impl[k]
        diff = i - d
        flag = ""
        if k != "卡高" and abs(diff) > 0.03:
            flag = "  ← 差得多"
        print(f"  {k:<22}{d:>10.3f}{i:>10.3f}{diff:>+10.3f}{flag}")
    print("\n  说明：竖条左缘极差 / 卡体右缘极差 = 0 表示那条边对齐；越大越参差。")
    return 0


if __name__ == "__main__":
    sys.exit(main())

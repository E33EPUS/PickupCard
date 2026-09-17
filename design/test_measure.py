#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
拿"已知答案的图"钉住 design/measure.py。

【为什么这是整个对照流程的第一块砖】v1 的 compare.py 里，"条-图标格间距"这个指标
**恒等于 1px**（判据是"从竖条右缘往右找第一个比竖条暗 75% 的像素"，而竖条比卡体亮，
条件在第一个像素就不成立）。它给出的每个数字都长得像个正常的数，所以没人怀疑它。
这个 bug 不是看出来的，是拿合成图测出来的 —— 那之后才明白：
**在拿一把尺子去量真东西之前，先量一个我知道多长的东西。**

所以这里自己画卡（几何全部已知），再让 measure 去量，看它量回来的是不是那些数。
合成图的画法与真实现刻意保持三处一致，否则测试就成了自欺欺人：
  - 竖条上下内缩、两侧圆角
  - 图标格里放一块**纯黑**的方块（真物品贴图就有纯黑的：龙蛋）
  - 数量用强调色画在名字框右端（和竖条同色，是竖条识别的主要干扰源）
"""

import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

import measure  # noqa: E402

BG = (0, 0, 0)
FILL_TOP = (38, 43, 56)
FILL_BOTTOM = (22, 26, 34)
NAME_RGB = (235, 239, 246)
ACCENTS = [("common", (154, 164, 173)), ("rare", (85, 235, 255)),
           ("xp", (125, 255, 138)), ("epic", (215, 139, 255))]

# 一像素 = 一个逻辑像素 * S。S>1 是为了让边界的抗锯齿误差相对更小，
# 也让"差 1px"这种判据有实际意义。
S = 4


class Card:
    """一张卡的几何，单位是屏幕像素（已乘 S）。"""

    def __init__(self, bar_w, gap, icon_w, pad_v, inset_y, radius, name_w, x, y):
        self.bar_w, self.gap, self.icon_w = bar_w, gap, icon_w
        self.pad_v, self.inset_y, self.radius = pad_v, inset_y, radius
        self.name_w = name_w
        self.x, self.y = x, y
        self.h = icon_w                       # 三段等高：图标格是正方形

    @property
    def width(self):
        return self.bar_w + self.gap + self.icon_w + self.gap + self.name_w


def _rounded(w, h, r):
    """本地窗口里的圆角矩形掩码，边是硬的（测试要的是精确答案，不是抗锯齿）。"""
    yy, xx = np.mgrid[0:h, 0:w]
    dx = np.abs(xx + 0.5 - w / 2.0) - max(w / 2.0 - r, 0.0)
    dy = np.abs(yy + 0.5 - h / 2.0) - max(h / 2.0 - r, 0.0)
    return (np.sqrt(np.maximum(dx, 0) ** 2 + np.maximum(dy, 0) ** 2)
            + np.minimum(np.maximum(dx, dy), 0.0) - r) <= 0


def _fill(img, x, y, w, h, rgb, r=0, border=None):
    """往局部窗口里填一个圆角矩形（可选 1px 描边）。"""
    assert x >= 0 and y >= 0 and x + w <= img.shape[1] and y + h <= img.shape[0],         f"窗口出界：x={x} y={y} w={w} h={h} img={img.shape[1]}x{img.shape[0]}"
    img[y:y + h, x:x + w][_rounded(w, h, r)] = rgb
    if border is not None:
        img[y:y + h, x:x + w][_rounded(w, h, r) & ~_rounded(w, h, max(r - 1, 0))] = border


def _stroke(img, x, y, w, h, r, rgb):
    """只画 1px 描边那一圈（真实现的每个框都有描边）。"""
    outer = _rounded(w, h, r)
    inner = np.zeros_like(outer)
    inner[1:h - 1, 1:w - 1] = _rounded(w - 2, h - 2, max(r - 1, 0))
    img[y:y + h, x:x + w][outer & ~inner] = rgb


def _gradient_fill(img, x, y, w, h, r):
    """渐变底：真实现的框底色是上下两色的线性渐变，所以不能用一个纯色冒充。"""
    assert x >= 0 and y >= 0 and x + w <= img.shape[1] and y + h <= img.shape[0],         f"窗口出界：x={x} y={y} w={w} h={h} img={img.shape[1]}x{img.shape[0]}"
    m = _rounded(w, h, r)
    t = np.linspace(0.0, 1.0, h)[:, None]
    top, bot = np.array(FILL_TOP, float), np.array(FILL_BOTTOM, float)
    plane = (1 - t) * top + t * bot                       # (h, 3)
    sub = np.broadcast_to(plane[:, None, :], (h, w, 3))
    img[y:y + h, x:x + w][m] = sub[m].astype(np.int64)


def render(cards, accent_of, size):
    """画一张测量页。每个框都带描边，名字与数量都画成色块 —— 真实现里都有。"""
    w, h = size
    img = np.zeros((h, w, 3), dtype=np.int64)
    img[:, :] = BG
    for c in cards:
        rgb = accent_of(c)
        # 1) 竖条：上下各内缩 inset_y
        _fill(img, c.x, c.y + c.inset_y, c.bar_w, c.h - 2 * c.inset_y, rgb,
              r=int(min(c.radius, c.bar_w / 2)))
        # 2) 图标格 + 3) 名字框：渐变底 + 1px 描边
        icon_x = c.x + c.bar_w + c.gap
        name_x = icon_x + c.icon_w + c.gap
        for bx, bw in ((icon_x, c.icon_w), (name_x, c.name_w)):
            _gradient_fill(img, bx, c.y, bw, c.h, int(c.radius))
            _stroke(img, bx, c.y, bw, c.h, int(c.radius), (46, 46, 46))
        # 图标：纯黑的方块，居中（真物品贴图里就有纯黑的）
        side = c.icon_w - 2 * c.pad_v
        _fill(img, icon_x + c.pad_v, c.y + c.pad_v, side, side, (0, 0, 0))
        # 名字（亮色）与数量（强调色）—— 数量故意用强调色，当竖条的干扰源
        ty, th = c.y + c.h // 2 - 5 * S // 2, 5 * S
        _fill(img, name_x + 2 * S, ty, int(c.name_w * 0.6), th, NAME_RGB)
        cnt_w = max(S * 4, int(c.name_w * 0.3))
        _fill(img, name_x + c.name_w - cnt_w - S, ty, cnt_w, th, rgb)
    return img


def stack(widths, gap=4 * S, bar_w=5 * S, icon_w=32 * S, pad_v=4 * S, inset=2 * S,
          radius=6 * S, pitch_extra=6 * S, align="right", size=(900, 840)):
    """排一列等高的卡。align 决定哪个 x 固定。"""
    cards = []
    y = 20
    widest = max(bar_w + gap + icon_w + gap + w for w in widths)
    for nw in widths:
        total = bar_w + gap + icon_w + gap + nw
        # 右对齐 = 右缘固定；左对齐 = 左缘固定
        x = size[0] - 40 - widest + (widest - total) if align == "right" else 40
        cards.append(Card(bar_w, gap, icon_w, pad_v, inset, radius, nw, x, y))
        y += icon_w + pitch_extra
    return cards


BASE = dict(widths=[30 * S, 46 * S, 60 * S, 84 * S, 110 * S])


def measure_stack(**kw):
    size = kw.pop("size", (900, 840))
    cards = stack(size=size, **kw)
    accent = {id(c): ACCENTS[2][1] for c in cards}      # 全部同色：最严的情形
    img = render(cards, lambda c: accent[id(c)], size)
    return cards, measure.measure_cards(img, [ACCENTS[2]])


def test_recovers_known_geometry():
    cards, m = measure_stack(**BASE)
    assert len(m["cards"]) == len(cards), "卡数不对：强调色分带出错了"
    mm = measure.metrics(m)
    c0 = cards[0]
    assert mm["卡高"] == pytest.approx(c0.h, abs=1)
    assert mm["竖条宽 / 卡高"] * mm["卡高"] == pytest.approx(c0.bar_w, abs=1)
    assert mm["竖条高 / 卡高"] * mm["卡高"] == pytest.approx(c0.h - 2 * c0.inset_y, abs=1)
    assert mm["条-图标格间距 / 卡高"] * mm["卡高"] == pytest.approx(c0.gap, abs=1)
    assert mm["图标格-名字框间距 / 卡高"] * mm["卡高"] == pytest.approx(c0.gap, abs=1)
    assert mm["图标格宽 / 卡高"] * mm["卡高"] == pytest.approx(c0.icon_w, abs=1)
    assert mm["级差 / 卡高"] * mm["卡高"] == pytest.approx(c0.h + 6 * S, abs=1)


def test_gap_metric_actually_responds_to_gap():
    """
    v1 的尸体：间距量出来恒等于 1px。这条测试要求"尺子"能区分两个已知长度 ——
    能量准 8px 和 24px 并说出它们不一样，比"量出来差不多对"更接近可信。
    """
    measured = {}
    for gap_px in (4, 12):
        cards, m = measure_stack(gap=gap_px * S, **BASE)
        mm = measure.metrics(m)
        measured[gap_px] = (mm["条-图标格间距 / 卡高"],
                            mm["图标格-名字框间距 / 卡高"]) 
    for gap_px in (4, 12):
        for got in measured[gap_px]:
            assert got * 32 * S == pytest.approx(gap_px * S, abs=1), \
                f"真实间距 {gap_px * S}px，量出来 {got * 32 * S:.1f}px"
    assert measured[12][0] > measured[4][0] + 0.2, "尺子分不出小间距和大间距"


def test_count_text_is_not_mistaken_for_the_bar():
    """数量文字与竖条同色，且可能堆得比竖条的一半还高。竖条必须是那个又高又窄的。"""
    cards, m = measure_stack(widths=[150 * S])
    bar = m["cards"][0]["bar"]
    assert bar[1] - bar[0] + 1 == pytest.approx(5 * S, abs=1), "把数量文字当成竖条了"


def test_black_icon_does_not_split_the_icon_box():
    """龙蛋这类贴图在卡片中线是纯黑的 —— 与纯黑背景同色。按列判定才不会把框切断。"""
    cards, m = measure_stack(**BASE)
    for c, got in zip(cards, m["cards"]):
        assert got["icon"][1] - got["icon"][0] + 1 == pytest.approx(c.icon_w, abs=1)


@pytest.mark.parametrize("align,expect", [("right", "右对齐"), ("left", "左对齐")])
def test_fixed_edge_is_detected(align, expect):
    _, m = measure_stack(align=align, **BASE)
    assert measure.fixed_edge(measure.metrics(m)) == expect


def test_checkerboard_background_is_rejected():
    """
    棋盘底、世界画面、辅助线都会让"按背景切段"失效。测量页必须拒收这种输入 ——
    宁可不出数，也不要出一个看不出错在哪的数。
    """
    cards = stack(**BASE)
    img = render(cards, lambda c: ACCENTS[2][1], (900, 840))
    cell = 12
    for y in range(0, img.shape[0], cell):
        for x in range(0, img.shape[1], cell):
            if (x // cell + y // cell) % 2:
                img[y:y + cell, x:x + cell] += 28
    assert measure.flatness(img, BG) < 0.4


# ===========================================================================
# 端到端自检：像素扫描 vs 浏览器自己报的 DOM 矩形
#
# 合成图只能证明"算法对不对"，证明不了"真实页面量得准不准"（真实页面有渐变底、
# 描边、圆角、贴图）。这里拿同一张真实测量页的两份独立度量对撞：一份是我扫像素
# 得到的，一份是浏览器 layout 出来的。对不上就说明**量错了**，而不是画错了。
#
# 需要先跑：python design/shot.py design/measure.html --geometry
# 没有产物就跳过（不进 CI 也能用，不因为少一张图就红）。
# ===========================================================================

PAGE_PNG = Path(__file__).resolve().parent / "measure.png"
PAGE_DOM = Path(__file__).resolve().parent / "measure.geometry.json"


@pytest.mark.skipif(not (PAGE_PNG.is_file() and PAGE_DOM.is_file()),
                    reason="没有测量页产物，先跑 python design/shot.py design/measure.html --geometry")
def test_real_page_measurement_matches_dom():
    import json
    import re

    from PIL import Image

    dom = json.loads(PAGE_DOM.read_text(encoding="utf-8"))
    dsf = dom["dpr"]
    img = np.array(Image.open(PAGE_PNG).convert("RGB")).astype(np.int64)

    css = (Path(__file__).resolve().parent / "tokens.css").read_text(encoding="utf-8")
    accents = []
    for name in ("common", "uncommon", "rare", "epic"):
        v = re.search(rf"--pc-accent-{name}\s*:\s*#([0-9a-fA-F]{{6}})", css).group(1)
        accents.append((name, tuple(int(v[i:i + 2], 16) for i in (0, 2, 4))))

    m = measure.measure_cards(img, accents, bg=(0, 0, 0))
    assert len(m["cards"]) == len(dom["cards"])

    for got, want in zip(m["cards"], dom["cards"]):
        bx0, bx1, by0, by1 = got["bar"]
        checks = {
            "竖条宽": (want["bar"]["w"] * dsf, bx1 - bx0 + 1),
            "竖条高": (want["bar"]["h"] * dsf, by1 - by0 + 1),
            "条-图标格间距": ((want["icon"]["x"] - (want["bar"]["x"] + want["bar"]["w"])) * dsf,
                        got["icon"][0] - bx1 - 1),
            "图标格-名字框间距": ((want["info"]["x"] - (want["icon"]["x"] + want["icon"]["w"])) * dsf,
                        got["namebox"][0] - got["icon"][1] - 1),
            "图标格宽": (want["icon"]["w"] * dsf, got["icon"][1] - got["icon"][0] + 1),
            "卡高": (want["card"]["h"] * dsf, got["card"][3] - got["card"][2] + 1),
        }
        # 两边都换算成设备像素再比：DOM 报的是 CSS px，扫描量的是设备 px
        for label, (want_px, got_px) in checks.items():
            assert got_px == pytest.approx(want_px, abs=2), (
                f"{want['accent']} 的{label}：DOM {want_px:.1f} 设备px vs 扫描 {got_px} 设备px")

    pitch_dom = (dom["cards"][1]["card"]["y"] - dom["cards"][0]["card"]["y"]) * dsf
    got_pitch = m["cards"][1]["card"][2] - m["cards"][0]["card"][2]
    assert got_pitch == pytest.approx(pitch_dom, abs=2)

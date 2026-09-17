#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
拿 DOM 几何钉住 design/animation.html 的排版与入场。

【为什么要有它】handoff 里记着的那次翻车：游戏里是"底锚 + 老的在上面"，草稿是"顶锚 + 新的
在上面"，两边**方向相反**，而 compare.py 只逐张比卡的结构（竖条宽/高/间距），
**不比对卡堆的锚点和顺序** —— 于是那种完全没照草稿的排版一路绿灯，靠用户截图才发现。

所以这里不比像素，比的是"锚在哪、谁在哪一位"：这四件事只要被改动就会红：
  1. 锚点：最新一张的底边贴着 `屏高 - 16`，右边贴着 `屏宽 - 16`
  2. 顺序：第 i 张在 `底线 - i * 级差`，最新的在最下面
  3. 入场口固定：连开三张，最新那张每次落点都是同一个 y（这才叫"锚定"）
  4. 竖条原地出现 + 卡片右缘不越界（拉幕档曾经量的是"还没展开"的宽度，卡会飞出屏幕）

单位是**逻辑 px**（MC 的逻辑分辨率 427x240）：读 `getBoundingClientRect`（眼睛看到的位置），
再按 `.stage` 那层 `scale(2)` 除回去。**别用 offsetTop**：卡片是用 transform 摆的，
offsetTop 只认排版位置，量出来永远是 0 —— 那样这套测试会全绿而什么都没测。
"""

import sys
from pathlib import Path

import pytest
from pytest import approx

sys.path.insert(0, str(Path(__file__).resolve().parent))

import shot  # noqa: E402  复用它的浏览器回退逻辑（自带 chromium → Edge → Chrome）

PAGE = Path(__file__).resolve().parent / "animation.html"

STAGE_W, STAGE_H = 427, 240
MARGIN = 16
PITCH = 26          # 卡高 22 + 间隙 4
MAX = 5
# 底边留白 52：MC 的快捷栏 + 血量/饥饿/护甲占到 H−49，卡堆必须躲开（与 CardStage.MARGIN_Y 同一个数）
HUD_SAFE = 52
BOTTOM = STAGE_H - HUD_SAFE
# 竖条左缘的锚点：草稿默认的"锚点 X"滑块值（427 宽的舞台 = 游戏在 guiScale 3 下的画布宽，
# 自动锚点 = 屏宽 - 右边距 - 屏宽×0.45 = 219）。**锚在左缘，不是右缘** —— 2026-09-17 用户拍到
# "竖条应该锚定位置，而不是跟着卡片长度"，右缘固定会让卡越宽竖条越靠左。
ANCHOR_X = 219
# 卡片用 transform 摆、外面还套了一层 scale(2)，量回来是亚像素，不能拿 == 跟整数比
TOL = 0.35

# 最新的那张是"最后一个非 leaving 的 .slot"：DOM 里按加入顺序排，我们的数组是反过来的。
GEOM = """
() => {
  // 【别用 offsetTop】卡片是用 transform 摆的，而 offsetTop 只认排版位置 ——
  // 量出来永远是 0（这个坑本仓库在 measure.py 里踩过一次：锚点一条都测不出来）。
  // getBoundingClientRect 才是"眼睛看到的位置"；再按 `.stage` 那层 scale(2) 除回去，
  // 得到的就是逻辑 px。
  const stage = document.getElementById('stage');
  const r0 = stage.getBoundingClientRect();
  const k = r0.width / 427;
  const box = el => { const r = el.getBoundingClientRect();
                      return { x: (r.x - r0.x) / k, y: (r.y - r0.y) / k,
                               w: r.width / k, h: r.height / k }; };
  const all = [...stage.querySelectorAll(':scope > .slot')];
  const live = all.filter(s => !s.classList.contains('leaving'));
  return {
    live: live.map(s => ({ ...box(s), card: box(s.firstElementChild),
                           bar: box(s.querySelector('.bar')),
                           body: box(s.querySelector('.body')),
                           tunnel: box(s.querySelector('.tunnel')) })),
    leaving: all.filter(s => s.classList.contains('leaving'))
                .map(s => ({ ...box(s), opacity: +getComputedStyle(s).opacity })),
  };
}
"""

@pytest.fixture(scope="module")
def page():
    from playwright.sync_api import sync_playwright

    if not PAGE.is_file():
        pytest.skip("没有 draft 页面")
    with sync_playwright() as pw:
        browser = shot.launch(pw)
        p = browser.new_page(viewport={"width": 1280, "height": 720})
        p.goto(PAGE.resolve().as_uri())
        p.wait_for_load_state("load")
        p.click("#btn-play")            # 停掉自动出卡，否则测试跟定时器抢时序
        p.wait_for_timeout(1100)        # 页面自己那两个延时 spawn 先落完
        yield p
        browser.close()


def reset(page, exit_mode="train"):
    page.evaluate("m => { document.documentElement.dataset.exit = m; }", exit_mode)
    page.evaluate("() => window.clear()")
    page.wait_for_timeout(50)


def spawn(page, n=1, settle=420):
    for _ in range(n):
        page.evaluate("() => window.spawn()")
        page.wait_for_timeout(settle)
    return page.evaluate(GEOM)


def test_anchor_is_bottom_left_of_the_bar(page):
    """最新的一张：底边贴 224、**竖条左缘**停在锚点；第 i 张在底线往上 i * 级差。"""
    reset(page)
    g = spawn(page, 3)
    assert len(g["live"]) == 3
    newest = g["live"][-1]
    assert newest["y"] + newest["card"]["h"] == approx(BOTTOM, abs=TOL), "最新的一张没贴着下边距"
    assert newest["x"] == approx(ANCHOR_X, abs=TOL), "竖条没停在锚点上"
    # live 按加入顺序排（老 -> 新），反过来就是"新的在最下面"
    for i, s in enumerate(reversed(g["live"])):
        want = BOTTOM - s["card"]["h"] - i * PITCH
        assert s["y"] == approx(want, abs=TOL), f"第 {i} 张不在底线往上 {i} 级差"
        assert s["x"] == approx(ANCHOR_X, abs=TOL),             "竖条没成一条线 —— 卡的长短不该影响竖条在哪（这正是用户报的那条）"


def test_entry_point_never_moves(page):
    """连开三张，最新那张每次都在同一个 y —— 锚定的意义就是这个。"""
    reset(page)
    seen = []
    for _ in range(3):
        seen.append(spawn(page, 1)["live"][-1]["y"])
    assert max(seen) - min(seen) < TOL, f"入场口在漂：{seen}"


def test_oldest_is_pushed_up_and_fades(page):
    """满员之后再来一张：老的往上走一格，最老的那张边升边淡出并消失。"""
    reset(page)
    g = spawn(page, MAX, settle=420)          # 先坐满
    assert len(g["live"]) == MAX
    top_before = g["live"][0]["y"]            # 最老的那张（live 是从老到新排的）

    page.evaluate("() => window.spawn()")     # 再来一张，旧的开始被顶
    page.wait_for_timeout(60)
    g = page.evaluate(GEOM)
    assert len(g["live"]) == MAX, "超过上限还留在屏上"
    assert len(g["leaving"]) == 1, "挤出去的那张应该还在淡出"
    gone = g["leaving"][0]
    assert gone["opacity"] < 1, "退役那张没有在淡出"
    assert gone["y"] < top_before, "退役那张没有往上走"
    assert gone["y"] > BOTTOM - gone["h"] - MAX * PITCH - 1, "退役那张跳过头了"

    page.wait_for_timeout(420)
    g = page.evaluate(GEOM)
    assert not g["leaving"], "淡出结束还没删掉"
    assert len(g["live"]) == MAX
    assert g["live"][-1]["y"] + g["live"][-1]["card"]["h"] == approx(BOTTOM, abs=TOL), \
        "补位之后入场口动了"


@pytest.mark.parametrize("exit_mode", ["train", "wipe"])
def test_bar_stands_still_and_card_never_leaves_screen(page, exit_mode):
    """竖条原地出现（x 全程不变），卡片右缘任何一刻都不越界。

    拉幕档曾经踩的坑：`card.offsetWidth` 在窗口宽度从 0 起跑时量到的是收缩态，
    右对齐于是把整张卡算到屏幕外面 —— 采样就能看见它一路往右爬。
    """
    reset(page, exit_mode)
    page.evaluate("() => window.spawn()")
    samples = []
    for _ in range(6):
        page.wait_for_timeout(70)
        g = page.evaluate(GEOM)["live"]
        if not g:
            continue
        s = g[-1]
        samples.append(s)
    assert samples
    xs = [s["bar"]["x"] for s in samples]
    assert max(xs) - min(xs) < TOL, f"竖条在动：x = {xs}"

    page.wait_for_timeout(500)
    settled = page.evaluate(GEOM)["live"][-1]
    assert settled["x"] == approx(ANCHOR_X, abs=TOL), "停稳后竖条没停在锚点上"
    assert settled["bar"]["x"] == approx(samples[0]["bar"]["x"], abs=TOL), \
        "竖条最终位置与起手位置不一致"
    # 拉幕档的窗宽是由 `--w`（整数 px）给的，跟内容自然宽最多差半像素；火车档不存在这一项
    assert settled["body"]["w"] == approx(settled["tunnel"]["w"], abs=0.6), \
        "内容宽与可见窗宽不等 —— 要么被压扁了，要么没收回去"

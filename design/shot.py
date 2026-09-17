#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
无头浏览器截图：把草稿页面拍成一张固定尺寸的 PNG。

【为什么要有它，而不是随手截屏】
"设计稿"必须是**可重复产出的**。手截的图换一次窗口大小、多一个浏览器书签栏，
像素级对照就全废了 —— 而对照表不会告诉你这一点，它只会给出数字。
这里把三件事钉死：视口尺寸、设备像素比、以及"等图片都加载完再拍"。

【--geometry 是干什么的】同一个页面，除了像素，还能把每个部件的**真实 DOM 矩形**
导出来。于是设计侧有两份独立的度量：像素扫描得到的、和浏览器自己报告的。
两者对不上就说明**量错了**，而不是画错了 —— 这条自检是像素对照能被信任的前提。

用法：
    python design/shot.py design/measure.html                 # 出 design/measure.png
    python design/shot.py design/measure.html -o x.png --dsf 4
    python design/shot.py design/measure.html --geometry      # 顺带导出 DOM 矩形
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 把每个部件的 DOM 矩形取出来。getBoundingClientRect 的单位是 CSS px。
GEOMETRY_JS = """
() => {
  const box = el => { const r = el.getBoundingClientRect();
                      return {x: r.x, y: r.y, w: r.width, h: r.height}; };
  return {
    dpr: window.devicePixelRatio,
    cards: [...document.querySelectorAll('.card')].map(card => ({
      accent: getComputedStyle(card).getPropertyValue('--accent').trim(),
      card: box(card),
      bar:  box(card.querySelector('.bar')),
      icon: box(card.querySelector('.icon')),
      info: box(card.querySelector('.info')),
    })),
  };
}
"""


def launch(pw):
    """
    起一个浏览器。先试 Playwright 自带的 chromium，没有就退回系统装的 Edge / Chrome。
    【为什么要有这个退回】自带 chromium 要先 `playwright install`（要联网），
    而 Windows 上几乎一定有 Edge。测量工具因为"浏览器没装"而跑不起来，
    结果就是没人跑它。
    """
    errors = []
    for channel in (None, "msedge", "chrome"):
        try:
            return pw.chromium.launch(**({} if channel is None else {"channel": channel}))
        except Exception as e:                      # noqa: BLE001 —— 换下一个再说的场景
            errors.append(f"{channel or 'bundled chromium'}: {type(e).__name__}")
    print("起不了浏览器：" + " ；".join(errors), file=sys.stderr)
    print("  （自带 chromium 装法：python -m playwright install chromium）", file=sys.stderr)
    raise SystemExit(1)


def shoot(html: Path, out: Path, width: int, height: int, dsf: int,
          geometry: Path | None) -> int:
    from playwright.sync_api import sync_playwright

    if not html.is_file():
        print(f"找不到 {html}", file=sys.stderr)
        return 1

    with sync_playwright() as pw:
        browser = launch(pw)
        page = browser.new_page(viewport={"width": width, "height": height},
                                device_scale_factor=dsf)
        page.goto(html.resolve().as_uri())
        page.wait_for_load_state("load")
        # 图片没加载完就拍，会拍到图标位置上的空白 —— 那张图看着"正常"，但少了两块像素
        page.evaluate("() => Promise.all([...document.images].map(i => i.decode().catch(() => {})))")
        if geometry is not None:
            data = page.evaluate(GEOMETRY_JS)
            geometry.parent.mkdir(parents=True, exist_ok=True)
            geometry.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                                encoding="utf-8", newline="\n")
            print(f"  DOM 矩形 {len(data['cards'])} 张卡（dpr={data['dpr']}） -> {geometry}")
        out.parent.mkdir(parents=True, exist_ok=True)
        page.screenshot(path=str(out))
        browser.close()

    print(f"  截图 {out}  （视口 {width}x{height} CSS px，设备像素比 {dsf} "
          f"-> 预计 {width * dsf}x{height * dsf} 像素）")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("html", type=Path, help="要拍的页面")
    ap.add_argument("-o", "--out", type=Path, default=None, help="输出 PNG（默认与页面同名）")
    ap.add_argument("--width", type=int, default=320, help="视口宽（CSS px）")
    ap.add_argument("--height", type=int, default=240, help="视口高（CSS px）")
    ap.add_argument("--dsf", type=int, default=4, help="设备像素比。越大边缘量得越准")
    ap.add_argument("--geometry", action="store_true", help="同时导出 DOM 矩形 JSON")
    args = ap.parse_args()

    out = args.out or args.html.with_suffix(".png")
    geom = out.with_suffix(".geometry.json") if args.geometry else None
    return shoot(args.html, out, args.width, args.height, args.dsf, geom)


if __name__ == "__main__":
    sys.exit(main())

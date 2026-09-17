#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
像素对照：把「设计稿」和「游戏截图」用同一套算法量一遍，按卡高归一，输出差异表。

【为什么需要它】在这之前，"还原得对不对"只能靠人眼看、靠嘴说。实测吃过一次亏：
草稿和游戏里差了"竖条该不该上下内缩""该左对齐还是右对齐"两条**结构差异**，
而当时两边没有任何可比对的依据 —— 我看不到用户看到的，用户也说不清我看到的是什么。

【为什么它必须配一张"测量页"】v1 是拿任意截图比的，量出来的一堆数都是垃圾，
而且**看起来很像真的**。当时把病归在输入上（"投影把间隙填住了"），其实算法本身就是坏的：
"从竖条右缘找第一个比竖条暗 75% 的像素"这条规则**永远**返回竖条右缘 +1，
因为竖条比卡体亮，条件在第一个像素就不成立 —— 间隙恒等于 1px，与真实值无关。
这个 bug 是 `design/test_measure.py` 拿合成图（已知答案）测出来的，不是看出来的。

三层各司其职：
    design/measure.py      看图算数（纯函数，有已知答案的测试钉着）
    design/probe.py        描述任意一张图里有什么（不产出几何）
    本文件                  拒绝不可信的输入 + 把差打印成人能读的表

用法：
    python design/compare.py 设计图.png 游戏截图.png --expect 4
    python design/compare.py 设计图.png 游戏图.png --out 并排.png
    python design/compare.py --solo 某张图.png
    python design/compare.py --solo 参考图.png --bg 202028,2C2C36   # 棋盘底
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import measure  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
TOKENS = ROOT / "design" / "tokens.css"

# 从 tokens.css 读强调色（对照工具跟着真源走，不自己另存一份）
ACCENT_NAMES = ["common", "uncommon", "rare", "epic", "xp"]

# 结构指标的报警阈值：差超过卡高的 2% 才算"差得多"。
# 【为什么是 2%】测量分辨率是 ±1 个设备像素；卡高 64px 时那就是 1.6%。
# 阈值比分辨率略宽一点，免得把量测噪声当成设计差异报出来。
TOL = 0.02

# 测量页专用的底色。纯黑不是随便挑的：投影是半透明的黑，叠在纯黑上等于没叠，
# 于是"卡片之外就是纯底"成立 —— 不用为测量往渲染里加任何开关。
MEASURE_BG = [(0, 0, 0)]


def load_accents():
    """从 tokens.css 读出强调色，返回 [(名字, (r,g,b))]，顺序固定。"""
    text = re.sub(r"/\*.*?\*/", " ", TOKENS.read_text(encoding="utf-8"), flags=re.S)
    root = re.search(r":root\s*\{(.*?)\}", text, flags=re.S)
    body = root.group(1) if root else ""
    out = []
    for name in ACCENT_NAMES:
        m = re.search(rf"--pc-accent-{name}\s*:\s*#([0-9a-fA-F]{{6}})", body)
        if m:
            v = m.group(1)
            out.append((name, tuple(int(v[i:i + 2], 16) for i in (0, 2, 4))))
    return out


def measure_one(path: Path, expect: int | None, bg=None):
    """
    读一张图 → 量它 → 顺手回答"这份输入能不能用"。

    量不出卡时**也返回一份结果**，因为"量不到"的原因必须报给用户 ——
    "纯底只占 12%，这是世界画面"比"没量到卡"有用得多。
    """
    a = np.array(Image.open(path).convert("RGB")).astype(np.int64)
    bg = MEASURE_BG if bg is None else bg
    flat = measure.flatness(a, bg)
    problems = []
    if flat < 0.4:
        problems.append(
            f"纯底只占 {flat:.0%} —— 这张图不能用。世界画面/辅助线/渐变底都会让"
            f"按底色切段失效。实现侧跑 -PharnessAuto=shot 会出 pickupcard-measure，"
            f"设计侧跑 python design/shot.py design/measure.html；"
            f"若底色不是纯黑（比如棋盘格），用 --bg RRGGBB[,RRGGBB] 说明")
    m = measure.measure_cards(a, load_accents(), bg=bg)
    if expect is not None:
        problems += measure.check_card_count(m, expect)
    mm = measure.metrics(m) if m["cards"] else None
    return {
        "m": m,
        "metrics": mm,
        "fixed": measure.fixed_edge(mm) if mm else None,
        "order": measure.accent_order(m) if mm else None,
        "px": int(round(mm["卡高"])) if mm else None,
        "flat": flat,
        "bg": bg,
        "problems": problems,
        "notes": m.get("notes", []),
        "path": path,
    }


def summarize_notes(notes: list[str]) -> str:
    """把一堆"某个行带里没竖条"的记账合成一句，别把报告淹掉。"""
    if not notes:
        return "无可疑行带"
    per: dict[str, int] = {}
    other = 0
    for n in notes:
        head = n.split(":", 1)[0]
        if head in ACCENT_NAMES:
            per[head] = per.get(head, 0) + 1
        else:
            other += 1
    parts = [f"{k} {v}" for k, v in per.items()]
    if other:
        parts.append(f"其它 {other}")
    return (f"跳过 {len(notes)} 个没有竖条的行带（" + "、".join(parts)
            + "）—— 多半是物品贴图用了同一个颜色")


def report(label: str, one) -> None:
    m = one["m"]
    per = measure.count_by_accent(m)
    got = " ".join(f"{k}×{v}" for k, v in per.items()) or "一张都没有"
    tall = f"卡高 {one['px']:.0f}px" if one["px"] else "卡高 ——"
    print(f"  {label:<8}{one['path'].name:<26}{m['size'][1]}x{m['size'][0]}"
          f"  纯底 {one['flat']:.0%}  {tall}  卡数 {len(m['cards'])}（{got}）")
    if m["cards"]:
        print(f"          {summarize_notes(one['notes'])}")
    for p in one["problems"]:
        print(f"      · {p}")


def table(first: dict, second: dict) -> int:
    """打印对照表。返回退出码：结构项不一致 = 客观门没过。"""
    a, b = first["metrics"], second["metrics"]
    print()
    print(f"  {'指标':<28}{first['name']:>10}{second['name']:>10}{'差异':>10}")
    print("  " + "-" * 58)
    bad: list[str] = []
    for kind, name in measure.METRICS:
        d, i = a[name], b[name]
        if kind == measure.REF:
            print(f"  {name:<28}{d:>10.1f}{i:>10.1f}{'':>10}   （绝对值，不比较）")
            continue
        diff = i - d
        if kind == measure.STRUCT:
            flag = "  ← 差得多" if abs(diff) > TOL else ""
            if flag:
                bad.append(f"{name} 差 {diff:+.3f}")
        else:
            flag = "  （跟着字体走，只报不判）"
        print(f"  {name:<28}{d:>10.3f}{i:>10.3f}{diff:>+10.3f}{flag}")

    fa, fb = first["fixed"], second["fixed"]
    mark = "" if fa == fb else "  ← 差得多"
    print()
    print(f"  {'固定的是哪条边':<26}{fa:>10}{fb:>10}{mark}")
    if fa != fb:
        bad.append(f"固定边 {fa} vs {fb}")

    # 【为什么要比颜色顺序】"最新的那张"在右下角锚定里只认得出位置、认不出身份：四张卡的
    # 几何一模一样。顺序反了（新的在下面 vs 老的在上面）逐张比结构是查不出来的，
    # 而它正是那次翻车真正错的地方。
    oa, ob = first["order"], second["order"]
    ja, jb = " → ".join(oa or []), " → ".join(ob or [])
    mark = "" if oa == ob else "  ← 差得多"
    # 单独一块：这两串比数字列长得多，塞进 10 字符的列里会糊成一团
    print("  强调色自上而下（谁在最上面）")
    print(f"    设计  {ja}")
    print(f"    实现  {jb}{mark}")
    if oa != ob:
        bad.append(f"颜色顺序 {ja} vs {jb}")

    res = 1.0 / min(first["px"], second["px"])
    print()
    print("  说明：结构项按卡高归一，两边分辨率不同也能比。")
    print("        内容项里卡宽跟着字体走（浏览器和游戏不是一个字体），差异是必然的。")
    print(f"        测量分辨率 ±1 设备像素 = 卡高的 {res:.1%}，报警阈值 {TOL:.0%}。")
    print()
    if bad:
        print("  客观门：不合格 —— " + "；".join(bad))
        return 1
    print("  客观门：结构项全部一致")
    return 0


def side_by_side(first: dict, second: dict, out: Path) -> None:
    """
    把两边的卡**放到同一个卡高**上，拼成一张图，一张一张对着看。

    【为什么要有这个】数字能回答"结构对不对"，回答不了"看着像不像"。而让人两眼在
    两个文件之间来回切没有效率，也容易把注意力放在无关的地方。拼到一张图上、
    同一个尺度、同一个左缘（都从竖条左缘起裁），差异会自己跳出来。

    【为什么是缩小而不是放大】把小的那张放大只是插值出并不存在的像素；缩小那张更大的
    正好相反 —— 相邻的真实像素取平均，不发明任何东西。这里 128 -> 64 是整数倍。
    """
    from PIL import ImageDraw

    img_a = Image.open(first["path"]).convert("RGB")
    img_b = Image.open(second["path"]).convert("RGB")
    target = min(first["px"], second["px"])
    margin = max(2, target // 16)
    pad = max(2, target // 8)

    def crop(img, card):
        bx0, _, _, _ = card["bar"]
        _, cx1, cy0, cy1 = card["card"]
        box = (max(0, bx0 - margin), max(0, cy0 - margin),
               min(img.width, cx1 + margin + 1), min(img.height, cy1 + margin + 1))
        c = img.crop(box)
        want = target + 2 * margin
        if c.height != want:
            c = c.resize((max(1, round(c.width * want / c.height)), want), Image.LANCZOS)
        return c

    strips = [(ca["accent"], crop(img_a, ca), crop(img_b, cb))
              for ca, cb in zip(first["m"]["cards"], second["m"]["cards"])]

    label_w = 96
    width = label_w + max(max(a.width, b.width) for _, a, b in strips)
    height = pad + sum(a.height + 4 + b.height + pad for _, a, b in strips)
    canvas = Image.new("RGB", (width, height), (0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    y = pad
    for accent, a, b in strips:
        draw.text((6, y + a.height // 2), accent, fill=(180, 190, 210))
        canvas.paste(a, (label_w, y))
        y += a.height
        draw.line((0, y + 1, width, y + 1), fill=(60, 60, 70), width=1)
        y += 4
        canvas.paste(b, (label_w, y))
        y += b.height + pad

    canvas.save(out)
    print()
    print(f"  并排对照图 -> {out}  （{canvas.width}x{canvas.height}）")
    print(f"    每对卡：上=左图 下=右图，都从竖条左缘裁起，都缩到卡高 {target}px")


def solo(path: Path, expect: int | None, bg=None) -> int:
    one = measure_one(path, expect, bg)
    print(f"单张测量：{path}")
    print()
    report("实测", one)
    if one["metrics"] is None:
        return 1
    print()
    for kind, name in measure.METRICS:
        print(f"  [{kind}] {name:<28}{one['metrics'][name]:>10.3f}")
    print()
    print(f"  固定的是哪条边：{one['fixed']}")
    return 1 if one["problems"] else 0


def main() -> int:
    argv = sys.argv[1:]

    def take(flag: str, count: int = 1):
        if flag not in argv:
            return None
        k = argv.index(flag)
        got = argv[k + 1:k + 1 + count]
        del argv[k:k + 1 + count]
        return got[0] if count == 1 else got

    expect = take("--expect")
    expect = int(expect) if expect is not None else None
    out = take("--out")
    out = Path(out) if out is not None else None
    raw_bg = take("--bg")
    bg = None
    if raw_bg is not None:
        bg = [tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) for c in raw_bg.split(",")]
    args = [a for a in argv if not a.startswith("--")]

    if "--solo" in argv:
        if not args:
            print(__doc__)
            return 1
        return solo(Path(args[0]), expect, bg)

    if len(args) != 2:
        print(__doc__)
        return 1

    design = measure_one(Path(args[0]), expect, bg)
    impl = measure_one(Path(args[1]), expect, bg)
    design["name"] = "设计"
    impl["name"] = "实现"

    print("输入检查（两边都必须是「卡片之外空无一物」的测量页）")
    print()
    report("设计", design)
    report("实现", impl)
    if design["metrics"] is None or impl["metrics"] is None:
        print()
        print("量不出卡，先修输入 —— 理由都在上面。")
        return 1

    nd, ni = len(design["m"]["cards"]), len(impl["m"]["cards"])
    if nd != ni:
        print()
        print(f"两边的卡数不一样（{nd} vs {ni}），逐项对比是在比不同的东西。")
        print("先把样例集对齐：设计侧 design/measure.html、实现侧 CardFixtures.measure()。")
        return 1

    code = table(design, impl)
    if out is not None:
        side_by_side(design, impl, out)
    return code


if __name__ == "__main__":
    sys.exit(main())

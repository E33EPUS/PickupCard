#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把反编译产物里的 SRG 名字（`m_237115_` / `f_96547_`）换成可读名。

【为什么需要它】生产版 mod jar 里的原版调用是 SRG 名字（Forge 的 reobf 干的），
而开发环境用的是官方（Mojang）名。于是"从 jar 反编译回来的源码"不能直接编译 ——
2026-09-17 恢复 pickupcard 0.1.0 时就是这么卡住的。

【怎么接】两份映射都在 ForgeGradle 的本地缓存里，各管一半：
    mcp_mappings.tsrg      obf 类名 → SRG 类名/成员名（还带数字 id）
    client_mappings.txt    obf 类名 → 官方名（Mojang 官方映射）
两条都从 obf 出发，所以**用 obf 做桥**就能拼出 SRG → 官方名。
重载要靠描述符消歧：tsrg 那边的描述符是 SRG 类型，先用"SRG 类 → 官方类"翻一遍再比。

【不猜】拼不出来的名字原样保留并报出来（宁可不换，也不要换错 —— 换错会编译过、
行为变，那是最难查的一类）。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# ForgeGradle 缓存里的两份映射（找不到就报出来，不静默）
DEFAULT_Tsrg = Path.home() / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/mcp_mappings.tsrg"
DEFAULT_OBF = Path.home() / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client_mappings.txt"

SRG_REF = re.compile(r"\b[fm]_(\d+)_\b")


class Maps:
    """两份映射拼出来的 SRG → 官方名。"""

    def __init__(self, tsrg: Path, obf: Path):
        self.srg_class_to_moj = self._read_class_bridge(tsrg, obf)
        self.obf_class_to_moj = self._read_obf_classes(obf)
        # (obf 类, obf 成员, 官方描述符) → 官方成员名
        self.members = {}
        self._read_obf_members(obf)
        # (obf 类, obf 成员, SRG 描述符) → SRG 成员名
        self.srg_here = {}
        self._read_srg_members(tsrg)
        self.miss = []

    # -- 读三张表 ---------------------------------------------------------

    def _read_class_bridge(self, tsrg: Path, obf: Path) -> dict:
        """obf 类名 → 官方类名（经 tsrg 的 obf→SRG 与官方文件的 obf→官方名）。"""
        srg_of_obf = {}
        for line in tsrg.read_text(encoding="utf-8").splitlines():
            if not line or line[0].isspace():
                continue
            parts = line.split()
            if len(parts) >= 2:
                srg_of_obf[parts[0]] = parts[1]
        moj_of_obf = {}
        for line in obf.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^(\S+) -> (\S+):$", line)
            if m:
                moj_of_obf[m.group(2)] = m.group(1).replace(".", "/")
        # obf → 官方名，直接拿官方的；SRG 类名只用来翻描述符
        return {srg_of_obf[o]: moj for o, moj in moj_of_obf.items() if o in srg_of_obf}

    def _read_obf_classes(self, obf: Path) -> dict:
        """obf 类名 → 官方类名。tsrg 的成员描述符用的是 **obf** 类名，翻描述符要用这张表。"""
        out = {}
        for line in obf.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^(\S+) -> (\S+):$", line)
            if m:
                out[m.group(2)] = m.group(1).replace(".", "/")
        return out

    def _read_obf_members(self, obf: Path) -> None:
        cls = None
        for line in obf.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^(\S+) -> (\S+):$", line)
            if m:
                cls = m.group(2)
                continue
            m = re.match(r"^\s+\S+ (\S+)\(([^)]*)\) -> (\S+)$", line)      # 方法
            if m and cls:
                self.members[(cls, m.group(3), "(" + m.group(2) + ")")] = m.group(1)
                continue
            m = re.match(r"^\s+\S+ (\S+) -> (\S+)$", line)                  # 字段
            if m and cls:
                self.members[(cls, m.group(2), "")] = m.group(1)

    def _read_srg_members(self, tsrg: Path) -> None:
        cls = None
        for line in tsrg.read_text(encoding="utf-8").splitlines():
            if not line:
                continue
            if not line[0].isspace():
                parts = line.split()
                cls = parts[0]
                continue
            parts = line.split()
            if len(parts) == 4:                                            # 方法
                self.srg_here[(cls, parts[0], self._moj_desc(parts[1]))] = parts[2]
            elif len(parts) == 3:                                          # 字段
                self.srg_here[(cls, parts[0], "")] = parts[1]

    def _params_only(self, desc: str) -> str:
        """描述符只留参数部分：`(Ljava/lang/String;)Ltj;` → `(Ljava/lang/String;)`。
        官方映射那边写的就是参数表，两边必须归一，否则永远配不上。"""
        depth = 0
        for i, ch in enumerate(desc):
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    return desc[:i + 1]
        return desc

    _PRIM = {"I": "int", "J": "long", "D": "double", "F": "float",
             "Z": "boolean", "B": "byte", "C": "char", "S": "short", "V": "void"}

    def _moj_desc(self, srg_desc: str) -> str:
        """tsrg 的 JVM 描述符 → **官方文件里那种源码写法**。

        【为什么必须转】两份文件的类型写法不是一回事：tsrg 写 `(Ljava/lang/String;)Ltj;`，
        Mojang 官方写 `(java.lang.String)`。不归一就永远配不上 —— 这一步卡了我一轮。
        """
        params = self._params_only(srg_desc)[1:-1]
        out, i = [], 0
        while i < len(params):
            arr = 0
            while params[i] == "[":
                arr += 1
                i += 1
            if params[i] == "L":
                j = params.index(";", i)
                name = self.obf_class_to_moj.get(params[i + 1:j], params[i + 1:j]).replace("/", ".")
                i = j + 1
            else:
                name = self._PRIM.get(params[i], params[i])
                i += 1
            out.append(name + "[]" * arr)
        return "(" + ",".join(out) + ")"

    # -- 查 ---------------------------------------------------------------

    def mojmap_name(self, srg: str, class_obf: str | None = None) -> str | None:
        """SRG 名 → 官方名。给不出就返回 None（调用方原样保留并记账）。"""
        for key, srg_name in self.srg_here.items():
            if srg_name != srg:
                continue
            obf_cls, obf_member, desc = key
            if class_obf is not None and obf_cls != class_obf:
                continue
            hit = self.members.get((obf_cls, obf_member, desc))
            if hit:
                return hit
        return None


def rename(src: Path, maps: Maps, dry: bool) -> int:
    """把 src 下所有 .java 里的 SRG 名换掉。返回换了多少处。"""
    changed = 0
    unresolved = {}
    for f in sorted(src.rglob("*.java")):
        text = f.read_text(encoding="utf-8")
        hits = set(SRG_REF.findall(text.strip()))

        def sub(m):
            nonlocal changed
            srg = m.group(0)
            new = maps.mojmap_name(srg)
            if new is None:
                unresolved[srg] = unresolved.get(srg, 0) + 1
                return srg
            changed += 1
            return new

        new_text = SRG_REF.sub(sub, text)
        if new_text != text and not dry:
            f.write_text(new_text, encoding="utf-8")
    print(f"  换了 {changed} 处")
    if unresolved:
        print(f"  ⚠ 有 {len(unresolved)} 个名字没拼出来（原样保留）：")
        for k, v in sorted(unresolved.items(), key=lambda kv: -kv[1])[:15]:
            print(f"      {k}  ×{v}")
    return changed


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("src", type=Path, help="反编译出来的源码目录")
    ap.add_argument("--tsrg", type=Path, default=DEFAULT_Tsrg)
    ap.add_argument("--obf", type=Path, default=DEFAULT_OBF)
    ap.add_argument("--dry", action="store_true", help="只说不写")
    args = ap.parse_args()

    for p in (args.src, args.tsrg, args.obf):
        if not p.exists():
            print(f"找不到 {p}", file=sys.stderr)
            return 1
    maps = Maps(args.tsrg, args.obf)
    print(f"映射表：SRG 类 {len(maps.srg_class_to_moj)} 个，成员 {len(maps.members)} 个")
    rename(args.src, maps, args.dry)
    return 0


if __name__ == "__main__":
    sys.exit(main())

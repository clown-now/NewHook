#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
字符串加密生成器 —— 复刻 WinHook 的 short[] XOR 手法。

WinHook 原始: out[i] = (char)(s[off+i] ^ key)
本工具把明文串 → short[] + key，运行时用同一个解密器还原。

用法:
  python3 strgen.py "com.luna.music"
  python3 strgen.py --batch strings.txt      # 每行一条，输出 Java 常量
"""
import sys

# 每个"解密器槽位"用不同的 key，混淆指纹；运行时 Key 也按槽位区分
KEYS = {
    0x5A3C: 0x5A3C,
    0x77E1: 0x77E1,
    0x2468: 0x2468,
    0x1357: 0x1357,
}


def enc(s: str, key: int):
    """明文 → (short[], key)，保证 XOR 后落在 char 范围。"""
    return [ord(c) ^ key for c in s], key


def fmt_array(vals):
    return "{" + ",".join(str(v) for v in vals) + "}"


def emit(s: str, key: int, name: str):
    vals, _ = enc(s, key)
    return (f"    private static final short[] {name} = {fmt_array(vals)};\n"
            f"    private static final int {name}_KEY = 0x{key:X};\n")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    if sys.argv[1] == "--batch":
        with open(sys.argv[2], encoding="utf-8") as f:
            lines = [l.rstrip("\n") for l in f if l.strip()]
        out = []
        for i, line in enumerate(lines):
            # round-robin 选 key
            key = list(KEYS.values())[i % len(KEYS)]
            out.append(emit(line, key, f"S{i}"))
        print("".join(out))
    else:
        key = 0x5A3C
        vals, _ = enc(sys.argv[1], key)
        print(f"plain = {sys.argv[1]!r}")
        print(f"key   = 0x{key:X}")
        print(f"arr   = {fmt_array(vals)}")

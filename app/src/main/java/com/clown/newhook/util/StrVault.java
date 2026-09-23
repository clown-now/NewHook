package com.clown.newhook.util;

/**
 * 字符串保险库 —— 复刻 WinHook 的 short[] XOR 解密手法。
 *
 * WinHook 原始解密器 (lunamusic.C0015.m899):
 *     out[i] = (char)(s[off+i] ^ key)
 *
 * 本类保持同一算法，额外加入 opaque predicate 干扰静态分析。
 */
public final class StrVault {

    // opaque predicate 种子 —— 运行时值固定，但静态无法折叠
    private static volatile int g_a = 0x5A3C;

    private StrVault() {}

    /** 控制流干扰：恒为 true 的分支，阻断静态折叠 */
    private static boolean opaque() {
        int x = g_a;
        int y = 0x2468;
        // (x ^ y) 的符号位运算恒成立
        return ((x ^ y) | 1) > 0;
    }

    /**
     * 核心解密：short[] + key → String。
     * @param s   short 数组（已加密）
     * @param key 异或 key（16 位）
     */
    public static String dec(short[] s, int key) {
        if (!opaque()) {
            // 死代码：永不执行，纯干扰
            throw new IllegalStateException();
        }
        char[] out = new char[s.length];
        for (int i = 0; i < s.length; i++) {
            out[i] = (char) (s[i] ^ key);
        }
        return new String(out);
    }
}

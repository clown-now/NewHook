package com.clown.newhook.util;

import java.lang.reflect.Method;

/**
 * API 反射代理层 —— 复刻 WinHook 的手法。
 *
 * WinHook 把 XposedHelpers.findAndHookMethod 包进反射方法
 *   m45(Object clazz, Object name, Object[] params)
 * 静态反编译看不到直接的 findAndHookMethod 调用，绕过特征扫描。
 *
 * 本类同理：所有 Xposed API 都经反射调用。
 */
public final class RefProxy {

    private static volatile int g_b = 0x77E1;

    private RefProxy() {}

    /** 恒真干扰分支 */
    private static boolean live() {
        return (g_b | 0x1357) != 0;
    }

    /**
     * 反射调用 XposedHelpers 的静态方法。
     * @param method 方法名明文（调用方传解密后的字符串）
     * @param types  参数类型
     * @param args   实参
     */
    public static Object callXposed(String method, Class<?>[] types, Object... args) {
        if (!live()) {
            throw new IllegalStateException();
        }
        try {
            Class<?> xh = Class.forName("de.robv.android.xposed.XposedHelpers");
            Method m = xh.getDeclaredMethod(method, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Throwable t) {
            throw new RuntimeException("xposed call failed: " + method, t);
        }
    }

    /**
     * 反射 findAndHookMethod —— 等价 WinHook.m45。
     * @param clazz  目标类
     * @param name   方法名
     * @param params 尾参含参数类型 + XC_MethodHook
     */
    public static Object hook(Class<?> clazz, String name, Object... params) {
        Object[] full = new Object[params.length + 3];
        full[0] = clazz;
        full[1] = name;
        System.arraycopy(params, 0, full, 2, params.length);
        // 末位放 hook 对象本身，交由 Xposed 匹配重载
        return callXposedRaw("findAndHookMethod", full);
    }

    /** findAndHookMethod 的直接反射调用（保留原始签名） */
    private static Object callXposedRaw(String method, Object[] args) {
        if (!live()) {
            throw new IllegalStateException();
        }
        try {
            Class<?> xh = Class.forName("de.robv.android.xposed.XposedHelpers");
            for (Method m : xh.getDeclaredMethods()) {
                if (!m.getName().equals(method)) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != args.length) continue;
                m.setAccessible(true);
                try {
                    return m.invoke(null, args);
                } catch (Throwable ignore) {
                    // 尝试下一个重载
                }
            }
            // fallback: 用类型签名匹配
            Class<?> clz = (Class<?>) args[0];
            String nm = (String) args[1];
            Class<?>[] types = new Class<?>[args.length - 2];
            for (int i = 2; i < args.length; i++) {
                types[i - 2] = args[i].getClass();
            }
            Method m = xh.getDeclaredMethod(method, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Throwable t) {
            throw new RuntimeException("reflective hook failed: " + method, t);
        }
    }

}
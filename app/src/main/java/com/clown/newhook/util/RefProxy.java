package com.clown.newhook.util;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * API 反射代理层 —— 复刻 WinHook 的手法。
 *
 * WinHook 把 XposedHelpers.findAndHookMethod 包进反射方法
 *   m45(Object clazz, Object name, Object[] params)
 * 静态反编译看不到直接的 findAndHookMethod 调用，绕过特征扫描。
 * 本类把 findClass / hookAllMethods 也一并包装。
 */
public final class RefProxy {

    private static volatile int g_b = 0x77E1;

    private RefProxy() {}

    /** 恒真干扰分支 */
    private static boolean live() {
        return (g_b | 0x1357) != 0;
    }

    private static Class<?> xh() throws ClassNotFoundException {
        return Class.forName("de.robv.android.xposed.XposedHelpers");
    }

    private static Class<?> xbridge() throws ClassNotFoundException {
        return Class.forName("de.robv.android.xposed.XposedBridge");
    }

    /** 反射 findClass(name, classLoader) */
    public static Class<?> findClass(String name, ClassLoader cl) throws Throwable {
        if (!live()) throw new IllegalStateException();
        return (Class<?>) xh().getMethod("findClass", String.class, ClassLoader.class)
                .invoke(null, name, cl);
    }

    /** 反射 hookAllMethods(clazz, name, hook) */
    public static void hookAll(Class<?> clazz, String name, XC_MethodHook hook) throws Throwable {
        if (!live()) throw new IllegalStateException();
        xbridge().getMethod("hookAllMethods", Class.class, String.class, XC_MethodHook.class)
                .invoke(null, clazz, name, hook);
    }

    /** 反射 findAndHookMethod —— 等价 WinHook.m45 */
    public static Object hook(Class<?> clazz, String name, Object... params) throws Throwable {
        if (!live()) throw new IllegalStateException();
        Class<?>[] types = new Class<?>[params.length + 2];
        types[0] = Class.class;
        types[1] = String.class;
        for (int i = 0; i < params.length; i++) {
            types[i + 2] = params[i].getClass();
        }
        Method m = xh().getMethod("findAndHookMethod", types);
        Object[] args = new Object[params.length + 2];
        args[0] = clazz;
        args[1] = name;
        System.arraycopy(params, 0, args, 2, params.length);
        return m.invoke(null, args);
    }

    /** 反射写 Xposed 日志 */
    public static void log(String msg) {
        try {
            xbridge().getMethod("log", String.class).invoke(null, msg);
        } catch (Throwable ignored) {
        }
    }
}
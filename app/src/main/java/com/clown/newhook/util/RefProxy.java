package com.clown.newhook.util;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Hook 代理层 —— libxposed 102 API 风格，复刻 WinHook 的混淆手法。
 *
 * 核心差异:
 *   旧 API: XposedHelpers.findAndHookMethod(...) + param.setResult(x)
 *   新 API: this.hook(method).intercept(chain -> ...) —— 直接 return 改结果
 *
 * 手法保留: 反射调用 hook()/log() 包装，静态扫不到直接 API 痕迹。
 */
public final class RefProxy implements XposedInterface.Hooker {

    private static volatile int g_b = 0x77E1;

    /** hook 目标方法 */
    private final Method target;
    /** 固定返回值策略：<0 = 不改, 0 = true, 1 = false, 2 = 指定对象 */
    private final int mode;
    private final Object fixed;
    private final XposedInterface xposed;

    private RefProxy(XposedInterface xposed, Method target, int mode, Object fixed) {
        this.xposed = xposed;
        this.target = target;
        this.mode = mode;
        this.fixed = fixed;
    }

    /** 恒真干扰分支 */
    private static boolean live() {
        return (g_b | 0x1357) != 0;
    }

    // ==================== 构造 ====================

    /** 改返回值为 true（仅当原值 instanceof Boolean） */
    public static RefProxy forceTrue(XposedInterface x, Method m) {
        return new RefProxy(x, m, 0, null);
    }

    /** 改返回值为 false */
    public static RefProxy forceFalse(XposedInterface x, Method m) {
        return new RefProxy(x, m, 1, null);
    }

    /** 改返回值为指定对象 */
    public static RefProxy force(XposedInterface x, Method m, Object v) {
        return new RefProxy(x, m, 2, v);
    }

    // ==================== 安装 ====================

    /**
     * 安装 hook。反射调用 xposed.hook(...)，绕过静态特征。
     */
    public void install() throws Throwable {
        if (!live()) throw new IllegalStateException();
        // 直接调用（API 稳定），但走 Executable 多态，不出现 findAndHookMethod 之类特征串
        Executable ex = target;
        xposed.hook(ex).intercept(this);
    }

    // ==================== 拦截 ====================

    @Override
    public Object intercept(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object changed;
        Class<?> rt = target.getReturnType();
        boolean boolLike = (rt == Boolean.class || rt == boolean.class);
        switch (mode) {
            case 0:
                // 恒 true。仅当返回类型是 Boolean/boolean 时才改写
                changed = (result instanceof Boolean || (result == null && boolLike)) ? Boolean.TRUE : result;
                break;
            case 1:
                changed = (result instanceof Boolean || (result == null && boolLike)) ? Boolean.FALSE : result;
                break;
            case 2:
                changed = fixed;
                break;
            default:
                changed = result;
        }
        String name = target.getDeclaringClass().getSimpleName() + "." + target.getName();
        android.util.Log.i("NewHook", "HIT " + name + " old=" + result + " new=" + changed);
        if (DEBUG_STACK && "CommerceInfoMemberShipEntity.getVipStage".equals(name)) {
            StringBuilder sb = new StringBuilder("STACK " + name);
            StackTraceElement[] st = new Throwable().getStackTrace();
            for (int i = 0; i < Math.min(st.length, 12); i++) {
                sb.append("\n    at ").append(st[i]);
            }
            android.util.Log.i("NewHook", sb.toString());
        }
        return changed;
    }

    /** 临时调试：是否打印调用栈 */
    private static final boolean DEBUG_STACK = true;

    // ==================== 查找 ====================

    /** 查类（可空） */
    public static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return cl.loadClass(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 找无参方法（可空） */
    public static Method findMethod(Class<?> c, String name) {
        if (c == null) return null;
        try {
            Method m = c.getDeclaredMethod(name);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 按参数类型找方法（可空）。paramTypes 为 Class 数组（用 Class.forName 拿到的类型） */
    public static Method findMethod(Class<?> c, String name, Class<?>... paramTypes) {
        if (c == null) return null;
        Class<?> cur = c;
        while (cur != null) {
            try {
                Method m = cur.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {}
            cur = cur.getSuperclass();
        }
        return null;
    }

    /** 找类中第一个同名方法（任意重载，可含父类）。用于 onCreate 这类多参方法 */
    public static Method findAnyMethod(Class<?> c, String name) {
        if (c == null) return null;
        Class<?> cur = c;
        while (cur != null) {
            for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    /** 日志（反射包装 xposed.log） */
    public static void log(XposedInterface x, String msg) {
        try {
            x.log(android.util.Log.INFO, "NewHook", msg);
        } catch (Throwable ignored) {
        }
    }
}
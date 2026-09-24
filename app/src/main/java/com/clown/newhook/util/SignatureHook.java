package com.clown.newhook.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/**
 * SignatureHook —— 移植自 MoonHook 的「类型签名扫描」策略。
 *
 * 传统硬编码 hook 的痛点：一旦目标类/方法改名，hook 全部失效。
 * MoonHook 的思路是<b>不看方法名，只看签名</b>：
 *   1) 0 参 + 返回 List  → 过滤/改写列表内容
 *   2) 1 参(类型X) + 返回 boolean/Boolean → 恒 true / 恒 false
 *   3) 3 参(X, boolean, Y) + 返回 boolean → 恒 true
 *   4) 按名称精确匹配（如 setValue）
 *   5) void 返回 + 首参 X → 触发回调
 *
 * 本类把上述规则做成可组合的扫描器。
 */
public final class SignatureHook {

    private SignatureHook() {}

    /** 统计安装结果 */
    public static final class Result {
        public int installed;
        public final List<String> tags = new ArrayList<>();
    }

    /**
     * 规则 1：无参 + 返回 List 的方法，交给 caller 改写返回内容。
     */
    public static int hookNoArgList(XposedInterface x, Class<?> c, ListRewriter rewriter) {
        if (c == null) return 0;
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (!List.class.isAssignableFrom(m.getReturnType())) continue;
            try {
                final ListRewriter rw = rewriter;
                x.hook(m).intercept(chain -> {
                    Object r = chain.proceed();
                    if (r instanceof List) {
                        List<?> in = (List<?>) r;
                        List<Object> out = new ArrayList<>();
                        for (Object o : in) {
                            if (rw != null && rw.drop(o)) continue;
                            out.add(o);
                        }
                        return out;
                    }
                    return r;
                });
                n++;
            } catch (Throwable ignored) {}
        }
        return n;
    }

    public interface ListRewriter {
        /** 返回 true 表示从列表中剔除该元素 */
        boolean drop(Object item);
    }

    /**
     * 规则 2：1 参（参数类型 == argType）+ 返回 boolean/Boolean 的方法，恒置 value。
     */
    public static int hook1ArgBool(XposedInterface x, Class<?> c, Class<?> argType, boolean value) {
        if (c == null) return 0;
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length != 1 || pt[0] != argType) continue;
            Class<?> rt = m.getReturnType();
            if (rt != boolean.class && rt != Boolean.class) continue;
            try {
                RefProxy.force(x, m, Boolean.valueOf(value)).install();
                n++;
            } catch (Throwable ignored) {}
        }
        return n;
    }

    /**
     * 规则 3：3 参（X, boolean, Y）+ 返回 boolean/Boolean 的方法，恒置 value。
     */
    public static int hook3ArgBool(XposedInterface x, Class<?> c, Class<?> a, Class<?> b, boolean value) {
        if (c == null) return 0;
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length != 3) continue;
            if (pt[0] != a || pt[1] != boolean.class || pt[2] != b) continue;
            Class<?> rt = m.getReturnType();
            if (rt != boolean.class && rt != Boolean.class) continue;
            try {
                RefProxy.force(x, m, Boolean.valueOf(value)).install();
                n++;
            } catch (Throwable ignored) {}
        }
        return n;
    }

    /**
     * 规则 4：按名称 + 参数个数精确匹配。
     */
    public static int hookByName(XposedInterface x, Class<?> c, String name, int argc, Object fixed) {
        if (c == null) return 0;
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            if (argc >= 0 && m.getParameterCount() != argc) continue;
            Class<?> rt = m.getReturnType();
            if (fixed instanceof Boolean && rt != boolean.class && rt != Boolean.class) continue;
            try {
                RefProxy.force(x, m, fixed).install();
                n++;
            } catch (Throwable ignored) {}
        }
        return n;
    }

    /**
     * 规则 5：全部无参 boolean 方法（含本类声明），恒置 value。
     * 用于"整个配置类开关全灭"。
     */
    public static int hookAllNoArgBool(XposedInterface x, Class<?> c, boolean value, boolean includeStatic) {
        if (c == null) return 0;
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) continue;
            if (!includeStatic && Modifier.isStatic(m.getModifiers())) continue;
            String mn = m.getName();
            if ("<clinit>".equals(mn) || "<init>".equals(mn)) continue;
            try {
                RefProxy.force(x, m, Boolean.valueOf(value)).install();
                n++;
            } catch (Throwable ignored) {}
        }
        return n;
    }

    /**
     * 规则 6：void 返回 + 首个参数类型为 argType 的方法，触发 onCall。
     */
    public static int hookVoidWithFirstArg(XposedInterface x, Class<?> c, Class<?> argType, Callback cb) {
        if (c == null) return 0;
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length < 1 || pt[0] != argType) continue;
            if (m.getReturnType() != void.class) continue;
            try {
                final Callback cbf = cb;
                x.hook(m).intercept(chain -> {
                    Object r = chain.proceed();
                    if (cbf != null) cbf.onCall(chain.getThisObject(), null);
                    return r;
                });
                n++;
            } catch (Throwable ignored) {}
        }
        return n;
    }

    public interface Callback {
        void onCall(Object thisObj, Object[] args);
    }
}
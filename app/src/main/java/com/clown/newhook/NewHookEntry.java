package com.clown.newhook;

import com.clown.newhook.util.RefProxy;
import com.clown.newhook.util.StrVault;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 全新模块入口 —— 复刻 WinHook 手法，目标 com.luna.music。
 *
 * 手法对照:
 *   WinHook                          →  本模块
 *   short[] + XOR 解密               →  StrVault.dec(strings)
 *   反射 m45() 包装 findAndHookMethod  →  RefProxy.hook()
 *   opaque predicate 干扰            →  StrVault.opaque() / RefProxy.live()
 *
 * hook 点:
 *   1. UserTier.F()      → 强制 "svip_user"
 *   2. MemberShipCenterImpl.getVipStatus() → 强制 VipStatus.SVIP
 */
public class NewHookEntry implements IXposedHookLoadPackage {

    // ===== 加密字符串表（由 strgen.py 生成）=====
    private static final short[] S0 = {23135, 23123, 23121, 23058, 23120, 23113, 23122, 23133, 23058, 23121, 23113, 23119, 23125, 23135};
    private static final int S0_KEY = 0x5A3C;   // "com.luna.music"

    private static final short[] S1 = {30594, 30606, 30604, 30671, 30605, 30612, 30607, 30592, 30671, 30595, 30600, 30619, 30671, 30596, 30607, 30613, 30600, 30613, 30605, 30596, 30604, 30596, 30607, 30613, 30671, 30592, 30595, 30600, 30605, 30600, 30613, 30600, 30596, 30610, 30671, 30604, 30596, 30604, 30595, 30596, 30611, 30610, 30601, 30600, 30609, 30671, 30636, 30596, 30604, 30595, 30596, 30611, 30642, 30601, 30600, 30609, 30626, 30596, 30607, 30613, 30596, 30611, 30632, 30604, 30609, 30605};
    private static final int S1_KEY = 0x77E1;   // MemberShipCenterImpl 全类名

    private static final short[] S2 = {9227, 9223, 9221, 9286, 9220, 9245, 9222, 9225, 9286, 9227, 9223, 9221, 9221, 9223, 9222, 9286, 9225, 9242, 9227, 9216, 9286, 9227, 9223, 9222, 9230, 9217, 9231, 9286, 9277, 9243, 9229, 9242, 9276, 9217, 9229, 9242};
    private static final int S2_KEY = 0x2468;   // UserTier 全类名

    private static final short[] S3 = {4916, 4920, 4922, 4985, 4923, 4898, 4921, 4918, 4985, 4916, 4920, 4922, 4922, 4920, 4921, 4985, 4918, 4901, 4916, 4927, 4985, 4915, 4917, 4985, 4914, 4921, 4899, 4926, 4899, 4910, 4985, 4865, 4926, 4903, 4868, 4899, 4918, 4899, 4898, 4900};
    private static final int S3_KEY = 0x1357;   // VipStatus 全类名

    private static final short[] S4 = {23145, 23119, 23129, 23118, 23144, 23125, 23129, 23118};
    private static final int S4_KEY = 0x5A3C;   // "UserTier"

    private static final short[] S5 = {30598, 30596, 30613, 30647, 30600, 30609, 30642, 30613, 30592, 30613, 30612, 30610};
    private static final int S5_KEY = 0x77E1;   // "getVipStatus"

    private static final short[] S6 = {9217, 9243, 9278, 9217, 9240};
    private static final int S6_KEY = 0x2468;   // "isVip"

    private static final short[] S7 = {4881};
    private static final int S7_KEY = 0x1357;   // "F"

    private static final short[] S8 = {23119, 23114, 23125, 23116, 23139, 23113, 23119, 23129, 23118};
    private static final int S8_KEY = 0x5A3C;   // "svip_user"

    private static final short[] S9 = {30610, 30615, 30600, 30609};
    private static final int S9_KEY = 0x77E1;   // "svip"

    // 目标包名（解密一次缓存）
    private static String TARGET_PKG;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return;

        if (TARGET_PKG == null) {
            TARGET_PKG = StrVault.dec(S0, S0_KEY);
        }
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        installHooks(lpparam.classLoader);
    }

    private void installHooks(ClassLoader cl) {
        try {
            hookUserTierF(cl);
        } catch (Throwable t) {
            // 静默 —— 目标未加载时不报错
        }
        try {
            hookGetVipStatus(cl);
        } catch (Throwable t) {
            // 同上
        }
    }

    /**
     * hook UserTier.F() —— 强制返回 "svip_user"。
     * 这是本地档位判定的源头，一旦为 svip_user，
     * isVip()/isSVip() 的 OR 链全部短路为 true。
     */
    private void hookUserTierF(ClassLoader cl) throws Throwable {
        final String clsName = StrVault.dec(S2, S2_KEY);   // UserTier 全类名
        final String mName = StrVault.dec(S7, S7_KEY);     // "F"
        final String svipUser = StrVault.dec(S8, S8_KEY);  // "svip_user"

        Class<?> clazz = Class.forName(clsName, false, cl);

        XC_MethodHook cb = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                // 直接替换返回值为 svip_user
                param.setResult(svipUser);
            }
        };

        // 经反射包装调用，静态反编译看不到 findAndHookMethod
        RefProxy.hook(clazz, mName, cb);
    }

    /**
     * hook MemberShipCenterImpl.getVipStatus() —— 强制返回 VipStatus.SVIP。
     * 补上 WinHook 的服务端字段那层：即使 memberShip 下发的不是 VIP，
     * 也把状态枚举顶成 SVIP。
     */
    private void hookGetVipStatus(ClassLoader cl) throws Throwable {
        final String implName = StrVault.dec(S1, S1_KEY);  // MemberShipCenterImpl 全类名
        final String vipStatusName = StrVault.dec(S3, S3_KEY); // VipStatus 全类名
        final String mName = StrVault.dec(S5, S5_KEY);     // "getVipStatus"
        final String svipStr = StrVault.dec(S9, S9_KEY);   // "svip"

        Class<?> implClazz = Class.forName(implName, false, cl);

        // VipStatus.SVIP 枚举实例（反射取，避免编译期依赖）
        Class<?> vsClazz = Class.forName(vipStatusName, false, cl);
        Object svipEnum = null;
        Object[] consts = vsClazz.getEnumConstants();
        if (consts != null) {
            for (Object c : consts) {
                if (svipStr.equalsIgnoreCase(String.valueOf(c))) {
                    svipEnum = c;
                    break;
                }
            }
        }
        final Object svipTarget = svipEnum;

        XC_MethodHook cb = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object orig = param.getResult();
                // 原值已是 VIP 系则放行；否则顶成 SVIP
                if (orig != null && svipTarget != null) {
                    String s = String.valueOf(orig);
                    if (!s.toLowerCase().contains("vip")) {
                        param.setResult(svipTarget);
                    }
                } else if (svipTarget != null) {
                    param.setResult(svipTarget);
                }
            }
        };

        RefProxy.hook(implClazz, mName, cb);
    }
}
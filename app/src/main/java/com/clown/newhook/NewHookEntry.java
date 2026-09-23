package com.clown.newhook;

import com.clown.newhook.util.RefProxy;
import com.clown.newhook.util.StrVault;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * NewHook —— 本地 + 服务端双层解锁（复刻 WinHook 混淆手法）。
 *
 * 手法:
 *   short[] + XOR 解密 (StrVault)  |  反射代理 (RefProxy)  |  opaque predicate
 *
 * ── 本地层 ──
 *   UserTier.F()  ->  "svip_user"   (档位源头)
 *
 * ── 服务端层 ──
 *   MemberShipCenterImpl:
 *     isVip / k / l / v  -> true
 *     a()  (getVipStatus)      -> VipStatus.SVIP
 *     f()  (getQualityMaxLevel)-> QualityLevelEnum.QUALITY_HIRES
 *     j()  (expireTimeMax)     -> 远期
 *     b()  (leftTime)          -> 远期剩余
 *
 * ── 去广告 ──
 *   AdService 系列布尔方法 -> false
 *   AdActivity / RewardAdActivity.onCreate -> 直接 finish + 回主界面
 */
public class NewHookEntry implements IXposedHookLoadPackage {

    // ===== 加密字符串表（strgen.py 生成）=====
    private static final short[] S0 = {23135, 23123, 23121, 23058, 23120, 23113, 23122, 23133, 23058, 23121, 23113, 23119, 23125, 23135};
    private static final int S0_KEY = 0x5A3C;   // com.luna.music

    private static final short[] S1 = {30594, 30606, 30604, 30671, 30605, 30612, 30607, 30592, 30671, 30595, 30600, 30619, 30671, 30596, 30607, 30613, 30600, 30613, 30605, 30596, 30604, 30596, 30607, 30613, 30671, 30592, 30595, 30600, 30605, 30600, 30613, 30600, 30596, 30610, 30671, 30604, 30596, 30604, 30595, 30596, 30611, 30610, 30601, 30600, 30609, 30671, 30636, 30596, 30604, 30595, 30596, 30611, 30642, 30601, 30600, 30609, 30626, 30596, 30607, 30613, 30596, 30611, 30632, 30604, 30609, 30605};
    private static final int S1_KEY = 0x77E1;   // MemberShipCenterImpl

    private static final short[] S2 = {9227, 9223, 9221, 9286, 9220, 9245, 9222, 9225, 9286, 9227, 9223, 9221, 9221, 9223, 9222, 9286, 9225, 9242, 9227, 9216, 9286, 9227, 9223, 9222, 9230, 9217, 9231, 9286, 9277, 9243, 9229, 9242, 9276, 9217, 9229, 9242};
    private static final int S2_KEY = 0x2468;   // com.luna.common.arch.config.UserTier

    private static final short[] S3 = {4916, 4920, 4922, 4985, 4923, 4898, 4921, 4918, 4985, 4916, 4920, 4922, 4922, 4920, 4921, 4985, 4918, 4901, 4916, 4927, 4985, 4915, 4917, 4985, 4914, 4921, 4899, 4926, 4899, 4910, 4985, 4865, 4926, 4903, 4868, 4899, 4918, 4899, 4898, 4900};
    private static final int S3_KEY = 0x1357;   // com.luna.common.arch.db.entity.VipStatus

    private static final short[] S4 = {23135, 23123, 23121, 23058, 23120, 23113, 23122, 23133, 23058, 23134, 23125, 23110, 23058, 23129, 23122, 23112, 23125, 23112, 23120, 23129, 23121, 23129, 23122, 23112, 23058, 23119, 23116, 23125, 23058, 23149, 23113, 23133, 23120, 23125, 23112, 23109, 23152, 23129, 23114, 23129, 23120, 23161, 23122, 23113, 23121};
    private static final int S4_KEY = 0x5A3C;   // com.luna.biz.entitlement.spi.QualityLevelEnum

    private static final short[] S5 = {30594, 30606, 30604, 30671, 30605, 30612, 30607, 30592, 30671, 30595, 30600, 30619, 30671, 30592, 30597, 30671, 30624, 30597, 30642, 30596, 30611, 30615, 30600, 30594, 30596};
    private static final int S5_KEY = 0x77E1;   // com.luna.biz.ad.AdService

    private static final short[] S6 = {9227, 9223, 9221, 9286, 9220, 9245, 9222, 9225, 9286, 9226, 9217, 9234, 9286, 9225, 9228, 9286, 9246, 9217, 9229, 9247, 9286, 9227, 9223, 9222, 9244, 9225, 9217, 9222, 9229, 9242, 9286, 9257, 9228, 9257, 9227, 9244, 9217, 9246, 9217, 9244, 9233};
    private static final int S6_KEY = 0x2468;   // com.luna.biz.ad.view.container.AdActivity

    private static final short[] S7 = {4916, 4920, 4922, 4985, 4923, 4898, 4921, 4918, 4985, 4917, 4926, 4909, 4985, 4918, 4915, 4985, 4901, 4914, 4896, 4918, 4901, 4915, 4985, 4869, 4914, 4896, 4918, 4901, 4915, 4886, 4915, 4886, 4916, 4899, 4926, 4897, 4926, 4899, 4910};
    private static final int S7_KEY = 0x1357;   // com.luna.biz.ad.reward.RewardAdActivity

    private static final short[] S8 = {23135, 23123, 23121, 23058, 23120, 23113, 23122, 23133, 23058, 23134, 23125, 23110, 23058, 23121, 23133, 23125, 23122, 23058, 23121, 23133, 23125, 23122, 23058, 23153, 23133, 23125, 23122, 23165, 23135, 23112, 23125, 23114, 23125, 23112, 23109};
    private static final int S8_KEY = 0x5A3C;   // com.luna.biz.main.main.MainActivity

    private static final short[] S9 = {30644, 30610, 30596, 30611, 30645, 30600, 30596, 30611};
    private static final int S9_KEY = 0x77E1;   // "UserTier"

    private static final short[] S10 = {9262};
    private static final int S10_KEY = 0x2468;  // "F"

    private static final short[] S11 = {4900, 4897, 4926, 4903, 4872, 4898, 4900, 4914, 4901};
    private static final int S11_KEY = 0x1357;  // "svip_user"

    private static final short[] S12 = {23151, 23146, 23157, 23148};
    private static final int S12_KEY = 0x5A3C;  // "SVIP"

    private static final short[] S13 = {30640, 30644, 30624, 30637, 30632, 30645, 30648, 30654, 30633, 30632, 30643, 30628, 30642};
    private static final int S13_KEY = 0x77E1;  // "QUALITY_HIRES"

    private static final short[] S14 = {9217, 9243, 9278, 9217, 9240};
    private static final int S14_KEY = 0x2468;  // "isVip"

    private static final short[] S15 = {4924};
    private static final int S15_KEY = 0x1357;  // "k"  (isSVip)

    private static final short[] S16 = {23120};
    private static final int S16_KEY = 0x5A3C;  // "l"  (isLosslessVip)

    private static final short[] S17 = {30615};
    private static final int S17_KEY = 0x77E1;  // "v"

    private static final short[] S18 = {9230};
    private static final int S18_KEY = 0x2468;  // "f"  (getQualityMaxLevel)

    private static final short[] S19 = {4918};
    private static final int S19_KEY = 0x1357;  // "a"  (getVipStatus)

    private static final short[] S20 = {23126};
    private static final int S20_KEY = 0x5A3C;  // "j"  (expireTimeMax)

    private static final short[] S21 = {30595};
    private static final int S21_KEY = 0x77E1;  // "b"  (leftTime)

    private static final short[] S22 = {9227,9225,9222,9275,9216,9223,9247,9275,9240,9220,9225,9243,9216,9259,9223,9220,9228,9257,9228};
    private static final int S22_KEY = 0x2468;  // canShowSplashColdAd

    private static final short[] S23 = {4921,4914,4914,4915,4868,4927,4920,4896,4895,4920,4899,4886,4915};
    private static final int S23_KEY = 0x1357;  // needShowHotAd

    private static final short[] S24 = {23125,23119,23165,23128,23150,23129,23115,23133,23118,23128,23151,23112,23133,23118,23112};
    private static final int S24_KEY = 0x5A3C;  // isAdRewardStart

    private static final short[] S25 = {30596,30607,30592,30595,30605,30596,30643,30596,30614,30592,30611,30597,30628,30607,30613,30611,30592,30607,30594,30596,30645,30606,30606,30605,30613,30600,30609,30610};
    private static final int S25_KEY = 0x77E1;  // enableRewardEntranceTooltips

    private static final short[] S26 = {9216,9225,9243,9278,9217,9240,9262,9217,9232,9229,9228,9261,9222,9244,9242,9225,9222,9227,9229,9255,9222,9276,9217,9244,9220,9229,9258,9225,9242};
    private static final int S26_KEY = 0x2468;  // hasVipFixedEntranceOnTitleBar

    private static final short[] S27 = {4926,4900,4886,4915,4871,4901,4914,4923,4920,4918,4915,4888,4903,4899,4882,4921,4918,4917,4923,4914,4915};
    private static final int S27_KEY = 0x1357;  // isAdPreloadOptEnabled

    private static final short[] S28 = {23125,23119,23157,23122,23165,23128,23165,23135,23112,23125,23114,23125,23112,23109};
    private static final int S28_KEY = 0x5A3C;  // isInAdActivity

    private static final short[] S29 = {30600,30610,30632,30607,30626,30606,30600,30607,30643,30596,30614,30592,30611,30597,30624,30597,30624,30594,30613,30600,30615,30600,30613,30616};
    private static final int S29_KEY = 0x77E1;  // isInCoinRewardAdActivity

    private static final short[] S30 = {9223,9222,9259,9242,9229,9225,9244,9229};
    private static final int S30_KEY = 0x2468;  // "onCreate"

    /** 广告判定方法加密表（数组 + key 一一对应） */
    private static final short[][] AD_ARR = {S22, S23, S24, S25, S26, S27, S28, S29};
    private static final int[] AD_KEY = {S22_KEY, S23_KEY, S24_KEY, S25_KEY,
            S26_KEY, S27_KEY, S28_KEY, S29_KEY};

    private static final long FAR_FUTURE_MS = 4102444800000L;  // 2100-01-01

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (lp == null || lp.packageName == null) return;
        if (!StrVault.dec(S0, S0_KEY).equals(lp.packageName)) return;

        RefProxy.log("[NewHook] load " + lp.packageName);
        hookLocalTier(lp);      // 本地层
        hookServerVip(lp);      // 服务端层
        hookAds(lp);            // 去广告
        RefProxy.log("[NewHook] all hooks installed");
    }

    // ==================== 本地层 ====================
    /** UserTier.F() -> "svip_user"：档位判定源头 */
    private void hookLocalTier(XC_LoadPackage.LoadPackageParam lp) {
        try {
            final String cls = StrVault.dec(S2, S2_KEY);
            final String mth = StrVault.dec(S10, S10_KEY);
            final String val = StrVault.dec(S11, S11_KEY);

            Class<?> c = RefProxy.findClass(cls, lp.classLoader);
            RefProxy.hookAll(c, mth, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.getResult() instanceof String) p.setResult(val);
                }
            });
            RefProxy.log("[NewHook] local UserTier.F() -> svip_user");
        } catch (Throwable t) {
            RefProxy.log("[NewHook] local tier skip: " + t);
        }
    }

    // ==================== 服务端层 ====================
    private void hookServerVip(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> impl;
        try {
            impl = RefProxy.findClass(StrVault.dec(S1, S1_KEY), lp.classLoader);
        } catch (Throwable t) {
            RefProxy.log("[NewHook] impl not found: " + t);
            return;
        }

        // 布尔判定 -> true：isVip / k / l / v
        for (final short[] arr : new short[][]{S14, S15, S16, S17}) {
            final int key = keyOf(arr);
            final String name = StrVault.dec(arr, key);
            try {
                RefProxy.hookAll(impl, name, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (p.getResult() instanceof Boolean) p.setResult(Boolean.TRUE);
                    }
                });
                RefProxy.log("[NewHook] server " + name + "() -> true");
            } catch (Throwable t) {
                RefProxy.log("[NewHook] skip " + name + ": " + t);
            }
        }

        // a() -> VipStatus.SVIP
        try {
            final Object svip = pickEnum(S3, S3_KEY, lp.classLoader,
                    StrVault.dec(S12, S12_KEY));
            if (svip != null) {
                RefProxy.hookAll(impl, StrVault.dec(S19, S19_KEY), new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        p.setResult(svip);
                    }
                });
                RefProxy.log("[NewHook] server a() -> SVIP");
            }
        } catch (Throwable t) {
            RefProxy.log("[NewHook] skip a(): " + t);
        }

        // f() -> QUALITY_HIRES
        try {
            final Object q = pickEnum(S4, S4_KEY, lp.classLoader,
                    StrVault.dec(S13, S13_KEY));
            if (q != null) {
                RefProxy.hookAll(impl, StrVault.dec(S18, S18_KEY), new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        p.setResult(q);
                    }
                });
                RefProxy.log("[NewHook] server f() -> HIRES");
            }
        } catch (Throwable t) {
            RefProxy.log("[NewHook] skip f(): " + t);
        }

        // j() 到期 / b() 剩余时长 -> 远期
        try {
            RefProxy.hookAll(impl, StrVault.dec(S20, S20_KEY), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.getResult() instanceof Long) p.setResult(FAR_FUTURE_MS);
                }
            });
            RefProxy.hookAll(impl, StrVault.dec(S21, S21_KEY), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.getResult() instanceof Long) {
                        p.setResult(FAR_FUTURE_MS - System.currentTimeMillis());
                    }
                }
            });
            RefProxy.log("[NewHook] server j()/b() -> far future");
        } catch (Throwable ignored) {
        }
    }

    // ==================== 去广告 ====================
    private void hookAds(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> ad;
        try {
            ad = RefProxy.findClass(StrVault.dec(S5, S5_KEY), lp.classLoader);
        } catch (Throwable t) {
            RefProxy.log("[NewHook] AdService not found: " + t);
            return;
        }
        for (int i = 0; i < AD_ARR.length; i++) {
            final String name = StrVault.dec(AD_ARR[i], AD_KEY[i]);
            try {
                RefProxy.hookAll(ad, name, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (p.getResult() instanceof Boolean) p.setResult(Boolean.FALSE);
                    }
                });
                RefProxy.log("[NewHook] ad " + name + "() -> false");
            } catch (Throwable t) {
                RefProxy.log("[NewHook] skip ad " + name + ": " + t);
            }
        }
        blockAdActivity(lp, StrVault.dec(S6, S6_KEY));
        blockAdActivity(lp, StrVault.dec(S7, S7_KEY));
    }

    /** 广告 Activity 一 onCreate 就 finish，清栈回主界面 */
    private void blockAdActivity(final XC_LoadPackage.LoadPackageParam lp, final String clsName) {
        try {
            final Class<?> c = RefProxy.findClass(clsName, lp.classLoader);
            final String main = StrVault.dec(S8, S8_KEY);
            final String pkg = StrVault.dec(S0, S0_KEY);
            RefProxy.hookAll(c, StrVault.dec(S30, S30_KEY), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    android.app.Activity act = (android.app.Activity) p.thisObject;
                    try {
                        android.content.Intent it = new android.content.Intent();
                        it.setClassName(pkg, main);
                        it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        act.startActivity(it);
                    } catch (Throwable ignored) {
                    }
                    try {
                        act.finish();
                    } catch (Throwable ignored) {
                    }
                    RefProxy.log("[NewHook] BLOCK " + clsName);
                }
            });
            RefProxy.log("[NewHook] blocked " + clsName);
        } catch (Throwable t) {
            RefProxy.log("[NewHook] block skip " + clsName + ": " + t);
        }
    }

    // ==================== 工具 ====================
    /** 反射取枚举常量（按名字） */
    private static Object pickEnum(short[] nameArr, int nameKey, ClassLoader cl, String want) {
        try {
            Class<?> c = RefProxy.findClass(StrVault.dec(nameArr, nameKey), cl);
            return c.getField(want).get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 根据数组引用反查 key（数组↔key 一一对应） */
    private static int keyOf(short[] arr) {
        if (arr == S14) return S14_KEY;
        if (arr == S15) return S15_KEY;
        if (arr == S16) return S16_KEY;
        if (arr == S17) return S17_KEY;
        return 0;
    }
}
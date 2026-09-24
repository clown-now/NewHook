package com.clown.newhook;

import com.clown.newhook.util.RefProxy;
import com.clown.newhook.util.SignatureHook;
import com.clown.newhook.util.StrVault;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * NewHook —— 本地 + 服务端双层解锁（libxposed 102 现代 API）。
 *
 * 手法保留: short[] + XOR 加密 (StrVault)  |  反射/Method 定位  |  opaque predicate
 *
 * ── 本地层 ──
 *   UserTier.F()  ->  "svip_user"
 *
 * ── 服务端层 ──
 *   MemberShipCenterImpl:
 *     isVip / k / l / v  -> true
 *     a()  -> VipStatus.SVIP
 *     f()  -> QualityLevelEnum.QUALITY_HIRES
 *     j()/b() -> 远期
 *
 * ── 去广告 ──
 *   AdService 系列布尔 -> false
 *   AdActivity / RewardAdActivity.onCreate -> clearTask + 回主界面
 */
public class NewHookEntry extends XposedModule {

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
    private static final int S15_KEY = 0x1357;  // "k"

    private static final short[] S16 = {23120};
    private static final int S16_KEY = 0x5A3C;  // "l"

    private static final short[] S17 = {30615};
    private static final int S17_KEY = 0x77E1;  // "v"

    private static final short[] S18 = {9230};
    private static final int S18_KEY = 0x2468;  // "f"

    private static final short[] S19 = {4918};
    private static final int S19_KEY = 0x1357;  // "a"

    private static final short[] S20 = {23126};
    private static final int S20_KEY = 0x5A3C;  // "j"

    private static final short[] S21 = {30595};
    private static final int S21_KEY = 0x77E1;  // "b"

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

    /** 广告判定方法加密表 */
    private static final short[][] AD_ARR = {S22, S23, S25, S26, S27, S28, S29};
    private static final int[] AD_KEY = {S22_KEY, S23_KEY, S25_KEY,
            S26_KEY, S27_KEY, S28_KEY, S29_KEY};

    /**
     * isAdRewardStart 是写入口而非判定口：真实原型 void isAdRewardStart(boolean)
     * （反汇编确认：ins=2、iput ...->q:Z、return-void）。
     * 它不参与 AD_ARR 的零参判定链，需单独按「带 1 参」拦截。
     */
    private static final short[] S24_ADREWARD = {23125,23119,23165,23128,23150,23129,23115,23133,23118,23128,23151,23112,23133,23118,23112};
    private static final int S24_ADREWARD_KEY = 0x5A3C;  // isAdRewardStart

    /** 会员布尔方法加密表 */
    private static final short[][] VIP_ARR = {S14, S15, S16, S17};
    private static final int[] VIP_KEY = {S14_KEY, S15_KEY, S16_KEY, S17_KEY};

    private static final long FAR_FUTURE_MS = 4102444800000L;  // 2100-01-01

    /** 目标包是否已处理（避免重复 hook） */
    private boolean done = false;

    // ==================== 入口 ====================

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (done) return;
        if (!StrVault.dec(S0, S0_KEY).equals(param.getPackageName())) return;
        done = true;

        ClassLoader cl = param.getClassLoader();
        log("load " + param.getPackageName());

        hookLocalTier(cl);
        hookServerVip(cl);
        hookAds(cl);
        probeCommerceEntity(cl);
        hookAdConfig(cl);
        hookSplashAd(cl);
        hookColdSplash(cl);
        hookBySignature(cl);
        log("all hooks installed");
    }

    // ==================== 类型签名扫描（移植自 MoonHook） ====================
    /**
     * 不依赖方法名，只按「参数/返回类型签名」批量 hook。
     * 抗混淆、抗版本改名 —— 汽水 20.x 改名后依然命中。
     */
    private void hookBySignature(ClassLoader cl) {
        // ---- 1. 试听区间列表：把"仅试听"条目剔除 ----
        Class<?> pc = RefProxy.findClass(
                "com.luna.biz.playing.player.PlayerController", cl);
        Class<?> pe = RefProxy.findClass(
                "com.luna.biz.playing.common.entitlement.PlayableEntitlementExtKt", cl);

        if (pc != null) {
            int n = SignatureHook.hookNoArgList(this, pc, item -> {
                if (item == null) return false;
                // 试听区间模型：字段名混淆，用值域启发式判断
                try {
                    long start = readLongField(item, 0);
                    long end = readLongField(item, 1);
                    // 试听段特征：start=0 且 end 在 30s~300s 之间
                    if (start == 0 && end > 20000 && end < 600000) {
                        log("SIG drop preview-range " + start + "~" + end);
                        return true;
                    }
                } catch (Throwable ignored) {}
                return false;
            });
            log("SIG PlayerController no-arg-List hooked = " + n);
        }

        // ---- 2. PlayableExtKt 全部 boolean 静态判定 -> false ----
        if (pe != null) {
            int n = SignatureHook.hookAllNoArgBool(this, pe, false, true);
            log("SIG PlayableExtKt booleans -> false = " + n);
        }

        // ---- 3. Track 的 1 参 boolean 鉴权方法 -> false ----
        Class<?> track = RefProxy.findClass("com.luna.common.arch.db.entity.Track", cl);
        if (track != null) {
            int n = SignatureHook.hook1ArgBool(this, track, String.class, false);
            log("SIG Track(String)->false = " + n);
        }

        // ---- 4. PlayerInfo 的 3 参 boolean 判定 -> true ----
        Class<?> pi = RefProxy.findClass("com.luna.common.arch.db.entity.PlayerInfo", cl);
        if (pi != null) {
            int n = SignatureHook.hookByName(this, pi, "setValue", 2, Boolean.FALSE);
            log("SIG PlayerInfo setValue->false = " + n);
        }
    }

    /** 读对象里第 idx 个 long 字段（按声明顺序，跳过静态） */
    private static long readLongField(Object obj, int idx) {
        int seen = 0;
        for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() != long.class && f.getType() != Long.class) continue;
            f.setAccessible(true);
            if (seen == idx) {
                try { return f.getLong(obj); } catch (Throwable t) { return 0; }
            }
            seen++;
        }
        return 0;
    }

    // ==================== 本地层 ====================
    private void hookLocalTier(ClassLoader cl) {
        try {
            Class<?> c = RefProxy.findClass(StrVault.dec(S2, S2_KEY), cl);
            Method m = RefProxy.findMethod(c, StrVault.dec(S10, S10_KEY));
            if (m == null) {
                log("local tier method not found");
                return;
            }
            RefProxy.force(this, m, StrVault.dec(S11, S11_KEY)).install();
            log("local UserTier.F() -> svip_user");
        } catch (Throwable t) {
            log("local tier skip: " + t);
        }
    }

    // ==================== 服务端层 ====================
    private void hookServerVip(ClassLoader cl) {
        Class<?> impl = RefProxy.findClass(StrVault.dec(S1, S1_KEY), cl);
        if (impl == null) {
            log("impl not found");
            return;
        }

        // 布尔 -> true
        for (int i = 0; i < VIP_ARR.length; i++) {
            String name = StrVault.dec(VIP_ARR[i], VIP_KEY[i]);
            Method m = RefProxy.findMethod(impl, name);
            if (m == null) {
                log("skip " + name);
                continue;
            }
            try {
                RefProxy.forceTrue(this, m).install();
                log("server " + name + "() -> true");
            } catch (Throwable t) {
                log("skip " + name + ": " + t);
            }
        }

        // a() -> SVIP
        installEnum(impl, cl, S19, S19_KEY, S3, S3_KEY, S12, S12_KEY, "a->SVIP");

        // f() -> QUALITY_HIRES
        installEnum(impl, cl, S18, S18_KEY, S4, S4_KEY, S13, S13_KEY, "f->HIRES");

        // j()/b() -> 远期
        for (short[] arr : new short[][]{S20, S21}) {
            int k = (arr == S20) ? S20_KEY : S21_KEY;
            String name = StrVault.dec(arr, k);
            Method m = RefProxy.findMethod(impl, name);
            if (m == null) continue;
            try {
                RefProxy.force(this, m, FAR_FUTURE_MS).install();
                log("server " + name + "() -> far");
            } catch (Throwable t) {
                log("skip " + name + ": " + t);
            }
        }
    }

    private void installEnum(Class<?> impl, ClassLoader cl,
                             short[] mName, int mKey,
                             short[] enumCls, int enumKey,
                             short[] constName, int constKey,
                             String tag) {
        try {
            Method m = RefProxy.findMethod(impl, StrVault.dec(mName, mKey));
            Class<?> e = RefProxy.findClass(StrVault.dec(enumCls, enumKey), cl);
            if (m == null || e == null) {
                log("skip " + tag);
                return;
            }
            Object v = e.getField(StrVault.dec(constName, constKey)).get(null);
            RefProxy.force(this, m, v).install();
            log("server " + tag);
        } catch (Throwable t) {
            log("skip " + tag + ": " + t);
        }
    }

    // ==================== 去广告 ====================
    private void hookAds(ClassLoader cl) {
        Class<?> ad = RefProxy.findClass(StrVault.dec(S5, S5_KEY), cl);
        if (ad == null) {
            log("AdService not found");
            return;
        }
        for (int i = 0; i < AD_ARR.length; i++) {
            String name = StrVault.dec(AD_ARR[i], AD_KEY[i]);
            Method m = RefProxy.findMethod(ad, name);
            if (m == null) {
                log("skip ad " + name);
                continue;
            }
            try {
                RefProxy.forceFalse(this, m).install();
                log("ad " + name + "() -> false");
            } catch (Throwable t) {
                log("skip ad " + name + ": " + t);
            }
        }
        blockAdActivity(cl, StrVault.dec(S6, S6_KEY));
        // 真实激励视频 Activity / 广告落地页浏览器（S7 旧名 RewardAdActivity 已不存在，弃用）
        blockAdActivity(cl, "com.luna.biz.ad.adns.luna.LunaRewardActivity");
        blockAdActivity(cl, "com.luna.biz.ad.adns.dsp.landing.AdLandingBrowserActivity");
        hookAdRewardStartSetter(ad);
    }

    /**
     * 单独拦截带参 setter：void isAdRewardStart(boolean)。
     * 老代码把它当零参判定方法 → findMethod 必然返回 null（这就是 skip ad isAdRewardStart 的来源）。
     * 正确做法是吞掉这次调用：原方法体（iput ...->q:Z）不执行，字段 q 永远保持 false。
     */
    private void hookAdRewardStartSetter(Class<?> ad) {
        String name = StrVault.dec(S24_ADREWARD, S24_ADREWARD_KEY);
        Method m = RefProxy.findMethodByArgc(ad, name, 1);
        if (m == null) {
            log("skip arg-setter " + name + " (1 arg)");
            return;
        }
        try {
            RefProxy.swallow(this, m).install();
            log("ad " + name + "(boolean) -> swallowed");
        } catch (Throwable t) {
            log("skip arg-setter " + name + ": " + t);
        }
    }

    private void blockAdActivity(ClassLoader cl, String clsName) {
        try {
            Class<?> c = RefProxy.findClass(clsName, cl);
            if (c == null) { log("block cls not found " + clsName); return; }
            // onCreate 有 (Bundle) / (Bundle,PersistableBundle) 多重载，用 findAnyMethod 兜底
            Method onCreate = RefProxy.findAnyMethod(c, StrVault.dec(S30, S30_KEY));
            if (onCreate == null) {
                log("block skip " + clsName);
                return;
            }
            final String pkg = StrVault.dec(S0, S0_KEY);
            final String main = StrVault.dec(S8, S8_KEY);
            final XposedInterface self = this;
            this.hook(onCreate).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object thisObj = chain.getThisObject();
                    try {
                        android.app.Activity act = (android.app.Activity) thisObj;
                        android.content.Intent it = new android.content.Intent();
                        it.setClassName(pkg, main);
                        it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        act.startActivity(it);
                        act.finish();
                    } catch (Throwable ignored) {
                    }
                    RefProxy.log(self, "BLOCK " + clsName);
                    return chain.proceed();
                }
            });
            log("blocked " + clsName + " (" + onCreate.getName() + ")");
        } catch (Throwable t) {
            log("block skip " + clsName + ": " + t);
        }
    }

    // ==================== 广告配置总开关（AdSettingsConfig） ====================
    /**
     * AdService 的广告判定全部来源于 AdSettingsConfig 的配置读取。
     * 把该配置类的所有无参布尔方法统一置 false，能覆盖：
     *   canShowSplashColdAd / isInAdActivity / isAdPreloadOptEnabled 等
     */
    private void hookAdConfig(ClassLoader cl) {
        Class<?> cfg = RefProxy.findClass(
                "com.luna.common.arch.config.commercial.ad.AdSettingsConfig", cl);
        if (cfg == null) { log("ADCFG not found"); return; }
        int n = 0;
        // 只 hook 本类声明的方法，避免误伤 BaseConfig.exist() 等全局配置判定
        for (Method m : cfg.getDeclaredMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() != boolean.class) continue;
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            try {
                RefProxy.forceFalse(this, m).install();
                n++;
            } catch (Throwable ignored) {}
        }
        log("ADCFG hooked booleans = " + n);
    }

    // ==================== 开屏广告观察者 ====================
    private void hookColdSplash(ClassLoader cl) {
        Class<?> c = RefProxy.findClass("com.luna.biz.ad.ColdSplashAdActivityObserver", cl);
        if (c == null) { log("SPLASHOBS not found"); return; }
        for (Method m : c.getDeclaredMethods()) {
            Class<?> rt = m.getReturnType();
            if (rt == boolean.class || rt == Boolean.class) {
                try { RefProxy.forceFalse(this, m).install(); log("SPLASHOBS " + m.getName() + " -> false"); }
                catch (Throwable ignored) {}
            }
        }
    }

    // ==================== 开屏广告加载任务 ====================
    private void hookSplashAd(ClassLoader cl) {
        for (String cn : new String[]{
                "com.luna.biz.ad.biz.init.AdSplashLoadTask$Companion",
                "com.luna.biz.ad.biz.init.AdSplashLoadTask"}) {
            Class<?> c = RefProxy.findClass(cn, cl);
            if (c == null) continue;
            for (Method m : c.getDeclaredMethods()) {
                if (m.getReturnType() == boolean.class) {
                    try { RefProxy.forceFalse(this, m).install(); log("SPLASH " + cn + "." + m.getName() + " -> false"); }
                    catch (Throwable ignored) {}
                }
            }
            // run/execute 类方法：直接吞掉返回（不加载）
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && void.class.equals(m.getReturnType())) {
                    try {
                        RefProxy.force(this, m, null).install();
                        log("SPLASH " + cn + "." + m.getName() + " -> noop");
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    // ==================== 探测：CommerceInfoMemberShipEntity ====================
    private void probeCommerceEntity(ClassLoader cl) {
        try {
            Class<?> c = RefProxy.findClass(
                    "com.luna.biz.entitlement.core.commerceinfo.entity.CommerceInfoMemberShipEntity", cl);
            if (c == null) { log("ENTITY not found"); return; }
            // 布尔类
            for (String n : new String[]{"isVip", "isPayingUser", "isAboutToExpired", "getInGracePeriod"}) {
                Method m = RefProxy.findMethod(c, n);
                if (m == null) { log("ENTITY skip " + n); continue; }
                try { RefProxy.forceTrue(this, m).install(); log("ENTITY " + n + " -> true"); }
                catch (Throwable t) { log("ENTITY skip " + n + ": " + t); }
            }
            // 字符串类：vipStage -> svip
            Method vs = RefProxy.findMethod(c, "getVipStage");
            if (vs != null) {
                try { RefProxy.force(this, vs, "svip").install(); log("ENTITY getVipStage -> svip"); }
                catch (Throwable t) { log("ENTITY getVipStage err: " + t); }
            } else {
                log("ENTITY getVipStage not found");
            }
            // ⭐ UI 渲染层字段（frida 实测：UI 读的是这组，而不是 isVip/getVipStage）
            hookCommerceUiFields(c);
        } catch (Throwable t) { log("ENTITY probe err: " + t); }
        hookSubscriptionEvent(cl);
        hookUserBrief(cl);
        hookVipStatus(cl);
        hookLancet(cl);
        hookAudioQuality(cl);
        hookTrackPlayable(cl);
        hookPlayerInfo(cl);
        hookPreview(cl);
    }

    // ==================== 会员 UI 渲染字段（frida 实测定向） ====================
    /**
     * 从 frida attach 实测（NewHook 生效态）拿到的真实字段值：
     *   getVipStage               = svip          （已被本模块改写）
     *   getExpireDate             = 1699729036    （2023-11-11，早已过期）
     *   getExpireTimeMax          = 1699729036    （同上）
     *   getLastMemberShipType     = "vip"
     *   getModifierVipLabelSuffix = "1元续费"     ← UI 实际渲染的标签
     *   getSideBarVipEntrance     = VipEntrance(title=1元续费, style=new_style_without_suffix)
     *
     * 结论：UI 展示的是这组字段，而不是 isVip()/getVipStage()。
     * 所以要把它们逐个覆盖成 SVIP 语义，UI 才会真的变化。
     */
    private void hookCommerceUiFields(Class<?> c) {
        // ---- 到期时间：拉到 2100-01-01（秒级）----
        final long FAR_FUTURE_SEC = 4102444800L;
        for (String n : new String[]{"getExpireDate", "getExpireTimeMax"}) {
            Method m = RefProxy.findMethod(c, n);
            if (m == null) { log("UI skip " + n); continue; }
            try { RefProxy.force(this, m, FAR_FUTURE_SEC).install(); log("UI " + n + " -> 2100"); }
            catch (Throwable t) { log("UI " + n + " err: " + t); }
        }

        // ---- 会员类型 / 标签后缀：改成 svip 语义，抹掉"1元续费"诱导 ----
        for (String n : new String[]{"getLastMemberShipType", "getModifierVipLabelSuffix",
                "getVipLabelSuffix"}) {
            Method m = RefProxy.findMethod(c, n);
            if (m == null) { log("UI skip " + n); continue; }
            try { RefProxy.force(this, m, "SVIP").install(); log("UI " + n + " -> SVIP"); }
            catch (Throwable t) { log("UI " + n + " err: " + t); }
        }

        // ---- VIP 入口对象：构造 VipEntrance(title=SVIP, style=new_style_with_svip_label) ----
        Object entrance = buildVipEntrance(c);
        if (entrance == null) { log("UI VipEntrance build fail"); return; }
        for (String n : new String[]{"getSideBarVipEntrance", "getVipEntranceLabel",
                "getDefaultVipEntrance"}) {
            Method m = RefProxy.findMethod(c, n);
            if (m == null) { log("UI skip " + n); continue; }
            try { RefProxy.force(this, m, entrance).install(); log("UI " + n + " -> VipEntrance(SVIP)"); }
            catch (Throwable t) { log("UI " + n + " err: " + t); }
        }
    }

    /** 构造 VipEntrance(title, style) 实例；类找不到时返回 null */
    private Object buildVipEntrance(Class<?> anyLoaded) {
        try {
            Class<?> ve = RefProxy.findClass("com.luna.biz.entitlement.entity.VipEntrance",
                    anyLoaded.getClassLoader());
            if (ve == null) return null;
            java.lang.reflect.Constructor<?> ctor = null;
            for (java.lang.reflect.Constructor<?> cc : ve.getDeclaredConstructors()) {
                Class<?>[] pt = cc.getParameterTypes();
                if (pt.length == 2 && pt[0] == String.class && pt[1] == String.class) {
                    ctor = cc; break;
                }
            }
            if (ctor == null) return null;
            ctor.setAccessible(true);
            return ctor.newInstance("SVIP", "new_style_with_svip_label");
        } catch (Throwable t) {
            log("VipEntrance ctor err: " + t);
            return null;
        }
    }

    // ==================== 试听(60s)区间解锁 ====================
    /**
     * 试听限制的两处闸门：
     *  1) PlayableEntitlementExtKt.x(Track) —— isPreviewResOnly，判定"是否仅试听资源"
     *  2) NetTrackPreview.getEnd()          —— 试听区间终点（常为 60000ms）
     * 双管齐下：判定改 false，区间终点拉到 duration。
     */
    private void hookPreview(ClassLoader cl) {
        // 1) isPreviewResOnly(x) / isCandidatePreview(m) / 相关布尔判定 -> false
        Class<?> pe = RefProxy.findClass(
                "com.luna.biz.playing.common.entitlement.PlayableEntitlementExtKt", cl);
        if (pe != null) {
            // Kt 文件类：目标方法全部是 static。只排除 <clinit> 等
            for (Method m : pe.getDeclaredMethods()) {
                if (m.getReturnType() != boolean.class) continue;
                String mn = m.getName();
                if ("<clinit>".equals(mn) || "<init>".equals(mn)) continue;
                try { RefProxy.forceFalse(this, m).install(); log("PREVIEW " + mn + "() -> false"); }
                catch (Throwable ignored) {}
            }
        } else {
            log("PREVIEW ext not found");
        }

        // 2) NetTrackPreview.getEnd() -> getDuration()，试听区间拉满
        Class<?> np = RefProxy.findClass(
                "com.luna.common.arch.net.entity.track.NetTrackPreview", cl);
        if (np == null) { log("PREVIEW NP not found"); return; }
        Method getEnd = RefProxy.findMethod(np, "getEnd");
        final Method getDur = RefProxy.findMethod(np, "getDuration");
        if (getEnd == null || getDur == null) { log("PREVIEW end/dur not found"); return; }
        try {
            final XposedInterface self = this;
            this.hook(getEnd).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object real = chain.proceed();
                    Object dur;
                    try { dur = getDur.invoke(chain.getThisObject()); } catch (Throwable t) { return real; }
                    try {
                        long e = ((Number) real).longValue();
                        long d = ((Number) dur).longValue();
                        if (e > 0 && d > e) {
                            RefProxy.log(self, "PREVIEW end " + e + " -> " + d);
                            return dur;
                        }
                    } catch (Throwable ignored) {}
                    return real;
                }
            });
            log("PREVIEW getEnd -> getDuration");
        } catch (Throwable t) { log("PREVIEW end err: " + t); }
    }

    // ==================== PlayerInfo 播放资源类型 ====================
    private void hookPlayerInfo(ClassLoader cl) {
        Class<?> rt = RefProxy.findClass("com.luna.common.player.mediaplayer.MediaResType", cl);
        Class<?> pi = RefProxy.findClass("com.luna.common.arch.db.entity.PlayerInfo", cl);
        if (rt == null || pi == null) { log("PLAYERINFO not found rt=" + (rt != null)); return; }
        try {
            // 取 FULL 枚举常量（真实名不是 NORMAL）
            Object normalConst = null;
            if (rt.isEnum()) {
                for (Object e : rt.getEnumConstants()) {
                    String n = String.valueOf(e);
                    String nm = (e instanceof Enum) ? ((Enum<?>) e).name() : n;
                    log("PLAYERINFO enum candidate name=" + nm + " str=" + n);
                    if ("FULL".equals(nm) || "FULL".equals(n) || "full".equals(n)) { normalConst = e; break; }
                }
                // 兜底：按 value 字段找 "full"
                if (normalConst == null) {
                    for (Object e : rt.getEnumConstants()) {
                        log("PLAYERINFO enum candidate = " + e);
                    }
                }
            }
            if (normalConst == null) { log("PLAYERINFO FULL not found isEnum=" + rt.isEnum()); return; }
            log("PLAYERINFO NORMAL = " + normalConst);
            Method g = RefProxy.findMethod(pi, "getMediaResType");
            if (g != null) {
                RefProxy.force(this, g, normalConst).install();
                log("PLAYERINFO getMediaResType -> NORMAL");
            }
            Method ex = RefProxy.findMethod(pi, "isExpired");
            if (ex != null) {
                RefProxy.forceFalse(this, ex).install();
                log("PLAYERINFO isExpired -> false");
            }
        } catch (Throwable t) { log("PLAYERINFO err: " + t); }
    }

    // ==================== Track 播放/音质鉴权字段 ====================
    private void hookTrackPlayable(ClassLoader cl) {
        Class<?> t = RefProxy.findClass("com.luna.common.arch.db.entity.Track", cl);
        if (t == null) { log("TRACK not found"); return; }
        // 布尔包装类：置 false（解除"仅VIP"限制）
        for (String n : new String[]{"getOnlyVipPlayable", "getOnlyVipDownload",
                "getQualityOnlyVipCanPlay", "getQualityOnlyVipDownload",
                "getQualityOnlyPurchasedCanPlay", "getQualityOnlyPurchasedCanDownload"}) {
            Method m = RefProxy.findMethod(t, n);
            if (m == null) { log("TRACK skip " + n); continue; }
            try { RefProxy.forceFalse(this, m).install(); log("TRACK " + n + " -> false"); }
            catch (Throwable ex) { log("TRACK " + n + " err: " + ex); }
        }
        // getPreview 不能置 null —— TrackPlayable.getPreviewVid() 会 NPE 崩溃
        // 仅保留 OnlyVip* 系列
        log("TRACK getPreview kept (avoid NPE)");
    }

    // ==================== 音质权益（无损/全景声） ====================
    /**
     * frida 实测调用链（NewHook 生效态）：
     *   AQCFG.M()/G()/N()                       -> lossless   （解析出无损）
     *   AQCFG.I(AudioQuality,boolean)           -> auto       （★降级点：传 lossless 出 auto）
     *   AQCFG.J(key,AudioQuality,boolean,int,..)-> auto       （★落盘校验：lossless 被降级写回）
     *   AQCFG.S()                               -> auto       （当前生效值）
     *
     * 原实现按名字找 resolveValueForEntitlement —— 该方法在 dex 中不存在（已被 R8 单字母化），
     * 所以 AQCFG 分支从未命中，导致"选了无损又自己跳回极高"。
     *
     * 现改为按签名定位：
     *   1) 静态 4 参 + 返回 AudioQuality        → J  (校验落盘)  → 放行 arg1(音质)
     *   2) 实例 2 参(首参 AudioQuality) + 返回  → I  (权益降级)  → 放行 arg0
     *   3) 实例 0 参 + 返回 AudioQuality        → S  (当前值)    → 强制 LOSSLESS
     */
    private void hookAudioQuality(ClassLoader cl) {
        Class<?> aq = RefProxy.findClass("com.luna.common.arch.playable.AudioQuality", cl);
        if (aq == null) { log("AQ not found"); return; }
        Object lossless = null;
        try {
            java.lang.reflect.Field f = aq.getDeclaredField("LOSSLESS");
            f.setAccessible(true);
            lossless = f.get(null);
            log("AQ LOSSLESS = " + lossless);
        } catch (Throwable t) { log("AQ LOSSLESS err: " + t); }
        if (lossless == null) return;

        Class<?> cfg = RefProxy.findClass(
                "com.luna.biz.playing.common.config.AudioQualityConfig", cl);
        if (cfg == null) { log("AQCFG not found"); return; }

        int nJ = 0, nI = 0, nS = 0;
        for (Method m : cfg.getDeclaredMethods()) {
            Class<?> rt = m.getReturnType();
            if (rt != aq) continue;                       // 只关心返回 AudioQuality 的
            Class<?>[] pt = m.getParameterTypes();
            boolean isStatic = java.lang.reflect.Modifier.isStatic(m.getModifiers());
            try {
                if (isStatic && pt.length == 4 && pt[1] == aq) {
                    // J: (key, AudioQuality, boolean, int) → 放行 arg[1]（音质）
                    RefProxy.passthroughArg1(this, m).install();
                    nJ++;
                } else if (!isStatic && pt.length == 2 && pt[0] == aq) {
                    // ⭐ I / K / h 三个实例方法签名完全相同 (AudioQuality, boolean)：
                    //    I 已被拦，但 K / h 同样可能是降级点 —— 全部拦下
                    RefProxy.passthroughArg0(this, m).install();
                    nI++;
                } else if (!isStatic && pt.length == 0) {
                    // S: () → 恒 LOSSLESS
                    RefProxy.force(this, m, lossless).install();
                    nS++;
                }
            } catch (Throwable t) { log("AQCFG " + m.getName() + " err: " + t); }
        }
        log("AQCFG installed: J(pass)=" + nJ + " (AudioQuality,bool)(pass)=" + nI
                + " noarg(->lossless)=" + nS);
    }

    // ==================== VipStatus 枚举（终极判定源） ====================
    private void hookVipStatus(ClassLoader cl) {
        Class<?> c = RefProxy.findClass("com.luna.common.arch.db.entity.VipStatus", cl);
        if (c == null) { log("VIPSTATUS not found"); return; }
        for (String n : new String[]{"isVip", "isSVip"}) {
            Method m = RefProxy.findMethod(c, n);
            if (m == null) { log("VIPSTATUS skip " + n); continue; }
            try { RefProxy.forceTrue(this, m).install(); log("VIPSTATUS " + n + " -> true"); }
            catch (Throwable t) { log("VIPSTATUS " + n + " err: " + t); }
        }
        Method gv = RefProxy.findMethod(c, "getValue");
        if (gv != null) {
            try { RefProxy.force(this, gv, "svip").install(); log("VIPSTATUS getValue -> svip"); }
            catch (Throwable t) { log("VIPSTATUS getValue err: " + t); }
        }
        // 同时也 hook CommerceInfoRepo.V()
        Class<?> repo = RefProxy.findClass(
                "com.luna.biz.entitlement.core.commerceinfo.core.CommerceInfoRepo", cl);
        if (repo != null) {
            Method v = RefProxy.findMethod(repo, "V");
            if (v != null) {
                try { RefProxy.forceTrue(this, v).install(); log("REPO V hooked"); }
                catch (Throwable t) { log("REPO V err: " + t); }
            }
        }
    }

    // ==================== Lancet Hook 检测 ====================
    private void hookLancet(ClassLoader cl) {
        for (String cn : new String[]{
                "com.luna.music.lint.lancet.LancetHookDetector",
                "com.luna.music.lint.lancet.LancetHookDetector$Companion"}) {
            Class<?> c = RefProxy.findClass(cn, cl);
            if (c == null) { log("LANCET not found " + cn); continue; }
            for (Method m : c.getDeclaredMethods()) {
                Class<?> rt = m.getReturnType();
                if (rt == boolean.class) {
                    try { RefProxy.forceFalse(this, m).install(); log("LANCET " + m.getName() + " -> false"); }
                    catch (Throwable ignored) {}
                }
            }
        }
    }

    // ==================== SubscriptionUpdateEvent（UI 状态源） ====================
    private void hookSubscriptionEvent(ClassLoader cl) {
        Class<?> c = RefProxy.findClass(
                "com.luna.biz.entitlement.event.SubscriptionUpdateEvent", cl);
        if (c == null) { log("EVENT not found"); return; }
        Method vs = RefProxy.findMethod(c, "getVipStage");
        if (vs != null) {
            try { RefProxy.force(this, vs, "svip").install(); log("EVENT getVipStage -> svip"); }
            catch (Throwable t) { log("EVENT getVipStage err: " + t); }
        }
        Method iv = RefProxy.findMethod(c, "isVip");
        if (iv != null) {
            // isVip() 若返回 boolean，强设字符串会 ClassCastException；按返回类型分派
            if (iv.getReturnType() == boolean.class) {
                try { RefProxy.forceTrue(this, iv).install(); log("EVENT isVip -> true"); }
                catch (Throwable t) { log("EVENT isVip err: " + t); }
            } else {
                try { RefProxy.force(this, iv, "true").install(); log("EVENT isVip -> \"true\""); }
                catch (Throwable t) { log("EVENT isVip err: " + t); }
            }
        }
        Method st = RefProxy.findMethod(c, "getStatus");
        if (st != null) {
            try { RefProxy.force(this, st, "svip").install(); log("EVENT getStatus -> svip"); }
            catch (Throwable t) { log("EVENT getStatus err: " + t); }
        }
    }

    // ==================== UserBrief（用户资料） ====================
    private void hookUserBrief(ClassLoader cl) {
        Class<?> c = RefProxy.findClass("com.luna.common.arch.net.entity.user.UserBrief", cl);
        if (c == null) { log("USER not found"); return; }
        Method vs = RefProxy.findMethod(c, "getVipStage");
        if (vs != null) {
            try { RefProxy.force(this, vs, "svip").install(); log("USER getVipStage -> svip"); }
            catch (Throwable t) { log("USER getVipStage err: " + t); }
        }
    }

    // ==================== 日志 ====================
    private void log(String msg) {
        RefProxy.log(this, msg);
    }
}
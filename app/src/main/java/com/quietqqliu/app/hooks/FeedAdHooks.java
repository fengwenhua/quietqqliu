package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;
import java.util.Collections;

import io.github.libxposed.api.XposedModule;

/**
 * AD-02 推荐页信息流广告（QQ浏览器 20.7.5.5035）。
 *
 * <p>「推荐」列表的广告由两层组成：
 * <ul>
 *   <li>原生信息流：{@code FeedsNativeAdServiceImpl} 负责向服务端声明广告位
 *       （getFeedsRequestAdInfo）、首屏广告重载（prepareFeedsFirstStageAdReload）
 *       并接收广告回填（onNotifyAdReloadResult / onNotifyCacheAdReloadResult）；</li>
 *   <li>优量汇 Hippy 信息流：{@code YLHFeedsAdManager.loadAd} 拉取并回传广告卡。</li>
 * </ul>
 *
 * <p>策略：不声明广告位、不发起首屏广告重载、不接收广告回填，并让优量汇
 * loadAd/validADCount/pickYLHFeedsAD 返回空。列表里的普通 UGC 卡不受影响。
 */
final class FeedAdHooks {

    private static final String FEEDS_SERVICE =
            "com.tencent.mtt.nativead.service.FeedsNativeAdServiceImpl";
    private static final String LIST_ITEM_MANAGER =
            "com.tencent.mtt.nativead.component.FeedsListItemComponentManager";
    private static final String YLH_MANAGER =
            "com.tencent.mtt.commercial.business.feeds.YLHFeedsAdManager";
    private static final String NHOME_SERVICE =
            "com.tencent.mtt.nativead.service.NHomeAdService";

    private FeedAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.FEED_AD_FILTER;
        try {
            installFeedsService(module, cl, f);
            installListItemManager(module, cl, f);
            installYlh(module, cl, f);
            installNHome(module, cl, f);
            HookState.note(f, "信息流：FeedsNativeAdService + YLH + NHome");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void installFeedsService(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClass(module, f, cl, FEEDS_SERVICE);
        if (cls == null) {
            return;
        }

        // 请求体里不再携带广告位信息 -> 服务端不下发广告卡。
        Method reqInfo = HookKit.findMethod(module, f, cls, "getFeedsRequestAdInfo");
        HookKit.install(module, f, "FeedsNativeAdService#getFeedsRequestAdInfo", reqInfo,
                HookKit.constValue(module, f, "信息流广告位声明清空", Collections.emptyMap()));

        skip(module, f, cls, "prepareFeedsFirstStageAdReload", 3, "首屏广告重载");
        skip(module, f, cls, "onNotifyAdReloadResult", 4, "广告重载回调");
        skip(module, f, cls, "onNotifyCacheAdReloadResult", 4, "缓存广告重载回调");
    }

    private static void installListItemManager(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, LIST_ITEM_MANAGER);
        if (cls == null) {
            return;
        }
        skip(module, f, cls, "a0", 3, "FeedsListItemComponentManager#a0");
        skip(module, f, cls, "U", 4, "FeedsListItemComponentManager#U");
        skip(module, f, cls, "V", 4, "FeedsListItemComponentManager#V");
    }

    private static void installYlh(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, YLH_MANAGER);
        if (cls == null) {
            return;
        }
        skip(module, f, cls, "loadAd", 5, "YlhFeedsAdManager#loadAd");

        Method count = HookKit.findMethod(module, f, cls, "validADCount", boolean.class);
        HookKit.install(module, f, "YlhFeedsAdManager#validADCount", count,
                HookKit.constValue(module, f, "优量汇可用广告数=0", 0));

        Method pick = HookKit.findMethod(module, f, cls, "pickYLHFeedsAD", boolean.class);
        HookKit.install(module, f, "YlhFeedsAdManager#pickYLHFeedsAD", pick,
                HookKit.constValue(module, f, "优量汇广告选择=null", null));
    }

    private static void installNHome(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, NHOME_SERVICE);
        if (cls == null) {
            return;
        }
        Method valid = HookKit.findMethod(module, f, cls, "isValidData");
        HookKit.install(module, f, "NHomeAdService#isValidData", valid,
                HookKit.constBool(module, f, "NHome 广告数据无效", false));
    }

    private static void skip(XposedModule module, Feature f, Class<?> cls,
                             String name, int arity, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, arity);
        HookKit.install(module, f, label, m,
                HookKit.skipVoid(module, f, label + " skipped"));
    }
}

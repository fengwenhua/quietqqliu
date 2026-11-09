package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * AD-06 激励视频 /「看广告免广告」(QQ浏览器 20.7.5.5035)。
 *
 * <p>视频激励广告由 {@code RewardAdController} 驱动；Hippy 页面还可以通过
 * {@code QBBusinessADModule} 的 showAmsRewardVideoAD / preloadRewardAd /
 * showRewardDialog 直接发起。
 *
 * <p>策略：不发激励视频请求、不弹广告。依赖「看广告」的领奖/解锁会失败，
 * 这是去广告的预期代价；服务端发奖本来也不会因为本地拦截而白给。
 *
 * <p>通用奖励弹窗（showRewardDialog/preloadRewardAd）不在这里拦截：它由
 * {@link DownloadAdUnlockHooks}（AD-10）接管，直接回传「广告完成」，
 * 用于小说/网页的「看广告下载」解锁。
 */
final class RewardAdHooks {

    private static final String CONTROLLER =
            "com.tencent.mtt.browser.video.adreward.RewardAdController";

    private RewardAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.REWARD_AD_BLOCK;
        try {
            Class<?> controller = HookKit.findClass(module, f, cl, CONTROLLER);
            if (controller != null) {
                skip(module, f, controller, "V0", 2, "RewardAdController#V0(展示)");
                skip(module, f, controller, "n0", 1, "RewardAdController#n0(展示)");
                skip(module, f, controller, "b", 1, "RewardAdController#b(解锁上报)");
                skip(module, f, controller, "onStartAdEvent", 1,
                        "RewardAdController#onStartAdEvent");
            }

            HookState.note(f, "激励视频：RewardAdController 展示/解锁拦截");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void skip(XposedModule module, Feature f, Class<?> cls,
                             String name, int arity, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, arity);
        HookKit.install(module, f, label, m,
                HookKit.skipVoid(module, f, label + " skipped"));
    }
}

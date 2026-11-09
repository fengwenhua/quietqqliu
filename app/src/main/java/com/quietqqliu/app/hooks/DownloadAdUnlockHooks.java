package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * AD-10 下载看广告解锁（QQ浏览器 20.7.5.5035）。
 *
 * <p>小说 H5 阅读页点「看广告下载」时，页面通过 {@code qb_bridge} 调用
 * {@code OpenJsapiTbsAd}，再由两条链路拉起奖励广告：
 * <ul>
 *   <li>通用奖励弹窗：{@code RewardDialogServiceImpl.showRewardDialog} /
 *       {@code preloadRewardAd}，回调是 Kotlin Function1&lt;Map&lt;String,String&gt;&gt;；</li>
 *   <li>AMS 激励视频：{@code BusinessAdServiceImp.showAmsRewardVideoAD} /
 *       {@code setAmsRewardVideoAD}，回调是 IBusinessADService.a。</li>
 * </ul>
 *
 * <p>策略：不加载、不展示广告，直接把「展示成功 + RewardFinished」回调给 H5；
 * H5 认为广告已完成，继续走离线下载。普通激励视频（AD-06）仍由独立功能拦截。
 */
final class DownloadAdUnlockHooks {

    private static final String REWARD_DIALOG =
            "com.tencent.mtt.nativead.service.RewardDialogServiceImpl";
    private static final String BUSINESS_AD =
            "com.tencent.mtt.business.adservice.BusinessAdServiceImp";
    private static final String AD_RESULT =
            "com.tencent.mtt.browser.business.ad.a";

    private DownloadAdUnlockHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.DOWNLOAD_AD_UNLOCK;
        try {
            installRewardDialog(module, cl, f);
            installBusinessAd(module, cl, f);
            HookState.note(f, "下载解锁：RewardDialogService + AMS 激励视频回调");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void installRewardDialog(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClass(module, f, cl, REWARD_DIALOG);
        if (cls == null) {
            return;
        }

        Method preload = HookKit.findByNameAndArity(module, f, cls, "preloadRewardAd", 2);
        HookKit.install(module, f, "RewardDialogService#preloadRewardAd", preload,
                callbackSuccess(module, f, "预加载", 1));

        Method show = HookKit.findByNameAndArity(module, f, cls, "showRewardDialog", 2);
        HookKit.install(module, f, "RewardDialogService#showRewardDialog", show,
                callbackSuccess(module, f, "展示", 1));
    }

    /**
     * 直接回调 H5：先回「展示成功」，再回 RewardFinished。
     * 真实链路也是先 a0(Show Ad Dialog)、后 D(RewardFinished)，这里按同样顺序模拟。
     */
    private static XposedInterface.Hooker callbackSuccess(XposedModule module, Feature f,
                                                          String label, int argIndex) {
        return chain -> {
            try {
                Object callback = chain.getArg(argIndex);
                if (callback == null) {
                    return chain.proceed();
                }
                Map<String, String> shown = new LinkedHashMap<>();
                shown.put("result", "Success");
                shown.put("message", "Show Ad Dialog");
                invokeCallback(callback, shown);

                Map<String, String> finished = new LinkedHashMap<>();
                finished.put("event", "RewardFinished");
                finished.put("result", "{}");
                finished.put("message", "Show Ad Dialog");
                invokeCallback(callback, finished);

                HookKit.hit(module, f, "H5 奖励广告 " + label + " 已模拟完成");
                return null;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error " + label, t);
                return chain.proceed();
            }
        };
    }

    private static void invokeCallback(Object callback, Map<String, String> map)
            throws Exception {
        Method invoke = null;
        for (Method m : callback.getClass().getMethods()) {
            if ("invoke".equals(m.getName()) && m.getParameterTypes().length == 1) {
                invoke = m;
                break;
            }
        }
        if (invoke == null) {
            throw new NoSuchMethodException("Function1.invoke on " + callback.getClass());
        }
        invoke.setAccessible(true);
        invoke.invoke(callback, map);
    }

    private static void installBusinessAd(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, BUSINESS_AD);
        if (cls == null) {
            return;
        }

        Method show = HookKit.findByNameAndArity(module, f, cls, "showAmsRewardVideoAD", 2);
        HookKit.install(module, f, "BusinessAdService#showAmsRewardVideoAD", show,
                amsSuccess(module, f, cl, "AMS 激励视频展示"));

        Method set = HookKit.findByNameAndArity(module, f, cls, "setAmsRewardVideoAD", 2);
        HookKit.install(module, f, "BusinessAdService#setAmsRewardVideoAD", set,
                amsSuccess(module, f, cl, "AMS 激励视频预加载"));

        Method loaded = HookKit.findMethod(module, f, cls, "isRewardVideoADLoaded", String.class);
        HookKit.install(module, f, "BusinessAdService#isRewardVideoADLoaded", loaded,
                HookKit.constBool(module, f, "激励视频视为已就绪", true));
    }

    private static XposedInterface.Hooker amsSuccess(XposedModule module, Feature f,
                                                     ClassLoader cl, String label) {
        return chain -> {
            try {
                Object callback = chain.getArg(1);
                String posId = chain.getArg(0) instanceof String ? (String) chain.getArg(0) : "";
                if (callback == null) {
                    return chain.proceed();
                }
                Object result = buildAdResult(cl, posId);
                if (result == null) {
                    return chain.proceed();
                }
                Method onResult = null;
                for (Method m : callback.getClass().getMethods()) {
                    if ("onResult".equals(m.getName()) && m.getParameterTypes().length == 1) {
                        onResult = m;
                        break;
                    }
                }
                if (onResult == null) {
                    return chain.proceed();
                }
                onResult.setAccessible(true);
                onResult.invoke(callback, result);
                HookKit.hit(module, f, label + " 已模拟成功");
                return null;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error " + label, t);
                return chain.proceed();
            }
        };
    }

    /** com.tencent.mtt.browser.business.ad.a(code,posId,data,errMsg)；code=0 成功。 */
    private static Object buildAdResult(ClassLoader cl, String posId) {
        try {
            Class<?> cls = Class.forName(AD_RESULT, false, cl);
            Constructor<?> ctor = cls.getConstructor(int.class, String.class,
                    String.class, String.class);
            return ctor.newInstance(0, posId, "{}", "");
        } catch (Throwable t) {
            return null;
        }
    }
}

package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-13 热启动全屏倒计时广告 / 右下角悬浮挂件（QQ浏览器 20.7.5.5035）。
 *
 * <p>实机表现：热启动（SplashActivity）结束后弹出全屏倒计时广告，倒计时结束或关闭时
 * 缩到右下角，变成带倒计时和「广告」小字的悬浮挂件。链路是：
 *
 * <pre>
 * CommonMaskService.onSplashHasEnd
 *   → z()/w() 请求 200598 的 mask 广告
 *   → D(o) 创建 CommonMaskFullScreenDialog（nativead.ui.mask.commonmask.y）
 *   → y.o0() 展示 CountDownMaskLayout 全屏倒计时
 *   → y.dismiss() 调 nativead.utils.q.o(o)，通知显示倒计时挂件
 *   → homepage.pendant.global.view.b.e(task, position)
 *   → CountDownPendantViewNum / CountDownPendantViewV4（pendantLayout + adTv + 倒计时）
 * </pre>
 *
 * <p>策略：
 * <ul>
 *   <li>跳过 CommonMaskService 的初始化、Splash 结束回调、请求与展示方法；</li>
 *   <li>跳过 nativead.utils.q.o 的挂件通知；</li>
 *   <li>首页挂件入口 b.e 只拦截 COUNT_DOWN_PENDANT / 商业化倒计时，其他挂件放行；</li>
 *   <li>AdMaskService 的 mask 请求与识别断开（信息流 mask 兜底）。</li>
 * </ul>
 * 普通下载挂件、文本挂件、游戏挂件等不受影响。
 */
final class MaskPendantAdHooks {

    private static final String COMMON_MASK =
            "com.tencent.mtt.nativead.service.CommonMaskService";
    private static final String AD_MASK =
            "com.tencent.mtt.nativead.service.AdMaskService";
    private static final String MASK_UTILS =
            "com.tencent.mtt.nativead.utils.q";
    private static final String FULL_SCREEN_MASK =
            "com.tencent.mtt.nativead.ui.mask.commonmask.y";
    private static final String PENDANT_VIEW =
            "com.tencent.mtt.browser.homepage.pendant.global.view.b";

    private MaskPendantAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.MASK_PENDANT_AD_BLOCK;
        try {
            installCommonMaskService(module, cl, f);
            installMaskUtils(module, cl, f);
            installFullScreenMask(module, cl, f);
            installPendantGate(module, cl, f);
            installAdMaskService(module, cl, f);
            HookState.note(f, "热启动全屏倒计时 + 右下角悬浮挂件");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    /** 全屏 mask 的总开关：初始化、Splash 结束、请求、回包、展示全部跳过。 */
    private static void installCommonMaskService(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClass(module, f, cl, COMMON_MASK);
        if (cls == null) {
            return;
        }
        skip(module, f, cls, "init", 0, "CommonMaskService#init");
        skip(module, f, cls, "onSplashHasEnd", 1, "CommonMaskService#onSplashHasEnd");
        skip(module, f, cls, "z", 0, "CommonMaskService#z");
        skip(module, f, cls, "w", 0, "CommonMaskService#w");
        skip(module, f, cls, "a", 1, "CommonMaskService#a(splashState)");
        skip(module, f, cls, "A", 1, "CommonMaskService#A(response)");
        skip(module, f, cls, "D", 1, "CommonMaskService#D(show)");
    }

    /** 全屏广告关闭时的「缩成挂件」通知。 */
    private static void installMaskUtils(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, MASK_UTILS);
        if (cls == null) {
            return;
        }
        skip(module, f, cls, "o", 1, "MaskUtils#o(notifyPendant)");
    }

    /** 即使上层漏了，也直接拒绝全屏 dialog 的 show。 */
    private static void installFullScreenMask(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, FULL_SCREEN_MASK);
        if (cls == null) {
            return;
        }
        Method show = HookKit.findByNameAndArity(module, f, cls, "o0", 0);
        HookKit.install(module, f, "CommonMaskDialog#o0", show,
                HookKit.constBool(module, f, "全屏倒计时广告不展示", false));
    }

    /**
     * 首页挂件入口：b.e(task, position) 是所有挂件展示的公共方法。
     * 只拦截 COUNT_DOWN_PENDANT / commercialization mask countdown，其余放行。
     */
    private static void installPendantGate(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, PENDANT_VIEW);
        if (cls == null) {
            return;
        }
        Method show = HookKit.findByNameAndArity(module, f, cls, "e", 2);
        HookKit.install(module, f, "PendantView#e(countdown)", show, chain -> {
            try {
                if (isCountDownPendant(chain.getArg(0))) {
                    HookKit.hit(module, f, "倒计时悬浮挂件不展示");
                    return Boolean.FALSE;
                }
                return chain.proceed();
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error PendantView#e", t);
                return chain.proceed();
            }
        });
    }

    /** 信息流 mask 广告入口（与全屏 mask 共用同一类广告素材）。 */
    private static void installAdMaskService(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, AD_MASK);
        if (cls == null) {
            return;
        }
        skip(module, f, cls, "requestMaskAdAndShow", 3, "AdMaskService#requestMaskAdAndShow");
        Method check = HookKit.findByNameAndArity(module, f, cls, "isMaskCountDownAd", 1);
        HookKit.install(module, f, "AdMaskService#isMaskCountDownAd", check,
                HookKit.constBool(module, f, "不识别为倒计时 mask", false));
    }

    /**
     * task.c 的 public 字段：
     * <ul>
     *   <li>{@code e}：GlobalTaskType，COUNT_DOWN_PENDANT 即倒计时挂件；</li>
     *   <li>{@code d}：任务 ID，商业化 mask 的 ID 以
     *       {@code commercialization mask countdown} 开头。</li>
     * </ul>
     */
    private static boolean isCountDownPendant(Object task) {
        if (task == null) {
            return false;
        }
        Class<?> cls = task.getClass();
        try {
            Field type = cls.getField("e");
            Object value = type.get(task);
            if (value != null && "COUNT_DOWN_PENDANT".equals(value.toString())) {
                return true;
            }
        } catch (Throwable ignored) {
            // 字段改名时继续看任务 ID。
        }
        try {
            Field id = cls.getField("d");
            Object value = id.get(task);
            if (value instanceof String) {
                String text = (String) value;
                return text.startsWith("commercialization mask countdown")
                        || text.startsWith("olympics mask countdown");
            }
        } catch (Throwable ignored) {
            // 两个字段都读不到时放行，避免误伤普通挂件。
        }
        return false;
    }

    private static void skip(XposedModule module, Feature f, Class<?> cls,
                             String name, int arity, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, arity);
        HookKit.install(module, f, label, m,
                HookKit.skipVoid(module, f, label + " skipped"));
    }
}

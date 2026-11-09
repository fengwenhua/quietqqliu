package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * AD-08 摇一摇/扭转跳转（QQ浏览器 20.7.5.5035）。
 *
 * <p>信息流广告的传感器逻辑统一收敛在 {@code ShakeManagerService}：
 * {@code register()} 注册加速度计，{@code setShakeListener(f)} 绑定跳转回调。
 * 跳过这两处后广告不会再因为晃动/扭转而打开落地页，普通页面手势不受影响。
 */
final class ShakeAdHooks {

    private static final String SHAKE_SERVICE =
            "com.tencent.mtt.browser.ShakeManagerService";
    private static final String HIPPY_AD =
            "com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule";

    private ShakeAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.SHAKE_AD_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, SHAKE_SERVICE);
            if (cls != null) {
                skip(module, f, cls, "register", 0, "ShakeManagerService#register");
                skip(module, f, cls, "setShakeListener", 1,
                        "ShakeManagerService#setShakeListener");
            }

            // Hippy 页面还可能直接调用 registerShake/registerTwist。
            Class<?> hippy = HookKit.findClassOptional(module, f, cl, HIPPY_AD);
            if (hippy != null) {
                rejectPromise(module, f, hippy, "registerShake",
                        "QBBusinessADModule#registerShake");
                rejectPromise(module, f, hippy, "registerTwist",
                        "QBBusinessADModule#registerTwist");
            }

            HookState.note(f, "摇一摇：ShakeManagerService + Hippy 桥");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void rejectPromise(XposedModule module, Feature f, Class<?> cls,
                                      String name, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, 2);
        HookKit.install(module, f, label, m, chain -> {
            try {
                Object promise = chain.getArg(1);
                if (promise != null) {
                    Method reject;
                    try {
                        reject = promise.getClass().getMethod("reject", String.class);
                    } catch (NoSuchMethodException e) {
                        reject = promise.getClass().getMethod("reject", Object.class);
                    }
                    reject.setAccessible(true);
                    reject.invoke(promise, "quietqqliu: shake disabled");
                }
                HookKit.hit(module, f, label + " rejected");
                return null;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error " + label, t);
                return chain.proceed();
            }
        });
    }

    private static void skip(XposedModule module, Feature f, Class<?> cls,
                             String name, int arity, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, arity);
        HookKit.install(module, f, label, m,
                HookKit.skipVoid(module, f, label + " skipped"));
    }
}

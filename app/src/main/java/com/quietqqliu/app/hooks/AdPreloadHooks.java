package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-07 广告预加载断源（QQ浏览器 20.7.5.5035）。
 *
 * <p>{@code PreFetchAdService} 在页面打开前后预取广告素材，用于信息流与
 * 页面级广告秒开。本功能不改变展示，只让预取请求不再发出，减少后台流量。
 * 展示侧由 AD-01/AD-02 兜底。
 */
final class AdPreloadHooks {

    private static final String PRE_FETCH =
            "com.tencent.mtt.nativead.service.PreFetchAdService";

    private AdPreloadHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.AD_PRELOAD_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, PRE_FETCH);
            if (cls == null) {
                return;
            }
            skip(module, f, cls, "initAd", 3, "PreFetchAdService#initAd");
            skip(module, f, cls, "getAd", 2, "PreFetchAdService#getAd");
            skip(module, f, cls, "a", 1, "PreFetchAdService#a");
            HookState.note(f, "预加载：PreFetchAdService 断源");
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

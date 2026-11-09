package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-01 开屏广告（QQ浏览器 20.7.5.5035）。
 *
 * <p>开屏有两条体系：
 * <ul>
 *   <li>AMS（腾讯广告）闪屏：{@code com.tencent.mtt.boot.browser.splash.v2.ams.a}
 *       是展示决策器，{@code AmsSplashAdkImpl} 负责真正 load/show；</li>
 *   <li>RMP 运营闪屏：{@code com.tencent.mtt.boot.browser.splash.v2.rmp.c/h}
 *       实现同一套 {@code w/z} 判定。</li>
 * </ul>
 *
 * <p>策略：把所有「展示判定」返回 false，并跳过真正的 load/show 与后台预加载。
 * 调用方在拿不到可展示素材时走官方失败分支直接进主页；不修改任何请求参数，
 * 也不 hook 广告 SDK 内部类，失败一律 fail-open。
 */
final class SplashAdHooks {

    private static final String AMS_DECIDER =
            "com.tencent.mtt.boot.browser.splash.v2.ams.a";
    private static final String AMS_ADAPTER =
            "com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl";
    private static final String RMP_SHELF =
            "com.tencent.mtt.boot.browser.splash.v2.rmp.c";
    private static final String RMP_LIVE =
            "com.tencent.mtt.boot.browser.splash.v2.rmp.h";
    private static final String PRELOAD =
            "com.tencent.mtt.boot.browser.splash.v2.SplashPreloadManager";

    private SplashAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.SPLASH_AD_BLOCK;
        try {
            installAmsDecider(module, cl, f);
            installAmsAdapter(module, cl, f);
            installRmp(module, cl, f);
            installPreload(module, cl, f);
            HookState.note(f, "开屏：AMS 决策/适配器 + RMP + 预加载");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    /** AMS 展示决策器：w/y/z/v/I/J 都是不同阶段的 boolean 闸门，统一返回 false。 */
    private static void installAmsDecider(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClass(module, f, cl, AMS_DECIDER);
        if (cls == null) {
            return;
        }
        blockBoolean(module, f, cls, "z", 2, "AMS 决策 z");
        blockBoolean(module, f, cls, "y", 3, "AMS 决策 y");
        blockBoolean(module, f, cls, "w", 2, "AMS 决策 w");
        blockBoolean(module, f, cls, "v", 3, "AMS 决策 v");
        blockBoolean(module, f, cls, "I", 2, "AMS 决策 I");
        blockBoolean(module, f, cls, "J", 3, "AMS 决策 J");
        blockBoolean(module, f, cls, "f", 1, "AMS 决策 f");

        // i(Context, String, String)：AMS 素材加载入口，跳过。
        Method load = HookKit.findByNameAndArity(module, f, cls, "i", 3);
        HookKit.install(module, f, "AmsSplashDecider#i", load,
                HookKit.skipVoid(module, f, "AMS 素材加载跳过"));
    }

    /** AMS 适配器：不 load、不 show，能力查询返回 false。 */
    private static void installAmsAdapter(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, AMS_ADAPTER);
        if (cls == null) {
            return;
        }
        skip(module, f, cls, "t", 3, "AmsSplashAdkImpl#t(load)");
        skip(module, f, cls, "E", 7, "AmsSplashAdkImpl#E(show)");
        skip(module, f, cls, "L", 6, "AmsSplashAdkImpl#L(show)");
        skip(module, f, cls, "c", 6, "AmsSplashAdkImpl#c(show)");
        skip(module, f, cls, "b", 2, "AmsSplashAdkImpl#b(attach)");
        skip(module, f, cls, "F", 4, "AmsSplashAdkImpl#F(load)");
        skip(module, f, cls, "M", 3, "AmsSplashAdkImpl#M(show)");

        blockBoolean(module, f, cls, "G", 0, "AmsSplashAdkImpl#G");
        blockBoolean(module, f, cls, "H", 0, "AmsSplashAdkImpl#H");
        blockBoolean(module, f, cls, "I", 0, "AmsSplashAdkImpl#I");
    }

    /** RMP 运营闪屏：c/h 的 w/z 与 g/Q 是展示入口。 */
    private static void installRmp(XposedModule module, ClassLoader cl, Feature f) {
        for (String name : new String[]{RMP_SHELF, RMP_LIVE}) {
            Class<?> cls = HookKit.findClassOptional(module, f, cl, name);
            if (cls == null) {
                continue;
            }
            blockBoolean(module, f, cls, "w", 2, shortName(name) + "#w");
            blockBoolean(module, f, cls, "z", 2, shortName(name) + "#z");
            skip(module, f, cls, "g", 0, shortName(name) + "#g");
            skip(module, f, cls, "Q", 0, shortName(name) + "#Q");
        }
    }

    /** 后台预加载：热启动回前台 / 冷启动结束时都不再拉素材。 */
    private static void installPreload(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, PRELOAD);
        if (cls == null) {
            return;
        }
        skip(module, f, cls, "onApplicationState", 1, "SplashPreloadManager#onApplicationState");
        skip(module, f, cls, "onColdLaunchEndEvent", 1, "SplashPreloadManager#onColdLaunchEndEvent");
    }

    /** 同名方法可能重载：按参数个数定位，找不到只记普通 skip。 */
    private static void blockBoolean(XposedModule module, Feature f, Class<?> cls,
                                     String name, int arity, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, arity);
        HookKit.install(module, f, label, m,
                HookKit.constBool(module, f, label + " blocked", false));
    }

    private static void skip(XposedModule module, Feature f, Class<?> cls,
                             String name, int arity, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, arity);
        HookKit.install(module, f, label, m,
                HookKit.skipVoid(module, f, label + " skipped"));
    }

    private static String shortName(String dotted) {
        int i = dotted.lastIndexOf('.');
        return i < 0 ? dotted : dotted.substring(i + 1);
    }
}

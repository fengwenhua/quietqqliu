package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-05 听书广告（QQ浏览器 20.7.5.5035）。
 *
 * <p>听书页基于 NVF 短视频信息流框架，广告通过
 * {@code com.tencent.mtt.nvf.business.ad.NVFAdHolder} 绑定到页面组件，
 * 由 {@code AdVideoLogic} 驱动播放/倒计时。小说内的音频广告还会经过
 * NovelAdManager，已由 AD-04 一并拦截。
 *
 * <p>策略：跳过 NVF 广告 holder 的数据绑定与视频广告逻辑；听书内容、
 * 播放控制与进度不受影响。
 */
final class ListenAdHooks {

    private static final String AD_HOLDER =
            "com.tencent.mtt.nvf.business.ad.NVFAdHolder";
    private static final String AD_VIDEO_LOGIC =
            "com.tencent.mtt.nvf.business.ad.logic.AdVideoLogic";
    private static final String AD_BLOCK_LOGIC =
            "com.tencent.mtt.nvf.business.ad.logic.AdBlockLogic";

    private ListenAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.LISTEN_AD_FILTER;
        try {
            Class<?> holder = HookKit.findClass(module, f, cl, AD_HOLDER);
            if (holder != null) {
                skip(module, f, holder, "z", 5, "NVFAdHolder#z(绑定广告)");
                skip(module, f, holder, "L", 1, "NVFAdHolder#L");
                skip(module, f, holder, "N", 1, "NVFAdHolder#N");
            }

            Class<?> video = HookKit.findClassOptional(module, f, cl, AD_VIDEO_LOGIC);
            if (video != null) {
                skip(module, f, video, "T1", 1, "AdVideoLogic#T1(更新广告组件)");
                Method h2 = HookKit.findByNameAndArity(module, f, video, "h2", 0);
                HookKit.install(module, f, "AdVideoLogic#h2", h2,
                        HookKit.constBool(module, f, "广告视频判定=否", false));
                Method i2 = HookKit.findByNameAndArity(module, f, video, "i2", 0);
                HookKit.install(module, f, "AdVideoLogic#i2", i2,
                        HookKit.constBool(module, f, "广告视频判定=否", false));
            }

            Class<?> block = HookKit.findClassOptional(module, f, cl, AD_BLOCK_LOGIC);
            if (block != null) {
                skip(module, f, block, "A1", 0, "AdBlockLogic#A1");
                skip(module, f, block, "D1", 0, "AdBlockLogic#D1");
            }

            HookState.note(f, "听书：NVFAdHolder + AdVideoLogic");
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

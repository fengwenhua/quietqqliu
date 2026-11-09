package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-03「我的」页面广告（QQ浏览器 20.7.5.5035）。
 *
 * <p>「我的」页面的广告有两处：
 * <ul>
 *   <li>头像挂件广告：{@code UCHeadOperationHandler.G()} 判断是否展示，
 *       {@code K()} 真正展示/上报；Hippy 侧通过 showHeadAdv 能力调用。</li>
 *   <li>模块位 Banner：{@code BannerPageAdapter/V2} 承载运营 Banner 广告。</li>
 * </ul>
 *
 * <p>策略：头像广告判定恒 false、展示跳过；Banner 适配器 itemCount 恒 0。
 * 普通功能入口不受影响。
 */
final class MineAdHooks {

    private static final String HEAD_HANDLER =
            "com.tencent.mtt.browser.account.usercenter.head.UCHeadOperationHandler";
    private static final String BANNER_ADAPTER =
            "com.tencent.mtt.browser.account.usercenter.newview.moduleslot.view.BannerPageAdapter";
    private static final String BANNER_ADAPTER_V2 =
            "com.tencent.mtt.browser.account.usercenter.newview.moduleslot.view.BannerPageAdapterV2";

    private MineAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.MINE_AD_FILTER;
        try {
            Class<?> head = HookKit.findClass(module, f, cl, HEAD_HANDLER);
            if (head != null) {
                Method canShow = HookKit.findByNameAndArity(module, f, head, "G", 0);
                HookKit.install(module, f, "UCHeadOperationHandler#G", canShow,
                        HookKit.constBool(module, f, "头像广告不展示", false));

                Method show = HookKit.findByNameAndArity(module, f, head, "K", 0);
                HookKit.install(module, f, "UCHeadOperationHandler#K", show,
                        HookKit.skipVoid(module, f, "头像广告展示跳过"));
            }

            emptyAdapter(module, cl, f, BANNER_ADAPTER);
            emptyAdapter(module, cl, f, BANNER_ADAPTER_V2);
            HookState.note(f, "我的：头像挂件广告 + Banner 位");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void emptyAdapter(XposedModule module, ClassLoader cl, Feature f,
                                     String className) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Method count = HookKit.findByNameAndArity(module, f, cls, "getItemCount", 0);
        HookKit.install(module, f, shortName(className) + "#getItemCount", count,
                HookKit.constValue(module, f, "Banner 适配器清空", 0));
    }

    private static String shortName(String dotted) {
        int i = dotted.lastIndexOf('.');
        return i < 0 ? dotted : dotted.substring(i + 1);
    }
}

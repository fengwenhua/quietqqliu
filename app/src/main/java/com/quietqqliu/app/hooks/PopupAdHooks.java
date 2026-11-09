package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-09 弹窗/插屏广告（QQ浏览器 20.7.5.5035）。
 *
 * <p>H5 页面里的广告弹窗/插屏通过 {@code H5AdManager.b(QBWebView, JSONObject, String)}
 * 分发；信息流「看广告免广告」浮层由 {@code FeedsAdFreeDialog.p(...)} 展示。
 * 两处都跳过，普通网页弹窗（非 H5AdManager 路径）不受影响。
 */
final class PopupAdHooks {

    private static final String H5_AD_MANAGER =
            "com.tencent.mtt.business.adservice.H5AdManager";
    private static final String AD_FREE_DIALOG =
            "com.tencent.mtt.nativead.feedsadfree.FeedsAdFreeDialog";

    private PopupAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.POPUP_AD_BLOCK;
        try {
            Class<?> h5 = HookKit.findClass(module, f, cl, H5_AD_MANAGER);
            if (h5 != null) {
                Method handle = HookKit.findByNameAndArity(module, f, h5, "b", 3);
                HookKit.install(module, f, "H5AdManager#b", handle,
                        HookKit.skipVoid(module, f, "H5 广告弹窗跳过"));
            }

            Class<?> dialog = HookKit.findClassOptional(module, f, cl, AD_FREE_DIALOG);
            if (dialog != null) {
                Method show = HookKit.findByNameAndArity(module, f, dialog, "p", 10);
                HookKit.install(module, f, "FeedsAdFreeDialog#p", show,
                        HookKit.skipVoid(module, f, "看广告免广告浮层跳过"));
            }

            HookState.note(f, "弹窗：H5AdManager + FeedsAdFreeDialog");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}

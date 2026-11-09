package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * UI-01 更新弹窗（QQ浏览器 20.7.5.5035）。
 *
 * <p>升级对话框的唯一入口是 {@code UpgradeDialogActivity.launch(Context, UpgradeStrategy)}；
 * 红点由 {@code UpgradeService.canShowRedDot(Type)} 决定。跳过 launch 并关闭红点后
 * 不再出现「发现新版本」打扰；主动检查更新入口仍可正常使用。
 */
final class UpdateDialogHooks {

    private static final String DIALOG_ACTIVITY = "com.tencent.upgrade.ui.UpgradeDialogActivity";
    private static final String ACTIVE_ACTIVITY = "com.tencent.upgrade.ui.UpgradeActiveActivity";
    private static final String UPGRADE_SERVICE = "com.tencent.mtt.browser.update.UpgradeService";

    private UpdateDialogHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.UPDATE_DIALOG_BLOCK;
        try {
            Class<?> dialog = HookKit.findClass(module, f, cl, DIALOG_ACTIVITY);
            if (dialog != null) {
                Method launch = HookKit.findByNameAndArity(module, f, dialog, "launch", 2);
                HookKit.install(module, f, "UpgradeDialogActivity#launch", launch,
                        HookKit.skipVoid(module, f, "更新弹窗不启动"));
            }

            Class<?> active = HookKit.findClassOptional(module, f, cl, ACTIVE_ACTIVITY);
            if (active != null) {
                Method onCreate = HookKit.findMethod(module, f, active, "onCreate",
                        android.os.Bundle.class);
                HookKit.install(module, f, "UpgradeActiveActivity#onCreate", onCreate,
                        chain -> {
                            Object result = chain.proceed();
                            try {
                                Method finish = chain.getThisObject().getClass()
                                        .getMethod("finish");
                                finish.invoke(chain.getThisObject());
                                HookKit.hit(module, f, "更新页关闭");
                            } catch (Throwable ignored) {
                                // 关闭失败不影响页面本身。
                            }
                            return result;
                        });
            }

            Class<?> service = HookKit.findClassOptional(module, f, cl, UPGRADE_SERVICE);
            if (service != null) {
                Method redDot = HookKit.findMethod(module, f, service, "canShowRedDot");
                HookKit.install(module, f, "UpgradeService#canShowRedDot", redDot,
                        HookKit.constBool(module, f, "更新红点关闭", false));
            }

            HookState.note(f, "更新：Dialog + 红点");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}

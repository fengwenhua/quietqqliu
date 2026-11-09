package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * PV-01 剪贴板广告口令（QQ浏览器 20.7.5.5035）。
 *
 * <p>QQ浏览器会读取剪贴板里的口令串并弹广告/活动引导，入口是
 * {@code ClipboardTokenHandler.handle(String, callback)}；{@code PasscodeClipboardConsumer}
 * 负责消费口令。默认关闭，避免影响用户主动复制的正常内容。
 */
final class ClipboardAdHooks {

    private static final String TOKEN_HANDLER =
            "com.tencent.mtt.browser.engine.clipboard.token.ClipboardTokenHandler";
    private static final String PASSCODE_CONSUMER =
            "com.tencent.mtt.browser.engine.clipboard.token.PasscodeClipboardConsumer";

    private ClipboardAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.CLIPBOARD_AD_BLOCK;
        try {
            Class<?> handler = HookKit.findClass(module, f, cl, TOKEN_HANDLER);
            if (handler != null) {
                Method handle = HookKit.findByNameAndArity(module, f, handler, "handle", 2);
                HookKit.install(module, f, "ClipboardTokenHandler#handle", handle,
                        HookKit.skipVoid(module, f, "剪贴板口令处理跳过"));
            }

            Class<?> consumer = HookKit.findClassOptional(module, f, cl, PASSCODE_CONSUMER);
            if (consumer != null) {
                Method consume = HookKit.findByNameAndArity(module, f, consumer, "consume", 0);
                HookKit.install(module, f, "PasscodeClipboardConsumer#consume", consume,
                        HookKit.skipVoid(module, f, "口令消费跳过"));
            }

            HookState.note(f, "剪贴板：ClipboardTokenHandler");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}

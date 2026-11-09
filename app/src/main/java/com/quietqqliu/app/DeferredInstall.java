package com.quietqqliu.app;

import android.app.Activity;
import android.util.Log;

import com.quietqqliu.app.hooks.Hooks;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * 延迟安装 hook。
 *
 * <p>QQ浏览器主包是标准多 Dex（34 个 classesN.dex），没有第三方壳，正常情况下
 * {@code onPackageReady} 时真实类已经可用，会立即安装。这里保留轮询兜底：
 * <ul>
 *   <li>立即尝试一次；</li>
 *   <li>起一个 100ms 轮询线程，依次尝试当前 ClassLoader、当前 Activity 的
 *       ClassLoader，最多 30 秒；</li>
 *   <li>用主包真实类做探针，探针能加载时才认为就绪；</li>
 *   <li>只安装一次，失败回滚标记，下一轮可重试。</li>
 * </ul>
 */
final class DeferredInstall {

    private static final String TAG = "QuietQQliu";

    /** 任一能加载，就说明主包类已经就绪。 */
    private static final String[] PROBES = {
            "com.tencent.mtt.MttApplication",
            "com.tencent.mtt.MainActivity",
            "com.tencent.mtt.boot.browser.splash.v2.common.SplashManager_V2",
            "com.tencent.mtt.nativead.service.FeedsNativeAdServiceImpl",
    };

    private static final AtomicBoolean sArmed = new AtomicBoolean();
    private static final AtomicBoolean sInstalled = new AtomicBoolean();

    private DeferredInstall() {
    }

    static void start(XposedModule module, ClassLoader baseCl) {
        if (!sArmed.compareAndSet(false, true)) {
            return;
        }
        module.log(Log.INFO, TAG, "deferred install armed, baseLoader=" + baseCl);
        startPolling(module, baseCl);
        tryInstall(module, baseCl, "package-ready");
    }

    private static void startPolling(XposedModule module, ClassLoader baseCl) {
        Thread thread = new Thread(() -> {
            for (int i = 0; i < 300 && !sInstalled.get(); i++) {
                tryInstall(module, baseCl, "poll-base");
                if (!sInstalled.get()) {
                    tryInstall(module, activityLoader(), "poll-activity");
                }
                if (!sInstalled.get()) {
                    tryInstall(module, Thread.currentThread().getContextClassLoader(),
                            "poll-tccl");
                }
                if (sInstalled.get()) {
                    return;
                }
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!sInstalled.get()) {
                module.log(Log.WARN, TAG, "deferred install timeout: target classes never visible");
            }
        }, "QuietQQliu-deferred");
        thread.setDaemon(true);
        thread.start();
    }

    private static void tryInstall(XposedModule module, ClassLoader cl, String source) {
        if (cl == null || sInstalled.get()) {
            return;
        }
        Class<?> probe = probe(cl);
        if (probe == null) {
            return;
        }
        ClassLoader installCl = probe.getClassLoader() != null ? probe.getClassLoader() : cl;
        if (!sInstalled.compareAndSet(false, true)) {
            return;
        }
        module.log(Log.INFO, TAG, "target visible via " + probe.getName()
                + " (" + source + "), installing hooks with " + installCl);
        try {
            Hooks.installAll(module, installCl, true);
            module.log(Log.INFO, TAG, "hook installation finished");
        } catch (Throwable t) {
            sInstalled.set(false);
            module.log(Log.ERROR, TAG, "hook installation failed, will retry", t);
        }
    }

    private static Class<?> probe(ClassLoader cl) {
        for (String name : PROBES) {
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {
                // 继续试下一个探针
            }
        }
        return null;
    }

    /** 从 ActivityThread.mActivities 拿当前 Activity 的 ClassLoader。 */
    private static ClassLoader activityLoader() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Field field = at.getDeclaredField("mActivities");
            field.setAccessible(true);
            Object raw = field.get(thread);
            if (!(raw instanceof Map)) {
                return null;
            }
            for (Object record : ((Map<?, ?>) raw).values()) {
                if (record == null) {
                    continue;
                }
                Field af = record.getClass().getDeclaredField("activity");
                af.setAccessible(true);
                Object activity = af.get(record);
                if (activity instanceof Activity) {
                    ClassLoader cl = ((Activity) activity).getClassLoader();
                    if (cl != null) {
                        return cl;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 页面还没创建时正常失败，下一轮再试。
        }
        return null;
    }
}

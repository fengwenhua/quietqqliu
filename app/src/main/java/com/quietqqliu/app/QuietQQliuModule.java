package com.quietqqliu.app;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Single Java entry class. API 102 only supports hot reload for modules that declare
 * exactly one entry, so this must stay the only class listed in java_init.list.
 *
 * <p>Nothing may be initialised before {@link #onModuleLoaded} runs: the framework attaches
 * itself to this instance after construction, and every API call before that throws.
 */
public class QuietQQliuModule extends XposedModule {

    static final String TAG = "QuietQQliu";
    static final String TARGET_PACKAGE = "com.tencent.mtt";

    /** 每个进程只安装一次；onPackageReady 对进程内每个已加载包都会回调。 */
    private static final AtomicBoolean sInstalled = new AtomicBoolean();

    /**
     * 进程名只在 onModuleLoaded 的 ModuleLoadedParam 上提供；
     * PackageReadyParam 没有 getProcessName()，所以在这里捕获供 onPackageReady 使用。
     */
    private static volatile String sProcessName = "";

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        sProcessName = param.getProcessName();
        log(Log.INFO, TAG, "loaded process=" + param.getProcessName()
                + " api=" + getApiVersion()
                + " frameworkProps=0x" + Long.toHexString(getFrameworkProperties()));
    }

    /**
     * QQ浏览器主包是标准多 Dex，onPackageReady 时真实类已可用；仍交给
     * {@link DeferredInstall} 做一次探针确认与轮询兜底。
     */
    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        if (!sInstalled.compareAndSet(false, true)) {
            log(Log.INFO, TAG, "already scheduled, skip");
            return;
        }
        // 广告与业务类都在主进程；:service、:mini*、:patch 等子进程不装。
        boolean mainProcess = TARGET_PACKAGE.equals(sProcessName);
        log(Log.INFO, TAG, "package ready: " + param.getPackageName()
                + " first=" + param.isFirstPackage()
                + " process=" + sProcessName
                + " mainProcess=" + mainProcess);
        if (!mainProcess) {
            log(Log.INFO, TAG, "non-main process, hooks skipped");
            return;
        }
        try {
            DeferredInstall.start(this, param.getClassLoader());
        } catch (Throwable t) {
            sInstalled.set(false);
            log(Log.ERROR, TAG, "deferred install setup failed", t);
        }
    }
}

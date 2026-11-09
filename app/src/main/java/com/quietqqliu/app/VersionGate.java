package com.quietqqliu.app;

/**
 * 目标应用版本门禁。
 *
 * <p>只声明“适配版本”这一件事：未知版本不阻断 hook（fail-open），
 * 由异常横幅提示；hook 内部仍按 Feature 开关执行。
 */
public final class VersionGate {

    /** QQ浏览器 20.7.5.5035（versionCode 20750500）：静态分析基线。 */
    public static final String SUPPORTED_VERSION = "20.7.5.5035";

    /** 对应的 versionCode。 */
    public static final long SUPPORTED_VERSION_CODE = 20750500L;

    private VersionGate() {
    }

    public static boolean isSupported(String versionName) {
        return SUPPORTED_VERSION.equals(versionName);
    }
}

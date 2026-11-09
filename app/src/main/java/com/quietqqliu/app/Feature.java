package com.quietqqliu.app;

/**
 * 功能清单。key 同时是模块 App 内 remote prefs 的键，也是 QQ浏览器进程
 * getRemotePreferences("config") 的键，两处必须一致。
 *
 * <p>id 是统一功能编号（AD/UI/PV + 两位序号），只用于文档、日志前缀、
 * 诊断页与 CHANGELOG；remote key、类名不随编号变化。
 *
 * <p>blockedReason 非空表示本版本不提供实现：保留编号与条目、默认关闭、
 * 不注册 hook、不在设置页渲染。
 *
 * <p>默认值：AD/UI 默认开；PV 默认关。
 * 适配基线：QQ浏览器 20.7.5.5035（versionCode 20750500）。
 */
public enum Feature {

    /** AD-01 开屏广告：AMS / RMP 闪屏与后台预加载。 */
    SPLASH_AD_BLOCK("AD-01", "ad_splash",
            R.string.f_ad_splash_title, R.string.f_ad_splash_summary,
            true, Group.AD, null),

    /** AD-02 推荐页信息流广告：原生卡片 + 优量汇 Hippy 信息流。 */
    FEED_AD_FILTER("AD-02", "ad_feed",
            R.string.f_ad_feed_title, R.string.f_ad_feed_summary,
            true, Group.AD, null),

    /** AD-03 我的页面广告：头像挂件广告与 Banner 位。 */
    MINE_AD_FILTER("AD-03", "ad_mine",
            R.string.f_ad_mine_title, R.string.f_ad_mine_summary,
            true, Group.AD, null),

    /** AD-04 小说阅读广告：章节内插页、书页广告。 */
    NOVEL_AD_FILTER("AD-04", "ad_novel",
            R.string.f_ad_novel_title, R.string.f_ad_novel_summary,
            true, Group.AD, null),

    /** AD-05 听书广告：听书信息流 / 播放页贴片。 */
    LISTEN_AD_FILTER("AD-05", "ad_listen",
            R.string.f_ad_listen_title, R.string.f_ad_listen_summary,
            true, Group.AD, null),

    /** AD-06 激励视频与「看广告免广告」入口。 */
    REWARD_AD_BLOCK("AD-06", "ad_reward",
            R.string.f_ad_reward_title, R.string.f_ad_reward_summary,
            true, Group.AD, null),

    /** AD-07 广告预加载断源。 */
    AD_PRELOAD_BLOCK("AD-07", "ad_preload",
            R.string.f_ad_preload_title, R.string.f_ad_preload_summary,
            true, Group.AD, null),

    /** AD-08 摇一摇/扭转跳转。 */
    SHAKE_AD_BLOCK("AD-08", "ad_shake",
            R.string.f_ad_shake_title, R.string.f_ad_shake_summary,
            true, Group.AD, null),

    /** AD-09 弹窗/插屏广告。 */
    POPUP_AD_BLOCK("AD-09", "ad_popup",
            R.string.f_ad_popup_title, R.string.f_ad_popup_summary,
            true, Group.AD, null),

    /** AD-10 下载看广告解锁：不放广告，直接向 H5 回传广告完成/解锁。 */
    DOWNLOAD_AD_UNLOCK("AD-10", "ad_download_unlock",
            R.string.f_ad_download_unlock_title, R.string.f_ad_download_unlock_summary,
            true, Group.AD, null),

    /** AD-11 Hippy 广告：小说阅读/推荐/短剧等 Hippy 页面的广告取数。 */
    HIPPY_AD_BLOCK("AD-11", "ad_hippy",
            R.string.f_ad_hippy_title, R.string.f_ad_hippy_summary,
            true, Group.AD, null),

    /** AD-12 模拟悦读卡：Hippy 阅读器认为账号已开通悦读卡（99 年），广告位不再取数、下载直连。 */
    HIPPY_VIP_UNLOCK("AD-12", "ad_vip",
            R.string.f_ad_vip_title, R.string.f_ad_vip_summary,
            true, Group.AD, null),

    /** UI-01 更新弹窗。 */
    UPDATE_DIALOG_BLOCK("UI-01", "ui_update_dialog",
            R.string.f_ui_update_dialog_title, R.string.f_ui_update_dialog_summary,
            true, Group.UI, null),

    /** PV-01 剪贴板广告口令。 */
    CLIPBOARD_AD_BLOCK("PV-01", "pv_clipboard_ad",
            R.string.f_pv_clipboard_ad_title, R.string.f_pv_clipboard_ad_summary,
            false, Group.PV, null);

    public final String id;
    public final String key;
    public final int titleRes;
    public final int summaryRes;
    /** 编译期内置默认值：无 PROP_CAP_REMOTE 时使用。 */
    public final boolean defaultEnabled;
    public final Group group;
    /** 非空 = 本版本不注册任何 hook。 */
    public final String blockedReason;

    Feature(String id, String key, int titleRes, int summaryRes,
            boolean defaultEnabled, Group group, String blockedReason) {
        this.id = id;
        this.key = key;
        this.titleRes = titleRes;
        this.summaryRes = summaryRes;
        this.defaultEnabled = defaultEnabled;
        this.group = group;
        this.blockedReason = blockedReason;
    }

    public boolean blocked() {
        return blockedReason != null;
    }

    public enum Group {
        AD, UI, PV, EN, EX
    }
}

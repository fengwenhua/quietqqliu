package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;
import java.util.ArrayList;

import io.github.libxposed.api.XposedModule;

/**
 * AD-04 小说阅读广告（QQ浏览器 20.7.5.5035）。
 *
 * <p>小说广告的本地数据中枢是 {@code com.tencent.mtt.external.novel.base.engine.g}
 * （NovelAdDataManager）。阅读器按 bookId/章节/位置向它取广告数据：
 * <ul>
 *   <li>{@code p(bookId, chapter, position)}：章节内插页广告；</li>
 *   <li>{@code o(bookId)}：书籍级广告（书页/详情）；</li>
 *   <li>{@code w(show, bookId)}：内容页付费广告是否展示；</li>
 *   <li>{@code b/c/m/e/q}：书城/书架 Banner 与广告位列表；</li>
 *   <li>{@code r/t/s/u}：向服务端请求广告素材。</li>
 * </ul>
 *
 * <p>策略：所有取数方法返回 null/空集合，所有请求方法不执行。调用方本来就
 * 按「没有广告」处理这些返回值（服务端也可能下发无广告），因此不会破坏
 * 阅读、翻页、书架与离线下载等正常流程；效果等价于悦读卡的免广告状态。
 */
final class NovelAdHooks {

    private static final String AD_MANAGER =
            "com.tencent.mtt.external.novel.base.engine.g";

    private NovelAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.NOVEL_AD_FILTER;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, AD_MANAGER);
            if (cls == null) {
                return;
            }

            // 章节内插页 / 书籍级广告。
            nullValue(module, f, cls, "p", 3, "NovelAdManager#p(章节广告)");
            nullValue(module, f, cls, "o", 1, "NovelAdManager#o(书籍广告)");
            nullValue(module, f, cls, "b", 3, "NovelAdManager#b(书架 Banner)");
            nullValue(module, f, cls, "c", 2, "NovelAdManager#c(书架位)");
            nullValue(module, f, cls, "q", 0, "NovelAdManager#q(广告位)");

            Method payAd = HookKit.findByNameAndArity(module, f, cls, "w", 2);
            HookKit.install(module, f, "NovelAdManager#w(付费广告)", payAd,
                    HookKit.constBool(module, f, "内容页付费广告不展示", false));

            Method list = HookKit.findByNameAndArity(module, f, cls, "e", 0);
            HookKit.install(module, f, "NovelAdManager#e(广告位列表)", list,
                    HookKit.constValue(module, f, "广告位列表清空", new ArrayList<>()));

            // 不再向服务端请求广告素材。
            skip(module, f, cls, "r", 3, "NovelAdManager#r");
            skip(module, f, cls, "t", 3, "NovelAdManager#t");
            skip(module, f, cls, "s", 1, "NovelAdManager#s");
            skip(module, f, cls, "u", 0, "NovelAdManager#u");

            HookState.note(f, "小说：NovelAdDataManager 取数/请求全断");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void nullValue(XposedModule module, Feature f, Class<?> cls,
                                  String name, int arity, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, arity);
        HookKit.install(module, f, label, m,
                HookKit.constValue(module, f, label + " = null", null));
    }

    private static void skip(XposedModule module, Feature f, Class<?> cls,
                             String name, int arity, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, arity);
        HookKit.install(module, f, label, m,
                HookKit.skipVoid(module, f, label + " skipped"));
    }
}

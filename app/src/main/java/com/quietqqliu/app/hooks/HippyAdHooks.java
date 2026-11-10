package com.quietqqliu.app.hooks;

import com.quietqqliu.app.Feature;
import android.util.Base64;

import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/**
 * AD-11 Hippy 页面广告（QQ浏览器 20.7.5.5035）。
 *
 * <p>小说阅读页、推荐、短剧等新页面都是 Hippy（React 风格）渲染，广告由
 * {@code @tencent/ads-sdk-qb-novelreader} 等 JS SDK 通过原生模块
 * {@code QBBusinessADModule} 取数：
 * <ul>
 *   <li>{@code fetchAd / fetchAdFormatJSON}：取广告 JSON；</li>
 *   <li>{@code getAdCache / getPreFetchAd}：取缓存/预取广告；</li>
 *   <li>{@code preloadAdInArticlePage / preloadAdInInFeeds}：页面级广告预加载；</li>
 *   <li>{@code popupAdModel / startMaskAdPendant}：弹窗/挂件。</li>
 * </ul>
 *
 * <p>策略：直接 reject 这些 Promise，JS SDK 走「无广告」失败分支，不渲染广告卡；
 * 普通内容、翻页、书架逻辑不受影响。奖励广告的「完成」回调由 AD-10 处理。
 */
final class HippyAdHooks {

    private static final String AD_MODULE =
            "com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule";
    private static final String NETWORK_MODULE =
            "com.tencent.mtt.hippy.modules.nativemodules.network.NetworkModule";
    private static final String WUP_MODULE =
            "com.tencent.mtt.hippy.qb.modules.QBWupModule";
    private static final String HIPPY_MAP = "com.tencent.mtt.hippy.common.HippyMap";
    private static final String PROMISE = "com.tencent.mtt.hippy.modules.Promise";

    /**
     * Hippy 端广告取数路径。当前版本是
     * {@code kde.qq.com/getAds} / {@code kde.qq.com/getGrayAds} /
     * {@code testreport.nfa.qq.com/getAds}；按路径关键字匹配（忽略大小写），
     * 这样广告域名被换成其它 CDN 时仍然能拦住。
     */
    private static final String[] AD_PATH_KEYS = {"getads", "getgrayads", "batch_get_ads"};

    private HippyAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.HIPPY_AD_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, AD_MODULE);
            if (cls != null) {
                reject(module, f, cls, "fetchAd", 2, "QBBusinessADModule#fetchAd");
                reject(module, f, cls, "fetchAdFormatJSON", 2,
                        "QBBusinessADModule#fetchAdFormatJSON");
                reject(module, f, cls, "getAdCache", 2, "QBBusinessADModule#getAdCache");
                reject(module, f, cls, "getPreFetchAd", 2, "QBBusinessADModule#getPreFetchAd");
                reject(module, f, cls, "preloadAdInArticlePage", 3,
                        "QBBusinessADModule#preloadAdInArticlePage");
                reject(module, f, cls, "preloadAdInInFeeds", 3,
                        "QBBusinessADModule#preloadAdInInFeeds");
                reject(module, f, cls, "popupAdModel", 2, "QBBusinessADModule#popupAdModel");
                reject(module, f, cls, "startMaskAdPendant", 2,
                        "QBBusinessADModule#startMaskAdPendant");
                reject(module, f, cls, "openPureAd", 2, "QBBusinessADModule#openPureAd");
            }
            blockAdFetch(module, f, cl);
            blockBatchGetAdsWup(module, f, cl);
            HookState.note(f, "Hippy 广告：QBBusinessADModule 取数 + getAds/batch_get_ads 拦截");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    /**
     * ads-sdk-qb-novelreader 的广告数据不是走 QBBusinessADModule，而是 JS
     * {@code fetch("https://kde.qq.com/getAds")}，由 Hippy 原生模块
     * {@code network.NetworkModule.fetch(HippyMap, Promise)} 代发。
     * 这里按 URL 精准拒绝三个广告取数地址；普通网络请求照常放行。
     */
    private static void blockAdFetch(XposedModule module, Feature f, ClassLoader cl) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, NETWORK_MODULE);
        Method fetch = HookKit.findByNameAndArity(module, f, cls, "fetch", 2);
        HookKit.install(module, f, "network.NetworkModule#fetch", fetch, chain -> {
            try {
                String url = findUrl(chain.getArg(0));
                if (url != null && isAdUrl(url)) {
                    rejectPromise(chain.getArg(1), "quietqqliu: hippy ad request blocked");
                    HookKit.hit(module, f, "blocked ad fetch " + adLabel(url));
                    return null;
                }
                return chain.proceed();
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error NetworkModule#fetch", t);
                return chain.proceed();
            }
        });
    }

    private static boolean isAdUrl(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        for (String key : AD_PATH_KEYS) {
            if (lower.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static String adLabel(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        for (String key : AD_PATH_KEYS) {
            if (lower.contains(key)) {
                return key;
            }
        }
        return "?";
    }

    /**
     * 小说频道「我的」页的轮播广告走 WUP：
     * {@code trpc.nfa.proxy_svr.ProxySvrWup/BatchGetAds}，
     * JS 通过 {@code QBWupModule.pbSend} 取数，返回结构是
     * {@code {ads:[...]}}。这里在原生 WUP 桥上直接返回空 ads，
     * JS 的 {@code gl()} 会得到空数组，轮播只保留普通运营卡。
     */
    private static void blockBatchGetAdsWup(XposedModule module, Feature f, ClassLoader cl) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, WUP_MODULE);
        if (cls == null) {
            return;
        }
        try {
            Class<?> mapClass = cl.loadClass(HIPPY_MAP);
            Class<?> promiseClass = cl.loadClass(PROMISE);
            Method getString = mapClass.getMethod("getString", String.class);
            Method pushObject = mapClass.getMethod("pushObject", String.class, Object.class);
            Method resolve = promiseClass.getMethod("resolve", Object.class);
            Method pbSend = HookKit.findByNameAndArity(module, f, cls, "pbsend", 2);
            Method send = HookKit.findByNameAndArity(module, f, cls, "send", 2);
            if (pbSend != null) {
                installWupBlock(module, f, "QBWupModule#pbSend(BatchGetAds)", pbSend,
                        mapClass, getString, pushObject, resolve);
            }
            if (send != null) {
                installWupBlock(module, f, "QBWupModule#send(BatchGetAds)", send,
                        mapClass, getString, pushObject, resolve);
            }
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install batch_get_ads failed", t);
        }
    }

    private static void installWupBlock(XposedModule module, Feature f, String label,
                                        Method method, Class<?> mapClass, Method getString,
                                        Method pushObject, Method resolve) {
        HookKit.install(module, f, label, method, chain -> {
            try {
                Object req = chain.getArg(0);
                String service = asString(invoke(getString, req, "serviceName"));
                String func = asString(invoke(getString, req, "funcName"));
                if (!isBatchGetAds(service, func)) {
                    return chain.proceed();
                }
                String json = "{\"ads\":[]}";
                String encode = asString(invoke(getString, req, "encode"));
                Object body;
                if ("utf8".equalsIgnoreCase(encode)) {
                    body = json;
                } else if ("none".equalsIgnoreCase(encode)) {
                    body = json.getBytes(StandardCharsets.UTF_8);
                } else {
                    body = Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8),
                            Base64.NO_WRAP);
                }
                Object response = mapClass.getConstructor().newInstance();
                pushObject.invoke(response, "code", 0);
                pushObject.invoke(response, "funcode", 0);
                pushObject.invoke(response, "errmsg", "");
                pushObject.invoke(response, "body", body);
                resolve.invoke(chain.getArg(1), response);
                HookKit.hit(module, f, "BatchGetAds 返回空广告列表");
                return null;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error BatchGetAds", t);
                return chain.proceed();
            }
        });
    }

    private static boolean isBatchGetAds(String service, String func) {
        String all = ((service == null ? "" : service) + " "
                + (func == null ? "" : func)).toLowerCase(Locale.ROOT);
        return all.contains("batchgetads")
                || (all.contains("proxy_svr") && all.contains("getads"));
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static Object invoke(Method method, Object target, Object arg) {
        try {
            return method.invoke(target, arg);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Hippy 的 fetch 参数把 URL 放在 "url"；兜底扫描所有字符串值。 */
    private static String findUrl(Object request) {
        if (request == null) {
            return null;
        }
        try {
            Method getString = request.getClass().getMethod("getString", String.class);
            Object direct = getString.invoke(request, "url");
            if (direct instanceof String && !((String) direct).isEmpty()) {
                return (String) direct;
            }
            Method keySet = request.getClass().getMethod("keySet");
            Method get = request.getClass().getMethod("get", String.class);
            Object keys = keySet.invoke(request);
            if (keys instanceof Set) {
                for (Object key : (Set<?>) keys) {
                    if (!(key instanceof String)) {
                        continue;
                    }
                    Object value = get.invoke(request, key);
                    if (value instanceof String && ((String) value).startsWith("http")) {
                        return (String) value;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 拿不到 URL 就按普通请求放行。
        }
        return null;
    }

    private static void rejectPromise(Object promise, String message) {
        if (promise == null) {
            return;
        }
        try {
            Method reject;
            try {
                reject = promise.getClass().getMethod("reject", String.class);
            } catch (NoSuchMethodException e) {
                reject = promise.getClass().getMethod("reject", Object.class);
            }
            reject.setAccessible(true);
            reject.invoke(promise, message);
        } catch (Throwable ignored) {
            // Promise 实现异常时不影响宿主。
        }
    }

    private static void reject(XposedModule module, Feature f, Class<?> cls,
                               String name, int arity, String label) {
        Method m = HookKit.findByNameAndArity(module, f, cls, name, arity);
        HookKit.install(module, f, label, m, chain -> {
            try {
                rejectPromise(chain.getArg(1), "quietqqliu: hippy ad disabled");
                HookKit.hit(module, f, label + " rejected");
                return null;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " intercept error " + label, t);
                return chain.proceed();
            }
        });
    }
}

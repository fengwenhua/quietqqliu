package com.quietqqliu.app.hooks;

import android.util.Base64;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

/**
 * AD-12 模拟悦读卡（Hippy 小说阅读器）。
 *
 * <p>20.7.5.5035 的小说阅读器是 Hippy 页面，广告闸门完全在 JS 的
 * {@code src/pages/reader/ad/presenters/...} 里：
 * <ul>
 *   <li>{@code AdUtils.getAdsBySDK()} 第一步就是
 *       {@code if (await isAdlessAsync()) return null;}；</li>
 *   <li>{@code isAdlessAsync()} 把 {@code plugin.vip.isVipUserAsync()} 作为
 *       {@code [Vip]} 标志，任一标志为真即视为免广告；</li>
 *   <li>{@code isVipUserAsync()} → {@code VipPresenter.isVipRoughly()}，
 *       最终只看 {@code vipInfo.status === OPENED}；</li>
 *   <li>「下载」按钮 {@code clickDownloadButton} 同样先
 *       {@code isAdlessAsync(DownloadReward)}，为真则直接 {@code startDownload()}。</li>
 * </ul>
 *
 * <p>{@code vipInfo} 来自
 * {@code wupFetch("trpc.pcg_novel.privilege_center.PrivilegeCenter",
 * ".../CheckUserStatus")}，JS 侧经 {@code QBWupModule.pbSend} 调原生
 * {@code QBWupModule.pbsend(HippyMap, Promise)}。这里直接返回一份
 * 「已开通、有效期 99 年」的 JSON，让 JS 认为账号持有悦读卡：
 * 阅读页所有 SDK 广告位不再取数，下载直接开始。
 *
 * <p>只伪造本地状态，不解锁付费章节、不产生订单，也不改任何内容数据。
 *
 * <p>适配策略：不修改任何 JS bundle。Hippy bundle 由服务端热更新（当前
 * {@code novelReader/1850}），但 JS 侧仍然要通过原生
 * {@code QBWupModule} 的 {@code pbSend/send} 发 WUP；本 hook 挂在原生桥接层，
 * 用 {@code @HippyMethod(name=...)} 注解定位方法（不依赖混淆后的方法名），
 * 用「service 含 privilege + func 含 status」的宽松匹配识别悦读卡状态请求，
 * 并按请求的 {@code encode} 字段（base64/utf8/none）返回对应格式，
 * 因此 JS bundle 升级不会让 hook 失效。只有宿主 APK 把整个 WUP 桥接层
 * 换掉、或悦读卡改为非 WUP 接口时才需要重新定位。
 */
final class HippyVipHooks {

    private static final String WUP_MODULE = "com.tencent.mtt.hippy.qb.modules.QBWupModule";
    private static final String HIPPY_MAP = "com.tencent.mtt.hippy.common.HippyMap";
    private static final String PROMISE = "com.tencent.mtt.hippy.modules.Promise";
    private static final String HIPPY_METHOD_ANNO =
            "com.tencent.mtt.hippy.annotation.HippyMethod";

    /** 99 年，按秒计；悦读卡到期时间只用于展示，不影响 OPENED 判定。 */
    private static final long VIP_SECONDS = 99L * 365L * 24L * 60L * 60L;

    /** 只用于升级后诊断：记录没见过但像 VIP/阅读状态的 WUP 请求，最多 32 条。 */
    private static final Set<String> UNHANDLED = ConcurrentHashMap.newKeySet();

    private HippyVipHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.HIPPY_VIP_UNLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, WUP_MODULE);
            if (cls == null) {
                return;
            }
            Class<?> mapClass = cl.loadClass(HIPPY_MAP);
            Class<?> promiseClass = cl.loadClass(PROMISE);
            Method getString = mapClass.getMethod("getString", String.class);
            Method pushObject = mapClass.getMethod("pushObject", String.class, Object.class);
            Method resolve = promiseClass.getMethod("resolve", Object.class);

            // Hippy 有两种入口：pbSend（PB proxy/JSON over PB）和 send（普通 JSON）。
            // 用注解名定位，方法被改名也不受影响。
            Method pbSend = findHippyMethod(cls, cl, "pbSend", 2);
            Method send = findHippyMethod(cls, cl, "send", 2);
            if (pbSend == null && send == null) {
                HookKit.warn(module, f.id + " skip: QBWupModule 未找到 pbSend/send");
                HookState.markNotFound(f, "QBWupModule#pbSend/send 未找到");
                return;
            }
            if (pbSend != null) {
                installOne(module, f, "QBWupModule#pbSend", pbSend,
                        mapClass, getString, pushObject, resolve);
            }
            if (send != null) {
                installOne(module, f, "QBWupModule#send", send,
                        mapClass, getString, pushObject, resolve);
            }
            HookState.note(f, "模拟悦读卡：CheckUserStatus/权益状态返回 OPENED/99 年");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void installOne(XposedModule module, Feature f, String label,
                                   Method method, Class<?> mapClass, Method getString,
                                   Method pushObject, Method resolve) {
        HookKit.install(module, f, label, method, chain -> {
            Object req = chain.getArg(0);
            String service = asString(invoke(getString, req, "serviceName"));
            String func = asString(invoke(getString, req, "funcName"));
            if (!isVipStatusRequest(service, func)) {
                warnUnhandled(module, f, service, func);
                return chain.proceed();
            }

            Object response = null;
            try {
                long endTime = System.currentTimeMillis() / 1000L + VIP_SECONDS;
                String json = "{\"status\":1,\"endTime\":" + endTime
                        + ",\"autopayType\":0,\"jumpUrl\":\"\",\"market\":{}}";
                String encode = asString(invoke(getString, req, "encode"));
                Object body;
                if ("utf8".equalsIgnoreCase(encode)) {
                    body = json;
                } else if ("none".equalsIgnoreCase(encode)) {
                    body = json.getBytes(StandardCharsets.UTF_8);
                } else {
                    body = Base64.encodeToString(
                            json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                }

                response = mapClass.getConstructor().newInstance();
                pushObject.invoke(response, "code", 0);
                pushObject.invoke(response, "funcode", 0);
                pushObject.invoke(response, "errmsg", "");
                pushObject.invoke(response, "body", body);
            } catch (Throwable t) {
                HookKit.error(module, f.id + " build fake vip response failed", t);
            }

            if (response == null) {
                // 没有构造出结果：交回原逻辑，保证 Promise 一定有回包。
                return chain.proceed();
            }
            try {
                resolve.invoke(chain.getArg(1), response);
                HookKit.hit(module, f, "fake CheckUserStatus OPENED 99y via " + label);
            } catch (Throwable t) {
                HookKit.error(module, f.id + " resolve fake vip response failed", t);
            }
            return null;
        });
    }

    /**
     * 悦读卡状态请求的宽松识别：
     * <ul>
     *   <li>func 里直接出现 {@code CheckUserStatus}（当前版本）；</li>
     *   <li>service/func 组合里同时出现 {@code privilege} 和 {@code status}
     *       （后续版本改名，例如 GetUserStatus/QueryPrivilegeStatus）。</li>
     * </ul>
     * 其它 WUP 请求原样放行。
     */
    private static boolean isVipStatusRequest(String service, String func) {
        String all = ((service == null ? "" : service) + " "
                + (func == null ? "" : func)).toLowerCase(Locale.ROOT);
        return all.contains("checkuserstatus")
                || (all.contains("privilege") && all.contains("status"));
    }

    /** 升级诊断：像阅读/VIP 状态但没被识别的请求记一次 warn，避免静默失效。 */
    private static void warnUnhandled(XposedModule module, Feature f,
                                      String service, String func) {
        String all = ((service == null ? "" : service) + " "
                + (func == null ? "" : func)).toLowerCase(Locale.ROOT);
        boolean interesting = all.contains("status")
                && (all.contains("vip") || all.contains("privilege")
                || all.contains("novel") || all.contains("read"));
        if (!interesting || UNHANDLED.size() >= 32) {
            return;
        }
        String key = service + "/" + func;
        if (UNHANDLED.add(key)) {
            HookKit.warn(module, f.id + " unhandled status wup " + key);
        }
    }

    /** 优先按 {@code @HippyMethod(name=...)} 找，其次按混淆前的常见方法名找。 */
    private static Method findHippyMethod(Class<?> cls, ClassLoader cl,
                                          String hippyName, int arity) {
        Class<?> anno = null;
        try {
            anno = Class.forName(HIPPY_METHOD_ANNO, false, cl);
        } catch (Throwable ignored) {
            // 注解类缺失时走方法名兜底。
        }
        Method byName = null;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getParameterTypes().length != arity) {
                continue;
            }
            if (anno != null) {
                try {
                    for (java.lang.annotation.Annotation annotation : m.getAnnotations()) {
                        if (annotation.annotationType() != anno) {
                            continue;
                        }
                        Object name = anno.getMethod("name").invoke(annotation);
                        if (hippyName.equals(name)) {
                            return m;
                        }
                    }
                } catch (Throwable ignored) {
                    // 继续尝试其他方法。
                }
            }
            if (byName == null && hippyName.equalsIgnoreCase(m.getName())) {
                byName = m;
            }
        }
        return byName;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static Object invoke(Method m, Object target, Object arg) {
        try {
            return m.invoke(target, arg);
        } catch (Throwable t) {
            return null;
        }
    }
}

package com.quietqqliu.app.hooks;

import android.view.View;
import android.content.Context;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import com.quietqqliu.app.Feature;
import com.quietqqliu.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-14 小说频道「我的」页轮播位（QQ浏览器 20.7.5.5035）。
 *
 * <p>小说频道的「我的」页是 Hippy 页面，轮播卡片由 JS 的 Banner 组件渲染，
 * 不是一个带资源 id 的原生 ViewPager。卡片内是 Hippy 的横向 ScrollView +
 * 底部小圆点，尺寸固定（全宽、约 200~300px 高）。广告卡由
 * {@code BatchGetAds} 单独填充，普通运营/VIP 卡来自页面数据。
 *
 * <p>因为轮播卡片本身没有可定位的类/ID，这里在
 * {@code HippyHorizontalScrollView#onLayout} 后按“横向滚动 + 全宽 + 矮卡片 +
 * 兄弟圆点”这一组结构特征识别它，并隐藏其父卡片，从而把整个轮播（含广告、
 * 运营和 VIP 卡）及占位一起移除。其他横向滚动列表（书架、书籍列表）高度更大，
 * 不会被这个尺寸规则命中。
 */
final class MineCarouselHooks {

    private static final String HORIZONTAL_SCROLL =
            "com.tencent.mtt.hippy.views.scroll.HippyHorizontalScrollView";
    private static final String PRELOAD_HELPER =
            "com.tencent.mtt.businesscenter.hippy.a";
    private static final String HIPPY_MAP =
            "com.tencent.mtt.hippy.common.HippyMap";

    private MineCarouselHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.MINE_CAROUSEL_BLOCK;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, HORIZONTAL_SCROLL);
            if (cls == null) {
                return;
            }
            Method onLayout = HookKit.findByNameAndArity(module, f, cls, "onLayout", 5);
            HookKit.install(module, f, "HippyHorizontalScrollView#onLayout", onLayout, chain -> {
                Object result;
                try {
                    result = chain.proceed();
                } catch (Throwable t) {
                    HookKit.error(module, f.id + " proceed error", t);
                    throw t;
                }
                try {
                    Object self = chain.getThisObject();
                    if (self instanceof View) {
                        hideCarouselCard(module, f, (View) self);
                    }
                } catch (Throwable t) {
                    HookKit.error(module, f.id + " inspect error", t);
                }
                return result;
            });
            installPreloadFilter(module, cl, f);
            HookState.note(f, "我的页轮播位：预加载数据过滤 + Hippy 横向卡片兜底隐藏");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    /**
     * 预加载数据里带有整页 cards；把 BANNER 卡片直接从 JSON 中删除，
     * 让 Hippy 重新布局，下面的「我的小说」自然上移。这样不会留下空白占位。
     */
    private static void installPreloadFilter(XposedModule module, ClassLoader cl, Feature f) {
        Class<?> helper = HookKit.findClassOptional(module, f, cl, PRELOAD_HELPER);
        if (helper == null) {
            return;
        }
        Method apply = HookKit.findByNameAndArity(module, f, helper, "i", 2);
        if (apply == null) {
            return;
        }
        try {
            Class<?> mapClass = cl.loadClass(HIPPY_MAP);
            Method getString = mapClass.getMethod("getString", String.class);
            Method pushString = mapClass.getMethod("pushString", String.class, String.class);
            HookKit.install(module, f, "PreloadHelper#i(filter banner)", apply, chain -> {
                try {
                    Object map = chain.getArg(1);
                    Object raw = getString.invoke(map, "data");
                    if (raw instanceof String && ((String) raw).length() > 0) {
                        String filtered = removeBannerCard((String) raw);
                        if (filtered != null) {
                            pushString.invoke(map, "data", filtered);
                            HookKit.hit(module, f, "预加载数据移除 BANNER 轮播卡片");
                        }
                    }
                } catch (Throwable t) {
                    HookKit.error(module, f.id + " preload filter error", t);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install preload filter failed", t);
        }
    }

    /**
     * 原生 View 的 GONE/removeView 不会改变 Hippy 的 FlexNode 高度，所以会留下
     * 297px 的空白。直接调用 DomManager.W(nodeId, width, 0) 把卡片在布局树里的
     * 高度改成 0，触发 Yoga 重新计算，后面的「我的小说」就会上移。
     */
    private static void collapseDomNode(XposedModule module, Feature f, View card) {
        try {
            int id = card.getId();
            if (id <= 0) {
                return;
            }
            Context context = card.getContext();
            Object engineContext = context.getClass().getMethod("getEngineContext").invoke(context);
            if (engineContext == null) {
                return;
            }
            Object dom = engineContext.getClass().getMethod("getDomManager").invoke(engineContext);
            if (dom == null) {
                return;
            }
            Method w = dom.getClass().getMethod("W", int.class, int.class, int.class);
            w.invoke(dom, id, card.getWidth(), 0);
            HookKit.hit(module, f, "Hippy 布局高度置 0 id=" + id);
        } catch (Throwable t) {
            HookKit.error(module, f.id + " collapse dom node failed", t);
        }
    }

    private static String removeBannerCard(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray cards = root.optJSONArray("cards");
            if (cards == null) {
                return null;
            }
            JSONArray kept = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < cards.length(); i++) {
                JSONObject card = cards.optJSONObject(i);
                if (card == null) {
                    kept.put(cards.get(i));
                    continue;
                }
                String uiType = card.optString("uiType", "");
                String cardKey = card.optString("cardKey", "");
                if ("BANNER".equals(uiType) || "Banner".equals(cardKey)) {
                    removed = true;
                } else {
                    kept.put(card);
                }
            }
            if (!removed) {
                return null;
            }
            root.put("cards", kept);
            return root.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void hideCarouselCard(XposedModule module, Feature f, View scroll) {
        if (scroll.getVisibility() != View.VISIBLE || scroll.getParent() == null) {
            return;
        }
        int width = scroll.getWidth();
        int height = scroll.getHeight();
        // 「我的」页轮播：全宽、约 228px 高，父卡片约 297px 高（给圆点留空间）。
        if (width < 500 || height < 120 || height > 450) {
            return;
        }
        if (!(scroll.getParent() instanceof ViewGroup)) {
            return;
        }
        ViewGroup card = (ViewGroup) scroll.getParent();
        if (card.getWidth() < 500 || card.getHeight() > 500 || card.getChildCount() < 2) {
            return;
        }
        View first = card.getChildAt(0);
        if (first == null || first.getHeight() < 120) {
            return;
        }
        // 父卡片必须比横向滚动区更高（多出的高度是底部圆点）。
        if (card.getHeight() <= first.getHeight()) {
            return;
        }
        final View target = card;
        target.post(() -> {
            if (target.getVisibility() == View.VISIBLE) {
                target.setVisibility(View.GONE);
                target.requestLayout();
                collapseDomNode(module, f, target);
                HookKit.hit(module, f, "隐藏我的页轮播卡片 "
                        + target.getWidth() + "x" + target.getHeight());
            }
        });
    }

}

# QuietQQliu 0.2.4

面向 QQ浏览器 Android（`com.tencent.mtt`）的 LSPosed 去广告模块，适配
**20.7.5.5035（versionCode 20750500）**。

## 本版内容

- 修复小说频道「我的」页轮播广告：
  - 广告数据实际走 `trpc.nfa.proxy_svr.ProxySvrWup/BatchGetAds` /
    `https://kde.qq.com/batch_get_ads`，不是普通 `getAds`；
  - AD-11 现在按 URL 拦截 `batch_get_ads`，并在 `QBWupModule#pbSend/send`
    的 `BatchGetAds` 请求上返回空 `ads` 列表作为兜底；
  - 轮播中的普通运营/VIP 卡片仍然保留。
- 保留 0.2.3 的热启动全屏倒计时/悬浮挂件拦截与其他全部能力。
- 锚点检查器暂不变（107 条）。

## 安装

1. 安装 APK；
2. 在支持 libxposed API 102 的 LSPosed / NPatch 中启用模块；
3. 作用域勾选 **QQ浏览器（`com.tencent.mtt`）**；
4. 强制停止 QQ浏览器后重新打开。

## 说明

- 适配基线为 20.7.5.5035；其他版本可能因类名/接口漂移导致部分 hook 失效，
  失败会 fail-open，不会导致宿主崩溃。
- 服务端直接拼接进正文/H5 的广告无法保证 100% 消除。
- 本项目仅用于学习研究与个人设备，与腾讯官方无任何关联。

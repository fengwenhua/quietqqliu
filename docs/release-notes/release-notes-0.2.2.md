# QuietQQliu 0.2.2

面向 QQ浏览器 Android（`com.tencent.mtt`）的 LSPosed 去广告模块，适配
**20.7.5.5035（versionCode 20750500）**。

## 本版内容

- 应用图标更换为 QQ 蓝渐变 + 白色 Q + 绿色对勾，包含 Android 13+ 主题图标。
- AD-12 模拟悦读卡：在原生 `QBWupModule#pbSend/send` 上返回
  `PrivilegeCenter/CheckUserStatus = OPENED`（有效期 99 年）。小说阅读页不再请求
  SDK 广告，点击下载直接开始。
- AD-11 Hippy 广告：拦截 `network.NetworkModule#fetch` 中
  `getads` / `getgrayads` 路径，作为悦读卡之外的网络兜底。
- AD-01~AD-10、UI-01、PV-01 保持 0.2.x 的广告拦截能力。
- 未修改 QQ浏览器 APK 或 Hippy JS bundle，JS 热更新不会覆盖 hook。

## 安装

1. 安装 APK；
2. 在支持 libxposed API 102 的 LSPosed / NPatch 中启用模块；
3. 作用域勾选 **QQ浏览器（`com.tencent.mtt`）**；
4. 强制停止 QQ浏览器后重新打开。

## 说明与边界

- 适配基线为 20.7.5.5035；其他版本可能因类名/接口漂移导致部分 hook 失效，
  失败会 fail-open，不会导致宿主崩溃。可用仓库内 `tools/check-anchors.py`
  对目标 APK 做锚点校验。
- AD-12 只伪造本地「悦读卡已开通」状态，不解锁付费章节，也不生成订单。
- 首次使用悦读卡免广告下载时，官方可能弹一次「尊贵的悦读卡用户，您正免广告下载」
  提示，这不是广告。
- 服务端直接拼接进正文/H5 的广告无法保证 100% 消除。
- 本项目仅用于学习研究与个人设备，与腾讯官方无任何关联。

## 校验

- 单元测试：76 个用例全部通过；
- 锚点：95 条，必需锚点 0 缺失；
- 签名：APK 使用 GitHub Actions 的正式签名密钥，签名校验见发布附件。

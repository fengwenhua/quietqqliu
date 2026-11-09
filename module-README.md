# QQ浏览器静净 (QuietQQliu)

面向 QQ浏览器 Android（`com.tencent.mtt`）的 LSPosed 去广告模块，基于
[libxposed](https://github.com/libxposed) Modern API 102，纯 Java，独立 APK。

- 源码与下载主页：[fengwenhua/quietqqliu](https://github.com/fengwenhua/quietqqliu)
- 适配基线：**QQ浏览器 20.7.5.5035（versionCode 20750500）**
- 作用域：`com.tencent.mtt`
- 最低框架：libxposed API 102（LSPosed / NPatch）

## 功能

- 开屏广告、推荐信息流、我的页面广告；
- 小说阅读页插页/底部广告、听书广告；
- 激励视频 / 看广告免广告；
- 下载「解锁本次下载特权」：模拟悦读卡已开通 99 年，阅读页不取广告、点下载直接开始；
- Hippy 页面 `getAds` 网络请求拦截；
- 更新弹窗、剪贴板口令（默认关闭）。

没有修改 QQ浏览器的 APK 或 Hippy JS bundle；hook 在原生桥接层，JS 热更新不会覆盖。
只伪造本地权益状态，不解锁付费章节、不生成订单。

## 安装

1. 安装本页 Release 的 APK；
2. 在 LSPosed 中启用 **QQ浏览器静净**；
3. 作用域勾选 **QQ浏览器（`com.tencent.mtt`）**；
4. 强制停止 QQ浏览器后重新打开；
5. 模块内可逐项开关，改动后重启宿主生效。

需要 root 和支持 libxposed API 102 的 LSPosed。首次安装/更新模块后必须强制停止
QQ浏览器，模块禁用了热重载。

## 实测与边界

- 主要实测环境：KernelSU + Zygisk-LSPosed，QQ浏览器 20.7.5.5035。
- 版本升级后混淆类名/接口可能漂移；hook 失败会 fail-open，不会导致宿主崩溃。
  仓库内 `tools/check-anchors.py` 可在升级后校验 95 条挂载点。
- 服务端直接拼接的广告无法保证 100% 消除。
- 本项目仅用于学习研究与个人设备，与腾讯官方无任何关联。

## 许可证

MIT。工程骨架来自 [Quiethupu](https://github.com/yigenhuobah/quiethupu)（MIT），
感谢原作者。

# QQ浏览器静净 (QuietQQliu)

[![build](https://github.com/fengwenhua/quietqqliu/actions/workflows/build.yml/badge.svg)](https://github.com/fengwenhua/quietqqliu/actions/workflows/build.yml)
[![release](https://github.com/fengwenhua/quietqqliu/actions/workflows/release.yml/badge.svg)](https://github.com/fengwenhua/quietqqliu/actions/workflows/release.yml)
[![GitHub release](https://img.shields.io/github/v/release/fengwenhua/quietqqliu)](https://github.com/fengwenhua/quietqqliu/releases)
[![Xposed-Modules-Repo](https://img.shields.io/badge/Xposed--Modules-Repo-blue)](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.quietqqliu)

面向 QQ浏览器 Android 客户端（`com.tencent.mtt`）的 LSPosed 去广告模块。基于
[libxposed](https://github.com/libxposed) Modern API 102，纯 Java 实现，独立 APK，
工程骨架与开关/诊断/回传链路参考 [静盒 QuietBox](https://github.com/yigenhuobah/quietbox)。

- 适配基线：**QQ浏览器 20.7.5.5035（versionCode 20750500，34 个 Dex）**
- 作用域：`com.tencent.mtt`（仅主进程安装 hook）
- 最低框架：libxposed API 102
- 下载：[GitHub Releases](https://github.com/fengwenhua/quietqqliu/releases) /
  [Xposed-Modules-Repo](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.quietqqliu/releases)

## 功能

| 编号 | key | 功能 | 主要挂载点（20.7.5.5035） |
|---|---|---|---|
| AD-01 | `ad_splash` | 开屏广告 | `splash.v2.ams.a` 的 w/y/z/v/I/J、`AmsSplashAdkImpl` load/show、RMP `rmp.c/h`、`SplashPreloadManager` |
| AD-02 | `ad_feed` | 推荐页信息流广告 | `FeedsNativeAdServiceImpl#getFeedsRequestAdInfo/prepareFeedsFirstStageAdReload/onNotify*`、`YLHFeedsAdManager#loadAd/validADCount/pickYLHFeedsAD`、`NHomeAdService#isValidData` |
| AD-03 | `ad_mine` | 「我的」页面广告 | `UCHeadOperationHandler#G/K`、`BannerPageAdapter(V2)#getItemCount` |
| AD-04 | `ad_novel` | 小说阅读广告 | `novel.base.engine.g` 的 p/o/b/c/m/e/q/w/r/t/s/u |
| AD-05 | `ad_listen` | 听书广告 | `NVFAdHolder#z/L/N`、`AdVideoLogic#T1/h2/i2`、`AdBlockLogic#A1/D1` |
| AD-06 | `ad_reward` | 激励视频 / 看广告免广告 | `RewardAdController#V0/n0/b/onStartAdEvent`、Hippy `QBBusinessADModule` 的 show/preload 桥 |
| AD-07 | `ad_preload` | 广告预加载断源 | `PreFetchAdService#initAd/getAd/a` |
| AD-08 | `ad_shake` | 摇一摇/扭转跳转 | `ShakeManagerService#register/setShakeListener`、Hippy `registerShake/registerTwist` |
| AD-09 | `ad_popup` | 弹窗/插屏广告 | `H5AdManager#b`、`FeedsAdFreeDialog#p` |
| AD-10 | `ad_download_unlock` | 下载看广告解锁 | `RewardDialogServiceImpl#preloadRewardAd/showRewardDialog`、`BusinessAdServiceImp#showAmsRewardVideoAD/setAmsRewardVideoAD` |
| AD-11 | `ad_hippy` | Hippy 页面广告 | `QBBusinessADModule#fetchAd/fetchAdFormatJSON/getAdCache/getPreFetchAd/preloadAdIn*`、`network.NetworkModule#fetch`（getAds 三地址） |
| AD-12 | `ad_vip` | 模拟悦读卡（免广告/直下） | `QBWupModule#pbSend` 的 `PrivilegeCenter/CheckUserStatus` 返回 OPENED/99 年 |
| UI-01 | `ui_update_dialog` | 更新弹窗 | `UpgradeDialogActivity#launch`、`UpgradeActiveActivity#onCreate`、`UpgradeService#canShowRedDot` |
| PV-01 | `pv_clipboard_ad` | 剪贴板口令（默认关） | `ClipboardTokenHandler#handle`、`PasscodeClipboardConsumer#consume` |

所有 hook 都遵守三条约束：

1. 类/方法/行为失败只记 `skip`，绝不向外抛，绝不阻断宿主启动；
2. 不修改任何广告请求参数，只让「是否展示 / 是否取数」返回空；
3. 单个功能失败不影响其他功能。

## 设计来源：参考项目分析

编写前对 `/Users/panghoo/code/java` 下的参考项目做了对照，重点是 quietbox、quietmap、tiebaquiet：

| 项目 | 目标 | 可复用的经验 |
|---|---|---|
| **quietbox** | 小红书 | libxposed 102 单入口、Feature/Config/HookState/Report 体系、延迟安装、设置/诊断 App；开屏/信息流/搜索广告分层、类级锚点与可选锚点分离。本项目以其骨架为参考 |
| **quietmap** | 高德地图 | DexKit 动态定位混淆类；锚点表 + 契约测试；多进程诊断回传 |
| **tiebaquiet** | 百度贴吧 | 展示层卡片过滤（不强改数据模型）、按 adapter 清扫、FeatureRegistry 注册表 |

其余项目提供的模式：MapAdKiller 的多目标配置、coolapk-purifier 的宿主内设置页与
DexKit 会话、BBZQ 的 Kotlin/Modern API、PureDuPan 的 HookPlan/HostProfile、
JumpClean 的 UI 净化清单、FanqieHook 的「阅读器/听书广告 + VIP 闸门」分层、
BetterHeybox 的 AdFilterHook/DexKitResolver、ForbidAd4TieBa 的组合式 Module。

FanqieHook 的 VIP 思路对本项目影响最大：**优先挂「广告取数/展示闸门」，而不是伪造账号权益**。
小说阅读同样如此——拦 `NovelAdDataManager` 的章节广告取数，比伪造悦读卡更稳，也不依赖服务端。

## QQ浏览器广告结构（20.7.5.5035 静态分析）

- **开屏**：`boot.browser.splash.v2`（AMS 腾讯广告 + RMP 运营位）。决策器 `v2.ams.a` 的
  w/y/z/v/I/J 返回 false 后走官方「无素材」分支；`SplashPreloadManager` 负责热启动预加载。
- **推荐信息流**：`nativead` 模块负责原生广告卡（`FeedsNativeAdServiceImpl`、
  `FeedsListItemComponentManager`），`commercial` 模块负责优量汇 Hippy 信息流
  （`YLHFeedsAdManager`）。`AdCardViewTypeUtils` 用 `0x7FFFD8F0..0x7FFFEC77` 区分广告 viewType。
- **我的**：`browser.account.usercenter`，头像挂件广告 `UCHeadOperationHandler`，
  模块位 Banner `BannerPageAdapter(V2)`。
- **小说**：`external.novel.base.engine.g`（NovelAdDataManager）是章节广告/书页广告/书架 Banner
  的本地数据中枢；广告位常量见 `MTT.ENOVELADSLOT`（1~28）。阅读器按 bookId/章节取数，
  取不到即不展示。
- **听书**：基于 NVF 信息流框架（`nvf.business.ad.NVFAdHolder` / `AdVideoLogic`），
  小说内音频广告同样走 NovelAdManager。
- **激励/免广告**：`browser.video.adreward.RewardAdController` + Hippy 桥
  `QBBusinessADModule`；信息流还有 `nativead.feedsadfree.FeedsAdFreeManager`（看广告换时长）。
- **摇一摇**：`browser.ShakeManagerService` 是全进程统一的传感器入口。
- **更新**：`com.tencent.upgrade`（应用宝升级 SDK）的 Dialog/Active Activity。

## 构建

```bash
# 需要 Android SDK（compileSdk 37）与 JDK 17+
echo 'sdk.dir=/path/to/Android/sdk' > local.properties
./gradlew :app:assembleDebug        # debug 包
./gradlew :app:testDebugUnitTest    # 纯 JVM 单测
```

release 需要 `keystore.properties`（`storeFile/storePassword/keyAlias/keyPassword`），
缺失时 release 任务会直接失败，不会静默回退 debug 签名。正式发布由 GitHub Actions
使用仓库 Secrets 中的 keystore 完成，见 [`RELEASING.md`](./RELEASING.md)。

## 升级校验 / 文档

QQ浏览器升级后，先跑锚点校验（类名 + 方法名 + 参数个数，95 条）：

```bash
python3 tools/check-anchors.py /path/to/base.apk
```

输出 `MISS` 的条目就是需要重新定位的挂载点。完整流程见仓库外文档：

- `qqliu-android/01-从零开始：QQ浏览器去广告模块完整教程.md`
- `qqliu-android/02-QuietQQliu 项目深度分析.md`
- `qqliu-android/03-QQ浏览器更新后的 Hook 排查与修复手册.md`

## 使用

1. 安装 APK，在支持 libxposed API 102 的框架（LSPosed / NPatch 等）中启用；
2. 作用域勾选「QQ浏览器」；
3. 强制停止并重新打开 QQ浏览器；
4. 模块内可逐项开关；改动后需重启宿主生效。

## 下载看广告解锁（AD-10）

小说 H5 阅读页点「看广告下载」时的实际链路（20.7.5.5035）：

1. 页面通过 `qb_bridge` 调 `OpenJsapiTbsAd`；
2. 通用奖励弹窗 → `RewardDialogServiceImpl.showRewardDialog/preloadRewardAd`，
   回调是 Kotlin `Function1<Map<String,String>>`；
   或 AMS 激励视频 → `BusinessAdServiceImp.showAmsRewardVideoAD`，
   回调是 `IBusinessADService.a`；
3. H5 收到 `event=RewardFinished`（或 AMS `code=0`）后继续调用
   `NovelJsExtension` 的 `offlinebook`，由 `offline.d.P()` 启动下载任务。

AD-10 不加载广告，直接按真实顺序回调「展示成功 → RewardFinished」；AMS 则构造
`code=0` 的 `BusinessAdActionInfo`。因此 H5 认为广告已完成，继续下载。
原生阅读器的下载弹窗本身没有广告：其正按钮直接调用 `offline.d.P()`。

## 小说阅读器：模拟悦读卡（AD-12）

20.7.5.5035 的小说阅读页是 Hippy 页面，广告和下载闸门都在 JS bundle
（`novelReader/1850`）里：

1. `AdUtils.getAdsBySDK()` 的第一行是 `if (await isAdlessAsync()) return null;`；
2. `isAdlessAsync()` 汇总 `isRewardAdlessFromCache()`、`plugin.vip.isVipUserAsync()`、
   新用户/章节付费/章节锁/UG/Feeds/Give/单书 VIP 等免广告来源，任一为真即免广告；
3. `isVipUserAsync()` → `isVipRoughly()`，只判断 `vipInfo.status === OPENED`；
4. 「下载」按钮同样先 `isAdlessAsync(DownloadReward)`，为真则直接 `startDownload()`；
5. `vipInfo` 来自 `trpc.pcg_novel.privilege_center.PrivilegeCenter/CheckUserStatus`
   （JSON over WUP PB proxy），JS 调原生 `QBWupModule#pbSend`。

AD-12 在原生 `QBWupModule#pbsend(HippyMap, Promise)` 上按 `serviceName/funcName`
精确命中该请求，返回 `code=0/funcode=0` 和 base64 JSON
`{"status":1,"endTime":<now+99y>,"autopayType":0,...}`。JS 解析后 `vipInfo.status`
变成 `OPENED`：阅读页不再请求任何广告，下载按钮直接开始下载，只弹一次
「尊贵的悦读卡用户，您正免广告下载」提示（非广告）。该伪造只影响本地展示状态，
不解锁付费章节，也不会生成订单。

AD-11 另外拦截 `network.NetworkModule#fetch` 中广告取数路径
（路径关键字 `getads` / `getgrayads`，忽略大小写，覆盖当前
`kde.qq.com/getAds`、`kde.qq.com/getGrayAds`、`testreport.nfa.qq.com/getAds`），
在悦读卡状态尚未加载或某个广告位绕过 `isAdlessAsync()` 时兜底。

### 没有改 JS，也不会被 JS 热更新冲掉

小说阅读器的 Hippy bundle（`files/public/hippy/bundles/novelReader/<版本>`）和
Hermes codecache 都没有被修改过。AD-12 挂在原生桥
`QBWupModule` 上：JS 无论怎么热更新，只要还需要通过 `pbSend`/`send` 拿悦读卡状态，
就会走到这个 hook。方法用 `@HippyMethod(name=...)` 注解定位（不依赖方法名），
请求按「privilege + status」宽松匹配，回包按 `encode` 字段适配 base64/utf8/none，
所以 bundle 版本号从 1850 升到 1900/2000 也不会失效。

AD-11 的 `network.NetworkModule#fetch` 是 Hippy 框架层类，同样不受 JS bundle 更新影响；
广告 URL 匹配改为路径关键字而非固定域名，CDN 换域名也能拦住。

真正会让它失效的是**宿主 APK 大版本更新**：如果腾讯把整个 Hippy WUP 桥接层换掉、
改掉 `@HippyMethod` 名称，或悦读卡状态改为非 WUP 接口。届时日志里会出现
`AD-12 unhandled status wup ...` 或 AD-12 无命中，按日志里的新 service/func
补一条匹配即可。其余针对混淆类名的 AD-01~AD-09 在 APK 更新后也可能因混淆名漂移
而 skip（诊断页会列出），这部分需要按版本重新核验锚点。

## 边界与风险

- **服务端下发的广告无法本地消除**：本模块拦的是客户端「取数/展示」；若某个广告位完全由
  服务端拼进正文（例如部分 H5 阅读页），本地只能做展示层过滤，不能保证 100%。
- **小说插件可能按需下载**：20.7.5.5035 的阅读器主体在 base dex，`qb.novelplugin` 资源
  也可能由插件提供；若某版本把广告逻辑整体搬进插件，AD-04 的锚点会全部 miss，
  诊断页会显示 skip，此时需要针对插件 Dex 重新定位。
- **下载广告解锁是「客户端回传成功」**：如果某个版本的 H5/服务端还会二次校验广告订单，
  下载可能仍被服务端拒绝；此时诊断页会显示 AD-10 命中但下载无效，需要改为伪造订单数据或放弃该路径。
- **激励视频/看广告免广告会失效**：这是去广告的预期代价；服务端发奖不会因本地拦截而白给。
- **版本漂移**：所有挂载点按 20.7.5.5035 定位，混淆类名/方法名可能随版本变化；
  失败会 fail-open，不会导致宿主崩溃，但功能会失效。
- 本模块与腾讯官方无任何关联，仅供学习、研究和个人设备使用。

## 许可证

MIT。工程骨架参考 [QuietBox](https://github.com/yigenhuobah/quietbox)（MIT）；
参考项目的思路仅用于学习，未复制其业务代码。

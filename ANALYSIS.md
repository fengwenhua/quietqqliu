# QQ浏览器 20.7.5.5035 去广告分析

本文记录 quietqqliu 的目标梳理过程：会员权益证据、广告位清单、参考项目结论与
每个 hook 的选择理由。所有类名/方法名均基于 `apk/base.apk`（versionCode 20750500，
34 个 Dex，168,835 个类）静态分析，并用脚本核验「类存在 + 方法名 + 参数个数」全部命中。

## 一、会员权益里能确认的「免广告」范围

QQ浏览器会员是分包权益模型，本地资源里能直接看到的证据：

| 权益 | 证据（res/values/strings.xml） | 对应广告位 |
|---|---|---|
| **小说悦读卡 / 小说阅读卡** | `小说悦读卡`（m5）、`小说阅读卡`（m1~m3）、`付费免广告`（b30） | 小说章节内插页、书页广告、书城/书架 Banner |
| VIP 章节折扣 | `VIP %d折`（az6/b2v）、`(VIP %d折：`（b2v） | 付费章节购买页 |
| 看广告免费读本章 | `看广告免费读本章`（b2y） | 非会员的激励广告替代路径（去广告后该路径失效） |
| 工具权益卡 | `「工具权益卡」会员尊享回收站文件保存%s天`（ade） | 文件/回收站，与广告无关 |
| 腾讯视频 VIP | `包括但不限于腾讯视频VIP`（m4） | 视频模块，独立权益 |

结论：小说侧的「悦读卡」本质是**服务端按账号下发的免广告权益**。客户端本地只能看到
`VIPAccountInfo.iValid/iEndTime` 等字段（`MTT.GetVIPAccountInfoRsp`），阅读器并不在
本地做 `isVip` 判断，而是直接向 `NovelAdDataManager` 取广告数据。因此更稳的做法不是伪造
会员，而是**让广告取数返回空**——效果等价于悦读卡的免广告状态，且不依赖服务端。

## 二、广告位与挂载点

### AD-01 开屏

- AMS（腾讯广告）闪屏：`boot.browser.splash.v2.ams.a` 是展示决策器，`w(boolean,session)`
  / `y(boolean,boolean,session)` / `z(boolean,session)` / `v(...)` / `I(...)` / `J(...)` 是
  不同阶段闸门；`AmsSplashAdkImpl.t/E/L/c/b/F/M` 负责 load/show。
- RMP 运营闪屏：`splash.v2.rmp.c` / `h` 同样实现 `w/z`。
- 预加载：`SplashPreloadManager.onApplicationState/onColdLaunchEndEvent`。
- 选择理由：让「能否展示」返回 false，调用方走官方无素材分支直接进主页；不修改请求参数，
  不使用 `SplashManager_V2.K0` 这类语义不明的高层方法。

### AD-02 推荐信息流

- 原生：`FeedsNativeAdServiceImpl.getFeedsRequestAdInfo(int,boolean)` 声明广告位；
  `prepareFeedsFirstStageAdReload(int,List,type)` 做首屏广告重载；
  `onNotifyAdReloadResult/onNotifyCacheAdReloadResult` 接收广告回填。
- 优量汇 Hippy：`YLHFeedsAdManager.loadAd(int,int,String,int,long)`、
  `validADCount(boolean)`、`pickYLHFeedsAD(boolean)`。
- 列表插入：`FeedsListItemComponentManager.a0/U/V`。
- 选择理由：不声明广告位 + 不重载 + 不接收回填，列表里普通 UGC 卡不受影响；
  未采用 `getAdCardViewType` 返回 0 的方案，避免 viewType 错乱导致空白/崩溃。

### AD-03「我的」

- 头像挂件广告：`UCHeadOperationHandler.G()` 判定、`K()` 展示。
- 模块位 Banner：`BannerPageAdapter/V2.getItemCount()`。
- 选择理由：只关广告位本身，不动用户中心其它模块。

### AD-04 小说阅读

- `com.tencent.mtt.external.novel.base.engine.g`（NovelAdDataManager）：
  - `p(bookId,chapter,position)` 章节内插页广告；
  - `o(bookId)` 书籍级广告；
  - `w(show,bookId)` 内容页付费广告判定；
  - `b/c/m/e/q` 书架/书城 Banner 与广告位列表；
  - `r/t/s/u` 向服务端请求广告素材。
- 广告位常量：`MTT.ENOVELADSLOT`（NSF_BAR_TOAST=20、NSF_FULL_SCREEN=24、AUDIO_READ_MENU=25 等）。
- 选择理由：阅读器/听书在取不到广告数据时按「无广告」渲染，这是服务端也会出现的合法状态；
  不伪造 VIP，也不改章节内容。

### AD-05 听书

- NVF 信息流框架：`NVFAdHolder.z/L/N` 绑定广告，`AdVideoLogic.T1/h2/i2` 驱动广告视频，
  `AdBlockLogic.A1/D1` 处理倒计时/输入锁。
- 小说内音频广告同样经过 NovelAdManager，由 AD-04 一并拦截。

### AD-06 激励视频 / 看广告免广告

- `browser.video.adreward.RewardAdController`：`V0/n0/b/onStartAdEvent`。
- Hippy 桥：`QBBusinessADModule.showAmsRewardVideoAD/preloadRewardAd/showRewardDialog`。
- 信息流还有 `nativead.feedsadfree.FeedsAdFreeManager`（MMKV key `FEEDS_AD_FREE`），
  属于「看广告换 N 小时免广告」，本模块通过 AD-09 的浮层拦截 + AD-02 的广告位清空覆盖。

### AD-07 预加载 / AD-08 摇一摇 / AD-09 弹窗

- `PreFetchAdService.initAd/getAd`：广告预取断源。
- `browser.ShakeManagerService.register/setShakeListener`：全进程统一传感器入口，
  `AdShakeLayout`/Hippy `registerShake/registerTwist` 都收敛到这里。
- `H5AdManager.b(QBWebView,JSONObject,String)`：H5 广告弹窗分发；
  `FeedsAdFreeDialog.p(...)`：看广告免广告浮层。

### AD-10 下载看广告解锁

用户看到的「点下载 → 弹框 → 看广告 → 可下载」不在原生 `NovelContentPage` 的缓存弹窗里
（该弹窗正按钮 `NovelContentPage$c.onClick` 直接调用 `offline.b.g()` → `offline.d.P()`
启动浏览器下载任务，本身没有广告）。它发生在 **H5 阅读页**：

- `NovelJsExtension` 实现 `offline.d.c`，`onOfflineDialogShow` 直接调用
  `w.showDownloadDialog(bookId, type, total, offline.d)`；
- H5 页面通过 `qb_bridge` 调 `OpenJsapiTbsAd`；
- 通用奖励弹窗：`RewardDialogServiceImpl.showRewardDialog(preloadRewardAd)`，
  回调 `Function1<Map<String,String>>`；
- AMS 激励视频：`BusinessAdServiceImp.showAmsRewardVideoAD/setAmsRewardVideoAD`，
  回调 `IBusinessADService.a.onResult(BusinessAdActionInfo)`；
- 完成事件：`RewordStateEvent.RewardFinished`，map 里 `event=RewardFinished`；
  AMS 侧 `BusinessAdActionInfo.code==0` 表示成功（`OpenJsapiTbsAd.d1.b.onResult` 用 code 判断）。
- 随后 H5 调用 `offlinebook` 命令，`NovelJsExtension.handleRequest` 解析书信息并调用
  `offline.c.b(...)` 创建离线任务。

AD-10 因此挂在「奖励广告回调」层，而不是小说广告取数层：不放广告，直接回调
「展示成功 + RewardFinished」/ `code=0`，让 H5 继续下载。

### UI-01 / PV-01

- `com.tencent.upgrade.ui.UpgradeDialogActivity.launch`、`UpgradeActiveActivity.onCreate`、
  `UpgradeService.canShowRedDot`。
- `ClipboardTokenHandler.handle` / `PasscodeClipboardConsumer.consume`：剪贴板口令广告，
  默认关闭（避免影响用户主动复制内容）。

## 三、参考项目结论

| 项目 | 关键做法 | 本项目采纳点 |
|---|---|---|
| quietbox | API 102 单入口、Feature/Config/HookState/Report、延迟安装、独立设置 App；开屏/信息流/搜索广告分层、类锚点 + 可选锚点 | 整体骨架与功能分层、可选锚点 fail-open |
| quietmap | DexKit 动态定位混淆类；锚点表 + 契约测试；多进程回传 | 锚点显式化、诊断页 skip 原因 |
| tiebaquiet | 展示层卡片过滤、adapter 清扫、FeatureRegistry | 信息流只清广告位、不碰数据模型 |
| FanqieHook | 阅读器/听书广告 + VIP 闸门；`isVip` 收敛点 + 服务端边界说明 | 「优先拦广告取数，不伪造会员」；明确服务端边界 |
| MapAdKiller / coolapk-purifier / BBZQ / PureDuPan / JumpClean / BetterHeybox / ForbidAd4TieBa | 多目标配置、宿主内设置、Kotlin Modern API、HookPlan、UI 净化清单、DexKit 解析、组合式模块 | 设置/诊断体验与 HookKit 约束 |

## 四、验证

1. `./gradlew :app:testDebugUnitTest`：Feature/Config/Report/锚点一致性单测通过。
2. `./gradlew :app:assembleRelease`：R8 收缩后生成 1.97MB release APK，V2 签名通过。
3. 锚点核验脚本（DEX 解析）对 27 个目标类、全部方法名与参数个数逐一比对：**0 缺失**。
4. APK 内 `META-INF/xposed/{module.prop,scope.list,java_init.list}` 正确：
   `scope=com.tencent.mtt`，入口 `com.quietqqliu.app.QuietQQliuModule`。

## 六、实机日志结论（2026-11-09 22:21–22:24）

- 模块正常注入主进程，AD-01 开屏命中（`AmsSplashAdkImpl#H/L`、`AMS 决策 v/I/z`、RMP `c/h`），
  AD-02 首屏广告重载命中一次；**AD-04/05/06/09/10 均无命中**。
- 用户看到的小说阅读页不是原生 `NovelContentPage`，而是 **Hippy 页面**：
  日志出现 `HippyNativePage`、`HippyQBWebViewInternal`；
  设备上的 bundle 为 `files/hippy/codecache/novelReader/index.android.jsbundle`。
- 该 bundle 内含广告模块：
  - `@tencent/ads-sdk-qb-novelreader`
  - `src/pages/reader/ad/presenters/free-ad-controller.js`、`reward-free-ad-controller.js`、
    `free-ad-chapter-end-lock-controller.js`、`give-free-ad-controller.js`
  - `src/pages/reader/ad/entity/free-ad-time.js`
  - `src/pages/reader/ad/utils/app-downloader.js`
- 「解锁本次下载特权」是宿主资源里的原生下滑弹框
  （`r/a1/a05.xml`：`解锁本次下载特权` + `打开广告页面浏览 5 秒即可解锁`），
  由 JS/原生广告桥触发，不是 `NovelAdManager`。
- 因此 AD-04 的原生小说锚点对 Hippy 阅读页无效；新增 AD-11 直接拦
  `QBBusinessADModule` 的广告取数，AD-10 负责奖励广告「完成」回调。

关于「模拟悦读卡 99 年」：Hippy 侧 VIP 由 `@tencent/luckbox-hippy-vip` 处理，
JS 调 `wupFetch("trpc.pcg_novel.privilege_center.PrivilegeCenter",
"/trpc.pcg_novel.privilege_center.PrivilegeCenter/CheckUserStatus")`，
经 `@tencent/luckbox-wup-hippy` → 原生 `QBWupModule`（Hippy 方法名 `pbSend`）→
`QBWupModule.pbsend(HippyMap, Promise)`。该请求 `protoType=JSON、usePBProxy=true`，
回包 `body` 是 base64 的 JSON，JS 解码后得到 `{status,endTime,autopayType,jumpUrl,market}`。
因此可以直接在 `pbsend` 上返回伪造回包（AD-12），无需再依赖存储字段。

## 八、0.2.0：悦读卡闸门与直下

实机确认阅读器 JS 的广告/下载判定如下（`novelReader/1850`）：

- `AdUtils.getAdsBySDK()` 首行 `if (await isAdlessAsync()) return null;`，
  所有 SDK 广告位（章节插页、底部广告、书页广告）都经过这里；
- `isAdlessAsync()` 把 `plugin.vip.isVipUserAsync()` 映射为 `[Vip]` 标志，
  任一标志为真即整体免广告；
- `isVipUserAsync()` → `VipPresenter.isVipRoughly()` → `vipInfo.status === OPENED`；
- 「下载」按钮 `clickDownloadButton` 先 `isAdlessAsync(DownloadReward)`：
  为真 → `vip.showVipDownloadToast(); startDownload()`；
  为假 → `showDownloadAdGuide()` → 「解锁本次下载特权」。
  `h[DownloadReward]=[Reward, ChapterLock]` 里列出的是**被忽略**的来源
  （`!includes(n) && e[n]`），所以悦读卡（`Vip`）确实能让下载直连。

AD-12 于是在 `QBWupModule.pbsend` 上伪造 `CheckUserStatus`：`code=0`、`funcode=0`、
`body=base64({"status":1,"endTime":now+99y,"autopayType":0,...})`。
JS 收到后 `setVipInfo(status=OPENED)`，同时向 store 派发 `isVipUser=true` 并
`clearChapterAd()`，随后 `isAdlessAsync()` 恒为真，下载也直接开始。
只改本地权益状态，不解锁付费章节、不产生订单。

AD-11 另加网络层兜底：广告数据实际由 JS `fetch("https://kde.qq.com/getAds")`
经 `network.NetworkModule.fetch` 发出，按路径关键字 `getads`/`getgrayads`
（忽略大小写）拒绝，域名变化也可覆盖。

### 抗更新设计（0.2.1）

- **没有修改 JS**：设备上的 `novelReader/1850` bundle 与 Hermes codecache 均未动过，
  模块只在 native 桥接层 hook。JS bundle 由服务端热更新，更新不会删除 hook。
- **方法定位不依赖方法名**：`QBWupModule` 的 `pbSend`/`send` 通过
  `@HippyMethod(name=...)` 注解查找，同时挂两个入口；方法被混淆/改名不影响。
- **接口识别宽松**：`func` 含 `CheckUserStatus`，或 service/func 组合同时含
  `privilege` 与 `status`，即视为悦读卡状态请求，后续接口名变化仍可命中。
- **回包格式自适应**：按请求里的 `encode` 返回 base64 / utf8 / raw bytes，
  对应不同版本的 WUP JSON 包装。
- **升级诊断**：遇到像 VIP/阅读状态但未识别的 WUP 请求时，打
  `AD-12 unhandled status wup <service>/<func>` warn（最多 32 条），
  新版本换接口时可据此快速补锚点。
- **边界**：宿主 APK 若整体替换 Hippy WUP 桥接层，或悦读卡改走非 WUP 接口，
  AD-12 需要重新定位；AD-01~AD-09 的混淆类名也会随 APK 版本漂移，
  需按版本核验（失败 fail-open，不会崩溃）。

## 七、边界

- 服务端直接拼进正文/H5 的广告只能做展示层过滤，不能保证 100%。
- 小说插件若被动态下发且广告逻辑随之迁移，AD-04 会全部 skip，需要按插件 Dex 重新定位。
- 「看广告免费读本章」「看视频免广告」等主动激励路径会失效；服务端发奖不会因此白给。
- 适配基线 20.7.5.5035；版本升级后混淆名可能漂移，失败 fail-open，不会崩溃。

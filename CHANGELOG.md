# 更新日志

## 0.2.5 - 2026-11-10

- AD-14 小说频道「我的」页轮播位：整张轮播卡片移除，不再保留空白占位。
  - 预加载数据中删除 `BANNER` 卡片；
  - `HippyHorizontalScrollView#onLayout` 识别轮播卡片；
  - 通过 `Hippy.dom.b#W(nodeId, width, 0)` 把 FlexNode 高度置 0，
    触发 Yoga 重新布局，后面内容自动上移；
  - 同时隐藏原生卡片 View 作为兜底。
- 设置页新增「我的页轮播位」开关（`ad_mine_carousel`，默认开启）。
- 锚点检查器更新为 109 条。

## 0.2.4 - 2026-11-10

- 修复小说频道「我的」页轮播广告：该广告走
  `trpc.nfa.proxy_svr.ProxySvrWup/BatchGetAds` / `kde.qq.com/batch_get_ads`，
  此前 `getAds` 拦截覆盖不到。AD-11 现在同时拦截 `batch_get_ads` 网络请求，
  并在 `QBWupModule#pbSend/send` 上对 `BatchGetAds` 返回空广告列表。
  轮播里的普通运营/VIP 卡片不受影响。

## 0.2.3 - 2026-11-10

- AD-13 热启动全屏倒计时广告 / 右下角悬浮挂件：
  - 跳过 `CommonMaskService` 的初始化、Splash 结束回调、mask 请求、回包与展示；
  - 跳过 `nativead.utils.q.o` 的「关闭后缩成挂件」通知；
  - 首页挂件入口 `homepage.pendant.global.view.b#e` 只拦截
    `COUNT_DOWN_PENDANT` / 商业化倒计时，其他挂件放行；
  - 断开 `AdMaskService.requestMaskAdAndShow` / `isMaskCountDownAd` 作为信息流兜底。
- 设置页新增「热启动全屏/悬浮广告」开关（`ad_mask_pendant`，默认开启）。
- 锚点检查器更新为 107 条。

## 0.2.2 - 2026-11-09

- 更换应用图标：QQ 蓝渐变背景 + 白色 Q 环形 + 绿色对勾（静净/无广告），
  同时提供 Android 13+ 主题图标（monochrome）。
- `tools/check-anchors.py` 重写为 QQ浏览器 95 条锚点（类/方法/参数个数），
  支持直接读 APK、目录或单个 DEX，升级后一条命令校验全部挂载点。
- 文档整理：教程、项目分析、更新后排障手册放到仓库外
  `qqliu-android/` 目录（不随 APK 分发）。

## 0.2.1 - 2026-11-09

增强「通杀」与抗更新能力，不修改任何 JS bundle。

- AD-12 方法定位改为读取 Hippy 的 `@HippyMethod(name="pbSend"/"send")` 注解，
  不再依赖方法名；同时挂 `pbSend` 与 `send` 两个入口，覆盖 PB proxy / 普通 JSON
  两种 WUP 通道。
- AD-12 识别条件放宽为「func 含 `CheckUserStatus`，或 service/func 组合同时含
  `privilege` 与 `status`」，后续版本改接口名（如 QueryPrivilegeStatus）仍可命中；
  按请求的 `encode`（base64/utf8/none）返回对应 body 格式。
- AD-12 增加升级诊断：遇到像 VIP/阅读状态但未识别的 WUP 请求时打一条
  `unhandled status wup` warn（最多 32 条），方便版本更新后快速定位新接口。
- AD-11 广告拦截由固定域名改为按路径关键字 `getads`/`getgrayads`（忽略大小写）
  匹配，广告域名换成其它 CDN 也不会漏。

## 0.2.0 - 2026-11-09

针对实机反馈「小说阅读页仍有插页/底部广告，点下载仍弹『解锁本次下载特权』」，
把 Hippy 小说阅读器的免广告链路补齐。

- AD-12 模拟悦读卡：拦截 `QBWupModule#pbSend` 的
  `trpc.pcg_novel.privilege_center.PrivilegeCenter/CheckUserStatus`，
  返回 `status=OPENED`、有效期 99 年的悦读卡状态。阅读器 JS 的
  `isAdlessAsync()` 因此恒为真——`getAdsBySDK()` 直接返回 null，
  所有 SDK 广告位（章节插页、底部广告等）不再取数；
  `clickDownloadButton` 走官方「悦读卡免广告下载」分支，点击后直接 `startDownload()`。
  仅伪造本地权益状态，不解锁付费章节、不生成订单。
- AD-11 增强：Hippy 广告取数实际走 JS
  `fetch("https://kde.qq.com/getAds")` → 原生
  `network.NetworkModule#fetch`。新增按 URL 拒绝
  `kde.qq.com/getAds`、`kde.qq.com/getGrayAds`、
  `testreport.nfa.qq.com/getAds` 三个地址，作为悦读卡之外的兜底。
- 设置页新增「模拟悦读卡（免广告）」开关（`ad_vip`，默认开启）。

## 0.1.0 - 2026-11-09

首个版本，参考 [QuietBox](https://github.com/yigenhuobah/quietbox) 骨架，
适配 QQ浏览器 **20.7.5.5035（versionCode 20750500）**。

- AD-01 开屏广告：AMS 决策器 `splash.v2.ams.a` 的 `w/y/z/v/I/J` 返回 false，
  `AmsSplashAdkImpl` load/show 跳过，RMP `rmp.c/h` 展示闸门关闭，
  `SplashPreloadManager` 不再后台预加载。
- AD-02 信息流广告：`FeedsNativeAdServiceImpl` 不再声明广告位、不做首屏广告重载、
  不接收广告回填；`YLHFeedsAdManager` loadAd/validADCount/pickYLHFeedsAD 返回空；
  `NHomeAdService.isValidData` 恒 false。
- AD-03「我的」广告：`UCHeadOperationHandler.G/K` 关闭头像挂件广告，
  `BannerPageAdapter(V2).getItemCount` 恒 0。
- AD-04 小说广告：`NovelAdDataManager(engine.g)` 的章节/书籍/书架广告取数全部返回
  null/空集合，广告请求全部跳过（等价悦读卡免广告状态）。
- AD-05 听书广告：`NVFAdHolder` 绑定与 `AdVideoLogic` 广告逻辑跳过。
- AD-06 激励视频：`RewardAdController` 展示/解锁跳过，Hippy 激励桥 reject。
- AD-07 预加载：`PreFetchAdService.initAd/getAd` 断源。
- AD-08 摇一摇：`ShakeManagerService.register/setShakeListener` 跳过。
- AD-09 弹窗：`H5AdManager.b` 与 `FeedsAdFreeDialog.p` 跳过。
- AD-10 下载看广告解锁：H5 `showRewardDialog/preloadRewardAd` 直接回调
  `RewardFinished`；AMS `showAmsRewardVideoAD` 直接回调 `code=0`，
  小说/网页「看广告下载」不再放广告。
- AD-11 Hippy 页面广告：`QBBusinessADModule` 的 fetchAd/fetchAdFormatJSON/
  getAdCache/getPreFetchAd/preloadAdInArticlePage/preloadAdInInFeeds 全部 reject，
  覆盖 Hippy 小说阅读页的插入广告与底部广告。
- UI-01 更新弹窗：`UpgradeDialogActivity.launch` 跳过、更新页自动关闭、红点关闭。
- PV-01 剪贴板口令（默认关）：`ClipboardTokenHandler.handle` 跳过。

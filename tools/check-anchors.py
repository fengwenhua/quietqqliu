#!/usr/bin/env python3
"""校验 QuietQQliu 的 Hook 锚点在 QQ浏览器 DEX 中是否存在。

用法：
    python3 tools/check-anchors.py <base.apk>
    python3 tools/check-anchors.py <解包目录或 *.dex ...>

输入可以是：
  * 手机里拉下来的 base.apk（r0dump 导出的标准多 Dex APK 也行）；
  * 包含 classes*.dex 的目录；
  * 单个 .dex 文件。

输出：
  OK   <类#方法>  arity=参数个数  说明
  MISS <类#方法>  arity=参数个数  说明
  OPT  <类#方法>  ...             可选锚点缺失（只提示，不影响退出码）

必需锚点缺失时退出码为 1。脚本只依赖 Python 标准库，不加载、不执行目标
DEX 中的任何代码，也不会修改 APK。

适配基线：QQ浏览器 20.7.5.5035（versionCode 20750500，34 个 Dex）。
APK 升级后先跑这个脚本：哪些类/方法名/参数个数变了，日志会对得很清楚。
"""
import os
import struct
import sys
import zipfile


def uleb(b, off):
    result = shift = 0
    while True:
        x = b[off]
        off += 1
        result |= (x & 0x7f) << shift
        shift += 7
        if not x & 0x80:
            return result, off


def parse_dex(b):
    """返回 {(类名, 方法名): {参数个数, ...}}，类名用点号形式。"""
    if b[:4] != b'dex\n':
        raise ValueError('not a standard dex')

    sid_n, sid_o, tid_n, tid_o, pid_n, pid_o, _fid_n, _fid_o, mid_n, mid_o, \
        _cd_n, _cd_o = struct.unpack_from('<12I', b, 56)

    def string_at(index):
        off = struct.unpack_from('<I', b, sid_o + 4 * index)[0]
        _, start = uleb(b, off)
        end = b.index(b'\0', start)
        return b[start:end].decode('utf-8', 'replace')

    proto_arity = []
    for k in range(pid_n):
        _, _, params_off = struct.unpack_from('<III', b, pid_o + 12 * k)
        if params_off == 0:
            proto_arity.append(0)
        else:
            proto_arity.append(struct.unpack_from('<I', b, params_off)[0])

    type_names = []
    for k in range(tid_n):
        type_names.append(string_at(struct.unpack_from('<I', b, tid_o + 4 * k)[0]))

    def clean(desc):
        if desc.startswith('L') and desc.endswith(';'):
            desc = desc[1:-1]
        return desc.replace('/', '.')

    methods = {}
    for k in range(mid_n):
        cls_idx, proto_idx, name_idx = struct.unpack_from('<HHI', b, mid_o + 8 * k)
        cls = clean(type_names[cls_idx])
        name = string_at(name_idx)
        methods.setdefault((cls, name), set()).add(proto_arity[proto_idx])
    return methods


# (类名, 方法名, 参数个数, 说明, 是否必需)
ANCHORS = [
    # AD-01 开屏
    ('com.tencent.mtt.boot.browser.splash.v2.ams.a', 'z', 2, 'AD-01 AMS 决策 z', True),
    ('com.tencent.mtt.boot.browser.splash.v2.ams.a', 'y', 3, 'AD-01 AMS 决策 y', True),
    ('com.tencent.mtt.boot.browser.splash.v2.ams.a', 'w', 2, 'AD-01 AMS 决策 w', True),
    ('com.tencent.mtt.boot.browser.splash.v2.ams.a', 'v', 3, 'AD-01 AMS 决策 v', True),
    ('com.tencent.mtt.boot.browser.splash.v2.ams.a', 'I', 2, 'AD-01 AMS 决策 I', True),
    ('com.tencent.mtt.boot.browser.splash.v2.ams.a', 'J', 3, 'AD-01 AMS 决策 J', True),
    ('com.tencent.mtt.boot.browser.splash.v2.ams.a', 'f', 1, 'AD-01 AMS 决策 f', True),
    ('com.tencent.mtt.boot.browser.splash.v2.ams.a', 'i', 3, 'AD-01 AMS 素材加载', True),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 't', 3, 'AD-01 适配器 load', False),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 'E', 7, 'AD-01 适配器 show', False),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 'L', 6, 'AD-01 适配器 show', False),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 'c', 6, 'AD-01 适配器 show', False),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 'b', 2, 'AD-01 适配器 attach', False),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 'F', 4, 'AD-01 适配器 load', False),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 'M', 3, 'AD-01 适配器 show', False),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 'G', 0, 'AD-01 适配器能力', False),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 'H', 0, 'AD-01 适配器能力', False),
    ('com.tencent.mtt.boot.browser.splash.ams.AmsSplashAdkImpl', 'I', 0, 'AD-01 适配器能力', False),
    ('com.tencent.mtt.boot.browser.splash.v2.rmp.c', 'w', 2, 'AD-01 RMP 展示判定', False),
    ('com.tencent.mtt.boot.browser.splash.v2.rmp.c', 'z', 2, 'AD-01 RMP 展示判定', False),
    ('com.tencent.mtt.boot.browser.splash.v2.rmp.c', 'g', 0, 'AD-01 RMP 入口', False),
    ('com.tencent.mtt.boot.browser.splash.v2.rmp.c', 'Q', 0, 'AD-01 RMP 入口', False),
    ('com.tencent.mtt.boot.browser.splash.v2.rmp.h', 'w', 2, 'AD-01 RMP 展示判定', False),
    ('com.tencent.mtt.boot.browser.splash.v2.rmp.h', 'z', 2, 'AD-01 RMP 展示判定', False),
    ('com.tencent.mtt.boot.browser.splash.v2.rmp.h', 'g', 0, 'AD-01 RMP 入口', False),
    ('com.tencent.mtt.boot.browser.splash.v2.rmp.h', 'Q', 0, 'AD-01 RMP 入口', False),
    ('com.tencent.mtt.boot.browser.splash.v2.SplashPreloadManager',
     'onApplicationState', 1, 'AD-01 热启动预加载', False),
    ('com.tencent.mtt.boot.browser.splash.v2.SplashPreloadManager',
     'onColdLaunchEndEvent', 1, 'AD-01 冷启动预加载', False),

    # AD-02 推荐信息流
    ('com.tencent.mtt.nativead.service.FeedsNativeAdServiceImpl',
     'getFeedsRequestAdInfo', 2, 'AD-02 广告位声明', True),
    ('com.tencent.mtt.nativead.service.FeedsNativeAdServiceImpl',
     'prepareFeedsFirstStageAdReload', 3, 'AD-02 首屏广告重载', True),
    ('com.tencent.mtt.nativead.service.FeedsNativeAdServiceImpl',
     'onNotifyAdReloadResult', 4, 'AD-02 广告回填', True),
    ('com.tencent.mtt.nativead.service.FeedsNativeAdServiceImpl',
     'onNotifyCacheAdReloadResult', 4, 'AD-02 缓存广告回填', True),
    ('com.tencent.mtt.nativead.component.FeedsListItemComponentManager',
     'a0', 3, 'AD-02 广告卡插入', False),
    ('com.tencent.mtt.nativead.component.FeedsListItemComponentManager',
     'U', 4, 'AD-02 广告卡插入', False),
    ('com.tencent.mtt.nativead.component.FeedsListItemComponentManager',
     'V', 4, 'AD-02 广告卡插入', False),
    ('com.tencent.mtt.commercial.business.feeds.YLHFeedsAdManager',
     'loadAd', 5, 'AD-02 优量汇 loadAd', False),
    ('com.tencent.mtt.commercial.business.feeds.YLHFeedsAdManager',
     'validADCount', 1, 'AD-02 优量汇广告数', False),
    ('com.tencent.mtt.commercial.business.feeds.YLHFeedsAdManager',
     'pickYLHFeedsAD', 1, 'AD-02 优量汇选广告', False),
    ('com.tencent.mtt.nativead.service.NHomeAdService',
     'isValidData', 1, 'AD-02 NHome 广告数据', False),

    # AD-03 我的
    ('com.tencent.mtt.browser.account.usercenter.head.UCHeadOperationHandler',
     'G', 0, 'AD-03 头像广告判定', True),
    ('com.tencent.mtt.browser.account.usercenter.head.UCHeadOperationHandler',
     'K', 0, 'AD-03 头像广告展示', True),
    ('com.tencent.mtt.browser.account.usercenter.newview.moduleslot.view.BannerPageAdapter',
     'getItemCount', 0, 'AD-03 Banner 位', False),
    ('com.tencent.mtt.browser.account.usercenter.newview.moduleslot.view.BannerPageAdapterV2',
     'getItemCount', 0, 'AD-03 Banner 位 V2', False),

    # AD-04 小说（原生阅读器；Hippy 阅读器由 AD-11/12 负责）
    ('com.tencent.mtt.external.novel.base.engine.g', 'p', 3, 'AD-04 章节广告', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 'o', 1, 'AD-04 书籍广告', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 'b', 3, 'AD-04 书架 Banner', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 'c', 2, 'AD-04 书架位', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 'q', 0, 'AD-04 广告位', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 'w', 2, 'AD-04 付费广告判定', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 'e', 0, 'AD-04 广告位列表', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 'r', 3, 'AD-04 广告请求', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 't', 3, 'AD-04 广告请求', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 's', 1, 'AD-04 广告请求', False),
    ('com.tencent.mtt.external.novel.base.engine.g', 'u', 0, 'AD-04 广告请求', False),

    # AD-05 听书
    ('com.tencent.mtt.nvf.business.ad.NVFAdHolder', 'z', 5, 'AD-05 广告绑定', True),
    ('com.tencent.mtt.nvf.business.ad.NVFAdHolder', 'L', 1, 'AD-05 广告绑定', True),
    ('com.tencent.mtt.nvf.business.ad.NVFAdHolder', 'N', 1, 'AD-05 广告绑定', True),
    ('com.tencent.mtt.nvf.business.ad.logic.AdVideoLogic', 'T1', 1, 'AD-05 广告视频', True),
    ('com.tencent.mtt.nvf.business.ad.logic.AdVideoLogic', 'h2', 0, 'AD-05 广告视频', True),
    ('com.tencent.mtt.nvf.business.ad.logic.AdVideoLogic', 'i2', 0, 'AD-05 广告视频', True),
    ('com.tencent.mtt.nvf.business.ad.logic.AdBlockLogic', 'A1', 0, 'AD-05 倒计时/锁', True),
    ('com.tencent.mtt.nvf.business.ad.logic.AdBlockLogic', 'D1', 0, 'AD-05 倒计时/锁', True),

    # AD-06 激励视频
    ('com.tencent.mtt.browser.video.adreward.RewardAdController', 'V0', 2, 'AD-06 展示', True),
    ('com.tencent.mtt.browser.video.adreward.RewardAdController', 'n0', 1, 'AD-06 展示', True),
    ('com.tencent.mtt.browser.video.adreward.RewardAdController', 'b', 1, 'AD-06 解锁上报', True),
    ('com.tencent.mtt.browser.video.adreward.RewardAdController',
     'onStartAdEvent', 1, 'AD-06 广告事件', True),

    # AD-07 预加载
    ('com.tencent.mtt.nativead.service.PreFetchAdService', 'initAd', 3, 'AD-07 预加载', True),
    ('com.tencent.mtt.nativead.service.PreFetchAdService', 'getAd', 2, 'AD-07 取预取广告', True),
    ('com.tencent.mtt.nativead.service.PreFetchAdService', 'a', 1, 'AD-07 预加载', True),

    # AD-08 摇一摇
    ('com.tencent.mtt.browser.ShakeManagerService', 'register', 0, 'AD-08 传感器注册', True),
    ('com.tencent.mtt.browser.ShakeManagerService',
     'setShakeListener', 1, 'AD-08 摇一摇监听', True),

    # AD-09 弹窗
    ('com.tencent.mtt.business.adservice.H5AdManager', 'b', 3, 'AD-09 H5 广告弹窗', True),
    ('com.tencent.mtt.nativead.feedsadfree.FeedsAdFreeDialog', 'p', 10, 'AD-09 免广告浮层', True),

    # AD-10 下载解锁
    ('com.tencent.mtt.nativead.service.RewardDialogServiceImpl',
     'preloadRewardAd', 2, 'AD-10 奖励广告预加载', True),
    ('com.tencent.mtt.nativead.service.RewardDialogServiceImpl',
     'showRewardDialog', 2, 'AD-10 奖励广告展示', True),
    ('com.tencent.mtt.business.adservice.BusinessAdServiceImp',
     'showAmsRewardVideoAD', 2, 'AD-10 AMS 激励展示', False),
    ('com.tencent.mtt.business.adservice.BusinessAdServiceImp',
     'setAmsRewardVideoAD', 2, 'AD-10 AMS 激励预加载', False),
    ('com.tencent.mtt.business.adservice.BusinessAdServiceImp',
     'isRewardVideoADLoaded', 1, 'AD-10 AMS 就绪判定', False),

    # AD-11 Hippy 广告
    ('com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule',
     'fetchAd', 2, 'AD-11 Hippy 取广告', False),
    ('com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule',
     'fetchAdFormatJSON', 2, 'AD-11 Hippy 取广告', False),
    ('com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule',
     'getAdCache', 2, 'AD-11 Hippy 广告缓存', False),
    ('com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule',
     'getPreFetchAd', 2, 'AD-11 Hippy 预取广告', False),
    ('com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule',
     'preloadAdInArticlePage', 3, 'AD-11 Hippy 文章页预加载', False),
    ('com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule',
     'preloadAdInInFeeds', 3, 'AD-11 Hippy 信息流预加载', False),
    ('com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule',
     'popupAdModel', 2, 'AD-11 Hippy 弹窗广告', False),
    ('com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule',
     'startMaskAdPendant', 2, 'AD-11 Hippy 挂件广告', False),
    ('com.tencent.mtt.browser.appstoreguide.hippy.QBBusinessADModule',
     'openPureAd', 2, 'AD-11 Hippy 纯广告页', False),
    ('com.tencent.mtt.hippy.modules.nativemodules.network.NetworkModule',
     'fetch', 2, 'AD-11 Hippy 网络请求（getAds 兜底）', True),

    # AD-12 模拟悦读卡
    ('com.tencent.mtt.hippy.qb.modules.QBWupModule',
     'pbsend', 2, 'AD-12 Hippy WUP pbSend', True),
    ('com.tencent.mtt.hippy.qb.modules.QBWupModule',
     'send', 2, 'AD-12 Hippy WUP send', True),

    # UI-01 更新弹窗
    ('com.tencent.upgrade.ui.UpgradeDialogActivity', 'launch', 2, 'UI-01 升级对话框', True),
    ('com.tencent.upgrade.ui.UpgradeActiveActivity', 'onCreate', 1, 'UI-01 升级页', True),
    ('com.tencent.mtt.browser.update.UpgradeService', 'canShowRedDot', 1, 'UI-01 更新红点', True),

    # PV-01 剪贴板口令（默认关闭）
    ('com.tencent.mtt.browser.engine.clipboard.token.ClipboardTokenHandler',
     'handle', 2, 'PV-01 剪贴板口令', False),
    ('com.tencent.mtt.browser.engine.clipboard.token.PasscodeClipboardConsumer',
     'consume', 0, 'PV-01 口令消费', False),
]


def collect_inputs(paths):
    """返回 [(显示名, dex_bytes)]；支持 APK / 目录 / 单文件。"""
    out = []
    for path in paths:
        if os.path.isdir(path):
            for name in sorted(os.listdir(path)):
                if name.endswith(('.dex', '.raw')):
                    full = os.path.join(path, name)
                    with open(full, 'rb') as f:
                        out.append((full, f.read()))
        elif zipfile.is_zipfile(path):
            with zipfile.ZipFile(path) as z:
                for name in sorted(z.namelist()):
                    if name.startswith('classes') and name.endswith('.dex'):
                        out.append((path + '!' + name, z.read(name)))
        else:
            with open(path, 'rb') as f:
                out.append((path, f.read()))
    return out


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2

    all_methods = {}
    for label, data in collect_inputs(argv[1:]):
        try:
            for key, arities in parse_dex(data).items():
                all_methods.setdefault(key, set()).update(arities)
        except Exception as exc:  # 跳过非标准/损坏文件
            print('WARN skip %s: %s' % (label, exc))

    missing_required = 0
    for cls, name, arity, desc, required in ANCHORS:
        arities = all_methods.get((cls, name))
        if arities and arity in arities:
            tag, detail = 'OK  ', ''
        elif required:
            tag = 'MISS'
            missing_required += 1
            detail = ('存在但参数个数=%s' % sorted(arities)) if arities else '类/方法不存在'
        else:
            tag = 'OPT '
            detail = ('存在但参数个数=%s' % sorted(arities)) if arities else ''
        print('%s %-72s arity=%-2d %-28s %s'
              % (tag, cls + '#' + name, arity, desc, detail))

    print('\n%d anchors, %d required missing' % (len(ANCHORS), missing_required))
    return 1 if missing_required else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))

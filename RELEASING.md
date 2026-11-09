# 发布说明（维护者）

本仓库使用 GitHub Actions 构建、签名并发布，本地不需要保存正式 keystore。
签名密钥以仓库 Secret 形式保存；日常开发不需要读取它。

## 一、CI

| 工作流 | 触发 | 内容 |
|---|---|---|
| `.github/workflows/build.yml` | push / PR / 手动 | JDK 17 + Android SDK 37，跑 76 个 JVM 单测、`lintDebug`、`assembleDebug`，上传 debug APK |
| `.github/workflows/release.yml` | push `v*` tag / 手动指定 tag | 跑单测 + lint，读取 secrets 解码 keystore，构建并签名 release，校验签名/包名/版本，创建 GitHub Release |

## 二、签名 Secrets

仓库 Settings → Secrets and variables → Actions 中保存：

| Secret | 内容 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | 正式 keystore 的 base64（`base64 < release.jks \| tr -d '\n'`） |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | key alias（当前为 `quietqqliu`） |
| `RELEASE_KEY_PASSWORD` | key 密码 |
| `MODULE_REPO_TOKEN`（可选） | 对 `Xposed-Modules-Repo/io.github.fengwenhua.quietqqliu` 有写权限的 token；未配置时跳过模块仓自动同步 |

`release-trust.properties` 固定了正式证书的 SHA-256；`scripts/check-signing.py`
会验证 APK 的 v2/v3 签名、证书、包名、versionCode/versionName 和 debug 标记。
换签名密钥时必须同时更新 `release-trust.properties`，否则 release 会失败——
这是有意的，防止静默用错 key。

## 三、发布步骤

1. 改 `version.properties` 的 `versionName/versionCode`；
2. 新增 `docs/release-notes/release-notes-<versionName>.md`；
3. 更新 `CHANGELOG.md`；
4. 如果锚点变化，更新 `tools/check-anchors.py` 并跑：
   ```bash
   python3 tools/check-anchors.py /path/to/qq-base.apk
   ```
5. 本地验证：
   ```bash
   ./gradlew :app:testDebugUnitTest :app:lintDebug
   ./gradlew :app:assembleRelease   # 需要本地 keystore.properties；CI 用 secrets
   ```
6. 提交并推送 `main`；
7. 打 tag 并推送，自动触发 release：
   ```bash
   git tag v0.2.2
   git push origin main v0.2.2
   ```
8. 在 Actions 里确认 build/sign/publish 全部成功；
9. 下载 Release APK，核对 SHA-256 与 `release-trust.properties` 的证书；
10. 如果配置了 `MODULE_REPO_TOKEN`，工作流会自动同步到 Xposed-Modules-Repo；
    否则按 `QQ浏览器更新后的 Hook 排查与修复手册` / 模块仓流程手动发布。

## 四、模块仓

- 仓库：`Xposed-Modules-Repo/io.github.fengwenhua.quietqqliu`
- 首页：`module-README.md` 同步为模块仓 `README.md`
- 简介：`module-SUMMARY` 同步为模块仓 `SUMMARY`
- release tag：`{versionCode}-{versionName}`，例如 `4-0.2.2`
- release 标题：`{versionName}`，正文使用同一份 release notes
- 附件：`quietqqliu-{versionName}.apk`、`quietqqliu-{versionName}.apk.sha256`

模块仓的有效 release 至少包含一个 APK，tag 必须是 `VersionCode-VersionName`。
首次发布前需要在 <https://modules.lsposed.org/submission> 提交新包申请，
由 XposedBot 创建仓库并邀请维护者。

## 五、密钥安全

- `keystore.jks` / `keystore.properties` / `local.properties` 都在 `.gitignore` 中；
- 不要把 keystore、密码、token 写进源码、release notes、日志或提交历史；
- 正式签名只允许在 GitHub Actions 的 `release` environment 中执行；
- 如果密钥泄漏，立即停止发布，生成新密钥、更新 secrets 与 `release-trust.properties`，
  并在 release notes 中说明升级需要卸载重装。

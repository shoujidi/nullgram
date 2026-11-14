# Nullgram 版本与分支

| 分支 | 构建 | 版本名称 | Android 版本码 |
| --- | --- | --- | --- |
| `codex/nullgram` | 普通公开 | `13.0.11` | `1285` |
| `codex/nullgram` | 普通本地 | `13.0.11-local` | `1285` |
| `codex/nullgram-disguise` | 伪装公开 | `13.0.11-disguise` | `1285` |
| `codex/nullgram-disguise` | 伪装本地 | `13.0.11-disguise-local` | `1285` |

四个构建使用 `top.nkbe.niagram` 和同一签名，占用同一个应用安装位置。发布轮次在 `gradle.properties` 的 `APP_VERSION_NAME` 和 `NULLGRAM_VERSION_CODE` 中维护。下一轮统一升到 `13.0.12` 和 `1286`，保留各构建的后缀。

普通版包含复读、提醒和通用外观功能。伪装版同时提供伪装设置和 `TMessagesProj/src/disguise` 中的三套伪装图标。公开构建使用蓝色开屏和 `src/publicPortrait` 中的中性图标引用，不含真人头像和桃图标。本地构建从 `TMessagesProj/src/localPortrait` 加载专属头像、桃图标、静态开屏和版权声明。中性图标用于从旧图标切换后恢复桌面启动入口。

## 构建

```powershell
$env:NATIVE_TARGET = 'arm64-v8a'
.\gradlew.bat :TMessagesProj:testDebugUnitTest :TMessagesProj:assembleDebug -PnullgramEdition=standard -PnullgramDistribution=public
.\gradlew.bat :TMessagesProj:testDebugUnitTest :TMessagesProj:assembleDebug -PnullgramEdition=standard -PnullgramDistribution=local
.\gradlew.bat :TMessagesProj:testDebugUnitTest :TMessagesProj:assembleDebug -PnullgramEdition=disguise -PnullgramDistribution=public
.\gradlew.bat :TMessagesProj:testDebugUnitTest :TMessagesProj:assembleDebug -PnullgramEdition=disguise -PnullgramDistribution=local
```

Gradle 未指定 `nullgramEdition` 时使用 `gradle.properties` 中的 `NULLGRAM_EDITION`。普通分支默认值为 `standard`，伪装分支默认值为 `disguise`。两个分支均默认构建 `public`，本地构建显式指定 `-PnullgramDistribution=local` 并使用本机头像资源。

## 提交安排

先将共用源码、普通版资源和版本配置提交至普通分支，再以该提交为基础将伪装图标及默认构建参数提交至伪装分支。共用修复在普通分支维护，再同步到伪装分支。`TMessagesProj/src/disguise` 默认由 Git 忽略，伪装分支单独添加该目录。专属头像与其开屏位于 Git 忽略的 `TMessagesProj/src/localPortrait`，供两个分支的本地构建使用。

`main` 与 `codex/nullgram` 发布普通版源码，`codex/nullgram-disguise` 发布伪装版源码。`v13.0.11` 和 `v13.0.11-disguise` 分别固定两份公开 APK 的源码。Release 仅附加 APK；通过对应 Git 标签递归克隆可获得完整源码及固定版本子模块。个人素材不进入两条公开分支。

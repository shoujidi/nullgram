# Nullgram

Nullgram 是基于 [Niagram](https://github.com/HSSkyBoy/Niagram) 修改的独立 Android 客户端。
基底提交：`bc99e5e9907a2ea47d9e6ed432469b8ff553644f`；修改日期：2026-11-01。
与 Niagram、NagramX、Nagram、Nekogram 及 Telegram 官方没有隶属或背书关系。

- 应用名：Nullgram；applicationId 保留 `top.nkbe.niagram`。
- Java namespace 保留 `org.telegram.messenger`，上游内部代码包名保留以避免无关重构。
- 保留两项重复发送功能及统一停止按钮。
- 新增单账号关键词提醒与默认关闭的总开关，不修改普通 Telegram 通知规则。

源码按 [GPLv3](LICENSE) 提供；上游与第三方版权、许可证及通知保留，详见 [来源说明](UPSTREAM.md)。
APK 分发时一并提供对应完整源码及构建说明，不以功能补丁代替完整源码。

构建：JDK 21、项目 Gradle Wrapper、SDK 37.0 / Build Tools 37.0.0、NDK 27.2.12479018、CMake 3.31.6+。
初始化固定版本子模块后，按上游 CI 应用 `TMessagesProj/jni/patches/tgcalls.sh` 中的准备补丁。
在忽略的 local.properties 中设置 SDK 路径和自己的签名，运行 `gradlew :TMessagesProj:assembleDebug`。
私钥、密码及 local.properties 不放入源码分发包。

安装包名及原服务配置保留 Niagram 基底内容；显示名、独立图标和来源说明使用 Nullgram 身份。
默认图标恢复 NiagramX，保留上游全部 15 个图标，另提供 3 个独立 Nullgram 矢量图标，共 18 个选项。
Nagram 专属图标因未获授权，不加入本发行包。

Git 提交、推送及 Release 发布仅在用户明确要求后执行。测试与实机验收状态以交付报告为准。

本分支源码：https://github.com/shoujidi/nullgram/tree/main

打包版本与对应源码：https://github.com/shoujidi/nullgram/releases

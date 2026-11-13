# NiagramX 两项重复发送功能

基底：HSSkyBoy/NiagramX（现名 HSSkyBoy/Niagram），提交 c819d178d0fed124117558054cddbf64dc746cfa。
applicationId 保持 top.nkbe.niagram，名称、图标、原设置、服务和生产依赖保持基底原样。

已有消息：长按消息，使用原有复读或复读为副本入口，设置次数和秒数。
次数为 1 时沿用 Niagram 原始 doRepeatMessage，保留原复制、引用及支持的消息类型。
多次复读使用消息快照和共用任务管理器，支持常规转发/副本及单条文字/贴纸回复；付费、受保护和特殊发送模式不进入多次任务。
原来的复读确认设置仍生效。

新文字：输入文字，长按发送，在原预览菜单选择重复发送。普通单击、静音及定时发送路径不变。
取消设置不发送；首次实际提交后清除相同原草稿；已有任务时拒绝再次启动。

两种任务共用次数 1–100、间隔 0.01–60 秒的配置。默认已有消息 1 次、新文字 5 次、间隔 3 秒。
按实际提交时刻调度下一轮，不等待上一轮回执；只在全部确认后标记完成。
已提交和已确认分开统计，多条消息一轮与消息条数分开计数。
每轮独立确认超时为 120 秒，收到发送失败、限速、权限或账号变化立即停止后续调度。
UI 卡顿后不补发错过的时间点。实际到达速度由设备、网络和服务器决定。

运行中使用输入栏的停止重复按钮；只读聊天可从聊天右上菜单停止同一账号任务。
离开应用前台或退出账号会停止。已交给原发送系统的消息不能由此模块撤回。

功能模块为 src/main/java/com/nullgram/client/repeat；这个内部 Java 包名不改变应用身份。
ChatActivity 接入原菜单及停止菜单；ChatActivityEnterView 接入新文字入口及状态条。
SendMessagesHelper 仅增加本地消息 ID 和 RPC 错误观察点，不重写发送请求。
LaunchActivity 接入停止；LocaleController 按基底已有命名空间机制注册重复功能资源，保持英文回退。

构建使用基底声明的 Gradle、JDK 21、SDK/Build Tools 37、NDK 27.2.12479018、CMake 3.31.6+。
先初始化子模块，并按 .github/workflows/pr.yml 应用仓库自带 tgcalls 准备补丁。
本机 local.properties 仅设置 SDK 和本地自签证书，必须保持忽略，不提交密码或私钥。
没有修改原生签名校验或复制旧 Nagram 的签名补丁。

测试：设置 JAVA_HOME 后运行 Tools/test-repeat.ps1；Android 控件测试使用 Robolectric，明确 SDK 28/36。
测试范围与通过数量以本次交付报告为准，旧 Nagram 的通过记录不代表本版本已通过。
新基底没有旧 Canvas 包装开关及 DialogsRecentForwardHelper 类，相应旧基底专属用例不迁入。
状态机测试不能替代真实聊天、登录、草稿、复制媒体及页面生命周期的实机验收。

所有功能修改保持工作区未提交。对照固定基底审核 diff，禁止混入其他功能或设置变更。

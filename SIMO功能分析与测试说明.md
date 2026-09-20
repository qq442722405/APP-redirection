> 本版新增专项关闭测试，最新操作步骤和验证范围见 [SIMO关闭专项测试说明](SIMO关闭专项测试说明.md)。

# SIMO 语音启动：APK 分析与接入说明

## 分析结论

分析对象：用户提供的 `simo-universe-v48.0.0.202608231-release.apk`，包名 `com.jidu.media.universe`，版本 `48.0.0.202608231`，versionCode `2104722`。SHA-256：`C5FEFBB5665278F0788CBF63410516CAB89E73E10830A0B386502B0F53040AD3`。

静态代码显示，它通过极越 VISO 服务登记应用的语音口令，接收车机语音回调，再由自身执行 APP 启动。“打开原软件后就能喊话启动第三方 APP”与这个初始化和登记过程一致；它并非仅仅给其他 APP 批量授予麦克风权限。没有连接用户车机，因此这条链路在用户具体固件上的实际响应尚未验证。

确认的主链路：

1. 绑定 `com.jidu.visoservice/com.jidu.visoservice.VisoService`，Intent action 为 `android.intent.action.VISO`。
2. 使用 `com.jidu.visoservice.aidl.IServiceInterface` 登记页面和命令，例如“打开＋APP 名称/别名”。
3. 通过 `com.jidu.visoservice.aidl.IAppAsyncCallback` 接收 `viso_selfid`、`viso_hotword`、`viso_action` 等字段。
4. 根据已登记命令查找 APP，执行启动，再回报结果。

另有 `com.jidu.mars.speech` 的 VTS 热词探测代码，但全局“用户应用语音”登记链路走 VISO。本次采用已经追踪到的 VISO 链路。

## 静态证据与接口格式

以下标识来自该 APK 的混淆类，仅用于定位分析依据；不同版本可能改变名称。

| 位置 | 发现 |
| --- | --- |
| `m32` 的绑定代码和 `f` 方法 | VISO 服务组件、页面序列化格式 |
| `xq.invokeSuspend` 的 VISO 分支 | 以文件描述符提交页面，事务编号 1，附带回调 Binder |
| `bc4.c` | 拼接“打开＋应用名称”，映射到 APP 启动动作 |
| `uc4.f` | 全局页面中登记用户 APP 及别名 |
| `h32.onTransact` 的 VISO 分支 | 回调编号 1，读取 Bundle，按命令标识/口令匹配 |
| `bp1.invokeSuspend` 的 VISO 分支 | 事务编号 6 回报结果；编号 2 删除页面 |

页面文件由 Android Parcel 生成：调用方包名、页面 ID、区域 `1`、层级 `0`、命令数量；每条命令依次为存在标记、控件 ID、命令 ID、`Button`、`visoClick`、口令文字、含一个空字符串的列表。结果 Bundle 使用 `viso_result_code`，处理请求成功为 `0`，不支持为 `-100`。

接入代码使用启动器自身包名 `com.acc.acc`，页面 ID 为 `com.acc.acc.voice.apps`。没有修改启动器为原软件包名，没有打包原软件的 DEX、资源或本地库。若固件限制调用方身份或接口发生变化，仍需依据车机返回情况适配。

## 新增功能

入口：**设置 → SIMO 语音控制**。界面沿用紫色、黑色、灰色主题及 160 DPI 字号。

- 语音启动开关：默认关闭；打开并保存后，启动有通知的后台服务。
- 开机恢复语音：独立开关，默认关闭；需要语音总开关同时开启。恢复是否允许由车机后台策略决定。
- 登记范围：默认“启动器中的 APP”；也可选择“全部第三方 APP”。只登记有启动入口的应用。
- APP 口令 / 别名：每个 APP 可单独关闭，或添加一个方便喊话的别名。说“打开＋名称”或“打开＋别名”。范围更改后先保存。
- 重新登记：重新连接或重新发送口令。增删应用、更改别名和登记范围也会触发刷新。
- 状态显示：服务不存在、连接拒绝、接口不匹配、已发送多少口令、最近收到的口令及启动请求问题。

语音启动沿用主界面 APP 下方选定的窗口预设；未选择预设则走原有直接启动逻辑。所选预设已删除时要求重新选择，避免误用其他预设。本版另已接入可见 APP 窗口的语音关闭，车状态悬浮窗已按要求移除。最新窗口启动改动和验证范围见《窗口恢复修复版说明.md》；不包含车辆控制、应用商店或语音移动 APP。

语音识别由车机完成。后台服务负责登记和处理回调，不自行采集语音。

重名口令会被跳过，直到设置不重名的别名；旧命令标识不会回退匹配到其他 APP。单页最多 500 条口令。关闭服务时发送当前页面注销请求并解除绑定；异常退出或车机未处理注销的情况，需要重新打开后登记或等待车机服务清理。

## 实车测试步骤

1. 用工程现有 GitHub Actions 或 Android 构建环境生成 APK 并安装。此次交付是源码工程，不包含已打包 APK。
2. 先关闭原软件中同名的应用语音功能，避免两个程序登记同样口令，影响判断。
3. 在启动器主界面添加一个 APP，并选择它的窗口预设。
4. 打开“设置 → SIMO 语音控制”，开启语音开关，范围保持“启动器中的 APP”，点击保存。
5. 确认状态显示“已连接 VISO，已发送 N 条口令”。**这只表示提交登记请求，尚不代表车机接受或语音测试通过。**
6. 对 SIMO 说“打开＋APP 名称”。检查目标 APP 是否打开，以及窗口预设是否应用。
7. 回到 SIMO 设置查看“最近口令”。若收到口令但提示系统未打开启动器，检查已有“权限与诊断”中的悬浮窗授权以及车机后台启动限制。
8. 返回车机首页再喊一次，检查后台响应；最后关闭语音开关，确认启动器停止响应。需要开机恢复时再开启对应开关并重启测试。

Android 的后台 Activity 启动和前台服务启动分别受系统限制。普通前台服务不等于拥有任意后台弹出界面的资格。参考：[后台启动 Activity 限制](https://developer.android.com/guide/components/activities/background-starts)、[后台启动前台服务限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)。极越固件可能有额外规则。

## 验证范围

已通过：23 项语音口令逻辑测试（别名、重名、旧 ID、移除应用、去重、长度和数量上限等）；10 项原有 APP 预设关联测试；20 项布局/关闭边界/窗口坐标测试；12 份 JSON 共 277 个控件的 ID/边界检查；设计器内置配置一致性及 px 字号检查；新增页面浏览器预览和脚本错误检查；全部 Java 源码编译检查。

Java 编译检查使用 Android 35、实际 AndroidX 依赖和占位 R 类，不能替代完整 Android 构建。尚未完成完整 Gradle APK 打包、Android 原生界面运行、真实 VISO Binder 往返、后台启动、开机恢复和实车窗口行为验证。因此这是待实车验证的接入版。

## 工程位置

- `VoiceCommands.java`：稳定命令 ID、口令匹配、重名排除与数量限制。
- `SimoApps.java`：登记范围与可启动 APP 列表。
- `SimoVoiceProtocol.java`：VISO 页面、Binder 事务和回调结果格式。
- `SimoVoiceService.java`：后台服务、重连、刷新、注销和启动请求。
- `SimoVoiceSettings.java`：设置页面、APP 开关和别名编辑。
- `app/src/main/assets/layouts/SIMO语音启动.json`：可编辑页面布局。
- `tools/tests/VoiceCommandsTest.java`：独立 JVM 逻辑测试，已接入构建流程。

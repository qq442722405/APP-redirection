# SIMO / VISO 语音能力复刻说明

## 对“小宇宙伪装(1).apk”的静态分析结果

这个 APK 不是简单靠 `Intent.ACTION_VOICE_COMMAND` 实现语音打开第三方 APP。
从 `classes.dex` 可以直接看到以下关键组件/字符串：

- `kmp.buquee.simo.core.platform.SimoVoiceManager`
- `kmp.buquee.simo.core.platform.VoiceAppLauncher`
- `kmp.buquee.simo.core.platform.SimoVoiceLocalLayer`
- `kmp.buquee.simo.core.platform.SimoVoiceLocalHandle`
- `kmp.buquee.simo.core.platform.SimoVoicePageDefinition`
- `kmp.buquee.simo.core.platform.SimoVoiceCatalogEntry`
- `kmp.buquee.simo.core.platform.SimoVoiceCommandFactoryKt`
- `android.intent.action.VISO`
- `com.jidu.visoservice`
- `com.jidu.visoservice.VisoService`
- `com.jidu.visoservice.aidl.IAppAsyncCallback`
- `com.jidu.visoservice.aidl.IServiceInterface`
- `jidu-viso-page-`

还能看到 VISO 相关字段：

- `viso_action`
- `viso_appname`
- `viso_hotword`
- `viso_pageid`
- `viso_widgetid`
- `viso_result_code`
- `viso_selfid`

以及语音页面：`simo.application.voice`。

## 结论

核心能力实际上是：

`SIMO VoiceManager -> VISO 私有 Binder 服务 -> 语音命令 -> VoiceAppLauncher -> PackageManager 启动目标 APP`

所以单纯给 APP 加一个普通 Android `intent-filter` 并不能复制原能力。

## 本工程已经加入

1. `SimoVoiceBridge.java`
   - 检测 `com.jidu.visoservice.VisoService`
   - 注册 `android.intent.action.VISO` 接收器
   - 读取 `viso_*` 参数
   - 根据 APP 中文名/热词查找已安装 APP
   - 通过 `PackageManager.getLaunchIntentForPackage()` 启动目标 APP
   - 自动生成当前车机已安装 APP 的语音目录

2. `SimoVoiceReceiver.java`
   - 作为导出的 VISO Receiver 接收车机动作

3. `FloatingService.java`
   - 悬浮服务启动时自动启动 VISO Bridge
   - 服务销毁时释放 Bridge

4. `AndroidManifest.xml`
   - 增加 `android.intent.action.VISO` Receiver

## 为什么没有伪造 AIDL

APK 中明确出现了 `IServiceInterface` / `IAppAsyncCallback`，说明 VISO 是私有 Binder 接口。
Android SDK 不提供这个 AIDL，当前 APK 也没有把它以可引用的 Java/Kotlin SDK 暴露给第三方工程。
直接猜 transaction code 和 Parcel 字段容易在极越车机上造成 Binder 崩溃，因此本版本采用兼容桥，不伪造私有协议。

如果安装到极越车机后 VISO Receiver 能收到动作，那么已经可以直接实现语音打开 APP。
如果车机只允许 VISO Binder 注册而不广播动作，则下一步需要从车机运行时抓一次 VISO Binder 日志，得到 `IServiceInterface` 的注册 transaction；拿到这一段后即可把 `registerPage/publishCommands` 做成真正的 VISO 注册，而不是兼容接收模式。

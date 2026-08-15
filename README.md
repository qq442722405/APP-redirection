# APP窗口容器 - 车机原生分辨率 / 全屏框选 / DPI / 权限增强版

本版保留：
- 车机真实 Display 分辨率
- 容器顶部 80px、底部 120px 固定避让区
- 全屏手指框选创建窗口预设
- 中文预设名称
- APP 图标和名称
- APP 双击直接启动
- APP + 预设启动
- APP 长按关闭/删除
- 预设长按编辑/删除
- 权限声明与特殊权限入口

## 窗口尺寸说明

启动目标 APP 时使用 Android 公共 API `ActivityOptions.setLaunchBounds()`，并指定当前 Display。

但是 Android 普通第三方 APK 无法保证强制修改另一个 APP 的窗口。车机 WindowManager、Launcher、目标 APP 或厂商策略如果把目标 Activity 设为强制全屏，目标 APP 可以忽略 launch bounds 或随后恢复全屏。

因此本版不会伪装成“100%强制窗口”。如果百度地图等 APP 仍然全屏，需要从车机的 WindowManager/多窗口策略、系统签名权限或厂商接口进一步处理。


## 本次构建修复
- 移除旧版 `com.example.carappjump` 源码，工程统一使用 `com.example.appwindowcontainer`。
- 修复多 Display 框选层中错误调用 `Presentation.getDisplay()` 导致的 Java 编译问题，改为从窗口 DecorView 获取实际 Display。
- GitHub Actions 改为直接执行一次 `clean :app:assembleDebug`，避免重复编译造成日志混淆。

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


## 本版修改
- 删除旧版触控纠正逻辑：MainActivity 不再对 MotionEvent 做人为 X/Y 偏移；80/120 只是容器内容留白。
- 添加 APP 页面改为“全部APP / 系统APP / 用户APP”分类，并使用方形图标+名称卡片。
- 窗口预设右侧的测试按钮改为“全屏1～全屏5”，专门测试全屏 APP 的不同启动策略。
- 全屏4/5会尝试请求 Android FREEFORM 窗口模式；如果车机 Android 12 的隐藏 API 策略阻止反射，程序会自动回退到 LaunchBounds，不会崩溃。
- DPI 200 仅作为预设/测试参数保存和显示。普通第三方 APK 无法通过 ActivityOptions 公共 API 直接修改另一个 APP 的独立 DPI，因此本版不伪装成“已经修改 DPI”。
- 全屏测试无法绕过车机 WindowManager/system 权限；如果系统在 Activity 启动后重新把地图等 APP 强制全屏，普通 APK 仍然不能强制接管其窗口。

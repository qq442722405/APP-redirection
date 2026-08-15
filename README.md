# APP窗口启动器 - 车机窗口预设 / 全屏模式 / 悬浮窗口

本版保留：
- 车机真实 Display 分辨率
- 容器顶部 80px、底部 120px 固定避让区
- 手动输入窗口预设位置和尺寸
- 中文预设名称
- APP 图标和名称
- APP 双击直接启动
- APP + 预设启动
- APP 长按关闭/删除
- 预设长按编辑/删除
- 主界面右下角显示屏幕分辨率、DPI、density
- 主界面右下角设置入口：悬浮窗口、自动启动、添加自动启动项目、权限诊断
- 数字输入支持 +100、-100、+10、-10、归零
- 启动模式 1~5 + 全屏模式，当前选中模式高亮
- 全屏模式自动使用整块真实屏幕尺寸并请求目标 APP 全屏启动
- 已移除窗口预设中的 APP DPI 输入项

## 窗口尺寸说明

启动目标 APP 时使用 Android 公共 API `ActivityOptions.setLaunchBounds()`，并指定当前 Display。

但是 Android 普通第三方 APK 无法保证强制修改另一个 APP 的窗口。车机 WindowManager、Launcher、目标 APP 或厂商策略如果把目标 Activity 设为强制全屏，目标 APP 可以忽略 launch bounds 或随后恢复全屏。

因此本版不会伪装成“100%强制窗口”。如果百度地图等 APP 仍然全屏，需要从车机的 WindowManager/多窗口策略、系统签名权限或厂商接口进一步处理。


## 本次构建修复
- 移除旧版 `com.example.carappjump` 源码，工程统一使用 `com.example.appwindowcontainer`。
- 修复多 Display 框选层中错误调用 `Presentation.getDisplay()` 导致的 Java 编译问题，改为从窗口 DecorView 获取实际 Display。
- GitHub Actions 改为直接执行一次 `clean :app:assembleDebug`，避免重复编译造成日志混淆。

\n## 构建稳定性修复
- 增加 Java 8 编译兼容配置，确保 Lambda/Android API 混用时由 Android Gradle Plugin 正确脱糖。
- 自动启动接入保存的窗口预设：有预设时使用 LaunchBounds 请求位置/大小；无预设则直接启动。
- 自动启动仍遵循车机系统限制；普通 APK 没有权限强制修改其他 APK 的窗口管理策略。


## 本次修改
- 应用名称改为 `APP窗口启动器`。
- 主界面右下角增加实时屏幕信息：真实分辨率、DPI、density。
- 悬浮窗口开关、自动启动开关、添加自动启动项目统一移动到右下角齿轮“设置”。
- 窗口预设中的 `X 左上位置` 改为 `左间距`，`Y 上下位置` 改为 `上间距`。
- 每个数字输入框增加 `+100 / -100 / +10 / -10 / 归零`。
- 启动模式按钮会高亮当前选择；全屏模式选择后自动填充整块屏幕尺寸。
- 删除窗口预设中的 `APP DPI` 配置字段及传递。

> 说明：Android 普通第三方 APK 无法通过公开 API 绝对强制修改另一个 APK 的系统级状态栏/导航栏策略。全屏模式会以整屏 LaunchBounds 启动，并向目标 APP 传递全屏标记；最终是否隐藏系统栏仍取决于车机 WindowManager 和目标 APP。

# APP窗口启动器 第74版

本版在第73版基础上增加启动稳定性修复：
1. 启动时不再一次性申请 READ_MEDIA_IMAGES / VIDEO / AUDIO 等媒体权限，避免部分 Android 模拟器和定制 ROM 在 Activity 启动阶段因权限请求闪退。
2. 通知权限改为 UI 启动完成后延迟申请，并增加异常保护。
3. AccessibilityService 在 Manifest 中改为 exported=true，便于 Android 系统正常绑定无障碍服务。
4. 保留第73版的全屏运行开关、半透明启动、弹窗左右边距、APP 内位置测试、复制参数、全部 APP 分类等功能。

如果仍然闪退，请在模拟器执行：
adb logcat -c
adb logcat AndroidRuntime:E *:S

然后启动 APP，把从 `FATAL EXCEPTION` 开始的内容发回来。

# APP窗口启动器 - 触控坐标修正版

本版本以第74版工程为基础，专门先解决车机/手机上的弹出窗口触控偏移问题。

## 本版调整

1. 删除“全屏运行/全屏半透明启动”功能。
2. 删除“窗口位置测试”功能及相关页面。
3. 主界面固定从顶部 80px 以下开始，继续避让车机状态栏区域。
4. 所有 AlertDialog 弹窗统一改为普通应用窗口：窗口自身位于顶部 80px 以下，不再使用 `FLAG_LAYOUT_IN_SCREEN`、`LAYOUT_FULLSCREEN` 或整屏 DecorView padding。
5. 所有弹窗限制最大宽度为屏幕约 90%，避免超宽车机横向铺满。
6. APP Theme 改为普通不透明窗口，降低 Android 12+ 模拟器/手机启动兼容问题。
7. 保留已有悬浮窗、自动启动、多任务、预设、添加 APP、全部分类、字体/界面大小等功能。

## 构建

```bash
gradle clean :app:assembleDebug --no-daemon --stacktrace
```

APK 输出：

`app/build/outputs/apk/debug/app-debug.apk`

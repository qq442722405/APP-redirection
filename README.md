# APP窗口启动器

Android 12+ 车机适配版。

## 当前功能
- 添加 APP 时不再显示“默认用户分类”，APP 分类默认显示为“用户”。
- 悬浮窗口支持横向/竖向排列。
- 悬浮窗口“添加”菜单可分别加入 APP、返回、首页、菜单；返回/首页/菜单使用图标显示。
- 已添加 APP 在悬浮条上只显示图标，不显示 APP 名称；长按图标可移除。
- 自动启动支持多个任务，可分别选择 APP 和窗口预设，并显示当前任务列表。
- 设置中可以配置开机启动延迟，以及多个任务之间的启动间隔。
- 悬浮窗添加 APP 的选择界面使用 WindowManager，不从 Service Context 创建 AlertDialog，以降低车机 ROM 点击闪退概率。
- 已移除历史遗留 shortcuts.xml 和 `@string/reselect` 引用，避免 AAPT 资源链接失败。

## GitHub Actions
工作流：`.github/workflows/build.yml`

构建命令：
```bash
gradle clean :app:assembleDebug --no-daemon --stacktrace
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

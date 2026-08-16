# APP窗口启动器（稳定构建版）

本版本针对 Android 12 车机环境整理，重点修复 GitHub Actions 构建时的资源/包名问题，并保留：

- 添加 APP：默认选中“用户”，不再显示“默认用户分类”。
- 悬浮窗支持横向/竖向切换。
- 返回、首页、菜单作为可选项目，并使用图标显示。
- 悬浮窗 APP 只显示图标，不显示 APP 名称；长按图标可删除。
- 自动启动支持多个任务，可分别选择 APP 和窗口预设。
- 设置中可查看当前自动启动任务。
- 支持设置任务启动间隔。
- 支持设置开机后的启动延迟。
- 悬浮窗“添加 APP”使用 WindowManager 自绘选择页，避免 Service Context Dialog 导致部分车机 ROM 闪退。
- 构建前检查旧 `com.example.carappjump` 包名和旧 `shortcuts.xml`，避免历史文件混入。

## GitHub Actions

手动运行：Actions → 构建 APP窗口启动器 → Run workflow。

构建命令：

`gradle clean :app:assembleDebug --no-daemon --stacktrace --console=plain`

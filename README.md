# APP窗口启动器

本版本在上一版 AAPT 修复工程基础上继续修改：

1. 添加 APP 分类仅保留「全部 / 系统 / 用户」，默认打开「用户」。
2. 悬浮窗口支持横向/竖向切换。
3. 悬浮窗口添加 APP 时只显示 APP 图标，不显示名称；长按图标可删除。
4. 返回 / 首页 / 菜单改为「添加」后才显示，并使用图标按钮。
5. 修复悬浮窗口点击「添加 APP」在部分车机上因 Service Context Dialog token 导致的闪退：改为 Overlay 类型 Dialog。
6. 自动启动项目改为项目管理列表，可添加多个 APP，列表显示 APP 图标、名称、窗口预设，并支持删除。
7. 自动启动任务间隔可设置，默认 1 秒。
8. 设置增加「开机延迟」，默认 5 秒；开机完成后先等待设定时间，再按自动启动列表执行。
9. 保留上一版窗口预设、全屏模式、坐标调整、记事本、屏幕信息等功能。

GitHub Actions：
`.github/workflows/build.yml`

构建命令：
`gradle clean :app:assembleDebug --no-daemon --stacktrace`

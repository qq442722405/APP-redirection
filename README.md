# APP窗口启动器

本工程为 Android 12+ 车机使用的 APP 窗口启动器。

## 本版修复与功能
- 应用名称：APP窗口启动器
- 新建窗口预设：左间距、上间距、窗口宽度、窗口高度新建时默认 0；支持 +100/-100/+10/-10/归零。
- 启动模式按钮高亮；全屏模式使用整屏启动边界。
- 添加 APP 分类：全部 / 系统 / 用户，默认进入“用户”。已删除“默认用户”分类。
- 主界面右下显示真实屏幕分辨率、DPI、density。
- 记事本与设置入口位于主界面右下角。
- 悬浮窗支持横向/竖向、拖动。APP 只显示图标。
- 悬浮窗“+”可分别添加/移除返回、首页、菜单图标按钮。
- 悬浮窗 APP 选择使用 Overlay Dialog，避免部分车机点击添加 APP 闪退。
- 自动启动支持多个 APP 任务，可逐项删除；可设置任务间隔。
- 设置中支持开机延迟启动时间。
- accessibility_service_config.xml 的 description 已改为 string 资源，避免 AAPT resource linking error。

## GitHub Actions
工作流：`.github/workflows/build.yml`，手动运行即可构建 Debug APK。

# 车机应用跳转器

用途：车机桌面点击一个“伪装/入口”APK 后，第一次选择需要启动的 APP；以后正常点击入口 APK 直接启动上次选择的目标 APP。

## 已实现

- 记住上次选择
- 自动扫描可启动的已安装 APP
- 显示 APP 图标、名称、包名
- 搜索 APP 名称或包名
- 目标 APP 被卸载/不可启动时，自动回到选择界面
- 支持重新选择目标 APP
- 通过 Android Launcher Shortcut 提供“重新选择目标 APP”入口

## 修改伪装 APP 名称

修改：
`app/src/main/res/values/strings.xml`

把：
`<string name="app_name">应用跳转器</string>`

改成需要显示在车机上的名称即可。

## GitHub Actions

上传项目到 GitHub 后，进入 `Actions -> Build APK -> Run workflow`。
生成的 APK 会作为 `CarAppJump-debug` Artifact 提供下载。

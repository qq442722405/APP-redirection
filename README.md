# APP窗口容器 2032x960 最终修正版

用于 6480×960 超长车机屏幕的 APP 窗口容器。

## 本版功能
- 单区域基准 2032×960
- 默认顶部空白 80 px
- 默认底部空白 120 px
- 设置中显示当前真实 Display 分辨率和系统 Density DPI
- APP 卡片显示图标和名称，不显示包名
- APP 长按：关闭 APP / 删除快捷方式
- 自己创建窗口预设，不自动创建“区域默认”
- 预设支持名称、X、Y、宽、高、DPI
- 预设卡片尺寸较小，适合超长屏
- 启动 APP 时对系统顶部可见区域进行坐标偏移补偿
- GitHub Actions 已加入旧版 `com.example.carappjump` 源码清理步骤，避免 `R does not exist` 编译错误

## GitHub 打包
Actions → 构建 APP窗口容器 2032x960 DPI版 → Run workflow。
APK 位于 Artifacts 中。

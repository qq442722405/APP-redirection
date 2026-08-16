APP窗口启动器 第80版 - 修复编译工程

本版本基于第79版工程直接修复，不重新设计功能。

本次修复：
1. 补回 MainActivity.java 中缺失的 refresh() 方法，解决构建时多处 cannot find symbol。
2. 已添加 APP 列表改为上下滚动，APP 多时可以继续向下选择。
3. 已保留“添加 APP”中的全部/用户/系统分类、正方形图标+名称。
4. 保留窗口预设复制/粘贴。
5. 保留设置中的开机启动、开机延迟、字体大小、界面大小、弹窗左右边距及统一保存设置。
6. 保留所有弹窗不透明和左右边距控制。
7. 已检查源码中不存在旧包名 com.example.carappjump。
8. 已检查不存在错误的 LinearLayout clp/closeLp LayoutParams 声明。
9. 已删除 shortcuts.xml 残留检查逻辑。
10. 未重新加入之前取消的全屏启动、测试窗口功能。

GitHub Actions：
.github/workflows/build.yml

构建：
gradle clean :app:assembleDebug --no-daemon --stacktrace

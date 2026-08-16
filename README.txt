Acc1-Acc10 Android 12 空壳权限助手
=================================
10个独立APK，共用固定 Acc.jks 签名。

APP名称：Acc1 ... Acc10
合法包名：com.acc.acc1 ... com.acc.acc10

功能：
- 退出
- APP颜色切换
- 常用运行时权限检查/申请
- 悬浮窗设置入口
- 无障碍设置入口
- Android 12 常用权限声明
- 固定 Acc.jks 签名

重要：
Android 不允许普通APK“无条件获得所有权限”。Manifest声明只是声明；
危险权限仍需系统授权，悬浮窗/无障碍/使用情况访问等特殊权限需要系统设置。
这10个APP虽然共用签名，但它们是10个不同包名，因此不会互相继承运行时权限。

构建：
gradle clean :app1:assembleDebug :app2:assembleDebug :app3:assembleDebug :app4:assembleDebug :app5:assembleDebug :app6:assembleDebug :app7:assembleDebug :app8:assembleDebug :app9:assembleDebug :app10:assembleDebug


GitHub Actions 打包：
1. 上传整个工程到 GitHub。
2. 进入 Actions。
3. 选择“构建 Acc1-Acc10 APK”。
4. 点击 Run workflow。
5. 构建成功后，在 Artifacts 下载 Acc1-Acc10-APK.zip。
6. 解压后得到 Acc1.apk 到 Acc10.apk。

本地打包：
gradle clean :app1:assembleDebug :app2:assembleDebug :app3:assembleDebug :app4:assembleDebug :app5:assembleDebug :app6:assembleDebug :app7:assembleDebug :app8:assembleDebug :app9:assembleDebug :app10:assembleDebug --no-daemon --stacktrace

也可以执行：
./build-all.sh

输出：
output/Acc1.apk
...
output/Acc10.apk

注意：Acc.jks 必须和工程一起保存。不要删除、替换或重新生成，否则以后同包名升级时可能无法通过签名校验。

本版本修改：
1. 使用用户上传的 Acc.jks 作为工程签名文件。
2. 应用包名/ applicationId 设置为 Acc（如果工程结构只有一个 app 模块）。
3. Android 12 常用可声明权限已加入 AndroidManifest.xml。
4. 增加 GitHub Actions：.github/workflows/build.yml。
5. 构建命令：gradle clean :app:assembleDebug --no-daemon --stacktrace

重要：
- Android 普通应用不能仅靠 Manifest 自动获得“所有权限”。
- 危险权限需要运行时授权。
- SYSTEM_ALERT_WINDOW、无障碍、PACKAGE_USAGE_STATS 等特殊权限需要系统设置/系统策略。
- 使用 Acc.jks 是为了保持同一签名，从而支持同一 applicationId 的覆盖升级。
- 请妥善保存 Acc.jks；丢失后无法用同一签名继续正常升级已有安装包。

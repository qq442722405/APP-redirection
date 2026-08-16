本版本修复说明

1. 原工程使用 namespace/applicationId/package = Acc，这是非法 Android 包名，AAPT 要求至少包含一个 '.'。
2. 为保证 Android 12/Gradle 8.7 能正常构建，已改为：
   namespace = com.acc.acc
   applicationId = com.acc.acc
   Manifest package = com.acc.acc
3. APP 显示名称仍可显示为“APP窗口启动器”。
4. 继续使用 Acc.jks / Alias Acc，密码沿用原固定签名信息。
5. build.yml 继续使用 assembleDebug，并且 Debug 也明确使用 Acc.jks 签名，因此后续相同 applicationId + Acc.jks 可以覆盖升级。

重要：不能把 Android applicationId 真正设置为单独的“Acc”，否则 AAPT/Manifest 会继续报：Package name 'Acc' ... should contain at least one '.'。
如果之前车机里安装的正式 APK 的 applicationId 就是 com.acc.acc，并且使用同一个 Acc.jks，则本版本可以覆盖升级。

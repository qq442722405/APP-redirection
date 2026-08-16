本版本包名：com.acc.acc
应用名称：Acc
签名：Acc.jks（保持原工程中的 Acc 签名配置）

注意：Android 覆盖升级必须同时满足“包名相同 + 签名相同”。
因此，本版本从 com.example.appwindowcontainer 改为 com.acc.acc 后，不能直接覆盖安装原 com.example.appwindowcontainer APK；需要先安装为新应用。
后续继续使用 com.acc.acc + 同一个 Acc.jks，即可作为同一应用链继续升级。

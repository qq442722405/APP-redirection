#!/bin/bash
set -e
gradle clean \
  :app1:assembleDebug \
  :app2:assembleDebug \
  :app3:assembleDebug \
  :app4:assembleDebug \
  :app5:assembleDebug \
  :app6:assembleDebug \
  :app7:assembleDebug \
  :app8:assembleDebug \
  :app9:assembleDebug \
  :app10:assembleDebug \
  --no-daemon --stacktrace

mkdir -p output
for n in 1 2 3 4 5 6 7 8 9 10; do
  cp "app${n}/build/outputs/apk/debug/app${n}-debug.apk" "output/Acc${n}.apk"
done
echo "全部 APK 已生成到 output/"

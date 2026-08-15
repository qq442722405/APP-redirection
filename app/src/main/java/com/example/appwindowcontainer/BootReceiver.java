package com.example.appwindowcontainer;

import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences p = context.getSharedPreferences(MainActivity.PREF, Context.MODE_PRIVATE);
        if (!p.getBoolean("auto_start_enabled", false)) return;

        try {
            JSONArray arr = new JSONArray(p.getString("auto_start_items", "[]"));
            if (arr.length() == 0) return;

            // 目前自动启动项目按添加顺序执行第一个项目，避免车机开机同时拉起多个 APP。
            JSONObject item = arr.getJSONObject(0);
            String pkg = item.optString("pkg", "");
            int presetIndex = item.optInt("preset", -1);

            if (pkg.isEmpty()) return;
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) return;

            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            if (presetIndex >= 0) {
                JSONArray presets = new JSONArray(p.getString(MainActivity.PRESETS, "[]"));
                if (presetIndex < presets.length()) {
                    JSONObject pr = presets.getJSONObject(presetIndex);
                    int x = Math.max(0, pr.optInt("x", 0));
                    int y = Math.max(0, pr.optInt("y", 0));
                    int w = Math.max(1, pr.optInt("w", 2160));
                    int h = Math.max(1, pr.optInt("h", 960));
                    int mode = pr.optInt("mode", 1);

                    launch.putExtra("com.example.appwindowcontainer.target_x", x);
                    launch.putExtra("com.example.appwindowcontainer.target_y", y);
                    launch.putExtra("com.example.appwindowcontainer.target_w", w);
                    launch.putExtra("com.example.appwindowcontainer.target_h", h);
                    launch.putExtra("com.example.appwindowcontainer.target_dpi",
                            pr.optInt("dpi", 160));
                    launch.putExtra("com.example.appwindowcontainer.fullscreen", mode == 6);

                    if (Build.VERSION.SDK_INT >= 24) {
                        ActivityOptions options = ActivityOptions.makeBasic();
                        options.setLaunchBounds(new Rect(x, y, x + w, y + h));
                        if (Build.VERSION.SDK_INT >= 26) {
                            try {
                                java.lang.reflect.Method m =
                                        ActivityOptions.class.getMethod("setLaunchDisplayId", int.class);
                                int displayId = pr.optInt("displayId", -1);
                                if (displayId >= 0) m.invoke(options, displayId);
                            } catch (Exception ignored) {}
                        }
                        try {
                            context.startActivity(launch, options.toBundle());
                            return;
                        } catch (Exception ignored) {}
                    }
                }
            }

            context.startActivity(launch);
        } catch (Exception ignored) {
            // 某些车机禁止 BOOT_COMPLETED 直接拉起第三方 Activity，静默失败即可。
        }
    }
}

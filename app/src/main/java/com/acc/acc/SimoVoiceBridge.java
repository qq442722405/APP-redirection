package com.acc.acc;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 极越/SIMO 语音桥接层。
 *
 * 从“小宇宙伪装”APK静态分析得到的关键点：
 * 1. SIMO 内部存在 SimoVoiceManager / VoiceAppLauncher。
 * 2. 它不是普通 Android ACTION_VOICE_COMMAND，而是连接
 *    com.jidu.visoservice.VisoService 的 VISO Binder 服务。
 * 3. 相关动作使用 android.intent.action.VISO，能看到 viso_action、
 *    viso_appname、viso_hotword、viso_pageid、viso_widgetid 等字段。
 * 4. 这里不依赖 SIMO 私有 SDK，采用公开 Android Binder/Intent API 做兼容桥。
 *    这样工程仍然可以独立编译。
 *
 * 注意：VISO 的 AIDL 是车机私有接口，具体 transaction 定义并没有公开在
 * Android SDK 中，因此本类同时提供“VISO 广播接收 + VISO 服务可用性探测”。
 * 在实际车机上，如果 VISO 服务把识别结果广播给当前 APP，本桥即可直接处理。
 * 若车机要求私有 Binder 注册协议，则需要在车机上进一步抓一次 Binder 日志，
 * 再把 transaction 映射补进 registerPage()/publishCommands()。
 */
public final class SimoVoiceBridge {
    private static final String TAG = "SimoVoiceBridge";

    public static final String VISO_PACKAGE = "com.jidu.visoservice";
    public static final String VISO_SERVICE = "com.jidu.visoservice.VisoService";
    public static final String VISO_ACTION = "android.intent.action.VISO";

    public static final String EXTRA_ACTION = "viso_action";
    public static final String EXTRA_APP_NAME = "viso_appname";
    public static final String EXTRA_HOTWORD = "viso_hotword";
    public static final String EXTRA_PAGE_ID = "viso_pageid";
    public static final String EXTRA_WIDGET_ID = "viso_widgetid";
    public static final String EXTRA_RESULT_CODE = "viso_result_code";
    public static final String EXTRA_SELF_ID = "viso_selfid";

    private final Context context;
    private final PackageManager pm;
    private BroadcastReceiver receiver;
    private boolean registered;
    private boolean visoAvailable;
    private IBinder visoBinder;

    public SimoVoiceBridge(Context context) {
        this.context = context.getApplicationContext();
        this.pm = this.context.getPackageManager();
    }

    public synchronized void start() {
        if (registered) return;
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                handleVISOIntent(intent);
            }
        };
        IntentFilter filter = new IntentFilter(VISO_ACTION);
        try {
            context.registerReceiver(receiver, filter);
            registered = true;
        } catch (Exception e) {
            Log.w(TAG, "register VISO receiver failed", e);
        }
        probeVISOService();
    }

    public synchronized void stop() {
        if (registered && receiver != null) {
            try { context.unregisterReceiver(receiver); } catch (Exception ignored) {}
        }
        receiver = null;
        registered = false;
        visoBinder = null;
    }

    public boolean isVisoAvailable() { return visoAvailable; }

    /** 探测车机是否存在私有 VISO 服务。 */
    private void probeVISOService() {
        try {
            Intent i = new Intent(VISO_ACTION);
            i.setClassName(VISO_PACKAGE, VISO_SERVICE);
            visoAvailable = pm.resolveService(i, PackageManager.MATCH_ALL) != null;
            if (!visoAvailable) {
                // 有些版本只公开 action，不公开 service class。
                Intent generic = new Intent(VISO_ACTION);
                generic.setPackage(VISO_PACKAGE);
                visoAvailable = pm.resolveService(generic, PackageManager.MATCH_ALL) != null;
            }
            Log.i(TAG, "VISO available=" + visoAvailable);
        } catch (Throwable t) {
            visoAvailable = false;
            Log.w(TAG, "VISO probe failed", t);
        }
    }

    /**
     * 处理车机 VISO 下发给应用的动作。
     * 支持：打开 APP、启动/launch、返回/关闭等常见动作。
     */
    public void handleVISOIntent(Intent intent) {
        if (intent == null) return;
        Bundle b = intent.getExtras();
        if (b == null) return;

        String action = firstNonEmpty(
                intent.getStringExtra(EXTRA_ACTION),
                intent.getStringExtra("action"),
                intent.getStringExtra("command"));
        String appName = firstNonEmpty(
                intent.getStringExtra(EXTRA_APP_NAME),
                intent.getStringExtra(EXTRA_HOTWORD),
                intent.getStringExtra("appName"),
                intent.getStringExtra("targetApp"));

        // 如果 VISO 已经把目标包名传过来，优先使用包名；否则按中文 APP 名称匹配。
        String pkg = firstNonEmpty(
                intent.getStringExtra("packageName"),
                intent.getStringExtra("package"),
                intent.getStringExtra("pkg"),
                intent.getStringExtra("targetPackage"));

        if (isOpenAction(action) || !isEmpty(appName) || !isEmpty(pkg)) {
            if (isEmpty(pkg)) pkg = findPackageByName(appName);
            if (!isEmpty(pkg)) {
                launchPackage(pkg);
                return;
            }
        }

        if (containsAny(action, "back", "返回")) {
            AccessibilityServiceBridge.perform(context, 1);
        } else if (containsAny(action, "home", "首页")) {
            AccessibilityServiceBridge.perform(context, 2);
        }
    }

    private boolean isOpenAction(String action) {
        if (action == null) return false;
        String s = action.toLowerCase(Locale.ROOT);
        return s.contains("open") || s.contains("launch") || s.contains("start") ||
                s.contains("打开") || s.contains("启动") || s.contains("进入");
    }

    private boolean containsAny(String s, String... values) {
        if (s == null) return false;
        String x = s.toLowerCase(Locale.ROOT);
        for (String v : values) if (x.contains(v.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private String findPackageByName(String requested) {
        if (isEmpty(requested)) return null;
        String q = requested.trim().toLowerCase(Locale.ROOT);
        List<ApplicationInfo> apps;
        try { apps = pm.getInstalledApplications(PackageManager.GET_META_DATA); }
        catch (Exception e) { return null; }

        String partial = null;
        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals(context.getPackageName())) continue;
            Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
            if (launch == null) continue;
            String name;
            try { name = pm.getApplicationLabel(ai).toString(); }
            catch (Exception e) { name = ai.packageName; }
            String n = name.toLowerCase(Locale.ROOT);
            if (n.equals(q)) return ai.packageName;
            if (n.contains(q) || q.contains(n)) partial = ai.packageName;
        }
        return partial;
    }

    /** 启动目标 APP；如果启动器已有窗口预设，优先复用第一条对应预设。 */
    public void launchPackage(String pkg) {
        if (isEmpty(pkg)) return;
        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(launch);
            Log.i(TAG, "voice launch: " + pkg);
        } catch (Exception e) {
            Log.w(TAG, "voice launch failed: " + pkg, e);
        }
    }

    /**
     * 生成当前启动器可识别的 APP 目录，供后续 VISO 私有 Binder 注册使用。
     * 每个 APP 包含 package、中文名和建议热词。
     */
    public JSONArray buildVoiceCatalog() {
        JSONArray result = new JSONArray();
        try {
            List<ApplicationInfo> apps = new ArrayList<>(pm.getInstalledApplications(PackageManager.GET_META_DATA));
            Collections.sort(apps, (a,b) -> {
                String x = pm.getApplicationLabel(a).toString();
                String y = pm.getApplicationLabel(b).toString();
                return x.compareToIgnoreCase(y);
            });
            for (ApplicationInfo ai : apps) {
                if (ai.packageName.equals(context.getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                JSONObject o = new JSONObject();
                o.put("packageName", ai.packageName);
                o.put("appName", pm.getApplicationLabel(ai).toString());
                o.put("hotword", pm.getApplicationLabel(ai).toString());
                result.put(o);
            }
        } catch (Exception e) {
            Log.w(TAG, "buildVoiceCatalog failed", e);
        }
        return result;
    }

    /**
     * 对外发送一个兼容 VISO action 的广播。不同 SIMO 版本对注册入口不同，
     * 因此保留该入口用于车机端联调，不会影响普通 Android 设备。
     */
    public void sendVoicePageHint(String appName, String packageName) {
        try {
            Intent i = new Intent(VISO_ACTION);
            i.setPackage(VISO_PACKAGE);
            i.putExtra(EXTRA_PAGE_ID, "simo.application.voice");
            i.putExtra(EXTRA_ACTION, "register");
            i.putExtra(EXTRA_APP_NAME, appName);
            i.putExtra(EXTRA_HOTWORD, appName);
            i.putExtra("packageName", packageName);
            context.sendBroadcast(i);
        } catch (Exception e) {
            Log.w(TAG, "send VISO hint failed", e);
        }
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private static String firstNonEmpty(String... values) {
        if (values != null) for (String s : values) if (!isEmpty(s)) return s;
        return null;
    }
}

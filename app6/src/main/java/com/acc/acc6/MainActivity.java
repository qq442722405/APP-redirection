package com.acc.acc6;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    LinearLayout root;
    int bg = Color.rgb(245,245,245);
    int dp(int x) { return (int)(x*getResources().getDisplayMetrics().density+0.5f); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(24),dp(24),dp(24));
        root.setBackgroundColor(bg);

        TextView title = new TextView(this);
        title.setText("Acc6");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(64)));

        Button check = new Button(this);
        check.setText("权限检查 / 申请");
        check.setOnClickListener(v -> checkRuntime());
        root.addView(check,new LinearLayout.LayoutParams(-1,dp(52)));

        Button color = new Button(this);
        color.setText("APP颜色设置");
        color.setOnClickListener(v -> {
            bg = (bg == Color.rgb(245,245,245)) ? Color.rgb(225,240,255) : Color.rgb(245,245,245);
            root.setBackgroundColor(bg);
        });
        root.addView(color,new LinearLayout.LayoutParams(-1,dp(52)));

        Button overlay = new Button(this);
        overlay.setText("悬浮窗权限设置");
        overlay.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName()))); } catch(Exception e) {}
        });
        root.addView(overlay,new LinearLayout.LayoutParams(-1,dp(52)));

        Button access = new Button(this);
        access.setText("无障碍设置");
        access.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch(Exception e) {}
        });
        root.addView(access,new LinearLayout.LayoutParams(-1,dp(52)));

        Button exit = new Button(this);
        exit.setText("退出");
        exit.setOnClickListener(v -> finishAndRemoveTask());
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1,dp(52));
        ep.topMargin=dp(20);
        root.addView(exit,ep);

        setContentView(root);
    }

    void checkRuntime() {
        if (android.os.Build.VERSION.SDK_INT < 23) return;
        String[] p = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_PHONE_STATE
        };
        java.util.ArrayList<String> miss = new java.util.ArrayList<>();
        for(String x:p) if(checkSelfPermission(x)!=PackageManager.PERMISSION_GRANTED) miss.add(x);
        if(!miss.isEmpty()) requestPermissions(miss.toArray(new String[0]),1001);
        else new android.app.AlertDialog.Builder(this).setTitle("权限检查")
            .setMessage("常用运行时权限已授予。悬浮窗、无障碍、使用情况访问等特殊权限请在系统设置中确认。")
            .setPositiveButton("确定",null).show();
    }
}

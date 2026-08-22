package com.acc.acc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        checkOverlayPermission();
        setContentView(createMainLayout());
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1234);
            }
        }
    }

    private View createMainLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("APP窗口启动器 - 车机测试版");
        tvTitle.setTextSize(20);
        tvTitle.setPadding(0, 20, 0, 30);
        layout.addView(tvTitle);

        // 1. 无障碍服务开启入口
        Button btnAcc = new Button(this);
        btnAcc.setText("开启无障碍服务");
        btnAcc.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        layout.addView(btnAcc);

        // 2. 悬浮控制条入口
        Button btnFloat = new Button(this);
        btnFloat.setText("启动悬浮控制条");
        btnFloat.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, FloatingService.class);
            startService(intent);
        });
        layout.addView(btnFloat);

        // 3. 替代原记事本位置的“测试”按钮
        Button btnTest = new Button(this);
        btnTest.setText("测试控制台窗口");
        btnTest.setTextSize(18);
        btnTest.setBackgroundColor(0xFF2196F3);
        btnTest.setTextColor(0xFFFFFFFF);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 30, 0, 0);
        btnTest.setLayoutParams(params);

        btnTest.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限！", Toast.LENGTH_SHORT).show();
                checkOverlayPermission();
                return;
            }
            Intent intent = new Intent(MainActivity.this, TestWindowService.class);
            startService(intent);
            Toast.makeText(this, "已启动测试窗口", Toast.LENGTH_SHORT).show();
        });
        layout.addView(btnTest);

        return layout;
    }
}
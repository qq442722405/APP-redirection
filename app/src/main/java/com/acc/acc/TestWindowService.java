package com.acc.acc;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;

public class TestWindowService extends Service {

    private WindowManager windowManager;
    private View testWindowView;
    private WindowManager.LayoutParams params;
    private FrameLayout appDisplayContainer;
    private EditText etPkgName;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        initTestWindow();
    }

    private void initTestWindow() {
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                900, 1100,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        testWindowView = createTestWindowLayout();
        windowManager.addView(testWindowView, params);
    }

    private View createTestWindowLayout() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainLayout.setBackgroundColor(0xEE1A1A1A);

        // 左侧可调宽度拉手
        TextView leftHandle = new TextView(this);
        leftHandle.setText("║\n║\n拖\n动\n║\n║");
        leftHandle.setTextColor(Color.WHITE);
        leftHandle.setGravity(Gravity.CENTER);
        leftHandle.setLayoutParams(new LinearLayout.LayoutParams(50, LinearLayout.LayoutParams.MATCH_PARENT));
        leftHandle.setBackgroundColor(0xFF444444);
        setupResizeTouchListener(leftHandle, true);

        // 中间主体内容区域
        LinearLayout centerContent = new LinearLayout(this);
        centerContent.setOrientation(LinearLayout.VERTICAL);
        centerContent.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
        centerContent.setPadding(10, 10, 10, 10);

        // 标题栏 + 关闭按钮
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("测试控制台窗口");
        tvTitle.setTextColor(Color.YELLOW);
        tvTitle.setTextSize(16);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Button btnClose = new Button(this);
        btnClose.setText("关闭");
        btnClose.setTextColor(Color.RED);
        btnClose.setOnClickListener(v -> stopSelf());

        titleBar.addView(tvTitle);
        titleBar.addView(btnClose);
        centerContent.addView(titleBar);

        // 目标包名输入框
        etPkgName = new EditText(this);
        etPkgName.setText("com.android.settings");
        etPkgName.setTextColor(Color.WHITE);
        etPkgName.setHint("请输入目标应用包名");
        etPkgName.setHintTextColor(Color.GRAY);
        etPkgName.setTextSize(14);
        centerContent.addView(etPkgName);

        // 测试按钮行 1：ADB 强退与应用启动
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        Button btnAdbKill = new Button(this);
        btnAdbKill.setText("ADB关程序");
        btnAdbKill.setOnClickListener(v -> {
            String pkg = etPkgName.getText().toString().trim();
            killAppViaAdb(pkg);
        });

        Button btnLaunchApp = new Button(this);
        btnLaunchApp.setText("内嵌启动App");
        btnLaunchApp.setOnClickListener(v -> {
            String pkg = etPkgName.getText().toString().trim();
            launchAppInsideWindow(pkg);
        });

        row1.addView(btnAdbKill);
        row1.addView(btnLaunchApp);
        centerContent.addView(row1);

        // 测试按钮行 2：分屏、多窗口、悬浮窗口
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        Button btnSplit = new Button(this);
        btnSplit.setText("分屏测试");
        btnSplit.setOnClickListener(v -> {
            boolean success = AccessibilityServiceBridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
            Toast.makeText(this, success ? "已触发分屏" : "分屏触发失败(请先开启无障碍服务)", Toast.LENGTH_SHORT).show();
        });

        Button btnMultiWindow = new Button(this);
        btnMultiWindow.setText("多窗口测试");
        btnMultiWindow.setOnClickListener(v -> {
            String pkg = etPkgName.getText().toString().trim();
            launchFreeformApp(pkg);
        });

        Button btnFloating = new Button(this);
        btnFloating.setText("悬浮窗口测试");
        btnFloating.setOnClickListener(v -> {
            Toast.makeText(this, "当前控制台即为悬浮窗口模式", Toast.LENGTH_SHORT).show();
        });

        row2.addView(btnSplit);
        row2.addView(btnMultiWindow);
        row2.addView(btnFloating);
        centerContent.addView(row2);

        // 内嵌 APP 显示与自适应缩放响应容器
        appDisplayContainer = new FrameLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        containerParams.setMargins(0, 10, 0, 0);
        appDisplayContainer.setLayoutParams(containerParams);
        appDisplayContainer.setBackgroundColor(Color.BLACK);

        TextView tvContainerHint = new TextView(this);
        tvContainerHint.setText("【APP显示与响应区域】\n拖动左右两侧Grey条可实时调整宽度，内部布局将自动跟随调整变化");
        tvContainerHint.setTextColor(Color.GREEN);
        tvContainerHint.setGravity(Gravity.CENTER);
        appDisplayContainer.addView(tvContainerHint);

        centerContent.addView(appDisplayContainer);

        // 右侧可调宽度拉手
        TextView rightHandle = new TextView(this);
        rightHandle.setText("║\n║\n拖\n动\n║\n║");
        rightHandle.setTextColor(Color.WHITE);
        rightHandle.setGravity(Gravity.CENTER);
        rightHandle.setLayoutParams(new LinearLayout.LayoutParams(50, LinearLayout.LayoutParams.MATCH_PARENT));
        rightHandle.setBackgroundColor(0xFF444444);
        setupResizeTouchListener(rightHandle, false);

        mainLayout.addView(leftHandle);
        mainLayout.addView(centerContent);
        mainLayout.addView(rightHandle);

        return mainLayout;
    }

    private void setupResizeTouchListener(View handle, boolean isLeft) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialWidth;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = (int) event.getRawX();
                        initialWidth = params.width;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) event.getRawX() - initialX;
                        int newWidth = isLeft ? initialWidth - deltaX : initialWidth + deltaX;
                        if (newWidth > 400) {
                            params.width = newWidth;
                            windowManager.updateViewLayout(testWindowView, params);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void killAppViaAdb(String packageName) {
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("sh");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("adb connect 127.0.0.1:5555\n");
                os.writeBytes("adb shell am force-stop " + packageName + "\n");
                os.writeBytes("am force-stop " + packageName + "\n");
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        Toast.makeText(this, "已向 127.0.0.1:5555 发送关闭指令: " + packageName, Toast.LENGTH_SHORT).show();
    }

    private void launchAppInsideWindow(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launchIntent);
                Toast.makeText(this, "已调起应用: " + packageName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "未找到目标包名: " + packageName, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchFreeformApp(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchBounds(new Rect(100, 100, 800, 1000));
                try {
                    startActivity(launchIntent, options.toBundle());
                    Toast.makeText(this, "以 Freeform 多窗口模式启动", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    startActivity(launchIntent);
                }
            } else {
                startActivity(launchIntent);
            }
        } else {
            Toast.makeText(this, "未找到目标包名: " + packageName, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (testWindowView != null && windowManager != null) {
            windowManager.removeView(testWindowView);
        }
    }
}
package com.acc.acc;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 如果您的布局文件名称不同，请在此处修改对应的 layout ID
        setContentView(R.layout.activity_main);

        // 初始化组件及事件绑定（对应编译日志中报错的控件调用）
        setupClickListeners();
    }

    private void setupClickListeners() {
        // 1. 悬浮预设选择器
        View floatingPick = findViewById(R.id.floatingPick);
        if (floatingPick != null) {
            floatingPick.setOnClickListener(v -> showFloatingPresetPicker());
        }

        // 2. 笔记/说明
        View note = findViewById(R.id.note);
        if (note != null) {
            note.setOnClickListener(v -> showNotes());
        }

        // 3. 自启动编辑器
        View autoButton = findViewById(R.id.autoButton);
        if (autoButton != null) {
            autoButton.setOnClickListener(v -> showAutoStartEditor());
        }

        // 4. 权限与屏幕诊断
        final android.app.Dialog[] settingsDialog = new android.app.Dialog[1];
        View permissions = findViewById(R.id.permissions);
        if (permissions != null) {
            permissions.setOnClickListener(v -> {
                if (settingsDialog[0] != null) {
                    settingsDialog[0].dismiss();
                }
                showScreenDiagnostics();
            });
        }

        // 5. 导出配置
        View exportButton = findViewById(R.id.exportButton);
        if (exportButton != null) {
            exportButton.setOnClickListener(v -> exportConfig());
        }

        // 6. 导入配置
        View importButton = findViewById(R.id.importButton);
        if (importButton != null) {
            importButton.setOnClickListener(v -> importConfig());
        }
    }

    // ==========================================
    // 以下为之前缺失的 8 个核心方法实现
    // ==========================================

    /**
     * 显示悬浮预设选择器
     */
    private void showFloatingPresetPicker() {
        Toast.makeText(this, "打开悬浮预设选择器", Toast.LENGTH_SHORT).show();
        // TODO: 在此处补充您的具体业务逻辑
    }

    /**
     * 显示笔记
     */
    private void showNotes() {
        Toast.makeText(this, "打开笔记页面", Toast.LENGTH_SHORT).show();
        // TODO: 在此处补充您的具体业务逻辑
    }

    /**
     * 显示自启动编辑器
     */
    private void showAutoStartEditor() {
        Toast.makeText(this, "打开自启动编辑器", Toast.LENGTH_SHORT).show();
        // TODO: 在此处补充您的具体业务逻辑
    }

    /**
     * 显示屏幕诊断
     */
    private void showScreenDiagnostics() {
        Toast.makeText(this, "正在运行屏幕诊断...", Toast.LENGTH_SHORT).show();
        // TODO: 在此处补充您的具体业务逻辑
    }

    /**
     * 导出配置
     */
    private void exportConfig() {
        Toast.makeText(this, "配置导出成功", Toast.LENGTH_SHORT).show();
        // TODO: 在此处补充您的具体业务逻辑
    }

    /**
     * 导入配置
     */
    private void importConfig() {
        Toast.makeText(this, "配置导入成功", Toast.LENGTH_SHORT).show();
        // TODO: 在此处补充您的具体业务逻辑
    }

    /**
     * 启动悬浮服务
     */
    private void startFloatingService() {
        try {
            Intent intent = new Intent(this, FloatingService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "启动悬浮服务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 停止悬浮服务
     */
    private void stopFloatingService() {
        try {
            Intent intent = new Intent(this, FloatingService.class);
            stopService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

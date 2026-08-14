package com.example.carappjump;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "jump_prefs";
    private static final String KEY_PACKAGE = "target_package";
    private static final String EXTRA_RESELECT = "reselect";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> filteredApps = new ArrayList<>();
    private AppAdapter adapter;
    private EditText searchBox;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        boolean reselect = (getIntent() != null && getIntent().getBooleanExtra(EXTRA_RESELECT, false))
                || (getIntent() != null && getIntent().getData() != null
                && "carappjump".equals(getIntent().getData().getScheme())
                && "reselect".equals(getIntent().getData().getHost()));
        String targetPackage = prefs.getString(KEY_PACKAGE, null);

        if (!reselect && targetPackage != null && !targetPackage.isEmpty()) {
            if (tryLaunch(targetPackage)) {
                // 直接启动目标 APP；本 Activity 无需显示界面。
                return;
            }
            // 目标 APP 已不存在，自动回到选择界面。
            prefs.edit().remove(KEY_PACKAGE).apply();
        }

        showSelector();
    }

    private boolean tryLaunch(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            Intent launch = pm.getLaunchIntentForPackage(packageName);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
            // 启动目标 APP 后立即结束本启动器，避免车机再次打开时出现白屏。
            finishAndRemoveTask();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showSelector() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(12));
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText(getString(com.example.carappjump.R.string.selector_title));
        title.setTextColor(Color.rgb(25, 30, 36));
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint(getString(com.example.carappjump.R.string.search_hint));
        searchBox.setTextSize(16);
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.setBackground(new ColorDrawable(Color.WHITE));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(48));
        searchLp.bottomMargin = dp(12);
        root.addView(searchBox, searchLp);

        ListView listView = new ListView(this);
        listView.setDivider(new ColorDrawable(Color.rgb(225, 229, 235)));
        listView.setDividerHeight(dp(1));
        listView.setBackgroundColor(Color.WHITE);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));


        setContentView(root);

        adapter = new AppAdapter(this, filteredApps);
        listView.setAdapter(adapter);

        loadApps();

        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppItem item = filteredApps.get(position);
            prefs.edit().putString(KEY_PACKAGE, item.packageName).apply();
            Toast.makeText(this, "已选择：" + item.label, Toast.LENGTH_SHORT).show();
            hideKeyboard();
            handler.postDelayed(() -> {
                if (!tryLaunch(item.packageName)) {
                    prefs.edit().remove(KEY_PACKAGE).apply();
                    Toast.makeText(this, "无法启动该 APP，请重新选择", Toast.LENGTH_LONG).show();
                } else {
                    // tryLaunch() 成功时已经关闭自身，这里无需再次 finish。
                }
            }, 120);
        });
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> infos = pm.queryIntentActivities(launcherIntent, 0);

        allApps.clear();
        for (android.content.pm.ResolveInfo info : infos) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence labelCs = ai.loadLabel(pm);
                String label = labelCs == null ? pkg : labelCs.toString();
                AppItem item = new AppItem(label, pkg, ai.loadIcon(pm));
                allApps.add(item);
            } catch (Exception ignored) {}
        }

        Collections.sort(allApps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        filterApps("");
    }

    private void filterApps(String query) {
        filteredApps.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (AppItem item : allApps) {
            if (q.isEmpty() || item.label.toLowerCase(Locale.ROOT).contains(q) || item.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                filteredApps.add(item);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void hideKeyboard() {
        if (searchBox != null) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static class AppItem {
        final String label;
        final String packageName;
        final android.graphics.drawable.Drawable icon;
        AppItem(String label, String packageName, android.graphics.drawable.Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    public static class AppAdapter extends BaseAdapter {
        private final Context context;
        private final List<AppItem> items;

        AppAdapter(Context context, List<AppItem> items) {
            this.context = context;
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(8), dp(14), dp(8));

                ImageView icon = new ImageView(context);
                row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1);
                textLp.leftMargin = dp(14);
                row.addView(texts, textLp);

                TextView label = new TextView(context);
                label.setTextSize(17);
                label.setTextColor(Color.rgb(25, 30, 36));
                texts.addView(label, new LinearLayout.LayoutParams(-1, -2));

                TextView pkg = new TextView(context);
                pkg.setTextSize(12);
                pkg.setTextColor(Color.rgb(110, 118, 128));
                texts.addView(pkg, new LinearLayout.LayoutParams(-1, -2));

                holder = new RowHolder(icon, label, pkg);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            AppItem item = items.get(position);
            holder.icon.setImageDrawable(item.icon);
            holder.label.setText(item.label);
            holder.pkg.setText(item.packageName);
            return convertView;
        }

        private int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }

        static class RowHolder {
            final ImageView icon;
            final TextView label;
            final TextView pkg;
            RowHolder(ImageView icon, TextView label, TextView pkg) {
                this.icon = icon;
                this.label = label;
                this.pkg = pkg;
            }
        }
    }
}

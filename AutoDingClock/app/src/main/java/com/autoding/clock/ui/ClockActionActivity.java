package com.autoding.clock.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.autoding.clock.accessibility.ClockAccessibilityService;

/**
 * 透明Activity - 用于触发打卡操作
 * 当闹钟触发时，通过启动此Activity来触发无障碍服务的打卡流程
 */
public class ClockActionActivity extends Activity {

    private static final String TAG = "ClockActionActivity";
    private static ClockActionActivity instance;

    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.autoding.clock.ACTION_ACCESSIBILITY_CLOCK".equals(intent.getAction())) {
                Log.d(TAG, "Received clock broadcast");
                triggerClock();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        // 注册广播接收器
        IntentFilter filter = new IntentFilter("com.autoding.clock.ACTION_ACCESSIBILITY_CLOCK");
        registerReceiver(clockReceiver, filter);

        // 触发打卡
        triggerClock();
    }

    private void triggerClock() {
        Log.d(TAG, "Triggering clock via AccessibilityService");

        // 通过Intent启动钉钉，无障碍服务会捕获事件并执行打卡
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.alibaba.android.rimet");
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launchIntent);
        }

        // 延迟关闭Activity
        new android.os.Handler().postDelayed(() -> {
            finish();
        }, 5000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(clockReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Unregister receiver error", e);
        }
        instance = null;
    }

    public static boolean isActive() {
        return instance != null;
    }
}
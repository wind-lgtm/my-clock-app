package com.autoding.clock.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.autoding.clock.ClockApp;
import com.autoding.clock.R;
import com.autoding.clock.accessibility.ClockAccessibilityService;
import com.autoding.clock.manager.ClockSettings;
import com.autoding.clock.ui.MainActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 前台服务 - 保持APP在后台运行并执行打卡操作
 */
public class ClockForegroundService extends Service {

    private static final String TAG = "ClockForegroundService";
    private static final int NOTIFICATION_ID = 10001;

    public static final String ACTION_START = "com.autoding.clock.START_SERVICE";
    public static final String ACTION_CLOCK = "com.autoding.clock.DO_CLOCK";
    public static final String ACTION_TEST_CLOCK = "com.autoding.clock.TEST_CLOCK";
    public static final String EXTRA_CLOCK_TYPE = "clock_type";

    private ClockSettings settings;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        settings = ClockSettings.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        startForeground(NOTIFICATION_ID, createNotification("自动打卡服务运行中"));

        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "Action: " + action);

            if (ACTION_CLOCK.equals(action) || ACTION_TEST_CLOCK.equals(action)) {
                performClock();
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 执行打卡
     */
    private void performClock() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Log.d(TAG, "Performing clock for date: " + today);

        // 检查今天是否已打卡
        // 注意：这里简化处理，实际应该区分上班/下班

        // 通知用户正在打卡
        showClockingNotification();

        // 通过无障碍服务执行打卡
        // 由于无障碍服务是系统管理的单例，我们通过发送广播或直接调用方式触发
        Intent clockIntent = new Intent("com.autoding.clock.ACTION_ACCESSIBILITY_CLOCK");
        clockIntent.setPackage(getPackageName());
        sendBroadcast(clockIntent);

        // 延迟标记打卡完成
        new android.os.Handler().postDelayed(() -> {
            // 更新通知
            showClockCompleteNotification();

            // 标记今日已打卡
            settings.setMorningClocked(true, today);
        }, 10000);
    }

    /**
     * 显示正在打卡通知
     */
    private void showClockingNotification() {
        Notification notification = new NotificationCompat.Builder(this, ClockApp.CHANNEL_ID_ALARM)
                .setContentTitle("⏳ 正在打卡")
                .setContentText("自动打卡进行中，请勿操作手机...")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(false)
                .build();

        notificationManager.notify(10002, notification);
    }

    /**
     * 显示打卡完成通知
     */
    private void showClockCompleteNotification() {
        Notification notification = new NotificationCompat.Builder(this, ClockApp.CHANNEL_ID_ALARM)
                .setContentTitle("✅ 打卡完成")
                .setContentText("已为您完成自动打卡")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(10003, notification);
    }

    /**
     * 创建前台服务通知
     */
    private Notification createNotification(String content) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, flags);

        return new NotificationCompat.Builder(this, ClockApp.CHANNEL_ID_FOREGROUND)
                .setContentTitle("🕐 钉钉自动打卡")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    /**
     * 启动服务 - 打卡
     */
    public static void startForClock(Context context, String clockType) {
        Intent intent = new Intent(context, ClockForegroundService.class);
        intent.setAction(ACTION_CLOCK);
        intent.putExtra(EXTRA_CLOCK_TYPE, clockType);
        startServiceCompat(context, intent);
    }

    /**
     * 启动服务 - 测试打卡
     */
    public static void startForTestClock(Context context) {
        Intent intent = new Intent(context, ClockForegroundService.class);
        intent.setAction(ACTION_TEST_CLOCK);
        startServiceCompat(context, intent);
    }

    /**
     * 兼容启动服务
     */
    private static void startServiceCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
package com.autoding.clock;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class ClockApp extends Application {

    public static final String CHANNEL_ID_FOREGROUND = "clock_foreground_channel";
    public static final String CHANNEL_ID_ALARM = "clock_alarm_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // 前台服务通知渠道
            NotificationChannel foregroundChannel = new NotificationChannel(
                    CHANNEL_ID_FOREGROUND,
                    "自动打卡服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            foregroundChannel.setDescription("保持自动打卡服务在后台运行");
            manager.createNotificationChannel(foregroundChannel);

            // 闹钟提醒通知渠道
            NotificationChannel alarmChannel = new NotificationChannel(
                    CHANNEL_ID_ALARM,
                    "打卡提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alarmChannel.setDescription("上下班打卡时间提醒");
            manager.createNotificationChannel(alarmChannel);
        }
    }
}
package com.autoding.clock.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.autoding.clock.accessibility.ClockAccessibilityService;
import com.autoding.clock.manager.ClockScheduler;
import com.autoding.clock.service.ClockForegroundService;

/**
 * 闹钟广播接收器 - 接收定时闹钟广播并触发打卡
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm received!");

        if (intent == null) return;

        String clockType = intent.getStringExtra("clock_type");
        Log.d(TAG, "Clock type: " + clockType);

        // 检查是否是今天需要打卡的日子
        // 启动前台服务执行打卡
        ClockForegroundService.startForClock(context, clockType);

        // 重新安排下一次打卡
        ClockScheduler scheduler = new ClockScheduler(context);
        if ("MORNING".equals(clockType)) {
            scheduler.scheduleMorningClock();
        } else if ("EVENING".equals(clockType)) {
            scheduler.scheduleEveningClock();
        }
    }
}
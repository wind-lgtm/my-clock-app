package com.autoding.clock.manager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.format.DateFormat;

import com.autoding.clock.receiver.AlarmReceiver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * 打卡定时调度器 - 使用 AlarmManager 设置精确闹钟
 */
public class ClockScheduler {

    private static final int REQUEST_CODE_MORNING = 1001;
    private static final int REQUEST_CODE_EVENING = 1002;

    private final Context context;
    private final ClockSettings settings;
    private final AlarmManager alarmManager;

    public ClockScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.settings = ClockSettings.getInstance(context);
        this.alarmManager = (AlarmManager) this.context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * 安排上班打卡
     */
    public void scheduleMorningClock() {
        if (!settings.isMorningEnabled()) return;

        Calendar target = getNextWorkdayMorning();
        if (target == null) return;

        long triggerAt = target.getTimeInMillis();
        PendingIntent pi = createPendingIntent(REQUEST_CODE_MORNING, ClockType.MORNING);

        setExactAlarm(triggerAt, pi);
    }

    /**
     * 安排下班打卡
     */
    public void scheduleEveningClock() {
        if (!settings.isEveningEnabled()) return;

        Calendar target = getNextWorkdayEvening();
        if (target == null) return;

        long triggerAt = target.getTimeInMillis();
        PendingIntent pi = createPendingIntent(REQUEST_CODE_EVENING, ClockType.EVENING);

        setExactAlarm(triggerAt, pi);
    }

    /**
     * 取消上班打卡
     */
    public void cancelMorningClock() {
        PendingIntent pi = createPendingIntent(REQUEST_CODE_MORNING, ClockType.MORNING);
        alarmManager.cancel(pi);
    }

    /**
     * 取消下班打卡
     */
    public void cancelEveningClock() {
        PendingIntent pi = createPendingIntent(REQUEST_CODE_EVENING, ClockType.EVENING);
        alarmManager.cancel(pi);
    }

    /**
     * 重新安排所有打卡（用于开机重启后调用）
     */
    public void rescheduleAll() {
        if (settings.isMorningEnabled()) scheduleMorningClock();
        if (settings.isEveningEnabled()) scheduleEveningClock();
    }

    /**
     * 获取下一个工作日的上班打卡时间
     */
    private Calendar getNextWorkdayMorning() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();

        int advanceMin = settings.getMorningAdvance();
        int hour = settings.getMorningHour();
        int minute = settings.getMorningMinute();

        // 从今天开始找
        for (int i = 0; i < 8; i++) {
            target.set(Calendar.HOUR_OF_DAY, hour);
            target.set(Calendar.MINUTE, minute - advanceMin);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            if (settings.isWorkday(target)) {
                if (target.after(now)) {
                    return target;
                }
            }

            // 尝试下一天
            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    /**
     * 获取下一个工作日的下班打卡时间
     */
    private Calendar getNextWorkdayEvening() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();

        int delayMin = settings.getEveningDelay();
        int hour = settings.getEveningHour();
        int minute = settings.getEveningMinute();

        for (int i = 0; i < 8; i++) {
            target.set(Calendar.HOUR_OF_DAY, hour);
            target.set(Calendar.MINUTE, minute + delayMin);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            if (settings.isWorkday(target)) {
                if (target.after(now)) {
                    return target;
                }
            }

            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    private PendingIntent createPendingIntent(int requestCode, ClockType type) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("clock_type", type.name());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    private void setExactAlarm(long triggerAt, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    /**
     * 获取下次打卡时间的描述
     */
    public String getNextClockTime() {
        Calendar morning = getNextWorkdayMorning();
        Calendar evening = getNextWorkdayEvening();

        if (morning == null && evening == null) return "未设置";

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();

        if (morning != null) {
            sb.append("上班: ").append(sdf.format(morning.getTime()));
        }
        if (evening != null) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("下班: ").append(sdf.format(evening.getTime()));
        }
        return sb.toString();
    }

    public enum ClockType {
        MORNING, EVENING
    }
}
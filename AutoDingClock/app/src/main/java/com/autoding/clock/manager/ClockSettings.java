package com.autoding.clock.manager;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 打卡设置管理类 - 使用 SharedPreferences 持久化存储
 */
public class ClockSettings {

    private static final String PREFS_NAME = "clock_settings";

    // 键名
    private static final String KEY_MORNING_ENABLED = "morning_enabled";
    private static final String KEY_EVENING_ENABLED = "evening_enabled";
    private static final String KEY_MORNING_HOUR = "morning_hour";
    private static final String KEY_MORNING_MINUTE = "morning_minute";
    private static final String KEY_EVENING_HOUR = "evening_hour";
    private static final String KEY_EVENING_MINUTE = "evening_minute";
    private static final String KEY_MORNING_ADVANCE = "morning_advance";
    private static final String KEY_EVENING_DELAY = "evening_delay";
    private static final String KEY_WEEKDAYS = "weekdays";
    private static final String KEY_MORNING_CLOCKED = "morning_clocked";
    private static final String KEY_EVENING_CLOCKED = "evening_clocked";
    private static final String KEY_LAST_CLOCK_DATE = "last_clock_date";

    private static ClockSettings instance;
    private final SharedPreferences prefs;

    private ClockSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ClockSettings getInstance(Context context) {
        if (instance == null) {
            instance = new ClockSettings(context);
        }
        return instance;
    }

    // 上班设置
    public boolean isMorningEnabled() { return prefs.getBoolean(KEY_MORNING_ENABLED, false); }
    public void setMorningEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_MORNING_ENABLED, enabled).apply(); }

    public int getMorningHour() { return prefs.getInt(KEY_MORNING_HOUR, 9); }
    public void setMorningHour(int hour) { prefs.edit().putInt(KEY_MORNING_HOUR, hour).apply(); }

    public int getMorningMinute() { return prefs.getInt(KEY_MORNING_MINUTE, 0); }
    public void setMorningMinute(int minute) { prefs.edit().putInt(KEY_MORNING_MINUTE, minute).apply(); }

    public int getMorningAdvance() { return prefs.getInt(KEY_MORNING_ADVANCE, 0); }
    public void setMorningAdvance(int minutes) { prefs.edit().putInt(KEY_MORNING_ADVANCE, minutes).apply(); }

    // 下班设置
    public boolean isEveningEnabled() { return prefs.getBoolean(KEY_EVENING_ENABLED, false); }
    public void setEveningEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_EVENING_ENABLED, enabled).apply(); }

    public int getEveningHour() { return prefs.getInt(KEY_EVENING_HOUR, 18); }
    public void setEveningHour(int hour) { prefs.edit().putInt(KEY_EVENING_HOUR, hour).apply(); }

    public int getEveningMinute() { return prefs.getInt(KEY_EVENING_MINUTE, 0); }
    public void setEveningMinute(int minute) { prefs.edit().putInt(KEY_EVENING_MINUTE, minute).apply(); }

    public int getEveningDelay() { return prefs.getInt(KEY_EVENING_DELAY, 0); }
    public void setEveningDelay(int minutes) { prefs.edit().putInt(KEY_EVENING_DELAY, minutes).apply(); }

    // 工作日设置（位掩码，bit0=周日...bit6=周六）
    public int getWeekdays() { return prefs.getInt(KEY_WEEKDAYS, 0b00111110); }
    public void setWeekdays(int weekdays) { prefs.edit().putInt(KEY_WEEKDAYS, weekdays).apply(); }

    // 打卡状态
    public boolean isMorningClocked(String date) {
        return prefs.getBoolean(KEY_MORNING_CLOCKED, false)
                && date.equals(prefs.getString(KEY_LAST_CLOCK_DATE, ""));
    }
    public boolean isEveningClocked(String date) {
        return prefs.getBoolean(KEY_EVENING_CLOCKED, false)
                && date.equals(prefs.getString(KEY_LAST_CLOCK_DATE, ""));
    }
    public void setMorningClocked(boolean clocked, String date) {
        prefs.edit()
                .putBoolean(KEY_MORNING_CLOCKED, clocked)
                .putString(KEY_LAST_CLOCK_DATE, date)
                .apply();
    }
    public void setEveningClocked(boolean clocked, String date) {
        prefs.edit()
                .putBoolean(KEY_EVENING_CLOCKED, clocked)
                .putString(KEY_LAST_CLOCK_DATE, date)
                .apply();
    }

    /**
     * 检查今天是否是设定的工作日
     */
    public boolean isWorkday(java.util.Calendar calendar) {
        int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        // Calendar: 1=周日, 2=周一, ..., 7=周六
        // 我们的位掩码: bit0=周日, bit1=周一, ..., bit6=周六
        int mask = 1 << (dayOfWeek - 1);
        return (getWeekdays() & mask) != 0;
    }
}
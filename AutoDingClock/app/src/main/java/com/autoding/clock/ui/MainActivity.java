package com.autoding.clock.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.autoding.clock.ClockApp;
import com.autoding.clock.R;
import com.autoding.clock.accessibility.AccessibilityHelper;
import com.autoding.clock.manager.ClockScheduler;
import com.autoding.clock.manager.ClockSettings;
import com.autoding.clock.service.ClockForegroundService;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

    // 视图组件
    private TextView tvAccessibilityStatus;
    private Button btnEnableAccessibility;
    private Switch switchMorning;
    private Switch switchEvening;
    private TimePicker timeMorning;
    private TimePicker timeEvening;
    private SeekBar seekMorningAdvance;
    private SeekBar seekEveningDelay;
    private TextView tvMorningAdvance;
    private TextView tvEveningDelay;
    private TextView tvMorningStatus;
    private TextView tvEveningStatus;
    private TextView tvNextClock;
    private Button btnClockNow;
    private Button[] dayButtons = new Button[7];

    private ClockSettings settings;
    private ClockScheduler scheduler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = ClockSettings.getInstance(this);
        scheduler = new ClockScheduler(this);

        initViews();
        initListeners();
        loadSettings();
        checkAccessibility();
        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccessibility();
        updateClockStatus();
    }

    private void initViews() {
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        btnEnableAccessibility = findViewById(R.id.btn_enable_accessibility);
        switchMorning = findViewById(R.id.switch_morning);
        switchEvening = findViewById(R.id.switch_evening);
        timeMorning = findViewById(R.id.time_morning);
        timeEvening = findViewById(R.id.time_evening);
        seekMorningAdvance = findViewById(R.id.seek_morning_advance);
        seekEveningDelay = findViewById(R.id.seek_evening_delay);
        tvMorningAdvance = findViewById(R.id.tv_morning_advance);
        tvEveningDelay = findViewById(R.id.tv_evening_delay);
        tvMorningStatus = findViewById(R.id.tv_morning_status);
        tvEveningStatus = findViewById(R.id.tv_evening_status);
        tvNextClock = findViewById(R.id.tv_next_clock);
        btnClockNow = findViewById(R.id.btn_clock_now);

        dayButtons[0] = findViewById(R.id.btn_day_1);
        dayButtons[1] = findViewById(R.id.btn_day_2);
        dayButtons[2] = findViewById(R.id.btn_day_3);
        dayButtons[3] = findViewById(R.id.btn_day_4);
        dayButtons[4] = findViewById(R.id.btn_day_5);
        dayButtons[5] = findViewById(R.id.btn_day_6);
        dayButtons[6] = findViewById(R.id.btn_day_7);

        // 24小时制
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timeMorning.setIs24HourView(true);
            timeEvening.setIs24HourView(true);
        }
    }

    private void initListeners() {
        // 无障碍服务按钮
        btnEnableAccessibility.setOnClickListener(v -> openAccessibilitySettings());

        // 上班开关
        switchMorning.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setMorningEnabled(isChecked);
            if (isChecked) scheduler.scheduleMorningClock();
            else scheduler.cancelMorningClock();
            updateClockStatus();
        });

        // 下班开关
        switchEvening.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setEveningEnabled(isChecked);
            if (isChecked) scheduler.scheduleEveningClock();
            else scheduler.cancelEveningClock();
            updateClockStatus();
        });

        // 上班时间
        timeMorning.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            settings.setMorningHour(hourOfDay);
            settings.setMorningMinute(minute);
            if (switchMorning.isChecked()) scheduler.scheduleMorningClock();
            updateClockStatus();
        });

        // 下班时间
        timeEvening.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            settings.setEveningHour(hourOfDay);
            settings.setEveningMinute(minute);
            if (switchEvening.isChecked()) scheduler.scheduleEveningClock();
            updateClockStatus();
        });

        // 上班提前
        seekMorningAdvance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvMorningAdvance.setText(progress == 0 ? "不提前" : "提前 " + progress + " 分钟");
                settings.setMorningAdvance(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (switchMorning.isChecked()) scheduler.scheduleMorningClock();
            }
        });

        // 下班延后
        seekEveningDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvEveningDelay.setText(progress == 0 ? "不延后" : "延后 " + progress + " 分钟");
                settings.setEveningDelay(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (switchEvening.isChecked()) scheduler.scheduleEveningClock();
            }
        });

        // 工作日按钮
        for (int i = 0; i < 7; i++) {
            final int day = i;
            dayButtons[i].setOnClickListener(v -> toggleDay(day));
        }

        // 选择工作日
        findViewById(R.id.btn_select_weekdays).setOnClickListener(v -> {
            settings.setWeekdays(0b00111110); // 周一到周五
            updateDayButtons();
            Toast.makeText(this, "已设为周一至周五", Toast.LENGTH_SHORT).show();
        });

        // 立即打卡
        btnClockNow.setOnClickListener(v -> {
            if (!AccessibilityHelper.isAccessibilityEnabled(this)) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show();
                openAccessibilitySettings();
                return;
            }
            ClockForegroundService.startForTestClock(this);
            Toast.makeText(this, "正在尝试打卡...", Toast.LENGTH_SHORT).show();
        });
    }

    private void toggleDay(int day) {
        int weekdays = settings.getWeekdays();
        int mask = 1 << day;
        if ((weekdays & mask) != 0) {
            weekdays &= ~mask;
        } else {
            weekdays |= mask;
        }
        settings.setWeekdays(weekdays);
        updateDayButtons();
    }

    private void updateDayButtons() {
        int weekdays = settings.getWeekdays();
        for (int i = 0; i < 7; i++) {
            int mask = 1 << i;
            boolean selected = (weekdays & mask) != 0;
            dayButtons[i].setSelected(selected);
            dayButtons[i].setBackgroundColor(selected
                    ? ContextCompat.getColor(this, R.color.accent)
                    : ContextCompat.getColor(this, R.color.bg_card));
            dayButtons[i].setTextColor(selected
                    ? ContextCompat.getColor(this, android.R.color.white)
                    : ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private void loadSettings() {
        switchMorning.setChecked(settings.isMorningEnabled());
        switchEvening.setChecked(settings.isEveningEnabled());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timeMorning.setHour(settings.getMorningHour());
            timeMorning.setMinute(settings.getMorningMinute());
            timeEvening.setHour(settings.getEveningHour());
            timeEvening.setMinute(settings.getEveningMinute());
        } else {
            timeMorning.setCurrentHour(settings.getMorningHour());
            timeMorning.setCurrentMinute(settings.getMorningMinute());
            timeEvening.setCurrentHour(settings.getEveningHour());
            timeEvening.setCurrentMinute(settings.getEveningMinute());
        }

        seekMorningAdvance.setProgress(settings.getMorningAdvance());
        seekEveningDelay.setProgress(settings.getEveningDelay());
        tvMorningAdvance.setText(settings.getMorningAdvance() == 0 ? "不提前" : "提前 " + settings.getMorningAdvance() + " 分钟");
        tvEveningDelay.setText(settings.getEveningDelay() == 0 ? "不延后" : "延后 " + settings.getEveningDelay() + " 分钟");

        updateDayButtons();
    }

    private void checkAccessibility() {
        boolean enabled = AccessibilityHelper.isAccessibilityEnabled(this);
        if (enabled) {
            tvAccessibilityStatus.setText("✅ 无障碍服务已开启");
            btnEnableAccessibility.setText("已开启");
            btnEnableAccessibility.setEnabled(false);
        } else {
            tvAccessibilityStatus.setText("⚠️ 无障碍服务未开启");
            btnEnableAccessibility.setText("去开启");
            btnEnableAccessibility.setEnabled(true);
        }
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }

        // 检查精确闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                new AlertDialog.Builder(this)
                        .setTitle("需要权限")
                        .setMessage("需要"精确闹钟"权限才能准确定时打卡，请前往设置开启。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        }

        // 忽略电池优化
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        }
    }

    private void updateClockStatus() {
        // 更新状态显示
        if (settings.isMorningEnabled()) {
            String time = String.format("%02d:%02d", settings.getMorningHour(), settings.getMorningMinute());
            tvMorningStatus.setText(time);
        } else {
            tvMorningStatus.setText("已关闭");
        }

        if (settings.isEveningEnabled()) {
            String time = String.format("%02d:%02d", settings.getEveningHour(), settings.getEveningMinute());
            tvEveningStatus.setText(time);
        } else {
            tvEveningStatus.setText("已关闭");
        }

        // 下次打卡
        String next = scheduler.getNextClockTime();
        tvNextClock.setText("下次打卡：" + next);
    }
}
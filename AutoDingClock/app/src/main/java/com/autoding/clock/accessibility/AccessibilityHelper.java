package com.autoding.clock.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * 无障碍服务辅助类
 */
public class AccessibilityHelper {

    /**
     * 检查无障碍服务是否已开启
     */
    public static boolean isAccessibilityEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC);

        for (AccessibilityServiceInfo info : services) {
            if (info.getId() != null &&
                    info.getId().contains("com.autoding.clock")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 打开无障碍设置页面
     */
    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * 获取当前状态描述
     */
    public static String getStatusDescription(Context context) {
        if (isAccessibilityEnabled(context)) {
            return "无障碍服务已开启";
        } else {
            return "无障碍服务未开启，自动打卡需要此权限";
        }
    }
}
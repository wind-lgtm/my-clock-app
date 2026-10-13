package com.autoding.clock.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 核心无障碍服务 - 通过模拟点击实现钉钉自动打卡
 *
 * 工作流程：
 * 1. 收到打卡指令后，打开钉钉APP
 * 2. 导航到工作台 -> 考勤打卡
 * 3. 找到并点击打卡按钮
 */
public class ClockAccessibilityService extends AccessibilityService {

    private static final String TAG = "ClockAccessibility";
    private static final String DINGTALK_PACKAGE = "com.alibaba.android.rimet";

    // 打卡状态
    private enum ClockState {
        IDLE,               // 空闲
        OPENING_DINGTALK,   // 正在打开钉钉
        ON_HOME_PAGE,       // 在钉钉首页
        FINDING_WORKBENCH,  // 寻找工作台
        ON_WORKBENCH,       // 在工作台页面
        FINDING_CLOCK,      // 寻找打卡入口
        ON_CLOCK_PAGE,      // 在打卡页面
        CLICKING_CLOCK,     // 正在点击打卡
        DONE                // 完成
    }

    private ClockState currentState = ClockState.IDLE;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isClockInProgress = false;
    private int retryCount = 0;
    private static final int MAX_RETRY = 3;
    private long clockStartTime = 0;
    private static final long CLOCK_TIMEOUT = 60000; // 60秒超时

    // 打卡回调
    private static ClockCallback callback;

    public interface ClockCallback {
        void onSuccess();
        void onFailed(String reason);
        void onProgress(String message);
    }

    public static void setCallback(ClockCallback cb) {
        callback = cb;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Accessibility Service Created");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        Log.d(TAG, "Event: " + event.getEventType() + " pkg=" + pkg + " state=" + currentState);

        // 只处理钉钉的事件
        if (!DINGTALK_PACKAGE.equals(pkg)) return;

        // 检查超时
        if (isClockInProgress && System.currentTimeMillis() - clockStartTime > CLOCK_TIMEOUT) {
            Log.w(TAG, "Clock timeout");
            finishClock(false, "打卡超时");
            return;
        }

        if (!isClockInProgress) return;

        // 根据当前状态处理
        switch (currentState) {
            case OPENING_DINGTALK:
                handleDingtalkOpened(event);
                break;
            case ON_HOME_PAGE:
                findAndClickWorkbench();
                break;
            case ON_WORKBENCH:
                findAndClickClockEntry();
                break;
            case ON_CLOCK_PAGE:
                findAndClickClockButton();
                break;
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted");
    }

    /**
     * 开始打卡流程
     */
    public void startClock() {
        Log.d(TAG, "startClock called, state=" + currentState);

        if (isClockInProgress) {
            Log.w(TAG, "Clock already in progress");
            if (callback != null) callback.onFailed("打卡正在进行中");
            return;
        }

        isClockInProgress = true;
        retryCount = 0;
        clockStartTime = System.currentTimeMillis();
        currentState = ClockState.OPENING_DINGTALK;

        if (callback != null) callback.onProgress("正在打开钉钉...");

        // 打开钉钉
        openDingTalk();

        // 设置超时检测
        handler.postDelayed(timeoutRunnable, CLOCK_TIMEOUT);
    }

    /**
     * 打卡完成
     */
    private void finishClock(boolean success, String message) {
        if (!isClockInProgress) return;

        isClockInProgress = false;
        handler.removeCallbacks(timeoutRunnable);
        currentState = ClockState.IDLE;

        Log.d(TAG, "Clock finished: success=" + success + " msg=" + message);

        if (callback != null) {
            if (success) callback.onSuccess();
            else callback.onFailed(message);
        }

        // 延迟回到桌面
        handler.postDelayed(() -> {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }, 2000);
    }

    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (isClockInProgress) {
                Log.w(TAG, "Timeout reached");
                finishClock(false, "打卡超时，请检查钉钉状态");
            }
        }
    };

    /**
     * 打开钉钉APP
     */
    private void openDingTalk() {
        Log.d(TAG, "Opening DingTalk...");
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(DINGTALK_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d(TAG, "DingTalk launch intent sent");
            } else {
                Log.e(TAG, "DingTalk not installed!");
                finishClock(false, "未检测到钉钉APP");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open DingTalk", e);
            finishClock(false, "打开钉钉失败: " + e.getMessage());
        }
    }

    /**
     * 钉钉已打开，等待页面加载
     */
    private void handleDingtalkOpened(AccessibilityEvent event) {
        Log.d(TAG, "DingTalk opened, waiting for page load... eventType=" + event.getEventType());

        // 延迟一下等待页面加载
        handler.postDelayed(() -> {
            if (currentState == ClockState.OPENING_DINGTALK) {
                currentState = ClockState.ON_HOME_PAGE;
                Log.d(TAG, "State -> ON_HOME_PAGE");
                if (callback != null) callback.onProgress("已进入钉钉，寻找工作台...");
                findAndClickWorkbench();
            }
        }, 2000);
    }

    /**
     * 寻找并点击"工作台"标签
     */
    private void findAndClickWorkbench() {
        Log.d(TAG, "Finding workbench...");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.w(TAG, "Root node is null");
            retryOrFail("无法获取页面内容");
            return;
        }

        // 尝试多种方式找到工作台
        // 方式1：通过文本查找
        List<AccessibilityNodeInfo> results = new ArrayList<>();
        findNodesByText(root, "工作台", results);

        if (!results.isEmpty()) {
            for (AccessibilityNodeInfo node : results) {
                if (clickNode(node)) {
                    Log.d(TAG, "Clicked workbench button");
                    currentState = ClockState.FINDING_WORKBENCH;
                    if (callback != null) callback.onProgress("已进入工作台，寻找打卡入口...");
                    handler.postDelayed(this::findAndClickClockEntry, 2500);
                    return;
                }
            }
        }

        // 方式2：通过描述查找底部Tab
        List<AccessibilityNodeInfo> tabs = new ArrayList<>();
        findNodesByDescription(root, "工作台", tabs);
        if (!tabs.isEmpty()) {
            for (AccessibilityNodeInfo node : tabs) {
                if (clickNode(node)) {
                    Log.d(TAG, "Clicked workbench tab by description");
                    currentState = ClockState.FINDING_WORKBENCH;
                    handler.postDelayed(this::findAndClickClockEntry, 2500);
                    return;
                }
            }
        }

        // 方式3：尝试点击底部Tab（通常在底部导航栏）
        if (clickBottomTab(root, "工作台")) {
            Log.d(TAG, "Clicked bottom tab: 工作台");
            currentState = ClockState.FINDING_WORKBENCH;
            handler.postDelayed(this::findAndClickClockEntry, 2500);
            return;
        }

        Log.w(TAG, "Workbench not found, retrying...");
        retryOrFail("找不到工作台入口");
    }

    /**
     * 寻找并点击考勤打卡入口
     */
    private void findAndClickClockEntry() {
        Log.d(TAG, "Finding clock entry...");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            retryOrFail("无法获取工作台页面内容");
            return;
        }

        // 查找"考勤打卡"入口
        List<AccessibilityNodeInfo> results = new ArrayList<>();
        findNodesByText(root, "考勤打卡", results);

        if (!results.isEmpty()) {
            for (AccessibilityNodeInfo node : results) {
                if (clickNode(node)) {
                    Log.d(TAG, "Clicked clock entry: 考勤打卡");
                    currentState = ClockState.ON_CLOCK_PAGE;
                    if (callback != null) callback.onProgress("已进入打卡页面，正在定位打卡按钮...");
                    handler.postDelayed(this::findAndClickClockButton, 3000);
                    return;
                }
            }
        }

        // 尝试"打卡"关键词
        results.clear();
        findNodesByText(root, "打卡", results);
        if (!results.isEmpty()) {
            for (AccessibilityNodeInfo node : results) {
                if (clickNode(node)) {
                    Log.d(TAG, "Clicked clock entry: 打卡");
                    currentState = ClockState.ON_CLOCK_PAGE;
                    handler.postDelayed(this::findAndClickClockButton, 3000);
                    return;
                }
            }
        }

        // 尝试通过滚动查找
        if (scrollAndFind(root, "考勤打卡")) {
            return;
        }

        Log.w(TAG, "Clock entry not found");
        retryOrFail("找不到考勤打卡入口");
    }

    /**
     * 寻找并点击打卡按钮（上班/下班）
     */
    private void findAndClickClockButton() {
        Log.d(TAG, "Finding clock button...");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            retryOrFail("无法获取打卡页面内容");
            return;
        }

        // 查找打卡按钮 - 通常是"上班打卡"或"下班打卡"的大按钮
        List<AccessibilityNodeInfo> results = new ArrayList<>();

        // 尝试多种可能的按钮文本
        String[] buttonTexts = {"上班打卡", "下班打卡", "签到", "打卡"};

        for (String text : buttonTexts) {
            results.clear();
            findNodesByText(root, text, results);
            if (!results.isEmpty()) {
                // 找到后延迟点击，模拟真实操作
                handler.postDelayed(() -> {
                    for (AccessibilityNodeInfo node : results) {
                        if (clickNode(node)) {
                            Log.d(TAG, "Clicked clock button: " + text);
                            currentState = ClockState.CLICKING_CLOCK;
                            if (callback != null) callback.onProgress("已点击" + text + "，请稍候...");
                            handler.postDelayed(() -> finishClock(true, "打卡成功"), 3000);
                            return;
                        }
                    }
                    retryOrFail("点击打卡按钮失败");
                }, 500);
                return;
            }
        }

        // 如果没找到文字按钮，尝试通过坐标点击（打卡按钮通常在屏幕中下方）
        if (tryClickByCoordinate(root)) {
            return;
        }

        Log.w(TAG, "Clock button not found");
        retryOrFail("找不到打卡按钮");
    }

    /**
     * 通过坐标尝试点击打卡按钮
     */
    private boolean tryClickByCoordinate(AccessibilityNodeInfo root) {
        // 获取屏幕中下方的圆形打卡区域
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);

        int centerX = bounds.width() / 2;
        int centerY = (int) (bounds.height() * 0.6); // 中下方

        Log.d(TAG, "Trying coordinate click at: " + centerX + ", " + centerY);

        Path path = new Path();
        path.moveTo(centerX, centerY);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 100);
        builder.addStroke(stroke);

        boolean result = dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "Gesture click completed");
                handler.postDelayed(() -> finishClock(true, "打卡完成"), 3000);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.w(TAG, "Gesture click cancelled");
                retryOrFail("坐标点击失败");
            }
        }, null);

        return result;
    }

    /**
     * 重试或失败
     */
    private void retryOrFail(String reason) {
        retryCount++;
        Log.w(TAG, "Retry " + retryCount + "/" + MAX_RETRY + ": " + reason);

        if (retryCount >= MAX_RETRY) {
            finishClock(false, reason);
            return;
        }

        // 重新尝试
        handler.postDelayed(() -> {
            if (currentState == ClockState.ON_HOME_PAGE) {
                findAndClickWorkbench();
            } else if (currentState == ClockState.ON_WORKBENCH) {
                findAndClickClockEntry();
            } else if (currentState == ClockState.ON_CLOCK_PAGE) {
                findAndClickClockButton();
            }
        }, 2000);
    }

    // ==================== 辅助方法 ====================

    /**
     * 递归查找包含指定文字的节点
     */
    private void findNodesByText(AccessibilityNodeInfo node, String text, List<AccessibilityNodeInfo> results) {
        if (node == null) return;

        CharSequence nodeText = node.getText();
        if (nodeText != null && nodeText.toString().contains(text)) {
            results.add(node);
        }

        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().contains(text)) {
            results.add(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            findNodesByText(node.getChild(i), text, results);
        }
    }

    /**
     * 递归查找描述匹配的节点
     */
    private void findNodesByDescription(AccessibilityNodeInfo node, String text, List<AccessibilityNodeInfo> results) {
        if (node == null) return;

        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().contains(text)) {
            results.add(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            findNodesByDescription(node.getChild(i), text, results);
        }
    }

    /**
     * 点击节点
     */
    private boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // 尝试直接点击
        if (node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

        // 向上查找可点击的父节点
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            parent = parent.getParent();
        }

        // 最后手段：通过坐标点击
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) return false;

        return clickAt(bounds.centerX(), bounds.centerY());
    }

    /**
     * 点击底部Tab
     */
    private boolean clickBottomTab(AccessibilityNodeInfo root, String tabName) {
        // 底部Tab通常在页面底部的LinearLayout中
        List<AccessibilityNodeInfo> tabLists = new ArrayList<>();
        findBottomNavigation(root, tabLists);

        for (AccessibilityNodeInfo tabList : tabLists) {
            for (int i = 0; i < tabList.getChildCount(); i++) {
                AccessibilityNodeInfo child = tabList.getChild(i);
                if (child != null) {
                    CharSequence text = child.getText();
                    CharSequence desc = child.getContentDescription();
                    if ((text != null && text.toString().contains(tabName)) ||
                        (desc != null && desc.toString().contains(tabName))) {
                        return clickNode(child);
                    }
                }
            }
        }
        return false;
    }

    /**
     * 查找底部导航栏
     */
    private void findBottomNavigation(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        // 底部导航通常在屏幕下方1/4区域
        // 通过资源ID或类名判断
        CharSequence id = node.getViewIdResourceName();
        CharSequence cls = node.getClassName();

        if (id != null && (id.toString().contains("tab") || id.toString().contains("bottom"))) {
            results.add(node);
        }
        if (cls != null && cls.toString().contains("TabWidget")) {
            results.add(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            findBottomNavigation(node.getChild(i), results);
        }
    }

    /**
     * 滚动查找
     */
    private boolean scrollAndFind(AccessibilityNodeInfo root, String text) {
        // 尝试在可滚动的容器中向下滚动
        List<AccessibilityNodeInfo> scrollables = new ArrayList<>();
        findScrollableNodes(root, scrollables);

        for (AccessibilityNodeInfo scrollable : scrollables) {
            if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                handler.postDelayed(() -> {
                    List<AccessibilityNodeInfo> results = new ArrayList<>();
                    AccessibilityNodeInfo newRoot = getRootInActiveWindow();
                    if (newRoot != null) {
                        findNodesByText(newRoot, text, results);
                        if (!results.isEmpty()) {
                            for (AccessibilityNodeInfo node : results) {
                                if (clickNode(node)) {
                                    Log.d(TAG, "Found and clicked after scroll: " + text);
                                    currentState = ClockState.ON_CLOCK_PAGE;
                                    handler.postDelayed(this::findAndClickClockButton, 3000);
                                    return;
                                }
                            }
                        }
                    }
                    retryOrFail("滚动后仍未找到: " + text);
                }, 1000);
                return true;
            }
        }
        return false;
    }

    /**
     * 查找可滚动节点
     */
    private void findScrollableNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;

        if (node.isScrollable()) {
            results.add(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            findScrollableNodes(node.getChild(i), results);
        }
    }

    /**
     * 通过坐标点击
     */
    private boolean clickAt(int x, int y) {
        Log.d(TAG, "Clicking at (" + x + ", " + y + ")");

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
        builder.addStroke(stroke);

        return dispatchGesture(builder.build(), null, null);
    }

    /**
     * 获取当前状态（供外部查询）
     */
    public boolean isClockInProgress() {
        return isClockInProgress;
    }
}
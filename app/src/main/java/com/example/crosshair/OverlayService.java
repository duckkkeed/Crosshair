package com.example.crosshair;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

/**
 * 悬浮准心服务。
 *
 * <p>使用系统悬浮窗（{@link WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY}）把准心
 * 叠加绘制在屏幕正中心，窗口不可触摸（点击穿透），因此不需要对目标游戏/应用做任何修改。</p>
 */
public class OverlayService extends Service {

    public static final String ACTION_START = "com.example.crosshair.action.START";
    public static final String ACTION_STOP = "com.example.crosshair.action.STOP";

    private static final String CHANNEL_ID = "crosshair_overlay";
    private static final int NOTIFICATION_ID = 1001;

    private static OverlayService instance;

    private WindowManager windowManager;
    private CrosshairView crosshairView;
    private WindowManager.LayoutParams layoutParams;

    /** 准心是否正在显示。 */
    public static boolean isRunning() {
        return instance != null;
    }

    /** 设置变化后立即刷新正在显示的准心。 */
    public static void applySettings(Context context) {
        OverlayService service = instance;
        if (service != null && service.crosshairView != null) {
            service.crosshairView.setAppearance(
                    CrosshairPrefs.getShape(context),
                    dpToPx(context, CrosshairPrefs.getSizeDp(context)),
                    CrosshairPrefs.getColor(context) & 0xFFFFFF,
                    CrosshairPrefs.getAlpha(context),
                    CrosshairPrefs.isOutline(context));
        }
    }

    private static float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
        startForegroundSafely();
        addOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (crosshairView == null) {
            addOverlay();
        }
        applySettings(this);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        instance = null;
        super.onDestroy();
    }

    private void addOverlay() {
        if (crosshairView != null) {
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        crosshairView = new CrosshairView(this);

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.setTitle("Crosshair");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 允许窗口延伸到刘海/挖孔区域，保证几何中心就是屏幕正中心。
            layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        applySettings(this);

        try {
            windowManager.addView(crosshairView, layoutParams);
        } catch (Throwable t) {
            // 典型情况：悬浮窗权限被撤销。
            crosshairView = null;
            CrosshairPrefs.setEnabled(this, false);
            stopSelf();
        }
    }

    private void removeOverlay() {
        if (windowManager != null && crosshairView != null) {
            try {
                windowManager.removeView(crosshairView);
            } catch (Throwable ignored) {
                // 窗口可能已经被系统移除。
            }
        }
        crosshairView = null;
        windowManager = null;
    }

    private void startForegroundSafely() {
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Throwable ignored) {
            // 极少数机型会拒绝前台通知，此时仍然尝试显示悬浮窗。
        }
    }

    private void createChannel() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notif_channel_desc));
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_crosshair)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(contentIntent)
                .addAction(0, getString(R.string.notif_stop), stopPendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }
}

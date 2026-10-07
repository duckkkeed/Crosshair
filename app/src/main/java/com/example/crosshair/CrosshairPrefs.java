package com.example.crosshair;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 准心设置项（形状 / 大小 / 颜色 / 透明度 / 描边 / 开关状态）的持久化。
 */
public final class CrosshairPrefs {

    private static final String FILE = "crosshair_settings";
    private static final String KEY_SHAPE = "shape";
    private static final String KEY_SIZE_DP = "size_dp";
    private static final String KEY_COLOR = "color";
    private static final String KEY_ALPHA = "alpha";
    private static final String KEY_OUTLINE = "outline";
    private static final String KEY_ENABLED = "enabled";

    public static final int DEFAULT_SHAPE = CrosshairView.SHAPE_DOT;
    public static final float DEFAULT_SIZE_DP = 6f;
    public static final int DEFAULT_COLOR = 0xFF3B30;
    public static final int DEFAULT_ALPHA = 255;
    public static final boolean DEFAULT_OUTLINE = true;

    /** 大小可调范围（dp）。 */
    public static final float MIN_SIZE_DP = 1f;
    public static final float MAX_SIZE_DP = 48f;
    /** 透明度可调范围（百分比）。 */
    public static final int MIN_ALPHA_PERCENT = 5;
    public static final int MAX_ALPHA_PERCENT = 100;

    private CrosshairPrefs() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int getShape(Context context) {
        return sp(context).getInt(KEY_SHAPE, DEFAULT_SHAPE);
    }

    public static void setShape(Context context, int shape) {
        sp(context).edit().putInt(KEY_SHAPE, shape).apply();
    }

    public static float getSizeDp(Context context) {
        return sp(context).getFloat(KEY_SIZE_DP, DEFAULT_SIZE_DP);
    }

    public static void setSizeDp(Context context, float sizeDp) {
        sp(context).edit().putFloat(KEY_SIZE_DP, sizeDp).apply();
    }

    public static int getColor(Context context) {
        return sp(context).getInt(KEY_COLOR, DEFAULT_COLOR);
    }

    public static void setColor(Context context, int rgb) {
        sp(context).edit().putInt(KEY_COLOR, rgb & 0xFFFFFF).apply();
    }

    /** @return 透明度 0..255 */
    public static int getAlpha(Context context) {
        return sp(context).getInt(KEY_ALPHA, DEFAULT_ALPHA);
    }

    /** @param alpha 透明度 0..255 */
    public static void setAlpha(Context context, int alpha) {
        sp(context).edit().putInt(KEY_ALPHA, Math.max(0, Math.min(255, alpha))).apply();
    }

    public static boolean isOutline(Context context) {
        return sp(context).getBoolean(KEY_OUTLINE, DEFAULT_OUTLINE);
    }

    public static void setOutline(Context context, boolean outline) {
        sp(context).edit().putBoolean(KEY_OUTLINE, outline).apply();
    }

    /** 用户是否希望显示准心（用于进程重启后自动恢复）。 */
    public static boolean isEnabled(Context context) {
        return sp(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        sp(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static void reset(Context context) {
        sp(context).edit().clear().apply();
    }

    /** 百分比 -> 0..255。 */
    public static int percentToAlpha(int percent) {
        int p = Math.max(MIN_ALPHA_PERCENT, Math.min(MAX_ALPHA_PERCENT, percent));
        return Math.round(p * 255f / 100f);
    }

    /** 0..255 -> 百分比。 */
    public static int alphaToPercent(int alpha) {
        int p = Math.round(alpha * 100f / 255f);
        return Math.max(MIN_ALPHA_PERCENT, Math.min(MAX_ALPHA_PERCENT, p));
    }
}

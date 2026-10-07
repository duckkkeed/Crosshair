package com.example.crosshair;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * HSV 色盘：角度 = 色相（H），半径 = 饱和度（S），明度（V）由外部滑杆控制。
 *
 * <p>拖动色盘只改变色相和饱和度，明度沿用当前值，因此不会出现「拖到一半颜色变黑」的跳变。</p>
 */
public class ColorWheelView extends View {

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    private static final int[] HUE_COLORS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
            0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };

    private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** hsv[0]=色相 0..360, hsv[1]=饱和度 0..1, hsv[2]=明度 0..1 */
    private final float[] hsv = {0f, 1f, 1f};

    private float centerX;
    private float centerY;
    private float radius;

    private OnColorChangedListener listener;
    /** 手指按下期间不接受外部同步，避免拖动时指针抖动。 */
    private boolean tracking;

    public ColorWheelView(Context context) {
        this(context, null);
    }

    public ColorWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        indicatorPaint.setStyle(Paint.Style.STROKE);
        indicatorPaint.setStrokeWidth(dp(2.5f));
        dimPaint.setColor(Color.BLACK);
        Color.colorToHSV(CrosshairPrefs.DEFAULT_COLOR, hsv);
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    /** 当前明度 0..1。 */
    public float getValue() {
        return hsv[2];
    }

    /** 外部「明度」滑杆调整明度。 */
    public void setValue(float value) {
        hsv[2] = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    /** 当前色盘对应的颜色（不透明）。 */
    public int getColor() {
        return Color.HSVToColor(hsv);
    }

    /** 由色块 / RGB 滑杆 / 手输颜色代码改色时，同步指针位置。 */
    public void setColor(int rgb) {
        if (tracking) {
            return;
        }
        Color.colorToHSV(rgb & 0xFFFFFF, hsv);
        invalidate();
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.max(0f, Math.min(w, h) / 2f - dp(12f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0f) {
            return;
        }

        Shader sweep = new SweepGradient(centerX, centerY, HUE_COLORS, null);
        Shader radial = new RadialGradient(centerX, centerY, radius,
                Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP);
        wheelPaint.setShader(new ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER));
        canvas.drawCircle(centerX, centerY, radius, wheelPaint);
        wheelPaint.setShader(null);

        // 明度越低，色盘整体越暗，所见即所得。
        if (hsv[2] < 1f) {
            dimPaint.setAlpha(Math.round((1f - hsv[2]) * 200f));
            canvas.drawCircle(centerX, centerY, radius, dimPaint);
        }

        double angle = Math.toRadians(hsv[0]);
        float px = centerX + (float) Math.cos(angle) * hsv[1] * radius;
        float py = centerY + (float) Math.sin(angle) * hsv[1] * radius;
        indicatorPaint.setColor(0x66000000);
        canvas.drawCircle(px, py, dp(9f), indicatorPaint);
        indicatorPaint.setColor(Color.WHITE);
        canvas.drawCircle(px, py, dp(7f), indicatorPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                updateFromTouch(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                tracking = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateFromTouch(float x, float y) {
        if (radius <= 0f) {
            return;
        }
        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.hypot(dx, dy);
        float hue = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (hue < 0f) {
            hue += 360f;
        }
        hsv[0] = hue;
        hsv[1] = Math.min(1f, distance / radius);
        invalidate();
        if (listener != null) {
            listener.onColorChanged(Color.HSVToColor(hsv));
        }
    }
}

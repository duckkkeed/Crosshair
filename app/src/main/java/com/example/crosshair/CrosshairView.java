package com.example.crosshair;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 准心绘制视图。
 *
 * <p>无论窗口多大，准心永远画在自身正中心；当该 View 铺满屏幕（悬浮窗）时，
 * 准心就落在屏幕正中心。</p>
 */
public class CrosshairView extends View {

    /** 实心圆点。 */
    public static final int SHAPE_DOT = 0;
    /** 空心圆环。 */
    public static final int SHAPE_RING = 1;
    /** 十字准星。 */
    public static final int SHAPE_CROSS = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int shape = SHAPE_DOT;
    /** 圆点/圆环半径，十字的半长（像素）。 */
    private float sizePx;
    /** 不含透明度的 RGB 颜色。 */
    private int color = Color.RED;
    /** 0..255。 */
    private int alpha = 255;
    private boolean outline;

    public CrosshairView(Context context) {
        this(context, null);
    }

    public CrosshairView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (sizePx <= 0f) {
            sizePx = dp(6f);
        }
    }

    /**
     * 更新准心外观。
     *
     * @param shape   {@link #SHAPE_DOT} / {@link #SHAPE_RING} / {@link #SHAPE_CROSS}
     * @param sizePx  圆点半径（像素）
     * @param color   不含透明度的 RGB 颜色
     * @param alpha   透明度 0..255
     * @param outline 是否加深色描边
     */
    public void setAppearance(int shape, float sizePx, int color, int alpha, boolean outline) {
        this.shape = shape;
        this.sizePx = Math.max(0.5f, sizePx);
        this.color = color & 0xFFFFFF;
        this.alpha = Math.max(0, Math.min(255, alpha));
        this.outline = outline;
        invalidate();
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float cx = getWidth() / 2f;
        final float cy = getHeight() / 2f;
        final float base = sizePx;
        final float thickness = Math.max(dp(1.6f), base * 0.42f);

        if (outline) {
            // 深色描边：先画一圈更大的深色形状，再把亮色形状盖在上面。
            final float rim = Math.max(dp(1.2f), base * 0.22f);
            paint.setColor(Color.BLACK);
            paint.setAlpha(Math.round(alpha * 0.8f));
            drawShape(canvas, cx, cy, base + rim, thickness + rim * 2f);
        }

        paint.setColor(color);
        paint.setAlpha(alpha);
        drawShape(canvas, cx, cy, base, thickness);
    }

    private void drawShape(Canvas canvas, float cx, float cy, float half, float thickness) {
        switch (shape) {
            case SHAPE_RING:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(thickness);
                canvas.drawCircle(cx, cy, half, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            case SHAPE_CROSS:
                final float h = thickness / 2f;
                rect.set(cx - half, cy - h, cx + half, cy + h);
                canvas.drawRoundRect(rect, h, h, paint);
                rect.set(cx - h, cy - half, cx + h, cy + half);
                canvas.drawRoundRect(rect, h, h, paint);
                break;
            case SHAPE_DOT:
            default:
                canvas.drawCircle(cx, cy, half, paint);
                break;
        }
    }
}

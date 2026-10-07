package com.example.crosshair;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.slider.Slider;

import java.util.Locale;

/**
 * 设置界面：开启/关闭悬浮准心，并实时调节形状、大小、颜色、透明度。
 */
public class MainActivity extends AppCompatActivity {

    private static final int[] PRESET_COLORS = {
            0xFF3B30, // 红
            0xFF9500, // 橙
            0xFFEB3B, // 黄
            0x00E676, // 绿
            0x00E5FF, // 青
            0x2979FF, // 蓝
            0xB388FF, // 紫
            0xFF4081, // 粉
            0xFFFFFF, // 白
    };

    private View statusDot;
    private TextView statusText;
    private Button toggleButton;
    private CrosshairView preview;
    private RadioGroup shapeGroup;
    private Slider sizeSlider;
    private Slider alphaSlider;
    private Slider redSlider;
    private Slider greenSlider;
    private Slider blueSlider;
    private Slider brightnessSlider;
    private ColorWheelView colorWheel;
    private EditText hexInput;
    private TextView sizeLabel;
    private TextView alphaLabel;
    private TextView brightnessLabel;
    private TextView hexText;
    private View colorPreview;
    private LinearLayout swatchRow;
    private SwitchCompat outlineSwitch;
    private Button resetButton;

    /** 代码回填控件状态时，抑制回调。 */
    private boolean syncing;
    /** 因缺少权限跳到设置页后，返回时是否自动开启。 */
    private boolean pendingStart;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                boolean wantStart = pendingStart;
                pendingStart = false;
                if (wantStart && Settings.canDrawOverlays(this)) {
                    startOverlay();
                }
                updateStatusUi();
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // 通知权限被拒绝也照常显示准心，只是看不到常驻通知。
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setUpEdgeToEdge();
        bindViews();
        buildSwatches();
        bindListeners();
        loadFromPrefs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 进程被杀或服务被系统回收后，只要用户之前开着准心，就自动恢复。
        if (!OverlayService.isRunning() && CrosshairPrefs.isEnabled(this)
                && Settings.canDrawOverlays(this)) {
            ContextCompat.startForegroundService(this,
                    new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_START));
        }
        updateStatusUi();
    }

    /**
     * targetSdk 35+ 会强制边到边显示（statusBarColor 失效），
     * 这里按 WindowInsets 给根布局加内边距，保证内容不会被状态栏 / 标题栏遮挡。
     */
    private void setUpEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        final View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);

        ViewCompat.requestApplyInsets(root);
    }

    private void bindViews() {
        statusDot = findViewById(R.id.status_dot);
        statusText = findViewById(R.id.status_text);
        toggleButton = findViewById(R.id.btn_toggle);
        preview = findViewById(R.id.preview);
        shapeGroup = findViewById(R.id.shape_group);
        sizeSlider = findViewById(R.id.slider_size);
        alphaSlider = findViewById(R.id.slider_alpha);
        redSlider = findViewById(R.id.slider_red);
        greenSlider = findViewById(R.id.slider_green);
        blueSlider = findViewById(R.id.slider_blue);
        brightnessSlider = findViewById(R.id.slider_brightness);
        colorWheel = findViewById(R.id.color_wheel);
        hexInput = findViewById(R.id.input_hex);
        sizeLabel = findViewById(R.id.size_label);
        alphaLabel = findViewById(R.id.alpha_label);
        brightnessLabel = findViewById(R.id.brightness_label);
        hexText = findViewById(R.id.color_hex);
        colorPreview = findViewById(R.id.color_preview);
        swatchRow = findViewById(R.id.swatch_row);
        outlineSwitch = findViewById(R.id.switch_outline);
        resetButton = findViewById(R.id.btn_reset);
    }

    private void bindListeners() {
        toggleButton.setOnClickListener(v -> toggleOverlay());

        shapeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (syncing) {
                return;
            }
            int shape = checkedId == R.id.shape_ring ? CrosshairView.SHAPE_RING
                    : checkedId == R.id.shape_cross ? CrosshairView.SHAPE_CROSS
                    : CrosshairView.SHAPE_DOT;
            CrosshairPrefs.setShape(this, shape);
            applyToViews();
        });

        sizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (syncing) {
                return;
            }
            CrosshairPrefs.setSizeDp(this, Math.round(value));
            applyToViews();
        });

        alphaSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (syncing) {
                return;
            }
            CrosshairPrefs.setAlpha(this, CrosshairPrefs.percentToAlpha(Math.round(value)));
            applyToViews();
        });

        Slider.OnChangeListener rgbListener = (slider, value, fromUser) -> {
            if (syncing) {
                return;
            }
            setColor(Color.rgb((int) redSlider.getValue(),
                    (int) greenSlider.getValue(),
                    (int) blueSlider.getValue()));
        };
        redSlider.addOnChangeListener(rgbListener);
        greenSlider.addOnChangeListener(rgbListener);
        blueSlider.addOnChangeListener(rgbListener);

        // 色盘：拖动只改色相 / 饱和度，明度沿用下面的滑杆。
        colorWheel.setOnColorChangedListener(this::setColor);

        brightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (syncing) {
                return;
            }
            colorWheel.setValue(value / 100f);
            setColor(colorWheel.getColor());
        });

        findViewById(R.id.btn_apply_hex).setOnClickListener(v -> applyHexInput());
        hexInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyHexInput();
                return true;
            }
            return false;
        });

        outlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncing) {
                return;
            }
            CrosshairPrefs.setOutline(this, isChecked);
            applyToViews();
        });

        resetButton.setOnClickListener(v -> {
            CrosshairPrefs.reset(this);
            hexInput.setText("");
            hexInput.clearFocus();
            loadFromPrefs();
        });
    }

    private void buildSwatches() {
        swatchRow.removeAllViews();
        int size = Math.round(dp(30f));
        int margin = Math.round(dp(4f));
        for (int color : PRESET_COLORS) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(lp);
            swatch.setTag(color);
            swatch.setContentDescription(String.format(Locale.US, "#%06X", color));
            swatch.setBackground(ovalDrawable(color, dp(1f), 0x33FFFFFF));
            swatch.setOnClickListener(v -> {
                Object tag = v.getTag();
                if (tag instanceof Integer) {
                    setColor((Integer) tag);
                }
            });
            swatchRow.addView(swatch);
        }
    }

    private void loadFromPrefs() {
        syncing = true;

        int shape = CrosshairPrefs.getShape(this);
        shapeGroup.check(shape == CrosshairView.SHAPE_RING ? R.id.shape_ring
                : shape == CrosshairView.SHAPE_CROSS ? R.id.shape_cross
                : R.id.shape_dot);

        sizeSlider.setValue(clamp(Math.round(CrosshairPrefs.getSizeDp(this)),
                CrosshairPrefs.MIN_SIZE_DP, CrosshairPrefs.MAX_SIZE_DP));
        alphaSlider.setValue(CrosshairPrefs.alphaToPercent(CrosshairPrefs.getAlpha(this)));

        int rgb = CrosshairPrefs.getColor(this) & 0xFFFFFF;
        redSlider.setValue(Color.red(rgb));
        greenSlider.setValue(Color.green(rgb));
        blueSlider.setValue(Color.blue(rgb));

        outlineSwitch.setChecked(CrosshairPrefs.isOutline(this));

        syncing = false;
        applyToViews();
    }

    /** 把当前设置同步到预览区、色盘和悬浮窗。 */
    private void applyToViews() {
        int shape = CrosshairPrefs.getShape(this);
        float sizeDp = CrosshairPrefs.getSizeDp(this);
        int rgb = CrosshairPrefs.getColor(this) & 0xFFFFFF;
        int alpha = CrosshairPrefs.getAlpha(this);
        boolean outline = CrosshairPrefs.isOutline(this);

        preview.setAppearance(shape, dp(sizeDp), rgb, alpha, outline);

        sizeLabel.setText(getString(R.string.section_size, Math.round(sizeDp)));
        alphaLabel.setText(getString(R.string.section_alpha,
                CrosshairPrefs.alphaToPercent(alpha)));
        brightnessLabel.setText(getString(R.string.section_brightness, brightnessPercent(rgb)));
        hexText.setText(String.format(Locale.US, "#%06X", rgb));
        colorPreview.setBackground(ovalDrawable(rgb, dp(1.5f), 0x33FFFFFF));
        updateSwatchSelection(rgb);

        colorWheel.setColor(rgb);

        syncing = true;
        brightnessSlider.setValue(brightnessPercent(rgb));
        syncing = false;

        OverlayService.applySettings(this);
    }

    private void setColor(int rgb) {
        rgb &= 0xFFFFFF;
        CrosshairPrefs.setColor(this, rgb);

        syncing = true;
        redSlider.setValue(Color.red(rgb));
        greenSlider.setValue(Color.green(rgb));
        blueSlider.setValue(Color.blue(rgb));
        syncing = false;

        applyToViews();
    }

    /** 解析手动输入的颜色代码（支持 #RRGGBB / RRGGBB / #RGB / RGB）。 */
    private void applyHexInput() {
        String raw = hexInput.getText().toString().trim();
        if (raw.startsWith("#")) {
            raw = raw.substring(1);
        }

        if (raw.length() == 3 && raw.matches("(?i)[0-9a-f]{3}")) {
            StringBuilder expanded = new StringBuilder(6);
            for (int i = 0; i < raw.length(); i++) {
                expanded.append(raw.charAt(i)).append(raw.charAt(i));
            }
            raw = expanded.toString();
        }

        if (!raw.matches("(?i)[0-9a-f]{6}")) {
            Toast.makeText(this, R.string.toast_bad_color, Toast.LENGTH_SHORT).show();
            hexInput.setText(String.format(Locale.US, "#%06X",
                    CrosshairPrefs.getColor(this) & 0xFFFFFF));
            hexInput.setSelection(hexInput.getText().length());
            return;
        }

        setColor((int) Long.parseLong(raw, 16));
        hexInput.setText(String.format(Locale.US, "#%06X",
                CrosshairPrefs.getColor(this) & 0xFFFFFF));
        hexInput.clearFocus();
        hideKeyboard();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(hexInput.getWindowToken(), 0);
        }
    }

    private void updateSwatchSelection(int selectedRgb) {
        for (int i = 0; i < swatchRow.getChildCount(); i++) {
            View swatch = swatchRow.getChildAt(i);
            Object tag = swatch.getTag();
            if (!(tag instanceof Integer)) {
                continue;
            }
            int color = (Integer) tag;
            boolean selected = color == selectedRgb;
            swatch.setBackground(ovalDrawable(
                    color,
                    selected ? dp(3f) : dp(1f),
                    selected ? 0xFFFFFFFF : 0x33FFFFFF));
        }
    }

    /**
     * 生成圆形色块。
     *
     * <p>注意：颜色统一按不透明处理（补上 0xFF）。否则 0x00RRGGBB 会被当成完全透明，
     * 色块和颜色预览就会"看不见颜色"。</p>
     */
    private GradientDrawable ovalDrawable(int color, float strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0xFF000000 | (color & 0xFFFFFF));
        if (strokeWidth > 0f) {
            drawable.setStroke(Math.round(strokeWidth), strokeColor);
        }
        return drawable;
    }

    private static int brightnessPercent(int rgb) {
        float[] hsv = new float[3];
        Color.colorToHSV(rgb & 0xFFFFFF, hsv);
        return Math.round(hsv[2] * 100f);
    }

    private void updateStatusUi() {
        boolean allowed = Settings.canDrawOverlays(this);
        boolean running = OverlayService.isRunning();

        if (!allowed) {
            statusText.setText(R.string.status_no_permission);
        } else if (running) {
            statusText.setText(R.string.status_running);
        } else {
            statusText.setText(R.string.status_idle);
        }

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(running ? 0xFF34C759 : (allowed ? 0xFF8E8E93 : 0xFFFF9500));
        statusDot.setBackground(dot);

        toggleButton.setText(running ? R.string.btn_hide : R.string.btn_show);
    }

    private void toggleOverlay() {
        if (OverlayService.isRunning()) {
            stopOverlay();
        } else {
            startOverlay();
        }
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true;
            Toast.makeText(this, R.string.toast_need_permission, Toast.LENGTH_LONG).show();
            Uri uri = Uri.parse("package:" + getPackageName());
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri);
            try {
                overlayPermissionLauncher.launch(intent);
            } catch (Exception e) {
                overlayPermissionLauncher.launch(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
            return;
        }

        requestNotificationPermission();
        CrosshairPrefs.setEnabled(this, true);
        ContextCompat.startForegroundService(this,
                new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_START));
        toggleButton.postDelayed(this::updateStatusUi, 300);
    }

    private void stopOverlay() {
        CrosshairPrefs.setEnabled(this, false);
        stopService(new Intent(this, OverlayService.class));
        toggleButton.postDelayed(this::updateStatusUi, 300);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

# 准心悬浮窗（Crosshair）

一个**纯叠加**的屏幕准心 App：准心固定在**屏幕正中心**，可以调节**圆点大小、颜色、透明度**（另附形状与描边），
**不修改、不注入、不读取**任何目标游戏或目标 App——只是一个系统级悬浮窗。

## 功能

| 功能 | 说明 |
| --- | --- |
| 位置 | 始终固定于屏幕正中心，横竖屏切换、刘海/挖孔屏都保持几何居中 |
| 大小 | 1–48 dp 无级调节（圆点直径 / 圆环半径 / 十字臂长） |
| 颜色 | 9 个预设色块 + **HSV 色盘**（色相/饱和度）+ R / G / B 滑杆 + **手动输入颜色代码**，四种方式互通 |
| 明度 | 色盘配套的明度滑杆 0–100%，色盘会随明度整体变暗，所见即所得 |
| 透明度 | 5%–100% 无级调节 |
| 附加 | 形状（圆点 / 圆环 / 十字）、深色描边（浅色背景也看得清） |
| 交互 | 准心窗口 **触控穿透**，点按、滑动全部落到游戏/应用上，不挡操作 |
| 预览 | 设置页内置实时预览（深浅渐变背景，直接看效果） |
| 常驻 | 前台服务 + 常驻通知，退出设置页后准心继续显示；通知里可一键关闭 |
| 记忆 | 设置写入 SharedPreferences；进程被回收后再次打开 App 自动恢复显示 |

## 构建

环境：Android Studio（AGP 9.3.3 / Gradle 9.5 / JDK 17+），`minSdk 26`、`targetSdk 36`。

```bash
# 命令行构建（Windows）
gradlew.bat :app:assembleDebug

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开本目录，点 Run 安装到手机。

已验证：`:app:assembleDebug` **BUILD SUCCESSFUL**（33 tasks）。

## 使用

1. 安装并打开 **准心悬浮窗**。
2. 点「开启悬浮准心」→ 首次会跳到系统设置，打开 **允许显示在其他应用上层 / 悬浮窗** 开关。
3. 返回 App，准心立即出现在屏幕正中心；此时可直接拖动滑杆、点选颜色，**所见即所得**。
4. 想关掉：App 内点「关闭悬浮准心」，或下拉通知栏点通知上的「关闭」。

## 实现原理

- `OverlayService`：前台服务，通过 `WindowManager.addView()` 添加
  `TYPE_APPLICATION_OVERLAY` 窗口，标志位 `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE |
  FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`，像素格式 `TRANSLUCENT`。
  - `NOT_TOUCHABLE` = 事件穿透，因此不会影响游戏操作；
  - `LAYOUT_IN_SCREEN + LAYOUT_NO_LIMITS` = 窗口铺满整块屏幕（含状态栏/挖孔区），
    于是「视图中心」== 「屏幕中心」；
  - 只在系统合成层叠加显示，**不涉及对目标 App 的任何 Hook、注入或内存读写**。
- `CrosshairView`：自绘 `View`，`onDraw` 里以 `getWidth()/2, getHeight()/2` 为圆心绘制，
  颜色与透明度在 `Paint.setColor/setAlpha` 中生效，形状/大小实时重绘。
- `CrosshairPrefs`：设置持久化 + 透明度百分比换算。
- `MainActivity`：设置界面，任何改动会同时刷新预览、色盘和正在显示的悬浮窗
  （`OverlayService.applySettings()`），无需重启服务。
- `ColorWheelView`：HSV 色盘，`SweepGradient`（色相）+ `RadialGradient`（饱和度）用
  `ComposeShader` 合成；明度用一层黑色遮罩体现。触摸时 `requestDisallowInterceptTouchEvent`
  防止被 ScrollView 抢走手势。
- 界面适配：`targetSdk 35+` 强制边到边显示，`android:statusBarColor` 已失效，
  因此主题改为 `NoActionBar` + 自绘标题栏，并在代码里消费 `WindowInsets` 给根布局加内边距，
  避免内容被状态栏/标题栏遮挡。

## 更新记录

- 修复：`targetSdk 36` 强制边到边导致顶部状态文字被状态栏/标题栏遮挡 → 自绘标题栏 + WindowInsets。
- 修复：色块与颜色预览不显示颜色 → 颜色值被 `& 0xFFFFFF` 掩码后 alpha 变成 0（全透明），
  现统一补 `0xFF` 不透明通道。
- 新增：HSV 色盘 + 明度滑杆。
- 新增：手动输入颜色代码（支持 `#RRGGBB` / `RRGGBB` / `#RGB` 简写，非法输入给出提示）。

## 常见问题

- **准心不显示？** 检查「显示在其他应用上层」权限是否授予；部分系统（MIUI/EMUI/ColorOS）
  还需额外的「后台弹出界面 / 悬浮窗」开关。
- **某些应用里看不到？** 少数应用（视频播放器、银行类、部分游戏）会主动隐藏悬浮窗，属系统/应用行为。
- **被杀后台？** 已用前台服务保活；若被厂商省电策略清掉，把本应用加入电池白名单即可。
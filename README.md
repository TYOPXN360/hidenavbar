# NavHide - 小白条沉浸 Xposed 模块

> ⚠️ **本模块完全由 AI 创建**，代码质量未经人工审核，请自行评估风险。

## 功能

| 功能 | 说明 |
|------|------|
| 🔲 **强制小白条沉浸** | 拉伸应用 edge-to-edge，让内容延伸到导航栏区域 |
| ⌨️ **删除键盘底部按钮** | 隐藏输入法底部的 IME 切换按钮 |
| 📐 **删除键盘底部抬起空间** | 消除键盘底部的导航栏高度，让键盘贴底 |

## 适用设备

- **Android 17 QPR 1 Beta/Canary** 设备
- 需要 **LSPosed / libxposed** 框架支持

## 技术栈

- **libxposed API 102** — 现代 Xposed 模块 API
- 理论支持 **热重载 (Hot Reload)** — 尚未详细测试，请确认自身框架支持

## 安装

1. 下载最新 Release 的 APK
2. 安装到设备
3. 在 LSPosed 管理器中启用模块
4. 设置作用域（根据自身设备勾选）：
   - `system` — system_server（导航栏沉浸 + 键盘按钮删除）
   - **你使用的输入法** — 例如：
     - `com.bytedance.android.doubaoime` — 豆包输入法
     - `com.sohu.inputmethod.sogou.xiaomi` — 搜狗输入法
     - `com.baidu.input_mi` — 百度输入法
     - `com.iflytek.inputmethod.miui` — 讯飞输入法
     - `com.google.android.inputmethod.latin` — Gboard
     - 其他输入法请根据自身情况勾选
5. 重启设备

## Hook 原理

| Hook | 目标 | 说明 |
|------|------|------|
| H3.ime | `Resources.getDimensionPixelSize` | 拦截 `input_method_navigation_bar_height` 返回 0 |
| H6.setFrame | `InsetsSource.setFrame` | 拦截 `TYPE_NAVIGATION_BARS` 的帧，设置高度为 0 |
| H6.ime | `Resources.getBoolean` | 拦截 `config_imeDrawsImeNavBar` 返回 false |

## 构建

```bash
# 需要 JDK 17 和 Android SDK
./gradlew clean assembleRelease
# APK 输出: app/build/outputs/apk/release/app-release.apk
```

## 已知限制

- 仅在 Android 17 QPR 1 Beta/Canary 上测试
- 热重载功能未详细测试
- 不保证在其他 Android 版本上工作

## License

MIT

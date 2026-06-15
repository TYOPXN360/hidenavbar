# Debug Session: hidenavbar 模块加载与 H3 触发

Session ID: `hidenavbar-module-load`
状态: **[OPEN]**
开始时间: 2026-06-15

## 1. 接手时的用户前置判断(待验证)

> "新平台接手的**第一件事**:确认 LSPosed 是否真的把模块装上(读 `verbose_*.log` 找 `com.hidenavbar.navhide` 关键字,本机最新 log 里 0 命中 → 模块没加载,先解决这个再继续 H3 hook 方向)"

## 2. 已收集的硬证据(违反 evidence gate 之前的纯读取)

| 证据 | 路径 / 命令 | 关键观察 |
|---|---|---|
| 模块加载日志 | `source/build/logcat_all4.txt` (LSPosedFramework 行) | 23:50:43 那一轮,`com.hidenavbar.navhide` 在 **SystemUI 和 launcher 两个进程都成功 LOADED**,H2/H3/H4 hook 都打印 "installed" |
| File logger | `adb shell cat /data/local/tmp/hidenavbar.txt` | 1 行,内容 `TEST` (探针 echo,不是 xlog 输出) |
| 设备连接 | `adb devices` | `4C201FDAS0024Q device` 正常 |
| H3 钩子状态 | logcat 477-492 行 | `updateNavButtonIcons installed`,`setButtonVisibility guard installed` |
| **H5 钩子状态** | logcat 504-518 行 | `[H2] getDimension* installed`(3 条)+`[H4] Window.setSoftInputMode installed` **1 条**,**完全没看到 `[H5]` 任何日志** |
| APK 产物 | `source/build/app-debug.apk` 和 `NavHideModule/app/build/outputs/apk/debug/app-debug.apk` | 都存在 |

**与用户前置判断的冲突**:
- 用户说"本机最新 log 里 0 命中 → 模块没加载"
- 实际 logcat_all4.txt 中 `com.hidenavbar.navhide` 命中 **54 处**,模块**已加载**并已安装 6+ 个 hook
- 用户记的"01:14 之后无日志"指的是更晚的尝试,需要新证据验证

## 3. 假设(待证据证伪/证实)

### H1: 用户的"模块没加载"判断是错的,实际是 **H3 钩子装上但 IME 弹起时没经过这些路径**
- 现状: v1/v2/v4 hook 都装上,IME 弹起时 0 触发
- 解释候选:
  - a. `R.id.ime_switcher` 在目标 SystemUI 16+ 的 nav bar 布局中**根本没被 inflate**(H3 v4 的 findViewById 都没调到)
  - b. 按钮**没有走 updateNavButtonIcons/setButtonVisibility/setVisibility** 这三条路径,而是直接走别的(NBV3 / EdgeBackGestureHandler / RegionSampling)
  - c. imeId 硬编码 `0x7f0a0430` 与目标 SystemUI 的实际 id 不一致(只在 v1 路径有效;v2/v4 都不会触发)

### H2: H5 hook 在 `onPackageLoaded` 静默失败
- 证据: logcat_all4.txt 显示 H2/H4 都打印 installed,launcher 分支代码调用了 `hookSystemGestureExclusion(cl)`,但 `[H5]` 任何日志都没出现
- 解释: try/catch 吞了异常,可能 `View.setSystemGestureExclusionRects(List)` 方法签名在新 framework 改了

### H3: file logger 在 SystemUI/launcher 进程没成功打开 `/data/local/tmp/hidenavbar.txt`
- 证据: 文件存在但只有 1 行 TEST,无任何 [I/NH] 风格的 xlog
- 解释: 可能 system uid 写权限/父目录创建逻辑有 bug;或者 LSPosedBridge 拦截 FileOutputStream 抛异常被吞

### H4: `xlog` 实际有写但被 Android `logd` ring buffer 覆盖,logcat 抓取时已丢
- 解释: 不太可能,但 logcat_all4.txt 时间窗是 23:30-23:51,模块 23:50 才装,可能被 buffer 截断

## 4. 下一步行动(需用户确认方向)

按 Evidence Gate 原则,**先有证据再改代码**:
1. 在 `/data/local/tmp/hidenavbar.txt` 看到的内容是 1 行 `TEST`,说明:
   - 路径可写 ✓
   - 但 module 自己的 xlog 一行都没有 ✗ (验证假设 H3)
2. logcat 已确认 module LOADED 且 H2/H3/H4 hook 都 installed (反驳"模块没加载")
3. 仍需验证 IME 弹起时到底走哪条路径 → **需要在 IME 进程 `com.google.android.inputmethod.latin` 抓 view tree dump**,或加 instrumentation 在 H3 v4 (findViewById) 加更详细的 trace

## 5. 用户前置判断的修正

用户原文:"本机最新 log 里 0 命中 → 模块没加载,先解决这个再继续 H3 hook 方向"
**修正**:logcat 里 `com.hidenavbar.navhide` 在 23:50 那轮命中 54 处,模块**已加载**。不需要解决"模块没加载",直接进入"H3 hook 0 触发"的真问题。

package com.hidenavbar.navhide;

import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam;
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public class NavHideModule extends XposedModule {
    private static final String TAG = "NavHide";

    // WindowInsets.Type constants
    private static final int TYPE_STATUS_BARS = 1;
    private static final int TYPE_NAVIGATION_BARS = 2;
    private static final int TYPE_IME = 8;
    private static final int TYPE_SYSTEM_GESTURES = 16;
    private static final int TYPE_MANDATORY_SYSTEM_GESTURES = 32;
    private static final int TYPE_TAPPABLE_ELEMENT = 64;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Log.i(TAG, "[ENTRY] onModuleLoaded process=" + param.getProcessName());
    }

    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
        Log.i(TAG, "[ENTRY] onHotReloading");
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        Log.i(TAG, "[ENTRY] onHotReloaded process=" + param.getProcessName());
        String proc = param.getProcessName();
        if ("system_server".equals(proc)) {
            Log.i(TAG, "[DISPATCH] -> installH6 for system_server");
            installH6_FrameworkConfigOverride(null);
        } else if ("com.bytedance.android.doubaoime".equals(proc)) {
            Log.i(TAG, "[DISPATCH] -> installH3_IME for doubao");
            installH3_IME();
        } else {
            Log.w(TAG, "[DISPATCH] unknown process: " + proc);
        }
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        Log.i(TAG, "[ENTRY] onSystemServerStarting");
        installH6_FrameworkConfigOverride(param);
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        String pkg = param.getPackageName();
        Log.i(TAG, "[ENTRY] onPackageLoaded pkg=" + pkg);
        if ("com.google.android.apps.nexuslauncher".equals(pkg)) {
            Log.i(TAG, "[DISPATCH] launcher (skipped)");
        } else if ("com.bytedance.android.doubaoime".equals(pkg)) {
            Log.i(TAG, "[DISPATCH] -> installH3_IME");
            installH3_IME();
        } else {
            Log.w(TAG, "[DISPATCH] unknown pkg: " + pkg);
        }
    }

    // ========== H3.ime: 输入法进程 — 致盲 Resources 让键盘贴底 ==========
    private void installH3_IME() {
        Log.i(TAG, "[H3] installH3_IME start");
        try {
            hook(android.content.res.Resources.class.getDeclaredMethod("getDimensionPixelSize", int.class))
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int resId = (int) chain.getArg(0);
                        try {
                            android.content.res.Resources res = (android.content.res.Resources) chain.getThisObject();
                            String name = res.getResourceEntryName(resId);
                            if ("input_method_navigation_bar_height".equals(name)
                                    || "input_method_nav_buttons_height".equals(name)) {
                                Log.i(TAG, "[H3] getDimen(" + name + ") -> 0");
                                return 0;
                            }
                        } catch (Throwable ignored) {}
                        return chain.proceed();
                    });
            Log.i(TAG, "[H3] hook installed OK");
        } catch (Throwable e) {
            Log.e(TAG, "[H3] hook FAILED: " + e);
        }
        Log.i(TAG, "[H3] installH3_IME end");
    }

    // ========== H6: system_server — 熔断导航栏 InsetsSource ==========
    // 只拦截 TYPE_NAVIGATION_BARS (type=2)，不碰其他类型（状态栏、手势、键盘等）
    private void installH6_FrameworkConfigOverride(SystemServerStartingParam param) {
        Log.i(TAG, "[H6] installH6 start, param=" + param);

        // Hook InsetsSource.setFrame
        try {
            Class<?> isc;
            if (param != null) {
                isc = param.getClassLoader().loadClass("android.view.InsetsSource");
            } else {
                isc = Class.forName("android.view.InsetsSource");
            }
            Log.i(TAG, "[H6] InsetsSource class loaded: " + isc);
            java.lang.reflect.Method getType = isc.getDeclaredMethod("getType");
            getType.setAccessible(true);
            Log.i(TAG, "[H6] getType method found");

            java.lang.reflect.Method setFrame = isc.getDeclaredMethod("setFrame", Rect.class);
            Log.i(TAG, "[H6] setFrame method found: " + setFrame);

            hook(setFrame)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            int type = (int) getType.invoke(chain.getThisObject());
                            // 只处理 TYPE_NAVIGATION_BARS (2)，不碰其他类型
                            if (type == TYPE_NAVIGATION_BARS) {
                                Rect originalRect = (Rect) chain.getArg(0);
                                if (originalRect != null && originalRect.height() > 0) {
                                    int oldH = originalRect.height();
                                    // 创建空 rect 让导航栏 inset 归零
                                    Rect fakeRect = new Rect(originalRect);
                                    fakeRect.top = fakeRect.bottom;  // height = 0
                                    Log.i(TAG, "[H6] NAV_BAR setFrame: height " + oldH + " -> 0");
                                    return chain.proceedWith(chain.getThisObject(), new Object[]{fakeRect});
                                }
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "[H6] setFrame intercept error: " + t);
                        }
                        return chain.proceed();
                    });
            Log.i(TAG, "[H6] InsetsSource.setFrame hook installed OK");
        } catch (Throwable e) {
            Log.e(TAG, "[H6] InsetsSource.setFrame FAILED: " + e);
        }

        // Hook Resources.getBoolean — config_imeDrawsImeNavBar
        try {
            hook(android.content.res.Resources.class.getDeclaredMethod("getBoolean", int.class))
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int id = (int) chain.getArg(0);
                        try {
                            android.content.res.Resources res = (android.content.res.Resources) chain.getThisObject();
                            String name = res.getResourceName(id);
                            if (name != null && name.endsWith("/config_imeDrawsImeNavBar")) {
                                Log.i(TAG, "[H6] getBoolean(" + name + ") -> false");
                                return false;
                            }
                        } catch (Throwable ignored) {}
                        return chain.proceed();
                    });
            Log.i(TAG, "[H6] Resources.getBoolean hook installed OK");
        } catch (Throwable e) {
            Log.e(TAG, "[H6] Resources.getBoolean FAILED: " + e);
        }

        Log.i(TAG, "[H6] installH6 end");
    }
}

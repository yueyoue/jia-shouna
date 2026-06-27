package com.jiashouna.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import com.jiashouna.app.ui.SplashActivity;
import com.jiashouna.app.utils.AppLogger;
import java.io.PrintWriter;
import java.io.StringWriter;

public class App extends Application {
    private static App instance;
    public static final String BASE_URL = "https://sn.tthsdd.top/backend/api/";
    public static final String IMAGE_BASE = "https://sn.tthsdd.top/backend/uploads/";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 初始化日志收集器
        AppLogger.init(this);

        // Global crash handler - show error in dialog and upload to server
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            // Keep only the first 5 lines for readability
            String[] lines = stackTrace.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 8); i++) {
                sb.append(lines[i]).append("\n");
            }
            String shortTrace = sb.toString();

            // 上传crash日志到服务器
            try { AppLogger.error("Crash", throwable.getMessage(), throwable); } catch (Exception ignored) {}

            android.content.Intent intent = new android.content.Intent(instance, SplashActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("crash_error", shortTrace);
            startActivity(intent);
            System.exit(1);
        });
    }

    public static App getInstance() { return instance; }

    public SharedPreferences getPrefs() {
        return getSharedPreferences("jiashouna", Context.MODE_PRIVATE);
    }

    public String getToken() {
        return getPrefs().getString("token", "");
    }

    public void setToken(String token) {
        getPrefs().edit().putString("token", token).apply();
    }

    public int getUserId() {
        return getPrefs().getInt("user_id", 0);
    }

    public void setUserId(int id) {
        getPrefs().edit().putInt("user_id", id).apply();
    }

    public int getCurrentHouseId() {
        return getPrefs().getInt("current_house_id", 0);
    }

    public void setCurrentHouseId(int id) {
        getPrefs().edit().putInt("current_house_id", id).apply();
    }

    public String getCurrentHouseName() {
        return getPrefs().getString("current_house_name", "我的家");
    }

    public void setCurrentHouseName(String name) {
        getPrefs().edit().putString("current_house_name", name).apply();
    }

    public boolean isLoggedIn() {
        return !getToken().isEmpty() && getUserId() > 0;
    }

    public void logout() {
        getPrefs().edit().clear().apply();
    }
}

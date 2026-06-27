package com.jiashouna.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.jiashouna.app.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * APP端日志收集器
 * 捕获未处理异常和关键操作日志，上报到服务器
 */
public class AppLogger {
    private static final String TAG = "AppLogger";
    private static final List<JSONObject> logBuffer = new ArrayList<>();
    private static final int MAX_BUFFER_SIZE = 20;
    private static final int UPLOAD_THRESHOLD = 5;
    private static boolean initialized = false;

    /**
     * 初始化全局异常捕获
     */
    public static void init(Context context) {
        if (initialized) return;
        initialized = true;

        // 设置全局未处理异常捕获
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                String stackTrace = sw.toString();

                // 记录crash日志
                logSync("crash", "UncaughtException", throwable.getMessage(), stackTrace);

                // 给一点时间上传
                Thread.sleep(1000);
            } catch (Exception ignored) {
            }

            // 调用默认处理器（显示崩溃对话框等）
            Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
            if (defaultHandler != null && defaultHandler != Thread.getDefaultUncaughtExceptionHandler()) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });

        Log.d(TAG, "AppLogger initialized");
    }

    /**
     * 记录错误日志
     */
    public static void error(String tag, String message) {
        error(tag, message, null);
    }

    public static void error(String tag, String message, Throwable throwable) {
        String stackTrace = "";
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            stackTrace = sw.toString();
        }
        log("error", tag, message, stackTrace);
    }

    /**
     * 记录警告日志
     */
    public static void warn(String tag, String message) {
        log("warn", tag, message, "");
    }

    /**
     * 记录信息日志
     */
    public static void info(String tag, String message) {
        log("info", tag, message, "");
    }

    /**
     * 记录API错误
     */
    public static void apiError(String url, int code, String message) {
        log("api", "ApiClient", "API Error: " + url + " code=" + code + " msg=" + message, "");
    }

    private static void log(String type, String tag, String message, String stackTrace) {
        try {
            JSONObject logEntry = new JSONObject();
            logEntry.put("type", type);
            logEntry.put("tag", tag);
            logEntry.put("message", message != null ? message : "");
            logEntry.put("stack_trace", stackTrace != null ? stackTrace : "");
            logEntry.put("device_info", getDeviceInfo());
            logEntry.put("app_version", getAppVersion());
            logEntry.put("user_id", App.getInstance().getUserId());

            synchronized (logBuffer) {
                logBuffer.add(logEntry);
                if (logBuffer.size() >= UPLOAD_THRESHOLD) {
                    flushLogs();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to log", e);
        }
    }

    /**
     * 同步记录（用于crash，确保日志被保存）
     */
    private static void logSync(String type, String tag, String message, String stackTrace) {
        try {
            JSONObject logEntry = new JSONObject();
            logEntry.put("type", type);
            logEntry.put("tag", tag);
            logEntry.put("message", message != null ? message : "");
            logEntry.put("stack_trace", stackTrace != null ? stackTrace : "");
            logEntry.put("device_info", getDeviceInfo());
            logEntry.put("app_version", getAppVersion());
            logEntry.put("user_id", App.getInstance().getUserId());

            // 同步上传
            uploadLog(logEntry);
        } catch (Exception e) {
            Log.e(TAG, "Failed to log sync", e);
        }
    }

    /**
     * 上传日志到服务器
     */
    private static void flushLogs() {
        synchronized (logBuffer) {
            if (logBuffer.isEmpty()) return;

            try {
                JSONArray logsArray = new JSONArray();
                for (JSONObject log : logBuffer) {
                    logsArray.put(log);
                }

                JSONObject body = new JSONObject();
                body.put("logs", logsArray);

                uploadLogs(body.toString());
                logBuffer.clear();
            } catch (Exception e) {
                Log.e(TAG, "Failed to flush logs", e);
            }
        }
    }

    private static void uploadLog(JSONObject logEntry) {
        try {
            uploadLogs(logEntry.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload log", e);
        }
    }

    private static void uploadLogs(String jsonBody) {
        try {
            java.net.URL url = new java.net.URL(App.BASE_URL + "log.php?action=upload");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            String token = App.getInstance().getToken();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            java.io.OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Log upload response: " + responseCode);
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload logs", e);
        }
    }

    private static String getDeviceInfo() {
        return Build.MANUFACTURER + " " + Build.MODEL + " Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    private static String getAppVersion() {
        try {
            return App.getInstance().getPackageManager()
                .getPackageInfo(App.getInstance().getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}

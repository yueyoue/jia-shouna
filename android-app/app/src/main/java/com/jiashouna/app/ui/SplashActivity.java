package com.jiashouna.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.jiashouna.app.App;
import com.jiashouna.app.api.ApiClient;
import com.google.gson.JsonObject;

/**
 * 启动页 - 检查登录状态 + 版本更新
 */
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show crash error if coming from crash handler
        String crashError = getIntent().getStringExtra("crash_error");
        if (crashError != null && !crashError.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("应用崩溃")
                .setMessage("错误信息：\n" + crashError)
                .setPositiveButton("确定", (d, w) -> {
                    d.dismiss();
                    navigateNext();
                })
                .setCancelable(false)
                .show();
            return;
        }

        new Handler().postDelayed(this::navigateNext, 1500);
    }

    private void navigateNext() {
        App app = App.getInstance();
        if (app.isLoggedIn()) {
            checkUpdate();
        } else {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void checkUpdate() {
        try {
            int currentCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            java.util.HashMap<String, String> params = new java.util.HashMap<>();
            params.put("action", "check");
            params.put("version_code", String.valueOf(currentCode));
            ApiClient.get("/version/check", params, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    runOnUiThread(() -> {
                        if (data.has("has_update") && data.get("has_update").getAsBoolean()) {
                            JsonObject latest = data.getAsJsonObject("latest");
                            showUpdateDialog(latest);
                        } else {
                            goMain();
                        }
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> goMain());
                }
            });
        } catch (Exception e) {
            goMain();
        }
    }

    private void showUpdateDialog(JsonObject latest) {
        boolean isForce = latest.has("is_force") && latest.get("is_force").getAsInt() == 1;
        String versionName = latest.get("version_name").getAsString();
        String changelog = latest.has("changelog") ? latest.get("changelog").getAsString() : "";
        String apkUrl = latest.has("apk_url") ? latest.get("apk_url").getAsString() : "";

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("发现新版本 v" + versionName)
                .setMessage("更新内容：\n" + changelog)
                .setPositiveButton("立即更新", (d, w) -> {
                    if (!apkUrl.isEmpty()) downloadApk(apkUrl);
                    if (!isForce) goMain();
                })
                .setCancelable(!isForce)
                .setNegativeButton(isForce ? null : "稍后再说", (d, w) -> goMain())
                .show();
    }

    private void downloadApk(String url) {
        // 使用浏览器下载APK
        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        startActivity(intent);
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}

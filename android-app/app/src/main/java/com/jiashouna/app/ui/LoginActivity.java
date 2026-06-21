package com.jiashouna.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;

public class LoginActivity extends AppCompatActivity {
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);

        btnLogin.setOnClickListener(v -> doLogin());
        tvRegister.setOnClickListener(v -> doRegister());
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);

        ApiClient.post("/auth/login", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("登录");
                    handleLoginSuccess(data);
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("登录");
                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void doRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "密码长度至少6位", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("注册中...");
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        body.addProperty("house_name", "我的家");

        ApiClient.post("/auth/register", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("登录");
                    handleLoginSuccess(data);
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("登录");
                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleLoginSuccess(JsonObject data) {
        App app = App.getInstance();
        String token = data.get("token").getAsString();
        JsonObject user = data.getAsJsonObject("user");
        app.setToken(token);
        app.setUserId(user.get("id").getAsInt());

        // 设置当前房屋
        if (data.has("houses") && !data.get("houses").isJsonNull()) {
            JsonArray houses = data.getAsJsonArray("houses");
            if (houses.size() > 0) {
                JsonObject house = houses.get(0).getAsJsonObject();
                app.setCurrentHouseId(house.get("id").getAsInt());
                app.setCurrentHouseName(house.has("name") ? house.get("name").getAsString() : "我的家");
                goToMain();
            } else {
                // 没有家庭，自动创建一个
                autoCreateHouse();
            }
        } else {
            autoCreateHouse();
        }
    }

    private void autoCreateHouse() {
        JsonObject body = new JsonObject();
        body.addProperty("name", "我的家");
        ApiClient.post("/house/create", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        App app = App.getInstance();
                        if (data.has("id")) {
                            app.setCurrentHouseId(data.get("id").getAsInt());
                            app.setCurrentHouseName(data.has("name") ? data.get("name").getAsString() : "我的家");
                        }
                    } catch (Exception ignored) {}
                    goToMain();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    // 即使创建失败也进入主页面
                    Toast.makeText(LoginActivity.this, "登录成功，请创建一个家庭", Toast.LENGTH_SHORT).show();
                    goToMain();
                });
            }
        });
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}

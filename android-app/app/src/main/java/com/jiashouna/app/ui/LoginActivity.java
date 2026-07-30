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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);

        btnLogin.setOnClickListener(v -> doLogin());

        // 注册链接
        TextView tvRegister = findViewById(R.id.tv_register);
        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        // 忘记密码
        TextView tvForgot = findViewById(R.id.tv_forgot_password);
        if (tvForgot != null) {
            tvForgot.setOnClickListener(v ->
                Toast.makeText(this, "忘记密码功能开发中", Toast.LENGTH_SHORT).show()
            );
        }

        // 微信登录
        View btnWechat = findViewById(R.id.btn_wechat_login);
        if (btnWechat != null) {
            btnWechat.setOnClickListener(v ->
                Toast.makeText(this, "微信登录功能开发中", Toast.LENGTH_SHORT).show()
            );
        }
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

        ApiClient.post("auth.php?action=login", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("立即登录");
                    handleLoginSuccess(data);
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("立即登录");
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

        // 设置当前房屋（不自动创建）
        if (data.has("houses") && !data.get("houses").isJsonNull()) {
            JsonArray houses = data.getAsJsonArray("houses");
            if (houses.size() > 0) {
                JsonObject house = houses.get(0).getAsJsonObject();
                app.setCurrentHouseId(house.get("id").getAsInt());
                app.setCurrentHouseName(house.has("name") ? house.get("name").getAsString() : "我的家");
            }
        }
        goToMain();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}

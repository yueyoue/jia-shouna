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
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);

        ApiClient.post("auth.php?action=login", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    handleLoginSuccess(data);
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
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

        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        body.addProperty("house_name", "我的家");

        ApiClient.post("auth.php?action=register", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> handleLoginSuccess(data));
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show());
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
        if (data.has("houses")) {
            JsonArray houses = data.getAsJsonArray("houses");
            if (houses.size() > 0) {
                JsonObject house = houses.get(0).getAsJsonObject();
                app.setCurrentHouseId(house.get("id").getAsInt());
                app.setCurrentHouseName(house.get("name").getAsString());
            }
        }

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}

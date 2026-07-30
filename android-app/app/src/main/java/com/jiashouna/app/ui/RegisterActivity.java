package com.jiashouna.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;

public class RegisterActivity extends AppCompatActivity {
    private EditText etUsername, etPassword, etConfirmPassword;
    private CheckBox cbTerms;
    private Button btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        cbTerms = findViewById(R.id.cb_terms);
        btnRegister = findViewById(R.id.btn_register);

        btnRegister.setOnClickListener(v -> doRegister());

        // 登录链接
        TextView tvLogin = findViewById(R.id.tv_login);
        tvLogin.setOnClickListener(v -> finish());
    }

    private void doRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (username.isEmpty()) {
            Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty()) {
            Toast.makeText(this, "请设置密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "密码长度至少6位", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "请先同意用户协议和隐私政策", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegister.setEnabled(false);
        btnRegister.setText("注册中...");
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        body.addProperty("house_name", "我的家");

        ApiClient.post("auth.php?action=register", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    btnRegister.setEnabled(true);
                    btnRegister.setText("注册并开启");
                    handleRegisterSuccess(data);
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnRegister.setEnabled(true);
                    btnRegister.setText("注册并开启");
                    Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleRegisterSuccess(JsonObject data) {
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
                autoCreateHouse();
            }
        } else {
            autoCreateHouse();
        }
    }

    private void autoCreateHouse() {
        JsonObject body = new JsonObject();
        body.addProperty("name", "我的家");
        ApiClient.post("house.php?action=create", body, new ApiClient.ApiCallback() {
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
                    Toast.makeText(RegisterActivity.this, "注册成功，请创建一个家庭", Toast.LENGTH_SHORT).show();
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

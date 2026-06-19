package com.jiashouna.app.ui;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.db.LocalDb;
import com.jiashouna.app.model.Space;
import com.jiashouna.app.utils.NetworkUtils;

public class AddSpaceActivity extends AppCompatActivity {
    private EditText etName;
    private Spinner spLevel;
    private Switch swShared;
    private Button btnSave;
    private String selectedIcon = "🏠";
    private String selectedColor = "#FF8C42";
    private LocalDb localDb;

    private final String[] icons = {"🏠", "🛋", "🍳", "🛏", "🚿", "📖", "📺", "❄", "🚪", "📦", "💊", "👟", "👔", "🍽", "🎮", "🧹"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_space);

        localDb = new LocalDb(this);

        etName = findViewById(R.id.et_name);
        spLevel = findViewById(R.id.sp_level);
        swShared = findViewById(R.id.sw_shared);
        btnSave = findViewById(R.id.btn_save);

        // 图标选择
        GridLayout iconGrid = findViewById(R.id.grid_icons);
        for (String icon : icons) {
            TextView tv = new TextView(this);
            tv.setText(icon);
            tv.setTextSize(24);
            tv.setPadding(16, 16, 16, 16);
            tv.setOnClickListener(v -> {
                selectedIcon = icon;
                Toast.makeText(this, "已选择: " + icon, Toast.LENGTH_SHORT).show();
            });
            iconGrid.addView(tv);
        }

        btnSave.setOnClickListener(v -> saveSpace());
    }

    private void saveSpace() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("请输入空间名称");
            return;
        }

        int level = spLevel.getSelectedItemPosition() + 1;

        if (NetworkUtils.isNetworkAvailable(this)) {
            JsonObject body = new JsonObject();
            body.addProperty("house_id", App.getInstance().getCurrentHouseId());
            body.addProperty("name", name);
            body.addProperty("level", level);
            body.addProperty("icon", selectedIcon);
            body.addProperty("color", selectedColor);
            body.addProperty("shared", swShared.isChecked() ? 1 : 0);

            ApiClient.post("space.php?action=create", body, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    runOnUiThread(() -> {
                        Toast.makeText(AddSpaceActivity.this, "创建成功", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> Toast.makeText(AddSpaceActivity.this, msg, Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            Space space = new Space();
            space.houseId = App.getInstance().getCurrentHouseId();
            space.name = name;
            space.level = level;
            space.icon = selectedIcon;
            space.color = selectedColor;
            space.shared = swShared.isChecked() ? 1 : 0;
            localDb.saveOfflineSpace(space);
            Toast.makeText(this, "已保存到本地，联网后自动同步", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}

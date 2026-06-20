package com.jiashouna.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.db.LocalDb;
import com.jiashouna.app.model.Space;
import com.jiashouna.app.utils.NetworkUtils;

import java.util.HashMap;

public class AddSpaceActivity extends AppCompatActivity {
    private EditText etName;
    private View levelRoom, levelContainer, levelArea;
    private Switch swShared;
    private Button btnSave;
    private RadioGroup rgHouses;
    private TextView tvNoHouseHint;
    private TextView btnCreateHouse;
    private String selectedIcon = "🏠";
    private String selectedColor = "#FF8C42";
    private int selectedLevel = 1; // 1=房间, 2=容器, 3=区域
    private int selectedHouseId = 0;
    private LocalDb localDb;
    private JsonArray houseList = new JsonArray();

    private final String[] roomIcons = {"🛋", "🍳", "🛏", "🚿", "📖", "📺", "❄", "🚪"};
    private final String[] containerIcons = {"📦", "🗄", "🧳", "🎒", "💼", "🪣"};
    private final String[] areaIcons = {"📂", "🗂", "🔖", "📌"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_space);

        localDb = new LocalDb(this);

        etName = findViewById(R.id.et_name);
        levelRoom = findViewById(R.id.level_room);
        levelContainer = findViewById(R.id.level_container);
        levelArea = findViewById(R.id.level_area);
        swShared = findViewById(R.id.sw_shared);
        btnSave = findViewById(R.id.btn_save);
        rgHouses = findViewById(R.id.rg_houses);
        tvNoHouseHint = findViewById(R.id.tv_no_house_hint);
        btnCreateHouse = findViewById(R.id.btn_create_house);

        // 层级选择 - 房间/容器/区域
        View.OnClickListener levelClick = v -> {
            resetLevelSelection();
            v.setSelected(true);
            v.setBackgroundResource(R.drawable.bg_button_primary);
            if (v.getId() == R.id.level_room) {
                selectedLevel = 1;
                selectedIcon = roomIcons[0];
            } else if (v.getId() == R.id.level_container) {
                selectedLevel = 2;
                selectedIcon = containerIcons[0];
            } else if (v.getId() == R.id.level_area) {
                selectedLevel = 3;
                selectedIcon = areaIcons[0];
            }
            updateIconGrid();
        };
        levelRoom.setOnClickListener(levelClick);
        levelContainer.setOnClickListener(levelClick);
        levelArea.setOnClickListener(levelClick);
        levelRoom.setSelected(true);
        selectedLevel = 1;
        selectedIcon = roomIcons[0];

        updateIconGrid();

        btnSave.setOnClickListener(v -> saveSpace());
        btnCreateHouse.setOnClickListener(v -> showCreateHouseDialog());

        // 返回
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadHouses();
    }

    private void loadHouses() {
        ApiClient.get("house.php?action=list", null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            houseList = data.getAsJsonArray("list");
                        }
                    } catch (Exception ignored) {}
                    updateHouseSelector();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> updateHouseSelector());
            }
        });
    }

    private void updateHouseSelector() {
        rgHouses.removeAllViews();

        if (houseList.size() == 0) {
            tvNoHouseHint.setVisibility(View.VISIBLE);
            rgHouses.setVisibility(View.GONE);
            return;
        }

        tvNoHouseHint.setVisibility(View.GONE);
        rgHouses.setVisibility(View.VISIBLE);

        int currentHouseId = App.getInstance().getCurrentHouseId();

        for (int i = 0; i < houseList.size(); i++) {
            JsonObject house = houseList.get(i).getAsJsonObject();
            int id = house.get("id").getAsInt();
            String name = house.has("name") ? house.get("name").getAsString() : "我的家";

            RadioButton rb = new RadioButton(this);
            rb.setText("🏠 " + name);
            rb.setTextSize(14);
            rb.setTextColor(Color.parseColor("#2D3748"));
            rb.setPadding(16, 12, 16, 12);
            rb.setId(id);

            // 默认选中当前家或第一个
            if (id == currentHouseId || (currentHouseId <= 0 && i == 0)) {
                rb.setChecked(true);
                selectedHouseId = id;
            }

            rgHouses.addView(rb);
        }

        rgHouses.setOnCheckedChangeListener((group, checkedId) -> {
            selectedHouseId = checkedId;
        });
    }

    private void showCreateHouseDialog() {
        EditText input = new EditText(this);
        input.setHint("例如：奶奶家、爷爷家");
        input.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
            .setTitle("创建新家")
            .setView(input)
            .setPositiveButton("创建", (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(this, "请输入家的名称", Toast.LENGTH_SHORT).show();
                    return;
                }
                createHouse(name);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void createHouse(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);

        ApiClient.post("house.php?action=create", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (data.has("id")) {
                            int houseId = data.get("id").getAsInt();
                            App app = App.getInstance();
                            app.setCurrentHouseId(houseId);
                            app.setCurrentHouseName(name);
                            // 刷新家列表
                            loadHouses();
                        }
                        Toast.makeText(AddSpaceActivity.this, "✅ 家庭创建成功", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(AddSpaceActivity.this, "创建成功", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(AddSpaceActivity.this, "创建失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void resetLevelSelection() {
        levelRoom.setSelected(false);
        levelContainer.setSelected(false);
        levelArea.setSelected(false);
        levelRoom.setBackgroundResource(R.drawable.bg_level_option);
        levelContainer.setBackgroundResource(R.drawable.bg_level_option);
        levelArea.setBackgroundResource(R.drawable.bg_level_option);
    }

    private void updateIconGrid() {
        GridLayout iconGrid = findViewById(R.id.grid_icons);
        iconGrid.removeAllViews();

        String[] icons;
        switch (selectedLevel) {
            case 1: icons = roomIcons; break;
            case 2: icons = containerIcons; break;
            case 3: icons = areaIcons; break;
            default: icons = roomIcons; break;
        }

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
    }

    private void saveSpace() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("请输入名称");
            etName.requestFocus();
            return;
        }

        if (selectedHouseId <= 0) {
            Toast.makeText(this, "请先选择或创建一个家庭", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);

        if (NetworkUtils.isNetworkAvailable(this)) {
            JsonObject body = new JsonObject();
            body.addProperty("house_id", selectedHouseId);
            body.addProperty("name", name);
            body.addProperty("level", selectedLevel);
            body.addProperty("icon", selectedIcon);
            body.addProperty("color", selectedColor);
            body.addProperty("shared", swShared.isChecked() ? 1 : 0);

            ApiClient.post("space.php?action=create", body, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    runOnUiThread(() -> {
                        Toast.makeText(AddSpaceActivity.this, "✅ 创建成功", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(AddSpaceActivity.this, "创建失败: " + msg, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            Space space = new Space();
            space.houseId = selectedHouseId;
            space.name = name;
            space.level = selectedLevel;
            space.icon = selectedIcon;
            space.color = selectedColor;
            space.shared = swShared.isChecked() ? 1 : 0;
            localDb.saveOfflineSpace(space);
            Toast.makeText(this, "已保存到本地，联网后自动同步", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}

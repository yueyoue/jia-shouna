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
    private View levelHome, levelRoom, levelContainer, levelArea;
    private Switch swShared;
    private Button btnSave;
    private String selectedIcon = "🏠";
    private String selectedColor = "#FF8C42";
    private int selectedLevel = 0; // 0=家, 1=房间, 2=容器, 3=区域
    private int selectedParentId = 0;
    private LocalDb localDb;
    private JsonArray existingSpaces = new JsonArray();

    private final String[] homeIcons = {"🏠", "🏡", "🏢", "🏘"};
    private final String[] roomIcons = {"🛋", "🍳", "🛏", "🚿", "📖", "📺", "❄", "🚪"};
    private final String[] containerIcons = {"📦", "🗄", "🧳", "🎒", "💼", "🪣"};
    private final String[] areaIcons = {"📂", "🗂", "🔖", "📌"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_space);

        localDb = new LocalDb(this);

        etName = findViewById(R.id.et_name);
        levelHome = findViewById(R.id.level_room);   // 复用第一个位置给"家"
        levelRoom = findViewById(R.id.level_container); // 复用第二个位置给"房间"
        levelContainer = findViewById(R.id.level_area);  // 复用第三个位置给"容器"
        swShared = findViewById(R.id.sw_shared);
        btnSave = findViewById(R.id.btn_save);

        // 修改层级选项的显示文本
        setupLevelLabels();

        // 层级选择
        View.OnClickListener levelClick = v -> {
            resetLevelSelection();
            v.setSelected(true);
            if (v.getId() == R.id.level_room) {
                selectedLevel = 0; // 家
                selectedIcon = homeIcons[0];
            } else if (v.getId() == R.id.level_container) {
                selectedLevel = 1; // 房间
                selectedIcon = roomIcons[0];
            } else if (v.getId() == R.id.level_area) {
                selectedLevel = 2; // 容器
                selectedIcon = containerIcons[0];
            }
            updateIconGrid();
        };
        levelHome.setOnClickListener(levelClick);
        levelRoom.setOnClickListener(levelClick);
        levelContainer.setOnClickListener(levelClick);
        levelHome.setSelected(true);
        selectedLevel = 0;
        selectedIcon = homeIcons[0];

        // 图标选择
        updateIconGrid();

        btnSave.setOnClickListener(v -> saveSpace());

        // 加载已有空间（用于选择父级）
        loadExistingSpaces();
    }

    private void setupLevelLabels() {
        // 修改三个层级选项的文本
        try {
            // level_room (第一个) → 家
            TextView tv1 = ((LinearLayout) levelHome).findViewWithTag("level_label");
            if (tv1 != null) tv1.setText("家");
            // 手动查找并修改子TextView
            for (int i = 0; i < ((LinearLayout) levelHome).getChildCount(); i++) {
                View child = ((LinearLayout) levelHome).getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    String text = tv.getText().toString();
                    if (text.equals("🏠") || text.equals("房间")) {
                        if (text.equals("房间")) tv.setText("家");
                    }
                    if (text.startsWith("如主卧")) tv.setText("如爷爷家、奶奶家");
                }
            }
            // level_container (第二个) → 房间
            for (int i = 0; i < ((LinearLayout) levelRoom).getChildCount(); i++) {
                View child = ((LinearLayout) levelRoom).getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    String text = tv.getText().toString();
                    if (text.equals("📦") || text.equals("容器")) {
                        if (text.equals("容器")) tv.setText("房间");
                    }
                    if (text.startsWith("如衣柜")) tv.setText("如主卧、厨房";
                }
            }
            // level_area (第三个) → 容器
            for (int i = 0; i < ((LinearLayout) levelContainer).getChildCount(); i++) {
                View child = ((LinearLayout) levelContainer).getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    String text = tv.getText().toString();
                    if (text.equals("📂") || text.equals("区域")) {
                        if (text.equals("区域")) tv.setText("容器");
                    }
                    if (text.startsWith("如上层")) tv.setText("如衣柜、冰箱";
                }
            }
        } catch (Exception ignored) {}
    }

    private void loadExistingSpaces() {
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) return;

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("space.php?action=tree", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (data.has("tree") && !data.get("tree").isJsonNull()) {
                            existingSpaces = data.getAsJsonArray("tree");
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void resetLevelSelection() {
        levelHome.setSelected(false);
        levelRoom.setSelected(false);
        levelContainer.setSelected(false);
        levelHome.setBackgroundResource(R.drawable.bg_level_option);
        levelRoom.setBackgroundResource(R.drawable.bg_level_option);
        levelContainer.setBackgroundResource(R.drawable.bg_level_option);
    }

    private void updateIconGrid() {
        GridLayout iconGrid = findViewById(R.id.grid_icons);
        iconGrid.removeAllViews();

        String[] icons;
        switch (selectedLevel) {
            case 0: icons = homeIcons; break;
            case 1: icons = roomIcons; break;
            case 2: icons = containerIcons; break;
            default: icons = homeIcons; break;
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

        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            Toast.makeText(this, "请先创建或加入一个家庭", Toast.LENGTH_SHORT).show();
            return;
        }

        // 如果是"家"级别，先创建房屋
        if (selectedLevel == 0) {
            createHouse(name);
            return;
        }

        // 其他级别，创建空间
        if (NetworkUtils.isNetworkAvailable(this)) {
            JsonObject body = new JsonObject();
            body.addProperty("house_id", houseId);
            body.addProperty("name", name);
            body.addProperty("level", selectedLevel);
            body.addProperty("parent_id", selectedParentId);
            body.addProperty("icon", selectedIcon);
            body.addProperty("color", selectedColor);
            body.addProperty("shared", swShared.isChecked() ? 1 : 0);

            btnSave.setEnabled(false);
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
            space.houseId = houseId;
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

    private void createHouse(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);

        btnSave.setEnabled(false);
        ApiClient.post("house.php?action=create", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        // 如果是第一个家，设为当前家
                        if (data.has("id")) {
                            int houseId = data.get("id").getAsInt();
                            App app = App.getInstance();
                            if (app.getCurrentHouseId() <= 0) {
                                app.setCurrentHouseId(houseId);
                                app.setCurrentHouseName(name);
                            }
                        }
                        Toast.makeText(AddSpaceActivity.this, "✅ 家庭创建成功", Toast.LENGTH_SHORT).show();
                        finish();
                    } catch (Exception e) {
                        Toast.makeText(AddSpaceActivity.this, "创建成功", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(AddSpaceActivity.this, "创建失败: " + msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}

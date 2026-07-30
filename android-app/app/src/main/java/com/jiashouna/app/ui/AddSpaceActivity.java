package com.jiashouna.app.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
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
    private TextView btnSave, btnCancel;
    private RadioGroup rgHouses;
    private TextView tvNoHouseHint, tvSelectedHouse, tvParentSpace;
    private TextView btnCreateHouse;
    private LinearLayout layoutIconSelector, layoutColorSelector;
    private String selectedIcon = "🏠";
    private String selectedColor = "#FF8C42";
    private int selectedLevel = 1;
    private int selectedHouseId = 0;
    private int parentSpaceId = 0;
    private String parentSpaceName = "";
    private LocalDb localDb;
    private JsonArray houseList = new JsonArray();
    private JsonArray spaceListForParent = new JsonArray();

    private final String[] allIcons = {"🏠", "🛏️", "🍳", "📦", "👕", "💊", "🎮", "📚"};
    private final int[] colorValues = {
        0xFFFF8C42, // primary-container
        0xFF79F3EA, // secondary-container
        0xFF5B9FED, // info-blue
        0xFFB89AFF, // tertiary-container
        0xFF48BB78, // success-green
        0xFFF56565, // error-red
        0xFFED8936  // warning-orange
    };
    private final String[] colorHex = {
        "#FF8C42", "#79F3EA", "#5B9FED", "#B89AFF", "#48BB78", "#F56565", "#ED8936"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_space);

        localDb = new LocalDb(this);

        parentSpaceId = getIntent().getIntExtra("parent_id", 0);
        parentSpaceName = getIntent().getStringExtra("parent_space_name");
        if (parentSpaceName == null) parentSpaceName = "";
        int intentHouseId = getIntent().getIntExtra("house_id", 0);
        if (intentHouseId > 0) selectedHouseId = intentHouseId;

        etName = findViewById(R.id.et_name);
        levelRoom = findViewById(R.id.level_room);
        levelContainer = findViewById(R.id.level_container);
        levelArea = findViewById(R.id.level_area);
        swShared = findViewById(R.id.sw_shared);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
        rgHouses = findViewById(R.id.rg_houses);
        tvNoHouseHint = findViewById(R.id.tv_no_house_hint);
        tvSelectedHouse = findViewById(R.id.tv_selected_house);
        tvParentSpace = findViewById(R.id.tv_parent_space);
        btnCreateHouse = findViewById(R.id.btn_create_house);
        layoutIconSelector = findViewById(R.id.layout_icon_selector);
        layoutColorSelector = findViewById(R.id.layout_color_selector);

        // Parent space hint
        if (parentSpaceId > 0 && !parentSpaceName.isEmpty()) {
            tvParentSpace.setText(parentSpaceName);
        }

        // Back / Cancel
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());

        // House selector click
        findViewById(R.id.btn_house_selector).setOnClickListener(v -> showHousePicker());

        // Parent selector click
        findViewById(R.id.btn_parent_selector).setOnClickListener(v -> showParentSpacePicker());

        // Build icon selector
        buildIconSelector();

        // Build color selector
        buildColorSelector();

        btnSave.setOnClickListener(v -> saveSpace());
        btnCreateHouse.setOnClickListener(v -> showCreateHouseDialog());

        loadHouses();
    }

    private void buildIconSelector() {
        layoutIconSelector.removeAllViews();
        for (int i = 0; i < allIcons.length; i++) {
            String icon = allIcons[i];
            TextView tv = new TextView(this);
            tv.setText(icon);
            tv.setTextSize(24);
            tv.setGravity(Gravity.CENTER);
            int size = dp(56);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(12));
            tv.setLayoutParams(lp);

            boolean isSelected = icon.equals(selectedIcon);
            tv.setBackgroundResource(isSelected ? R.drawable.bg_icon_selected : R.drawable.bg_icon_unselected);

            final int idx = i;
            tv.setOnClickListener(v -> {
                selectedIcon = allIcons[idx];
                buildIconSelector(); // refresh
            });
            layoutIconSelector.addView(tv);
        }
    }

    private void buildColorSelector() {
        layoutColorSelector.removeAllViews();
        int size = dp(32);
        int margin = dp(8);
        for (int i = 0; i < colorValues.length; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(margin);
            dot.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(colorValues[i]);
            dot.setBackground(bg);

            boolean isSelected = colorHex[i].equals(selectedColor);
            if (isSelected) {
                dot.setElevation(dp(4));
                // Add ring effect via padding + border
                GradientDrawable ringBg = new GradientDrawable();
                ringBg.setShape(GradientDrawable.OVAL);
                ringBg.setColor(colorValues[i]);
                ringBg.setStroke(dp(3), 0xFF9B4500);
                dot.setBackground(ringBg);
            }

            final int idx = i;
            dot.setOnClickListener(v -> {
                selectedColor = colorHex[idx];
                buildColorSelector(); // refresh
            });
            layoutColorSelector.addView(dot);
        }
    }

    private void showHousePicker() {
        if (houseList.size() == 0) {
            Toast.makeText(this, "暂无家庭，请先创建", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[houseList.size()];
        for (int i = 0; i < houseList.size(); i++) {
            JsonObject h = houseList.get(i).getAsJsonObject();
            names[i] = "🏠 " + (h.has("name") ? h.get("name").getAsString() : "我的家");
        }
        new AlertDialog.Builder(this)
            .setTitle("选择家庭")
            .setItems(names, (d, which) -> {
                JsonObject h = houseList.get(which).getAsJsonObject();
                selectedHouseId = h.get("id").getAsInt();
                tvSelectedHouse.setText(h.has("name") ? h.get("name").getAsString() : "我的家");
            })
            .show();
    }

    private void showParentSpacePicker() {
        if (selectedHouseId <= 0) {
            Toast.makeText(this, "请先选择家庭", Toast.LENGTH_SHORT).show();
            return;
        }
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(selectedHouseId));
        ApiClient.get("space.php?action=tree", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonArray tree = data.has("tree") ? data.getAsJsonArray("tree") : new JsonArray();
                        showParentPicker(tree);
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void showParentPicker(JsonArray spaces) {
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<Integer> ids = new java.util.ArrayList<>();

        names.add("🏠 一级空间 (无上级)");
        ids.add(0);

        for (int i = 0; i < spaces.size(); i++) {
            JsonObject space = spaces.get(i).getAsJsonObject();
            int id = space.get("id").getAsInt();
            String sName = space.has("name") ? space.get("name").getAsString() : "";
            String icon = space.has("icon") ? space.get("icon").getAsString() : "🏠";
            names.add(icon + " " + sName);
            ids.add(id);

            if (space.has("children") && !space.get("children").isJsonNull()) {
                JsonArray children = space.getAsJsonArray("children");
                for (int j = 0; j < children.size(); j++) {
                    JsonObject child = children.get(j).getAsJsonObject();
                    int cId = child.get("id").getAsInt();
                    String cName = child.has("name") ? child.get("name").getAsString() : "";
                    String cIcon = child.has("icon") ? child.get("icon").getAsString() : "📦";
                    names.add("  " + cIcon + " " + cName);
                    ids.add(cId);
                }
            }
        }

        new AlertDialog.Builder(this)
            .setTitle("选择上级空间")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                parentSpaceId = ids.get(which);
                tvParentSpace.setText(which == 0 ? "一级空间 (无上级)" : names.get(which));
            })
            .show();
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
                    updateHouseDisplay();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> updateHouseDisplay());
            }
        });
    }

    private void updateHouseDisplay() {
        if (houseList.size() == 0) {
            tvSelectedHouse.setText("暂无家庭");
            tvNoHouseHint.setVisibility(View.VISIBLE);
            btnCreateHouse.setVisibility(View.VISIBLE);
            return;
        }
        tvNoHouseHint.setVisibility(View.GONE);
        btnCreateHouse.setVisibility(View.GONE);

        int currentHouseId = App.getInstance().getCurrentHouseId();
        for (int i = 0; i < houseList.size(); i++) {
            JsonObject house = houseList.get(i).getAsJsonObject();
            int id = house.get("id").getAsInt();
            if (id == currentHouseId || (currentHouseId <= 0 && i == 0)) {
                selectedHouseId = id;
                tvSelectedHouse.setText(house.has("name") ? house.get("name").getAsString() : "我的家");
                break;
            }
        }
    }

    private void showCreateHouseDialog() {
        EditText input = new EditText(this);
        input.setHint("例如：奶奶家、爷爷家");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

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
        doSaveSpace(name);
    }

    private void doSaveSpace(String name) {
        btnSave.setEnabled(false);

        if (NetworkUtils.isNetworkAvailable(this)) {
            JsonObject body = new JsonObject();
            body.addProperty("house_id", selectedHouseId);
            body.addProperty("name", name);
            body.addProperty("level", selectedLevel);
            body.addProperty("icon", selectedIcon);
            body.addProperty("color", selectedColor);
            body.addProperty("shared", swShared.isChecked() ? 1 : 0);
            if (parentSpaceId > 0) {
                body.addProperty("parent_id", parentSpaceId);
            }

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

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

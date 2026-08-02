package com.jiashouna.app.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.TypedValue;
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
    private Button btnSave;
    private RadioGroup rgHouses;
    private TextView tvNoHouseHint;
    private TextView btnCreateHouse;
    private TextView tvParentSpace;
    private TextView btnSelectParent;
    private TextView btnCancel;
    private LinearLayout layoutIconSelector;
    private LinearLayout layoutColorSelector;

    private String selectedIcon = "🏠";
    private String selectedColor = "#FF8C42";
    private int selectedLevel = 1;
    private int selectedHouseId = 0;
    private int parentSpaceId = 0;
    private String parentSpaceName = "";
    private int editSpaceId = 0; // 0=新建, >0=编辑
    private LocalDb localDb;
    private JsonArray houseList = new JsonArray();

    private final String[] icons = {"🏠", "🛏", "🍳", "📦", "👕", "💊", "🎮", "📚"};
    private final String[] colorValues = {"#FF8C42", "#FFB380", "#4A90D9", "#9B59B6", "#27AE60", "#E74C3C", "#F39C12"};

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
        editSpaceId = getIntent().getIntExtra("edit_space_id", 0);

        etName = findViewById(R.id.et_name);
        levelRoom = findViewById(R.id.level_room);
        levelContainer = findViewById(R.id.level_container);
        levelArea = findViewById(R.id.level_area);
        swShared = findViewById(R.id.sw_shared);
        btnSave = findViewById(R.id.btn_save);
        rgHouses = findViewById(R.id.rg_houses);
        tvNoHouseHint = findViewById(R.id.tv_no_house_hint);
        btnCreateHouse = findViewById(R.id.btn_create_house);
        tvParentSpace = findViewById(R.id.tv_parent_space);
        btnSelectParent = findViewById(R.id.btn_select_parent);
        btnCancel = findViewById(R.id.btn_cancel);
        layoutIconSelector = findViewById(R.id.layout_icon_selector);
        layoutColorSelector = findViewById(R.id.layout_color_selector);

        if (parentSpaceId > 0) {
            tvParentSpace.setText(parentSpaceName.isEmpty() ? "已选上级" : parentSpaceName);
            int parentLevel = getIntent().getIntExtra("parent_level", 1);
            if (parentLevel < 3) selectedLevel = parentLevel + 1;
        }

        buildIconSelector();
        buildColorSelector();

        btnSave.setOnClickListener(v -> saveSpace());
        btnCancel.setOnClickListener(v -> finish());
        if (btnCreateHouse != null) btnCreateHouse.setOnClickListener(v -> showCreateHouseDialog());
        btnSelectParent.setOnClickListener(v -> loadParentSpaces());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadHouses();

        // 编辑模式：加载现有空间数据
        if (editSpaceId > 0) {
            if (btnSave != null) btnSave.setText("保存修改");
            if (tvTitle != null) tvTitle.setText("编辑空间");
            loadSpaceData();
        }
    }

    private android.widget.TextView tvTitle;

    private void loadSpaceData() {
        ApiClient.get("space.php?action=detail&id=" + editSpaceId, new java.util.HashMap<>(), new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        String name = data.has("name") ? data.get("name").getAsString() : "";
                        String icon = data.has("icon") ? data.get("icon").getAsString() : "🏠";
                        String color = data.has("color") ? data.get("color").getAsString() : "#FF8C42";
                        int level = data.has("level") ? data.get("level").getAsInt() : 1;
                        boolean shared = data.has("shared") && data.get("shared").getAsInt() == 1;
                        int houseId = data.has("house_id") ? data.get("house_id").getAsInt() : 0;

                        etName.setText(name);
                        selectedIcon = icon;
                        selectedColor = color;
                        selectedLevel = level;
                        if (houseId > 0) selectedHouseId = houseId;
                        swShared.setChecked(shared);
                        buildIconSelector();
                        buildColorSelector();
                    } catch (Exception e) {
                        Toast.makeText(AddSpaceActivity.this, "加载空间信息失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(AddSpaceActivity.this, "加载失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });

    private void buildIconSelector() {
        layoutIconSelector.removeAllViews();
        int sizePx = dpToPx(44);
        int marginPx = dpToPx(8);

        for (int i = 0; i < icons.length; i++) {
            final String icon = icons[i];
            TextView tv = new TextView(this);
            tv.setText(icon);
            tv.setTextSize(20);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(i == 0 ? 0 : marginPx, 0, 0, 0);
            tv.setLayoutParams(lp);

            boolean selected = icon.equals(selectedIcon);
            tv.setBackgroundResource(selected ? R.drawable.bg_icon_selected : R.drawable.bg_icon_unselected);
            if (selected) {
                tv.setShadowLayer(4f, 0, 2, Color.parseColor("#33FF8C42"));
            }

            tv.setOnClickListener(v -> {
                selectedIcon = icon;
                buildIconSelector();
            });

            layoutIconSelector.addView(tv);
        }
    }

    private void buildColorSelector() {
        layoutColorSelector.removeAllViews();
        int sizePx = dpToPx(36);
        int marginPx = dpToPx(12);

        for (int i = 0; i < colorValues.length; i++) {
            final String color = colorValues[i];
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(i == 0 ? 0 : marginPx, 0, 0, 0);
            dot.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(color));

            if (color.equals(selectedColor)) {
                // Selected: ring effect
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setStroke(dpToPx(2), Color.parseColor("#9B4500"));
                ring.setColor(Color.TRANSPARENT);

                LayerDrawable layer = new LayerDrawable(new android.graphics.drawable.Drawable[]{bg, ring});
                int pad = dpToPx(3);
                dot.setPadding(pad, pad, pad, pad);
                dot.setBackground(layer);
            } else {
                dot.setBackground(bg);
            }

            dot.setOnClickListener(v -> {
                selectedColor = color;
                buildColorSelector();
            });

            layoutColorSelector.addView(dot);
        }
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

    private void loadParentSpaces() {
        if (selectedHouseId <= 0) {
            Toast.makeText(this, "请先选择一个家庭", Toast.LENGTH_SHORT).show();
            return;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(selectedHouseId));

        ApiClient.get("space.php?action=tree", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonArray tree = new JsonArray();
                        if (data.has("tree") && !data.get("tree").isJsonNull()) {
                            tree = data.getAsJsonArray("tree");
                        } else if (data.has("list") && !data.get("list").isJsonNull()) {
                            tree = data.getAsJsonArray("list");
                        }
                        showParentPicker(tree);
                    } catch (Exception e) {
                        Toast.makeText(AddSpaceActivity.this, "加载空间列表失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(AddSpaceActivity.this, "加载失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showParentPicker(JsonArray spaces) {
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        java.util.List<Integer> levels = new java.util.ArrayList<>();

        names.add("🏠 一级空间 (无上级)");
        ids.add(0);
        levels.add(0);

        for (int i = 0; i < spaces.size(); i++) {
            JsonObject space = spaces.get(i).getAsJsonObject();
            int id = space.has("id") ? space.get("id").getAsInt() : 0;
            String sName = space.has("name") ? space.get("name").getAsString() : "";
            String icon = space.has("icon") ? space.get("icon").getAsString() : "🛋";
            int level = space.has("level") ? space.get("level").getAsInt() : 1;
            names.add(icon + " " + sName + " (房间)");
            ids.add(id);
            levels.add(level);

            if (space.has("children") && !space.get("children").isJsonNull()) {
                JsonArray children = space.getAsJsonArray("children");
                for (int j = 0; j < children.size(); j++) {
                    JsonObject child = children.get(j).getAsJsonObject();
                    int cId = child.has("id") ? child.get("id").getAsInt() : 0;
                    String cName = child.has("name") ? child.get("name").getAsString() : "";
                    String cIcon = child.has("icon") ? child.get("icon").getAsString() : "📦";
                    int cLevel = child.has("level") ? child.get("level").getAsInt() : 2;
                    names.add("  " + cIcon + " " + cName + " (容器)");
                    ids.add(cId);
                    levels.add(cLevel);
                }
            }
        }

        String[] nameArr = names.toArray(new String[0]);

        new AlertDialog.Builder(this)
            .setTitle("选择上级空间")
            .setItems(nameArr, (d, which) -> {
                int chosenId = ids.get(which);
                int chosenLevel = levels.get(which);
                if (chosenId > 0) {
                    parentSpaceId = chosenId;
                    parentSpaceName = nameArr[which];
                    tvParentSpace.setText(parentSpaceName);
                    if (chosenLevel == 1) selectedLevel = 2;
                    else if (chosenLevel == 2) selectedLevel = 3;
                } else {
                    parentSpaceId = 0;
                    parentSpaceName = "";
                    tvParentSpace.setText("一级空间 (无上级)");
                    selectedLevel = 1;
                }
            })
            .setNegativeButton("取消", null)
            .show();
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

            String url;
            if (editSpaceId > 0) {
                body.addProperty("id", editSpaceId);
                url = "space.php?action=update";
            } else {
                url = "space.php?action=create";
            }

            ApiClient.post(url, body, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    runOnUiThread(() -> {
                        Toast.makeText(AddSpaceActivity.this, editSpaceId > 0 ? "✅ 修改成功" : "✅ 创建成功", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(AddSpaceActivity.this, (editSpaceId > 0 ? "修改" : "创建") + "失败: " + msg, Toast.LENGTH_SHORT).show();
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

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}

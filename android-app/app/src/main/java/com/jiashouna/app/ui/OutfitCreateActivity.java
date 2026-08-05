package com.jiashouna.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.*;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.util.*;

/**
 * 创建/编辑套装
 * 支持多维筛选（分类/颜色/季节）快速添加物品
 */
public class OutfitCreateActivity extends AppCompatActivity {
    private EditText etName, etNote, etSearch;
    private Spinner spSeason, spOccasion, spFilterCategory, spFilterColor, spFilterSeason;
    private LinearLayout layoutSelected, layoutPicker;
    private TextView tvSelectedCount, tvPickerHint;
    private Button btnSave;

    private boolean isEditMode = false;
    private int editOutfitId = 0;
    private int houseId = 0;

    // 已选物品: goodsId -> {name, slot, color, category, coverImage}
    private LinkedHashMap<Integer, SelectedItem> selectedItems = new LinkedHashMap<>();
    // 物品池
    private JsonArray allClothing = new JsonArray();

    private static final String[] SLOT_NAMES = {"上装", "下装", "帽子", "鞋子", "外套", "配饰"};
    private static final String[] SLOT_VALUES = {"top", "bottom", "hat", "shoes", "outer", "accessory"};

    static class SelectedItem {
        int goodsId;
        String name;
        String slot;
        String color;
        String category;
        String coverImage;
        String season;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outfit_create);

        isEditMode = getIntent().getBooleanExtra("edit_mode", false);
        editOutfitId = getIntent().getIntExtra("outfit_id", 0);
        houseId = App.getInstance().getCurrentHouseId();

        // 如果从 AddItemActivity 传入预选物品
        int preselectGoodsId = getIntent().getIntExtra("preselect_goods_id", 0);
        String preselectGoodsName = getIntent().getStringExtra("preselect_goods_name");
        String preselectGoodsCategory = getIntent().getStringExtra("preselect_goods_category");
        String preselectGoodsColor = getIntent().getStringExtra("preselect_goods_color");
        String preselectGoodsCover = getIntent().getStringExtra("preselect_goods_cover");

        initViews();
        loadClothingItems();

        // 预选物品
        if (preselectGoodsId > 0) {
            SelectedItem si = new SelectedItem();
            si.goodsId = preselectGoodsId;
            si.name = preselectGoodsName != null ? preselectGoodsName : "";
            si.slot = guessSlot(si.name, preselectGoodsCategory != null ? preselectGoodsCategory : "");
            si.color = preselectGoodsColor != null ? preselectGoodsColor : "";
            si.category = preselectGoodsCategory != null ? preselectGoodsCategory : "";
            si.coverImage = preselectGoodsCover != null ? preselectGoodsCover : "";
            selectedItems.put(preselectGoodsId, si);
            renderSelected();
        }

        if (isEditMode && editOutfitId > 0) {
            loadOutfitDetail();
        }
    }

    private void initViews() {
        etName = findViewById(R.id.et_name);
        etNote = findViewById(R.id.et_note);
        etSearch = findViewById(R.id.et_search);
        spSeason = findViewById(R.id.sp_season);
        spOccasion = findViewById(R.id.sp_occasion);
        spFilterCategory = findViewById(R.id.sp_filter_category);
        spFilterColor = findViewById(R.id.sp_filter_color);
        spFilterSeason = findViewById(R.id.sp_filter_season);
        layoutSelected = findViewById(R.id.layout_selected);
        layoutPicker = findViewById(R.id.layout_picker);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        tvPickerHint = findViewById(R.id.tv_picker_hint);
        btnSave = findViewById(R.id.btn_save);

        // 返回
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 季度 Spinner
        String[] seasons = {"不指定", "春", "夏", "秋", "冬", "四季", "春秋"};
        spSeason.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, seasons));

        // 场合 Spinner
        String[] occasions = {"不指定", "通勤", "运动", "约会", "居家", "正装", "休闲"};
        spOccasion.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, occasions));

        // 筛选 - 分类
        String[] cats = {"全部分类", "服装", "鞋帽"};
        spFilterCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cats));
        spFilterCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { filterPicker(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // 筛选 - 颜色
        String[] colors = {"全部颜色"};
        spFilterColor.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, colors));
        spFilterColor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { filterPicker(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // 筛选 - 季节
        String[] filterSeasons = {"全部季节", "春", "夏", "秋", "冬", "四季", "春秋"};
        spFilterSeason.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filterSeasons));
        spFilterSeason.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { filterPicker(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // 搜索
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            filterPicker();
            return true;
        });

        // 保存
        btnSave.setOnClickListener(v -> saveOutfit());
    }

    private void loadClothingItems() {
        if (houseId <= 0) return;

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        params.put("category", "服装");
        params.put("page_size", "100");

        // 加载服装
        ApiClient.get("goods.php?action=list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
                        for (int i = 0; i < list.size(); i++) allClothing.add(list.get(i));
                    } catch (Exception ignored) {}

                    // 再加载鞋帽
                    HashMap<String, String> params2 = new HashMap<>();
                    params2.put("house_id", String.valueOf(houseId));
                    params2.put("category", "鞋帽");
                    params2.put("page_size", "100");
                    ApiClient.get("goods.php?action=list", params2, new ApiClient.ApiCallback() {
                        @Override public void onSuccess(JsonObject data2) {
                            runOnUiThread(() -> {
                                try {
                                    JsonArray list2 = data2.has("list") ? data2.getAsJsonArray("list") : new JsonArray();
                                    for (int i = 0; i < list2.size(); i++) allClothing.add(list2.get(i));
                                } catch (Exception ignored) {}
                                updateColorFilter();
                                filterPicker();
                            });
                        }
                        @Override public void onError(String msg) { runOnUiThread(() -> filterPicker()); }
                    });
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void updateColorFilter() {
        Set<String> colors = new TreeSet<>();
        for (int i = 0; i < allClothing.size(); i++) {
            JsonObject item = allClothing.get(i).getAsJsonObject();
            String c = item.has("color") && !item.get("color").isJsonNull() ? item.get("color").getAsString() : "";
            if (!c.isEmpty()) colors.add(c);
        }
        List<String> colorList = new ArrayList<>();
        colorList.add("全部颜色");
        colorList.addAll(colors);
        spFilterColor.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, colorList.toArray(new String[0])));
    }

    private void filterPicker() {
        layoutPicker.removeAllViews();
        String search = etSearch.getText().toString().trim().toLowerCase();
        String catFilter = spFilterCategory.getSelectedItemPosition() > 0 ? spFilterCategory.getSelectedItem().toString() : "";
        String colorFilter = spFilterColor.getSelectedItemPosition() > 0 ? spFilterColor.getSelectedItem().toString() : "";
        String seasonFilter = spFilterSeason.getSelectedItemPosition() > 0 ? spFilterSeason.getSelectedItem().toString() : "";

        int shown = 0;
        for (int i = 0; i < allClothing.size(); i++) {
            JsonObject item = allClothing.get(i).getAsJsonObject();
            int gid = item.get("id").getAsInt();
            String name = item.has("name") ? item.get("name").getAsString() : "";
            String cat = item.has("category") && !item.get("category").isJsonNull() ? item.get("category").getAsString() : "";
            String color = item.has("color") && !item.get("color").isJsonNull() ? item.get("color").getAsString() : "";
            String season = item.has("season") && !item.get("season").isJsonNull() ? item.get("season").getAsString() : "";
            String cover = item.has("cover_image") && !item.get("cover_image").isJsonNull() ? item.get("cover_image").getAsString() : "";

            // 筛选
            if (!search && !name.toLowerCase().contains(search) && !color.toLowerCase().contains(search)) {
                // 如果有搜索词但不匹配名称和颜色，跳过
            }
            if (!search.isEmpty() && !name.toLowerCase().contains(search) && !color.toLowerCase().contains(search)) continue;
            if (!catFilter.isEmpty() && !cat.equals(catFilter)) continue;
            if (!colorFilter.isEmpty() && !color.equals(colorFilter)) continue;
            if (!seasonFilter.isEmpty() && !season.equals(seasonFilter) && !season.equals("四季")) continue;

            addPickerItem(gid, name, cat, color, season, cover);
            shown++;
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("没有匹配的物品");
            empty.setTextColor(0xFFA0AEC0);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(32), 0, dp(32));
            layoutPicker.addView(empty);
        }

        tvPickerHint.setText("可选物品 (" + shown + " 件)");
    }

    private void addPickerItem(int gid, String name, String cat, String color, String season, String cover) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowLp);

        boolean isSelected = selectedItems.containsKey(gid);
        row.setBackgroundColor(isSelected ? 0xFFFFF7F0 : 0xFFFFFFFF);

        // 缩略图
        FrameLayout imgFrame = new FrameLayout(this);
        imgFrame.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        android.widget.ImageView img = new android.widget.ImageView(this);
        img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        img.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        android.graphics.drawable.GradientDrawable imgBg = new android.graphics.drawable.GradientDrawable();
        imgBg.setCornerRadius(dp(8));
        imgBg.setColor(0xFFEDF2F7);
        img.setBackground(imgBg);
        if (!cover.isEmpty()) {
            try { com.bumptech.glide.Glide.with(this).load(cover).centerCrop().into(img); } catch (Exception ignored) {}
        }
        imgFrame.addView(img);
        row.addView(imgFrame);

        // 信息
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.leftMargin = dp(12);
        info.setLayoutParams(infoLp);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(14);
        tvName.setTextColor(0xFF2D3748);
        tvName.setTypeface(null, Typeface.BOLD);
        info.addView(tvName);

        String meta = cat;
        if (!color.isEmpty()) meta += " · " + color;
        if (!season.isEmpty()) meta += " · " + season;
        TextView tvMeta = new TextView(this);
        tvMeta.setText(meta);
        tvMeta.setTextSize(11);
        tvMeta.setTextColor(0xFF718096);
        info.addView(tvMeta);

        row.addView(info);

        // 选中状态
        TextView check = new TextView(this);
        check.setText(isSelected ? "✅" : "➕");
        check.setTextSize(18);
        check.setPadding(dp(8), 0, 0, 0);
        row.addView(check);

        row.setClickable(true);
        row.setFocusable(true);
        final int finalGid = gid;
        final String finalName = name;
        final String finalCat = cat;
        final String finalColor = color;
        final String finalSeason = season;
        final String finalCover = cover;
        row.setOnClickListener(v -> {
            if (selectedItems.containsKey(finalGid)) {
                selectedItems.remove(finalGid);
            } else {
                SelectedItem si = new SelectedItem();
                si.goodsId = finalGid;
                si.name = finalName;
                si.slot = guessSlot(finalName, finalCat);
                si.color = finalColor;
                si.category = finalCat;
                si.coverImage = finalCover;
                si.season = finalSeason;
                selectedItems.put(finalGid, si);
            }
            renderSelected();
            filterPicker(); // 刷新选中状态
        });

        layoutPicker.addView(row);

        // 分割线
        View divider = new View(this);
        divider.setBackgroundColor(0xFFF7FAFC);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        layoutPicker.addView(divider);
    }

    private void renderSelected() {
        layoutSelected.removeAllViews();
        tvSelectedCount.setText(selectedItems.size() + " 件");

        if (selectedItems.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("点击下方物品列表选择");
            hint.setTextSize(12);
            hint.setTextColor(0xFFA0AEC0);
            hint.setPadding(0, dp(8), 0, dp(8));
            layoutSelected.addView(hint);
            return;
        }

        for (Map.Entry<Integer, SelectedItem> entry : selectedItems.entrySet()) {
            SelectedItem si = entry.getValue();
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
            chipBg.setCornerRadius(dp(10));
            chipBg.setColor(0xFFFFFAF0);
            chipBg.setStroke(dp(1), 0xFFFFD3B0);
            chip.setBackground(chipBg);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.setMargins(0, 0, dp(8), dp(6));
            chip.setLayoutParams(chipLp);

            TextView tvName = new TextView(this);
            tvName.setText(si.name);
            tvName.setTextSize(12);
            tvName.setTextColor(0xFF2D3748);
            chip.addView(tvName);

            // Slot 下拉
            Spinner spSlot = new Spinner(this);
            spSlot.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SLOT_NAMES));
            // 选中当前slot
            for (int j = 0; j < SLOT_VALUES.length; j++) {
                if (SLOT_VALUES[j].equals(si.slot)) { spSlot.setSelection(j); break; }
            }
            spSlot.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    si.slot = SLOT_VALUES[position];
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
            spSlot.setLayoutParams(new LinearLayout.LayoutParams(dp(70), dp(30)));
            LinearLayout.LayoutParams spLp = (LinearLayout.LayoutParams) spSlot.getLayoutParams();
            spLp.leftMargin = dp(6);
            chip.addView(spSlot);

            // 删除
            TextView remove = new TextView(this);
            remove.setText("✕");
            remove.setTextSize(14);
            remove.setTextColor(0xFFF56565);
            remove.setPadding(dp(6), 0, 0, 0);
            remove.setOnClickListener(v -> {
                selectedItems.remove(entry.getKey());
                renderSelected();
                filterPicker();
            });
            chip.addView(remove);

            layoutSelected.addView(chip);
        }
    }

    private String guessSlot(String name, String category) {
        if (category.equals("鞋帽") || name.contains("鞋") || name.contains("靴")) return "shoes";
        if (name.contains("帽")) return "hat";
        if (name.contains("裤") || name.contains("裙")) return "bottom";
        if (name.contains("外套") || name.contains("夹克") || name.contains("大衣") || name.contains("羽绒")) return "outer";
        if (name.contains("包") || name.contains("袋") || name.contains("项链") || name.contains("手表")) return "accessory";
        return "top";
    }

    private void loadOutfitDetail() {
        ApiClient.get("outfit.php?action=detail&id=" + editOutfitId, null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonObject o = data.getAsJsonObject("outfit");
                        etName.setText(o.has("name") ? o.get("name").getAsString() : "");
                        etNote.setText(o.has("note") && !o.get("note").isJsonNull() ? o.get("note").getAsString() : "");

                        String season = o.has("season") && !o.get("season").isJsonNull() ? o.get("season").getAsString() : "";
                        String occasion = o.has("occasion") && !o.get("occasion").isJsonNull() ? o.get("occasion").getAsString() : "";

                        // 选中season
                        String[] seasonArr = {"", "春", "夏", "秋", "冬", "四季", "春秋"};
                        for (int i = 0; i < seasonArr.length; i++) {
                            if (seasonArr[i].equals(season)) { spSeason.setSelection(i); break; }
                        }
                        String[] occasionArr = {"", "通勤", "运动", "约会", "居家", "正装", "休闲"};
                        for (int i = 0; i < occasionArr.length; i++) {
                            if (occasionArr[i].equals(occasion)) { spOccasion.setSelection(i); break; }
                        }

                        // 加载已关联物品
                        if (o.has("items") && !o.get("items").isJsonNull()) {
                            JsonArray items = o.getAsJsonArray("items");
                            for (int i = 0; i < items.size(); i++) {
                                JsonObject it = items.get(i).getAsJsonObject();
                                SelectedItem si = new SelectedItem();
                                si.goodsId = it.get("goods_id").getAsInt();
                                si.name = it.has("goods_name") ? it.get("goods_name").getAsString() : "";
                                si.slot = it.has("slot") && !it.get("slot").isJsonNull() ? it.get("slot").getAsString() : "top";
                                si.color = it.has("color") && !it.get("color").isJsonNull() ? it.get("color").getAsString() : "";
                                si.category = it.has("category") && !it.get("category").isJsonNull() ? it.get("category").getAsString() : "";
                                si.coverImage = it.has("cover_image") && !it.get("cover_image").isJsonNull() ? it.get("cover_image").getAsString() : "";
                                selectedItems.put(si.goodsId, si);
                            }
                            renderSelected();
                            filterPicker();
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void saveOutfit() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入套装名称", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("house_id", houseId);
        body.addProperty("name", name);

        String season = spSeason.getSelectedItemPosition() > 0 ? spSeason.getSelectedItem().toString() : "";
        String occasion = spOccasion.getSelectedItemPosition() > 0 ? spOccasion.getSelectedItem().toString() : "";
        body.addProperty("season", season);
        body.addProperty("occasion", occasion);
        body.addProperty("note", etNote.getText().toString().trim());

        if (isEditMode && editOutfitId > 0) {
            body.addProperty("id", editOutfitId);
        }

        JsonArray items = new JsonArray();
        for (SelectedItem si : selectedItems.values()) {
            JsonObject item = new JsonObject();
            item.addProperty("goods_id", si.goodsId);
            item.addProperty("slot", si.slot);
            items.add(item);
        }
        body.add("items", items);

        btnSave.setEnabled(false);
        btnSave.setText("保存中...");

        String url = isEditMode ? "outfit.php?action=update" : "outfit.php?action=create";

        ApiClient.post(url, body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    Toast.makeText(OutfitCreateActivity.this, "✅ 保存成功", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("💾 保存套装");
                    Toast.makeText(OutfitCreateActivity.this, "保存失败: " + msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

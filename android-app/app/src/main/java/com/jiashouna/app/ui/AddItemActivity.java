package com.jiashouna.app.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
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
import com.jiashouna.app.model.Goods;
import com.jiashouna.app.utils.NetworkUtils;

import java.util.*;

public class AddItemActivity extends AppCompatActivity {
    private EditText etName, etBarcode, etQuantity, etUnit, etExpiry, etPrice, etNote;
    private View spacePicker;
    private TextView tvSpaceName, tvSpacePath;
    private Switch swPrivate;
    private Button btnSave;
    private FrameLayout scanContainer;
    private View tabScan, tabPhoto, tabManual;
    private int selectedSpaceId = 0;
    private String selectedSpaceName = "";
    private LocalDb localDb;
    private JsonArray spaceList = new JsonArray();
    private String currentMode = "scan";

    private static final int REQUEST_BARCODE_SCAN = 100;
    private static final int REQUEST_PHOTO = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        localDb = new LocalDb(this);

        currentMode = getIntent().getStringExtra("mode");
        if (currentMode == null) currentMode = "scan";

        etName = findViewById(R.id.et_name);
        etBarcode = findViewById(R.id.et_barcode);
        etQuantity = findViewById(R.id.et_quantity);
        etUnit = findViewById(R.id.et_unit);
        etExpiry = findViewById(R.id.et_expiry);
        etPrice = findViewById(R.id.et_price);
        etNote = findViewById(R.id.et_note);
        spacePicker = findViewById(R.id.space_picker);
        tvSpaceName = findViewById(R.id.tv_space_name);
        tvSpacePath = findViewById(R.id.tv_space_path);
        swPrivate = findViewById(R.id.sw_private);
        btnSave = findViewById(R.id.btn_save);
        scanContainer = findViewById(R.id.scan_container);
        tabScan = findViewById(R.id.tab_scan);
        tabPhoto = findViewById(R.id.tab_photo);
        tabManual = findViewById(R.id.tab_manual);

        // 选项卡切换
        tabScan.setOnClickListener(v -> switchTab("scan"));
        tabPhoto.setOnClickListener(v -> switchTab("photo"));
        tabManual.setOnClickListener(v -> switchTab("manual"));

        switchTab(currentMode);

        // 保质期
        etExpiry.setFocusable(false);
        etExpiry.setClickable(true);
        etExpiry.setOnClickListener(v -> showDatePicker());

        // 空间选择
        spacePicker.setOnClickListener(v -> showSpacePickerDialog());

        // 保存
        btnSave.setOnClickListener(v -> saveItem());

        loadSpaces();
    }

    private void switchTab(String mode) {
        currentMode = mode;
        tabScan.setTextColor(Color.parseColor("#718096"));
        tabPhoto.setTextColor(Color.parseColor("#718096"));
        tabManual.setTextColor(Color.parseColor("#718096"));
        ((TextView) tabScan).setTypeface(null, android.graphics.Typeface.NORMAL);
        ((TextView) tabPhoto).setTypeface(null, android.graphics.Typeface.NORMAL);
        ((TextView) tabManual).setTypeface(null, android.graphics.Typeface.NORMAL);

        switch (mode) {
            case "scan":
                tabScan.setTextColor(Color.parseColor("#FF8C42"));
                ((TextView) tabScan).setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                startBarcodeScan();
                break;
            case "photo":
                tabPhoto.setTextColor(Color.parseColor("#FF8C42"));
                ((TextView) tabPhoto).setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                startPhotoCapture();
                break;
            case "manual":
                tabManual.setTextColor(Color.parseColor("#FF8C42"));
                ((TextView) tabManual).setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.GONE);
                break;
        }
    }

    private void startBarcodeScan() {
        try {
            // 尝试使用ZXing的SCAN intent
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "PRODUCT_MODE");
            startActivityForResult(intent, REQUEST_BARCODE_SCAN);
        } catch (Exception e) {
            // 如果没有安装ZXing扫描器，使用系统相机
            try {
                Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, REQUEST_BARCODE_SCAN);
            } catch (Exception e2) {
                Toast.makeText(this, "未找到扫码应用，请手动输入条码", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startPhotoCapture() {
        try {
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, REQUEST_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "未找到相机应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_BARCODE_SCAN) {
            // 尝试从ZXing结果获取条码
            String barcode = data.getStringExtra("SCAN_RESULT");
            if (barcode != null && !barcode.isEmpty()) {
                etBarcode.setText(barcode);
                lookupBarcode(barcode);
            } else {
                Toast.makeText(this, "未识别到条码，请手动输入", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_PHOTO) {
            Toast.makeText(this, "拍照识别功能开发中，请手动输入物品信息", Toast.LENGTH_SHORT).show();
        }
    }

    private void lookupBarcode(String barcode) {
        HashMap<String, String> params = new HashMap<>();
        params.put("barcode", barcode);
        ApiClient.get("barcode.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (data.has("name") && !data.get("name").isJsonNull()) {
                            etName.setText(data.get("name").getAsString());
                        }
                        Toast.makeText(AddItemActivity.this, "✅ 已识别商品", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(AddItemActivity.this, "未找到该条码对应的商品", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadSpaces() {
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) return;

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("space.php?action=tree", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (data.has("tree") && !data.get("tree").isJsonNull()) {
                            spaceList = flattenTree(data.getAsJsonArray("tree"));
                        } else if (data.has("list") && !data.get("list").isJsonNull()) {
                            spaceList = data.getAsJsonArray("list");
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private JsonArray flattenTree(JsonArray tree) {
        JsonArray result = new JsonArray();
        flattenTreeRecursive(tree, "", result);
        return result;
    }

    private void flattenTreeRecursive(JsonArray items, String prefix, JsonArray result) {
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            String name = item.has("name") ? item.get("name").getAsString() : "";
            item.addProperty("display_name", prefix + name);
            result.add(item);
            if (item.has("children") && !item.get("children").isJsonNull()) {
                JsonArray children = item.getAsJsonArray("children");
                if (children.size() > 0) {
                    flattenTreeRecursive(children, prefix + name + " > ", result);
                }
            }
        }
    }

    private void showSpacePickerDialog() {
        if (spaceList.size() == 0) {
            Toast.makeText(this, "暂无空间，请先创建收纳空间", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[spaceList.size()];
        for (int i = 0; i < spaceList.size(); i++) {
            JsonObject item = spaceList.get(i).getAsJsonObject();
            names[i] = item.has("display_name") ? item.get("display_name").getAsString() : item.get("name").getAsString();
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择存放位置")
            .setItems(names, (dialog, which) -> {
                JsonObject selected = spaceList.get(which).getAsJsonObject();
                selectedSpaceId = selected.get("id").getAsInt();
                selectedSpaceName = selected.has("display_name") ? selected.get("display_name").getAsString() : selected.get("name").getAsString();
                String icon = selected.has("icon") && !selected.get("icon").isJsonNull() ? selected.get("icon").getAsString() : "🏠";
                tvSpaceName.setText(icon + " " + selected.get("name").getAsString());
                tvSpacePath.setText(selectedSpaceName);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            etExpiry.setText(year + "-" + String.format("%02d", month + 1) + "-" + String.format("%02d", day));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveItem() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("请输入物品名称");
            etName.requestFocus();
            return;
        }

        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            Toast.makeText(this, "请先创建或加入一个家庭", Toast.LENGTH_SHORT).show();
            return;
        }

        Goods goods = new Goods();
        goods.houseId = houseId;
        goods.spaceId = selectedSpaceId;
        goods.name = name;
        goods.barcode = etBarcode.getText().toString().trim();
        goods.category = "";
        String qtyStr = etQuantity.getText().toString().trim();
        goods.quantity = qtyStr.isEmpty() ? 1 : Double.parseDouble(qtyStr);
        goods.unit = etUnit.getText().toString().trim().isEmpty() ? "个" : etUnit.getText().toString().trim();
        goods.expiryDate = etExpiry.getText().toString().trim();
        goods.note = etNote.getText().toString().trim();
        goods.isPrivate = swPrivate.isChecked() ? 1 : 0;

        btnSave.setEnabled(false);
        btnSave.setText("保存中...");

        if (NetworkUtils.isNetworkAvailable(this)) {
            JsonObject body = new JsonObject();
            body.addProperty("house_id", goods.houseId);
            body.addProperty("space_id", goods.spaceId);
            body.addProperty("name", goods.name);
            body.addProperty("barcode", goods.barcode);
            body.addProperty("category", goods.category);
            body.addProperty("quantity", goods.quantity);
            body.addProperty("unit", goods.unit);
            body.addProperty("expiry_date", goods.expiryDate);
            body.addProperty("note", goods.note);
            body.addProperty("is_private", goods.isPrivate);

            ApiClient.post("goods.php?action=create", body, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    runOnUiThread(() -> {
                        Toast.makeText(AddItemActivity.this, "✅ 保存成功", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("保存");
                        Toast.makeText(AddItemActivity.this, "保存失败: " + msg, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            localDb.saveOfflineGoods(goods);
            Toast.makeText(this, "已保存到本地，联网后自动同步", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}

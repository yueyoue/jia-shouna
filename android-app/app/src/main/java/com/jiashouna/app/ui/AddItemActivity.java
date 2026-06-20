package com.jiashouna.app.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.db.LocalDb;
import com.jiashouna.app.model.Goods;
import com.jiashouna.app.utils.NetworkUtils;
import com.journeyapps.barcodescanner.CaptureActivity;

import java.io.*;
import java.util.*;

public class AddItemActivity extends AppCompatActivity {
    private EditText etName, etBarcode, etQuantity, etUnit, etExpiryDays, etPrice, etNote;
    private View spacePicker;
    private TextView tvSpaceName, tvSpacePath, tvScanHint;
    private Switch swPrivate;
    private Button btnSave, btnStartScan, btnAddPhoto, btnAddTag;
    private LinearLayout scanContainer, llPhotos, llTags;
    private TextView tabScan, tabPhoto, tabManual;
    private int selectedSpaceId = 0;
    private LocalDb localDb;
    private JsonArray spaceList = new JsonArray();
    private JsonArray tagList = new JsonArray();
    private List<String> selectedTags = new ArrayList<>();
    private List<Bitmap> photos = new ArrayList<>();

    private static final int REQUEST_BARCODE = 100;
    private static final int REQUEST_PHOTO = 101;
    private static final int REQUEST_GALLERY = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        localDb = new LocalDb(this);

        String mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "scan";

        etName = findViewById(R.id.et_name);
        etBarcode = findViewById(R.id.et_barcode);
        etQuantity = findViewById(R.id.et_quantity);
        etUnit = findViewById(R.id.et_unit);
        etExpiryDays = findViewById(R.id.et_expiry_days);
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
        tvScanHint = findViewById(R.id.tv_scan_hint);
        btnStartScan = findViewById(R.id.btn_start_scan);
        btnAddPhoto = findViewById(R.id.btn_add_photo);
        btnAddTag = findViewById(R.id.btn_add_tag);
        llPhotos = findViewById(R.id.ll_photos);
        llTags = findViewById(R.id.ll_tags);

        // 选项卡
        tabScan.setOnClickListener(v -> switchTab("scan"));
        tabPhoto.setOnClickListener(v -> switchTab("photo"));
        tabManual.setOnClickListener(v -> switchTab("manual"));

        btnStartScan.setOnClickListener(v -> startBarcodeScan());

        switchTab(mode);

        // 空间选择
        spacePicker.setOnClickListener(v -> showSpacePickerDialog());

        // 添加照片
        btnAddPhoto.setOnClickListener(v -> showPhotoOptions());

        // 添加标签
        btnAddTag.setOnClickListener(v -> showTagDialog());

        // 保存
        btnSave.setOnClickListener(v -> saveItem());

        loadSpaces();
        loadTags();
    }

    private void switchTab(String mode) {
        tabScan.setTextColor(Color.parseColor("#718096"));
        tabPhoto.setTextColor(Color.parseColor("#718096"));
        tabManual.setTextColor(Color.parseColor("#718096"));
        tabScan.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabPhoto.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabManual.setTypeface(null, android.graphics.Typeface.NORMAL);

        switch (mode) {
            case "scan":
                tabScan.setTextColor(Color.parseColor("#FF8C42"));
                tabScan.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                tvScanHint.setText("点击下方按钮开始扫码识别条形码");
                btnStartScan.setText("📷 开始扫码");
                btnStartScan.setOnClickListener(v -> startBarcodeScan());
                break;
            case "photo":
                tabPhoto.setTextColor(Color.parseColor("#FF8C42"));
                tabPhoto.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                tvScanHint.setText("拍照识别物品，自动填充信息");
                btnStartScan.setText("📸 拍照识别");
                btnStartScan.setOnClickListener(v -> startPhotoCapture());
                break;
            case "manual":
                tabManual.setTextColor(Color.parseColor("#FF8C42"));
                tabManual.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.GONE);
                break;
        }
    }

    private void startBarcodeScan() {
        try {
            Intent intent = new Intent(this, CaptureActivity.class);
            intent.putExtra("SCAN_MODE", "PRODUCT_MODE");
            intent.putExtra("SCAN_FORMATS", "QR_CODE,DATA_MATRIX,EAN_13,EAN_8,CODE_128,CODE_39");
            startActivityForResult(intent, REQUEST_BARCODE);
        } catch (Exception e) {
            Toast.makeText(this, "扫码启动失败，请使用手动录入", Toast.LENGTH_SHORT).show();
        }
    }

    private void startPhotoCapture() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQUEST_PHOTO);
            } else {
                Toast.makeText(this, "未找到相机", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "相机启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhotoOptions() {
        String[] options = {"拍照", "从相册选择"};
        new AlertDialog.Builder(this)
            .setTitle("添加照片")
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    startPhotoCapture();
                } else {
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent, REQUEST_GALLERY);
                }
            })
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_BARCODE && data != null) {
            String barcode = data.getStringExtra("SCAN_RESULT");
            if (barcode != null && !barcode.isEmpty()) {
                etBarcode.setText(barcode);
                lookupBarcode(barcode);
            }
        } else if (requestCode == REQUEST_PHOTO && data != null) {
            try {
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap bitmap = (Bitmap) extras.get("data");
                    if (bitmap != null) {
                        addPhotoToList(bitmap);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "拍照处理失败", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_GALLERY && data != null) {
            try {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    addPhotoToList(bitmap);
                }
            } catch (Exception e) {
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addPhotoToList(Bitmap bitmap) {
        photos.add(bitmap);
        // 添加照片预览
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bitmap);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(80), dp(80));
        lp.rightMargin = dp(8);
        iv.setLayoutParams(lp);
        iv.setBackgroundResource(R.drawable.bg_card_16);
        llPhotos.addView(iv);
        Toast.makeText(this, "📷 已添加照片 (" + photos.size() + "/3)", Toast.LENGTH_SHORT).show();
    }

    private void lookupBarcode(String barcode) {
        HashMap<String, String> params = new HashMap<>();
        params.put("action", "lookup");
        params.put("barcode", barcode);
        ApiClient.get("barcode.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (data.has("found") && data.get("found").getAsBoolean()) {
                            if (data.has("data") && !data.get("data").isJsonNull()) {
                                JsonObject info = data.getAsJsonObject("data");
                                if (info.has("name") && !info.get("name").isJsonNull()) {
                                    etName.setText(info.get("name").getAsString());
                                }
                            }
                            Toast.makeText(AddItemActivity.this, "✅ 已识别商品", Toast.LENGTH_SHORT).show();
                        } else {
                            String msg = data.has("msg") ? data.get("msg").getAsString() : "未找到该条码对应的商品";
                            Toast.makeText(AddItemActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(AddItemActivity.this, "查询成功但解析失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(AddItemActivity.this, "条码查询失败: " + msg, Toast.LENGTH_SHORT).show());
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
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void loadTags() {
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) return;

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("tag.php?action=list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            tagList = data.getAsJsonArray("list");
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void showTagDialog() {
        if (tagList.size() == 0) {
            // 没有标签，提示创建
            EditText input = new EditText(this);
            input.setHint("输入标签名称");
            new AlertDialog.Builder(this)
                .setTitle("创建标签")
                .setView(input)
                .setPositiveButton("创建", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) createTag(name);
                })
                .setNegativeButton("取消", null)
                .show();
            return;
        }

        String[] names = new String[tagList.size()];
        boolean[] checked = new boolean[tagList.size()];
        for (int i = 0; i < tagList.size(); i++) {
            JsonObject tag = tagList.get(i).getAsJsonObject();
            names[i] = tag.has("name") ? tag.get("name").getAsString() : "";
            checked[i] = selectedTags.contains(names[i]);
        }

        new AlertDialog.Builder(this)
            .setTitle("选择标签")
            .setMultiChoiceItems(names, checked, (d, which, isChecked) -> {
                if (isChecked) {
                    selectedTags.add(names[which]);
                } else {
                    selectedTags.remove(names[which]);
                }
            })
            .setPositiveButton("确定", (d, w) -> updateTagDisplay())
            .setNeutralButton("+ 新建", (d, w) -> {
                EditText input = new EditText(this);
                input.setHint("输入标签名称");
                new AlertDialog.Builder(this)
                    .setTitle("创建标签")
                    .setView(input)
                    .setPositiveButton("创建", (d2, w2) -> {
                        String name = input.getText().toString().trim();
                        if (!name.isEmpty()) createTag(name);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            })
            .show();
    }

    private void createTag(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("house_id", App.getInstance().getCurrentHouseId());
        body.addProperty("name", name);
        ApiClient.post("tag.php?action=create", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    Toast.makeText(AddItemActivity.this, "标签已创建", Toast.LENGTH_SHORT).show();
                    loadTags();
                    selectedTags.add(name);
                    updateTagDisplay();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(AddItemActivity.this, "创建失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateTagDisplay() {
        llTags.removeAllViews();
        for (String tag : selectedTags) {
            TextView tv = new TextView(this);
            tv.setText(tag);
            tv.setTextSize(12);
            tv.setTextColor(Color.parseColor("#5B9FED"));
            tv.setBackgroundResource(R.drawable.bg_tag_blue);
            tv.setPadding(dp(12), dp(4), dp(12), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(4);
            tv.setLayoutParams(lp);
            llTags.addView(tv);
        }
    }

    private JsonArray flattenTree(JsonArray tree) {
        JsonArray result = new JsonArray();
        flattenRecursive(tree, "", result);
        return result;
    }

    private void flattenRecursive(JsonArray items, String prefix, JsonArray result) {
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            String name = item.has("name") ? item.get("name").getAsString() : "";
            item.addProperty("display_name", prefix + name);
            result.add(item);
            if (item.has("children") && !item.get("children").isJsonNull()) {
                JsonArray children = item.getAsJsonArray("children");
                if (children.size() > 0) flattenRecursive(children, prefix + name + " > ", result);
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

        new AlertDialog.Builder(this)
            .setTitle("选择存放位置")
            .setItems(names, (dialog, which) -> {
                JsonObject selected = spaceList.get(which).getAsJsonObject();
                selectedSpaceId = selected.get("id").getAsInt();
                String icon = selected.has("icon") && !selected.get("icon").isJsonNull() ? selected.get("icon").getAsString() : "🏠";
                tvSpaceName.setText(icon + " " + selected.get("name").getAsString());
                tvSpacePath.setText(selected.has("display_name") ? selected.get("display_name").getAsString() : "");
            })
            .setNegativeButton("取消", null)
            .show();
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
        // 保质期：天数转日期
        String daysStr = etExpiryDays.getText().toString().trim();
        if (!daysStr.isEmpty()) {
            try {
                int days = Integer.parseInt(daysStr);
                long expiryMillis = System.currentTimeMillis() + (long) days * 24 * 60 * 60 * 1000;
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                goods.expiryDate = sdf.format(new java.util.Date(expiryMillis));
            } catch (Exception e) {
                goods.expiryDate = "";
            }
        }
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
                        // 上传照片
                        if (photos.size() > 0 && data.has("id")) {
                            int goodsId = data.get("id").getAsInt();
                            uploadPhotos(goodsId);
                        }
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

    private void uploadPhotos(int goodsId) {
        // 异步上传照片，不阻塞UI
        new Thread(() -> {
            for (Bitmap photo : photos) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    photo.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    byte[] imageBytes = baos.toByteArray();
                    // TODO: 使用multipart上传到 upload.php?action=image
                } catch (Exception e) {}
            }
        }).start();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

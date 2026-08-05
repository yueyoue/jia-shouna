package com.jiashouna.app.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.Gravity;
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
import com.jiashouna.app.ui.BarcodeScanActivity;

import java.io.*;
import java.util.*;

public class AddItemActivity extends AppCompatActivity {
    private EditText etName, etBarcode, etBrand, etSpec, etManufacturer, etQuantity, etExpiryDays, etPrice, etNote, etThreshold;
    private Spinner etUnit;
    private EditText etPurchaseDate;
    private TextView tvExpiryDateAuto;
    private Spinner spExpiryUnit, spCategory, spExpiryReminder, spSeason;
    private View btnCreateOutfit;
    private String selectedPurchaseDate = "";
    private View spacePicker;
    private TextView tvSpaceName, tvSpacePath, tvScanHint;
    private Switch swPrivate;
    private Button btnSave, btnSaveContinue;
    private TextView btnStartScan, btnAddPhoto, btnAddTag, btnAlbum;
    private LinearLayout scanContainer, llPhotos, llTags;
    private TextView tabScan, tabPhoto, tabManual, tabAi;

    // 分类相关容器
    private LinearLayout layoutFieldsFood, layoutFieldsClothing, layoutFieldsDigital, layoutFieldsCosmetics;
    private View layoutShoeSize, layoutSizeOnly;

    // 分类扩展字段
    private EditText etSize, etColor, etMaterial, etShoeSize;
    private EditText etModel, etSerialNumber, etWarranty;
    private EditText etEffect, etSkinType;
    private int selectedSpaceId = 0;
    private LocalDb localDb;
    private JsonArray spaceList = new JsonArray();
    private JsonArray tagList = new JsonArray();
    private List<Integer> selectedTagIds = new ArrayList<>();
    private List<String> selectedTagNames = new ArrayList<>();
    private List<Bitmap> photos = new ArrayList<>();
    private List<String> existingImagePaths = new ArrayList<>(); // 已上传的图片路径

    private static final int REQUEST_BARCODE = 100;
    private static final int REQUEST_PHOTO = 101;
    private static final int REQUEST_GALLERY = 102;
    private static final int REQUEST_CAMERA_PERMISSION = 103;
    private static final int REQUEST_AI_PHOTO = 104;

    private boolean isEditMode = false;
    private int editGoodsId = 0;
    private int pendingOutfitId = 0; // 待关联的套装ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        localDb = new LocalDb(this);

        // 检查编辑模式
        isEditMode = getIntent().getBooleanExtra("edit_mode", false);
        editGoodsId = getIntent().getIntExtra("goods_id", 0);

        String mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "scan";

        etName = findViewById(R.id.et_name);
        etBarcode = findViewById(R.id.et_barcode);
        etBrand = findViewById(R.id.et_brand);
        etSpec = findViewById(R.id.et_spec);
        etManufacturer = findViewById(R.id.et_manufacturer);
        etQuantity = findViewById(R.id.et_quantity);
        etUnit = findViewById(R.id.et_unit);

        // 问题10: 单位Spinner
        String[] unitOptions = {"个", "盒", "瓶", "包", "袋", "罐", "箱", "件", "套"};
        android.widget.ArrayAdapter<String> unitAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, unitOptions);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        etUnit.setAdapter(unitAdapter);
        etExpiryDays = findViewById(R.id.et_expiry_days);
        etPrice = findViewById(R.id.et_price);
        etThreshold = findViewById(R.id.et_threshold);
        etPurchaseDate = findViewById(R.id.et_purchase_date);
        etNote = findViewById(R.id.et_note);
        spExpiryUnit = findViewById(R.id.sp_expiry_unit);
        spacePicker = findViewById(R.id.space_picker);
        tvSpaceName = findViewById(R.id.tv_space_name);
        tvSpacePath = findViewById(R.id.tv_space_path);
        swPrivate = findViewById(R.id.sw_private);
        btnSave = findViewById(R.id.btn_save);
        btnSaveContinue = findViewById(R.id.btn_save_continue);
        tvExpiryDateAuto = findViewById(R.id.tv_expiry_date_auto);
        scanContainer = findViewById(R.id.scan_container);
        tabScan = findViewById(R.id.tab_scan);
        tabPhoto = findViewById(R.id.tab_photo);
        tabManual = findViewById(R.id.tab_manual);
        tabAi = findViewById(R.id.tab_ai);
        tvScanHint = findViewById(R.id.tv_scan_hint);
        btnStartScan = findViewById(R.id.btn_start_scan);
        btnAddPhoto = findViewById(R.id.btn_add_photo);
        btnAlbum = findViewById(R.id.btn_album);
        btnAddTag = findViewById(R.id.btn_add_tag);
        llPhotos = findViewById(R.id.ll_photos);
        llTags = findViewById(R.id.ll_tags);

        // 分类相关容器和字段
        layoutFieldsFood = findViewById(R.id.layout_fields_food);
        layoutFieldsClothing = findViewById(R.id.layout_fields_clothing);
        layoutFieldsDigital = findViewById(R.id.layout_fields_digital);
        layoutFieldsCosmetics = findViewById(R.id.layout_fields_cosmetics);
        layoutShoeSize = findViewById(R.id.layout_shoe_size);
        layoutSizeOnly = findViewById(R.id.layout_size_only);
        etSize = findViewById(R.id.et_size);
        etColor = findViewById(R.id.et_color);
        etMaterial = findViewById(R.id.et_material);
        etShoeSize = findViewById(R.id.et_shoe_size);
        etModel = findViewById(R.id.et_model);
        etSerialNumber = findViewById(R.id.et_serial_number);
        etWarranty = findViewById(R.id.et_warranty);
        etEffect = findViewById(R.id.et_effect);
        etSkinType = findViewById(R.id.et_skin_type);

        // 初始化分类 Spinner
        spCategory = findViewById(R.id.sp_category);
        String[] categoryOptions = {"食品", "药品", "日用品", "数码", "化妆品", "服装", "鞋帽", "图书", "其他"};
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryOptions);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(categoryAdapter);
        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateCategoryFields(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 初始化过期提醒 Spinner
        spExpiryReminder = findViewById(R.id.sp_expiry_reminder);

        // 服装季节 Spinner
        spSeason = findViewById(R.id.sp_season);
        if (spSeason != null) {
            String[] seasonOptions = {"不指定", "春", "夏", "秋", "冬", "四季", "春秋"};
            ArrayAdapter<String> seasonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, seasonOptions);
            seasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spSeason.setAdapter(seasonAdapter);
        }

        // 加入套装按钮 - 弹出选择对话框
        btnCreateOutfit = findViewById(R.id.btn_create_outfit);
        if (btnCreateOutfit != null) {
            btnCreateOutfit.setOnClickListener(v -> showOutfitDialog());
        }
        if (spExpiryReminder != null) {
            String[] reminderOptions = {"不提醒", "过期前3天", "过期前7天", "过期前14天", "过期前30天"};
            ArrayAdapter<String> reminderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reminderOptions);
            reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spExpiryReminder.setAdapter(reminderAdapter);
        }

        // 选项卡
        tabScan.setOnClickListener(v -> switchTab("scan"));
        tabPhoto.setOnClickListener(v -> switchTab("photo"));
        tabManual.setOnClickListener(v -> switchTab("manual"));
        tabAi.setOnClickListener(v -> switchTab("ai"));

        btnStartScan.setOnClickListener(v -> startBarcodeScan());

        // 相册按钮
        btnAlbum.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_GALLERY);
        });

        // 返回按钮
        View tvBack = findViewById(R.id.tv_back);
        if (tvBack != null) {
            tvBack.setOnClickListener(v -> finish());
        }

        switchTab(mode);

        // 空间选择
        spacePicker.setOnClickListener(v -> showSpacePickerDialog());

        // 添加照片 - 问题7: 直接调用相机
        btnAddPhoto.setOnClickListener(v -> startPhotoCapture());

        // 添加标签
        btnAddTag.setOnClickListener(v -> showTagDialog());

        // 保存返回
        btnSave.setOnClickListener(v -> saveItem(false));

        // 保存继续
        btnSaveContinue.setOnClickListener(v -> saveItem(true));

        // 购买日期 - 年月日快速选择
        etPurchaseDate.setOnClickListener(v -> showDatePicker());

        // 保质期单位选择器
        if (spExpiryUnit != null) {
            String[] units = {"天", "月", "年"};
            android.widget.ArrayAdapter<String> expiryUnitAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
            expiryUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spExpiryUnit.setAdapter(expiryUnitAdapter);
            spExpiryUnit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { calcExpiryDate(); }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // 保质期天数变化时自动计算过期日期
        etExpiryDays.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { calcExpiryDate(); }
        });

        loadSpaces();
        loadTags();

        // 问题5: 编辑模式加载数据
        if (isEditMode && editGoodsId > 0) {
            loadItemForEdit(editGoodsId);
        }

        // 恢复上次选择的空间
        int lastSpaceId = getSharedPreferences("add_item_prefs", MODE_PRIVATE).getInt("last_space_id", 0);
        String lastSpaceName = getSharedPreferences("add_item_prefs", MODE_PRIVATE).getString("last_space_name", "");
        String lastSpacePath = getSharedPreferences("add_item_prefs", MODE_PRIVATE).getString("last_space_path", "");
        if (lastSpaceId > 0 && !lastSpaceName.isEmpty()) {
            selectedSpaceId = lastSpaceId;
            tvSpaceName.setText(lastSpaceName);
            tvSpacePath.setText(lastSpacePath);
        }
    }

    private void switchTab(String mode) {
        tabScan.setBackgroundResource(R.drawable.bg_tab_inactive_glass);
        tabPhoto.setBackgroundResource(R.drawable.bg_tab_inactive_glass);
        tabManual.setBackgroundResource(R.drawable.bg_tab_inactive_glass);
        tabAi.setBackgroundResource(R.drawable.bg_tab_inactive_glass);
        tabScan.setTextColor(Color.parseColor("#564338"));
        tabPhoto.setTextColor(Color.parseColor("#564338"));
        tabManual.setTextColor(Color.parseColor("#564338"));
        tabAi.setTextColor(Color.parseColor("#564338"));
        tabScan.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabPhoto.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabManual.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabAi.setTypeface(null, android.graphics.Typeface.NORMAL);

        switch (mode) {
            case "scan":
                tabScan.setBackgroundResource(R.drawable.bg_tab_active_filled);
                tabScan.setTextColor(Color.parseColor("#FFFFFF"));
                tabScan.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                tvScanHint.setText("点击下方按钮开始扫码识别条形码");
                btnStartScan.setText("📷 开始扫码");
                btnStartScan.setOnClickListener(v -> startBarcodeScan());
                break;
            case "photo":
                tabPhoto.setBackgroundResource(R.drawable.bg_tab_active_filled);
                tabPhoto.setTextColor(Color.parseColor("#FFFFFF"));
                tabPhoto.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                tvScanHint.setText("拍照识别物品,自动填充信息");
                btnStartScan.setText("📸 拍照识别");
                btnStartScan.setOnClickListener(v -> startPhotoCapture());
                break;
            case "manual":
                tabManual.setBackgroundResource(R.drawable.bg_tab_active_filled);
                tabManual.setTextColor(Color.parseColor("#FFFFFF"));
                tabManual.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.GONE);
                break;
            case "ai":
                tabAi.setBackgroundResource(R.drawable.bg_tab_active_filled);
                tabAi.setTextColor(Color.parseColor("#FFFFFF"));
                tabAi.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                tvScanHint.setText("AI 智能识别:拍照自动提取物品信息");
                btnStartScan.setText("AI 智能识别");
                btnStartScan.setOnClickListener(v -> startAiRecognize());
                break;
        }
    }

    /**
     * 根据分类选择更新字段显示/隐藏
     */
    private void updateCategoryFields(int categoryPosition) {
        // 先全部隐藏
        if (layoutFieldsFood != null) layoutFieldsFood.setVisibility(View.GONE);
        if (layoutFieldsClothing != null) layoutFieldsClothing.setVisibility(View.GONE);
        if (layoutFieldsDigital != null) layoutFieldsDigital.setVisibility(View.GONE);
        if (layoutFieldsCosmetics != null) layoutFieldsCosmetics.setVisibility(View.GONE);
        if (layoutShoeSize != null) layoutShoeSize.setVisibility(View.GONE);
        if (layoutSizeOnly != null) layoutSizeOnly.setVisibility(View.GONE);

        // 更新容器标题
        TextView tvClothingHeader = findViewById(R.id.tv_clothing_header);

        // 分类选项: 0=食品, 1=药品, 2=日用品, 3=数码, 4=化妆品, 5=服装, 6=鞋帽, 7=其他
        switch (categoryPosition) {
            case 0: // 食品
            case 1: // 药品
                if (layoutFieldsFood != null) layoutFieldsFood.setVisibility(View.VISIBLE);
                break;
            case 2: // 日用品
                if (layoutFieldsClothing != null) {
                    layoutFieldsClothing.setVisibility(View.VISIBLE);
                    if (tvClothingHeader != null) tvClothingHeader.setText("📦 日用品信息");
                }
                // 日用品不显示尺码和鞋码
                if (layoutSizeOnly != null) layoutSizeOnly.setVisibility(View.GONE);
                if (layoutShoeSize != null) layoutShoeSize.setVisibility(View.GONE);
                break;
            case 3: // 数码
                if (layoutFieldsDigital != null) layoutFieldsDigital.setVisibility(View.VISIBLE);
                break;
            case 4: // 化妆品
                if (layoutFieldsCosmetics != null) layoutFieldsCosmetics.setVisibility(View.VISIBLE);
                break;
            case 5: // 服装
                if (layoutFieldsClothing != null) {
                    layoutFieldsClothing.setVisibility(View.VISIBLE);
                    if (tvClothingHeader != null) tvClothingHeader.setText("👔 服装信息");
                }
                if (layoutSizeOnly != null) layoutSizeOnly.setVisibility(View.VISIBLE);
                if (layoutShoeSize != null) layoutShoeSize.setVisibility(View.GONE);
                if (btnCreateOutfit != null) btnCreateOutfit.setVisibility(View.VISIBLE);
                break;
            case 6: // 鞋帽
                if (layoutFieldsClothing != null) {
                    layoutFieldsClothing.setVisibility(View.VISIBLE);
                    if (tvClothingHeader != null) tvClothingHeader.setText("👟 鞋帽信息");
                }
                if (layoutSizeOnly != null) layoutSizeOnly.setVisibility(View.VISIBLE);
                if (layoutShoeSize != null) layoutShoeSize.setVisibility(View.VISIBLE);
                if (btnCreateOutfit != null) btnCreateOutfit.setVisibility(View.VISIBLE);
                break;
            case 7: // 其他
                if (layoutFieldsClothing != null) {
                    layoutFieldsClothing.setVisibility(View.VISIBLE);
                    if (tvClothingHeader != null) tvClothingHeader.setText("📦 其他信息");
                }
                // 其他不显示尺码和鞋码
                if (layoutSizeOnly != null) layoutSizeOnly.setVisibility(View.GONE);
                if (layoutShoeSize != null) layoutShoeSize.setVisibility(View.GONE);
                if (btnCreateOutfit != null) btnCreateOutfit.setVisibility(View.GONE);
                break;
        }
    }

    /**
     * 将分类名称映射到 Spinner 位置
     */
    private int getCategoryPosition(String category) {
        if (category == null || category.isEmpty()) return -1;
        switch (category) {
            case "食品": return 0;
            case "药品": return 1;
            case "日用品": return 2;
            case "数码": return 3;
            case "化妆品": return 4;
            case "服装": return 5;
            case "鞋帽": return 6;
            case "其他": return 7;
            default:
                // 尝试模糊匹配
                if (category.contains("食") || category.contains("饮") || category.contains("奶") || category.contains("粮")) return 0;
                if (category.contains("药") || category.contains("医")) return 1;
                if (category.contains("日用") || category.contains("清洁") || category.contains("洗")) return 2;
                if (category.contains("数码") || category.contains("电子") || category.contains("手机") || category.contains("电脑")) return 3;
                if (category.contains("化妆") || category.contains("护肤") || category.contains("美容")) return 4;
                if (category.contains("服装") || category.contains("衣") || category.contains("裤")) return 5;
                if (category.contains("鞋") || category.contains("帽")) return 6;
                return 7; // 默认"其他"
        }
    }

    /**
     * 将API返回的分类映射到Spinner选项
     */
    private String mapCategoryToSpinner(String apiCategory) {
        if (apiCategory == null) return "其他";
        String cat = apiCategory.toLowerCase();
        if (cat.contains("食") || cat.contains("饮") || cat.contains("奶") || cat.contains("粮") || cat.contains("food")) return "食品";
        if (cat.contains("药") || cat.contains("医") || cat.contains("medicine")) return "药品";
        if (cat.contains("日用") || cat.contains("清洁") || cat.contains("洗") || cat.contains("daily")) return "日用品";
        if (cat.contains("数码") || cat.contains("电子") || cat.contains("手机") || cat.contains("tech")) return "数码";
        if (cat.contains("化妆") || cat.contains("护肤") || cat.contains("美容") || cat.contains("cosmetic")) return "化妆品";
        if (cat.contains("服装") || cat.contains("衣") || cat.contains("裤") || cat.contains("cloth")) return "服装";
        if (cat.contains("鞋") || cat.contains("帽") || cat.contains("shoe")) return "鞋帽";
        if (cat.contains("书") || cat.contains("book") || cat.contains("图书") || cat.contains("读")) return "图书";
        return "其他";
    }

    private void startAiRecognize() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        Intent aiIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            java.io.File storageDir = getExternalCacheDir();
            if (storageDir != null && !storageDir.exists()) storageDir.mkdirs();
            java.io.File photoFile = java.io.File.createTempFile("ai_photo_", ".jpg", storageDir);
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            aiIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            aiIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            aiIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        startActivityForResult(aiIntent, REQUEST_AI_PHOTO);
    }

    private void callAiRecognize(byte[] imageBytes) {
        Toast.makeText(this, "AI 正在识别...", Toast.LENGTH_SHORT).show();
        android.util.Log.d("AddItem", "callAiRecognize: imageBytes.length=" + imageBytes.length);
        okhttp3.MultipartBody.Builder builder = new okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("image", "photo.jpg",
                okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/jpeg"), imageBytes));
        int houseId = App.getInstance().getCurrentHouseId();
        if (selectedSpaceId > 0) builder.addFormDataPart("space_id", String.valueOf(selectedSpaceId));
        if (houseId > 0) builder.addFormDataPart("house_id", String.valueOf(houseId));
        okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
            .url(App.BASE_URL + "image-recognize.php?action=recognize")
            .post(builder.build());
        String token = App.getInstance().getToken();
        if (token != null && !token.isEmpty()) reqBuilder.addHeader("Authorization", "Bearer " + token);
        new okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            .newCall(reqBuilder.build())
            .enqueue(new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(AddItemActivity.this, "AI 识别失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    android.util.Log.d("AddItem", "AI response code=" + response.code() + " body=" + body.substring(0, Math.min(200, body.length())));
                    runOnUiThread(() -> handleAiResult(body));
                }
            });
    }

    private void handleAiResult(String responseBody) {
        try {
            JsonObject json = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
            int code = json.has("code") && !json.get("code").isJsonNull() ? json.get("code").getAsInt() : -1;
            if (code != 0) {
                String msg = json.has("msg") && !json.get("msg").isJsonNull() ? json.get("msg").getAsString() : "识别失败";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!json.has("data") || json.get("data").isJsonNull()) {
                Toast.makeText(this, "识别失败: 返回数据为空", Toast.LENGTH_SHORT).show();
                return;
            }
            JsonObject data = json.getAsJsonObject("data");
            if (data == null) {
                Toast.makeText(this, "识别失败: 返回数据为空", Toast.LENGTH_SHORT).show();
                return;
            }
            // 兼容两种返回格式: image-recognize.php 和 ai/recognize.php
            String tmpName = safeGetString(data, "suggested_name");
            if (tmpName.isEmpty()) tmpName = safeGetString(data, "goods_name");
            final String name = tmpName;
            String tmpBrand = safeGetString(data, "suggested_brand");
            if (tmpBrand.isEmpty()) tmpBrand = safeGetString(data, "brand");
            final String brand = tmpBrand;
            final String barcode = safeGetString(data, "barcode");
            String expireDate = safeGetString(data, "expire_date");
            double confidence = safeGetDouble(data, "confidence");
            if (data.has("suggested_space_id") && !data.get("suggested_space_id").isJsonNull()) {
                try {
                    int sid = data.get("suggested_space_id").getAsInt();
                    if (sid > 0) {
                        selectedSpaceId = sid;
                        String sn = safeGetString(data, "suggested_space_name");
                        tvSpaceName.setText(sn);
                    }
                } catch (Exception ignored) {}
            }
            if (!name.isEmpty()) etName.setText(name);
            if (!barcode.isEmpty()) etBarcode.setText(barcode);
            if (!brand.isEmpty()) etBrand.setText(brand);
            if (!expireDate.isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    java.util.Date expiry = sdf.parse(expireDate);
                    if (expiry != null) {
                        int days = (int) ((expiry.getTime() - System.currentTimeMillis()) / 86400000L);
                        if (days > 0) etExpiryDays.setText(String.valueOf(days));
                    }
                } catch (Exception ignored) {}
            }
            // 问题5: 提取分类(兼容两种格式)
            String category = safeGetString(data, "suggested_category");
            if (category.isEmpty()) category = safeGetString(data, "category");
            final String spec = safeGetString(data, "spec");
            final String storageTip = safeGetString(data, "storage_tip");

            // AI识别颜色和季节
            String aiColor = safeGetString(data, "color");
            String aiSeason = safeGetString(data, "season");
            // 自动填入颜色
            if (!aiColor.isEmpty() && etColor != null) {
                etColor.setText(aiColor);
            }
            // 自动选中季节 Spinner
            if (!aiSeason.isEmpty() && spSeason != null) {
                for (int i = 0; i < spSeason.getCount(); i++) {
                    if (spSeason.getItemAtPosition(i).toString().equals(aiSeason)) {
                        spSeason.setSelection(i);
                        break;
                    }
                }
            }
            String msg = "名称: " + name;
            if (!brand.isEmpty()) msg += "\n品牌: " + brand;
            if (!spec.isEmpty()) msg += "\n规格: " + spec;
            if (!category.isEmpty()) msg += "\n分类: " + category;
            if (!storageTip.isEmpty()) msg += "\n存放建议: " + storageTip;
            msg += "\n置信度: " + String.format(Locale.getDefault(), "%.0f%%", confidence * 100);

            // 计算分类Spinner位置
            final int categoryPos = getCategoryPosition(category);

            // 构建可选择性填入的对话框
            LinearLayout dialogLayout = new LinearLayout(this);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setPadding(dp(24), dp(16), dp(24), dp(8));

            // 识别结果概览
            TextView tvSummary = new TextView(this);
            tvSummary.setText("识别置信度: " + String.format(Locale.getDefault(), "%.0f%%", confidence * 100));
            tvSummary.setTextSize(13);
            tvSummary.setTextColor(0xFF718096);
            tvSummary.setPadding(0, 0, 0, dp(12));
            dialogLayout.addView(tvSummary);

            // 每个字段一个复选框
            java.util.List<android.widget.CheckBox> checkBoxes = new java.util.ArrayList<>();

            if (!name.isEmpty()) {
                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                cb.setText("名称: " + name);
                cb.setChecked(true);
                cb.setTextSize(14);
                dialogLayout.addView(cb);
                checkBoxes.add(cb);
            }
            if (!brand.isEmpty()) {
                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                cb.setText("品牌: " + brand);
                cb.setChecked(true);
                cb.setTextSize(14);
                dialogLayout.addView(cb);
                checkBoxes.add(cb);
            }
            if (!spec.isEmpty()) {
                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                cb.setText("规格: " + spec);
                cb.setChecked(true);
                cb.setTextSize(14);
                dialogLayout.addView(cb);
                checkBoxes.add(cb);
            }
            if (!barcode.isEmpty()) {
                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                cb.setText("条码: " + barcode);
                cb.setChecked(true);
                cb.setTextSize(14);
                dialogLayout.addView(cb);
                checkBoxes.add(cb);
            }
            if (!category.isEmpty()) {
                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                cb.setText("分类: " + category);
                cb.setChecked(true);
                cb.setTextSize(14);
                dialogLayout.addView(cb);
                checkBoxes.add(cb);
            }
            if (!storageTip.isEmpty()) {
                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                cb.setText("存放建议: " + storageTip);
                cb.setChecked(etNote.getText().toString().trim().isEmpty());
                cb.setTextSize(14);
                dialogLayout.addView(cb);
                checkBoxes.add(cb);
            }

            // 全选/全不选
            android.widget.CheckBox cbAll = new android.widget.CheckBox(this);
            cbAll.setText("全选");
            cbAll.setChecked(true);
            cbAll.setTextSize(13);
            cbAll.setTextColor(0xFF4A90D9);
            cbAll.setOnCheckedChangeListener((btn, checked) -> {
                for (android.widget.CheckBox cb : checkBoxes) cb.setChecked(checked);
            });
            // 插入到最前面
            dialogLayout.addView(cbAll, 1);

            // 字段名到复选框的映射
            final String fName = name, fBrand = brand, fSpec = spec, fBarcode = barcode, fCategory = category, fStorageTip = storageTip;

            new AlertDialog.Builder(this).setTitle("AI 识别结果").setView(dialogLayout)
                .setPositiveButton("填入选中项", (d, w) -> {
                    for (android.widget.CheckBox cb : checkBoxes) {
                        if (!cb.isChecked()) continue;
                        String text = cb.getText().toString();
                        if (text.startsWith("名称:")) etName.setText(fName);
                        else if (text.startsWith("品牌:")) etBrand.setText(fBrand);
                        else if (text.startsWith("规格:")) etSpec.setText(fSpec);
                        else if (text.startsWith("条码:")) etBarcode.setText(fBarcode);
                        else if (text.startsWith("分类:") && categoryPos >= 0) spCategory.setSelection(categoryPos);
                        else if (text.startsWith("存放建议:")) etNote.setText(fStorageTip);
                    }
                    // 将AI识别的照片添加到物品图片
                    if (cameraImageUri != null) {
                        try {
                            Bitmap aiPhoto = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), cameraImageUri);
                            if (aiPhoto != null) addPhotoToList(aiPhoto);
                        } catch (Exception ignored) {}
                    }
                    Toast.makeText(this, "✅ 已填入选中项", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("全部填入", (d, w) -> {
                    if (!fName.isEmpty()) etName.setText(fName);
                    if (!fBarcode.isEmpty()) etBarcode.setText(fBarcode);
                    if (!fBrand.isEmpty()) etBrand.setText(fBrand);
                    if (!fSpec.isEmpty()) etSpec.setText(fSpec);
                    if (!fStorageTip.isEmpty() && etNote.getText().toString().trim().isEmpty()) etNote.setText(fStorageTip);
                    if (categoryPos >= 0) spCategory.setSelection(categoryPos);
                    if (cameraImageUri != null) {
                        try {
                            Bitmap aiPhoto = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), cameraImageUri);
                            if (aiPhoto != null) addPhotoToList(aiPhoto);
                        } catch (Exception ignored) {}
                    }
                    Toast.makeText(this, "✅ 已填入全部", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("重新识别", (d, w) -> startAiRecognize())
                .show();
        } catch (Exception e) {
            android.util.Log.e("AddItem", "AI result parse error: " + e.getMessage(), e);
            String detail = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "null");
            new AlertDialog.Builder(this)
                .setTitle("解析失败")
                .setMessage("错误: " + detail + "\n\n原始数据:\n" + (responseBody != null ? responseBody.substring(0, Math.min(300, responseBody.length())) : "null"))
                .setPositiveButton("确定", null)
                .show();
        }
    }

    /** 安全获取 JsonObject 中的字符串值,避免 null/NPE */
    private static String safeGetString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        try { return obj.get(key).getAsString(); } catch (Exception e) { return ""; }
    }

    /** 安全获取 JsonObject 中的 double 值,兼容 int/double/string 类型 */
    private static double safeGetDouble(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return 0;
        try {
            com.google.gson.JsonElement el = obj.get(key);
            if (el.getAsJsonPrimitive().isNumber()) return el.getAsDouble();
            return Double.parseDouble(el.getAsString());
        } catch (Exception e) { return 0; }
    }

    private void startBarcodeScan() {
        try {
            Intent intent = new Intent(this, BarcodeScanActivity.class);
            startActivityForResult(intent, REQUEST_BARCODE);
        } catch (Exception e) {
            Toast.makeText(this, "扫码启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Uri cameraImageUri;

    private void startPhotoCapture() {
        // 检查相机权限
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        doStartPhotoCapture();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                doStartPhotoCapture();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void doStartPhotoCapture() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // 创建临时文件保存拍照结果
            java.io.File photoFile = null;
            try {
                java.io.File storageDir = getExternalCacheDir();
                if (storageDir != null && !storageDir.exists()) storageDir.mkdirs();
                photoFile = java.io.File.createTempFile("photo_", ".jpg", storageDir);
            } catch (Exception ignored) {}

            if (photoFile != null) {
                cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            startActivityForResult(intent, REQUEST_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "相机启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        } else if (requestCode == REQUEST_PHOTO) {
            try {
                Bitmap bitmap = null;
                if (cameraImageUri != null) {
                    // 从FileProvider URI加载完整照片
                    try {
                        bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), cameraImageUri);
                    } catch (Exception ignored) {}
                }
                if (bitmap == null && data != null) {
                    // 回退:尝试获取缩略图
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        bitmap = (Bitmap) extras.get("data");
                    }
                }
                if (bitmap != null) {
                    addPhotoToList(bitmap);
                } else {
                    Toast.makeText(this, "拍照获取失败", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "拍照处理失败", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_AI_PHOTO) {
            try {
                Bitmap bitmap = null;
                if (cameraImageUri != null) {
                    try { bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), cameraImageUri); } catch (Exception e) { android.util.Log.e("AddItem", "load from URI failed", e); }
                }
                if (bitmap == null && data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) bitmap = (Bitmap) extras.get("data");
                }
                if (bitmap != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                    callAiRecognize(baos.toByteArray());
                } else {
                    Toast.makeText(this, "拍照获取失败,请重试", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "拍照处理失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                android.util.Log.e("AddItem", "AI photo error", e);
            }
        } else if (requestCode == REQUEST_GALLERY && data != null) {
            try {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    addPhotoToList(bitmap);
                    // 从相册选取后也调用AI识别
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                    callAiRecognize(baos.toByteArray());
                }
            } catch (Exception e) {
                Toast.makeText(this, "图片加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 编辑模式:显示已有的远程图片
     */
    private void addExistingPhotoToView(String imageUrl, int existingIndex) {
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(dp(80), dp(80));
        containerLp.rightMargin = dp(8);
        container.setLayoutParams(containerLp);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        iv.setBackgroundResource(R.drawable.bg_card_16);
        try {
            com.bumptech.glide.Glide.with(this).load(imageUrl).centerCrop().into(iv);
        } catch (Exception ignored) {}
        container.addView(iv);

        // 删除按钮
        TextView btnDelete = new TextView(this);
        btnDelete.setText("✕");
        btnDelete.setTextSize(10);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable deleteBg = new android.graphics.drawable.GradientDrawable();
        deleteBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        deleteBg.setColor(0xCC000000);
        btnDelete.setBackground(deleteBg);
        android.widget.FrameLayout.LayoutParams deleteLp = new android.widget.FrameLayout.LayoutParams(dp(20), dp(20));
        deleteLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        deleteLp.topMargin = dp(2);
        deleteLp.rightMargin = dp(2);
        btnDelete.setLayoutParams(deleteLp);
        btnDelete.setOnClickListener(v -> {
            int idx = llPhotos.indexOfChild(container);
            if (idx >= 0 && idx < existingImagePaths.size()) {
                existingImagePaths.remove(idx);
            }
            llPhotos.removeView(container);
        });
        container.addView(btnDelete);
        llPhotos.addView(container);
    }

    private void addPhotoToList(Bitmap bitmap) {
        photos.add(bitmap);
        // 添加照片预览容器
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(dp(80), dp(80));
        containerLp.rightMargin = dp(8);
        container.setLayoutParams(containerLp);

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bitmap);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        iv.setBackgroundResource(R.drawable.bg_card_16);
        container.addView(iv);

        // 右上角X删除按钮
        TextView btnDelete = new TextView(this);
        btnDelete.setText("✕");
        btnDelete.setTextSize(10);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable deleteBg = new android.graphics.drawable.GradientDrawable();
        deleteBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        deleteBg.setColor(0xCC000000);
        btnDelete.setBackground(deleteBg);
        android.widget.FrameLayout.LayoutParams deleteLp = new android.widget.FrameLayout.LayoutParams(dp(20), dp(20));
        deleteLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        deleteLp.topMargin = dp(2);
        deleteLp.rightMargin = dp(2);
        btnDelete.setLayoutParams(deleteLp);
        btnDelete.setOnClickListener(v -> {
            int idx = llPhotos.indexOfChild(container);
            if (idx >= 0 && idx < photos.size()) {
                photos.remove(idx);
            }
            llPhotos.removeView(container);
        });
        container.addView(btnDelete);

        llPhotos.addView(container);
        // 添加照片不自动调用识别
    }

    private void recognizeImage(Bitmap bitmap) {
        // 将Bitmap转为字节流上传
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        byte[] imageBytes = baos.toByteArray();

        // 构建multipart请求
        okhttp3.MultipartBody.Builder builder = new okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("image", "photo.jpg",
                okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/jpeg"), imageBytes));

        String url = App.BASE_URL + "image-recognize.php?action=recognize";
        okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
            .url(url)
            .post(builder.build());

        String token = App.getInstance().getToken();
        if (token != null && !token.isEmpty()) {
            reqBuilder.addHeader("Authorization", "Bearer " + token);
        }

        new okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            .newCall(reqBuilder.build())
            .enqueue(new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(AddItemActivity.this,
                        "识别请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> handleRecognizeResult(body));
                }
            });
    }

    private void handleRecognizeResult(String responseBody) {
        try {
            JsonObject json = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
            int respCode = json.has("code") && !json.get("code").isJsonNull() ? json.get("code").getAsInt() : -1;
            if (respCode != 0) {
                String errMsg = json.has("msg") && !json.get("msg").isJsonNull() ? json.get("msg").getAsString() : "识别失败";
                Toast.makeText(this, "识别失败: " + errMsg, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!json.has("data") || json.get("data").isJsonNull()) {
                Toast.makeText(this, "识别失败: 返回数据为空", Toast.LENGTH_SHORT).show();
                return;
            }
            JsonObject data = json.getAsJsonObject("data");
            if (data == null) {
                Toast.makeText(this, "识别失败: 返回数据为空", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean recognized = data.has("recognized") && !data.get("recognized").isJsonNull() && data.get("recognized").getAsBoolean();

            if (recognized) {
                String name = safeGetString(data, "suggested_name");
                String category = safeGetString(data, "suggested_category");
                String brand = safeGetString(data, "suggested_brand");
                String barcode = safeGetString(data, "barcode");
                String spec = safeGetString(data, "spec");
                String expireDate = safeGetString(data, "expire_date");
                String storageTip = safeGetString(data, "storage_tip");

                // 处理保质期
                if (!expireDate.isEmpty()) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        java.util.Date expiry = sdf.parse(expireDate);
                        if (expiry != null) {
                            int days = (int) ((expiry.getTime() - System.currentTimeMillis()) / 86400000L);
                            if (days > 0) etExpiryDays.setText(String.valueOf(days));
                        }
                    } catch (Exception ignored) {}
                }

                // 显示识别结果确认对话框
                StringBuilder msg = new StringBuilder();
                if (!name.isEmpty()) msg.append("名称: ").append(name).append("\n");
                if (!category.isEmpty()) msg.append("分类: ").append(category).append("\n");
                if (!brand.isEmpty()) msg.append("品牌: ").append(brand).append("\n");
                if (!spec.isEmpty()) msg.append("规格: ").append(spec).append("\n");
                if (!barcode.isEmpty()) msg.append("条码: ").append(barcode).append("\n");
                if (!storageTip.isEmpty()) msg.append("存放建议: ").append(storageTip).append("\n");

                if (msg.length() == 0) {
                    Toast.makeText(this, "未能识别物品,请手动输入", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 自动填入识别到的标签
                if (data.has("suggested_tags") && !data.get("suggested_tags").isJsonNull()) {
                    com.google.gson.JsonArray suggestedTags = data.getAsJsonArray("suggested_tags");
                    for (int i = 0; i < suggestedTags.size(); i++) {
                        String tagName = suggestedTags.get(i).getAsString();
                        if (!selectedTagNames.contains(tagName)) {
                            selectedTagNames.add(tagName);
                            // 标签ID会在保存时自动创建
                        }
                    }
                    updateTagDisplay();
                }

                final int catPos = getCategoryPosition(category);

                // 构建可选择性填入的对话框
                LinearLayout dialogLayout2 = new LinearLayout(this);
                dialogLayout2.setOrientation(LinearLayout.VERTICAL);
                dialogLayout2.setPadding(dp(24), dp(16), dp(24), dp(8));

                java.util.List<android.widget.CheckBox> cbs = new java.util.ArrayList<>();

                if (!name.isEmpty()) { android.widget.CheckBox c = new android.widget.CheckBox(this); c.setText("名称: " + name); c.setChecked(true); c.setTextSize(14); dialogLayout2.addView(c); cbs.add(c); }
                if (!brand.isEmpty()) { android.widget.CheckBox c = new android.widget.CheckBox(this); c.setText("品牌: " + brand); c.setChecked(true); c.setTextSize(14); dialogLayout2.addView(c); cbs.add(c); }
                if (!spec.isEmpty()) { android.widget.CheckBox c = new android.widget.CheckBox(this); c.setText("规格: " + spec); c.setChecked(true); c.setTextSize(14); dialogLayout2.addView(c); cbs.add(c); }
                if (!barcode.isEmpty()) { android.widget.CheckBox c = new android.widget.CheckBox(this); c.setText("条码: " + barcode); c.setChecked(true); c.setTextSize(14); dialogLayout2.addView(c); cbs.add(c); }
                if (!category.isEmpty()) { android.widget.CheckBox c = new android.widget.CheckBox(this); c.setText("分类: " + category); c.setChecked(true); c.setTextSize(14); dialogLayout2.addView(c); cbs.add(c); }
                if (!storageTip.isEmpty()) { android.widget.CheckBox c = new android.widget.CheckBox(this); c.setText("存放建议: " + storageTip); c.setChecked(etNote.getText().toString().trim().isEmpty()); c.setTextSize(14); dialogLayout2.addView(c); cbs.add(c); }

                android.widget.CheckBox cbAll2 = new android.widget.CheckBox(this);
                cbAll2.setText("全选"); cbAll2.setChecked(true); cbAll2.setTextSize(13); cbAll2.setTextColor(0xFF4A90D9);
                cbAll2.setOnCheckedChangeListener((btn, checked) -> { for (android.widget.CheckBox c : cbs) c.setChecked(checked); });
                dialogLayout2.addView(cbAll2, 1);

                final String rName = name, rBrand = brand, rSpec = spec, rBarcode = barcode, rCategory = category, rStorageTip = storageTip;

                new AlertDialog.Builder(this)
                    .setTitle("✅ 识别结果")
                    .setView(dialogLayout2)
                    .setPositiveButton("填入选中项", (d, w) -> {
                        for (android.widget.CheckBox c : cbs) {
                            if (!c.isChecked()) continue;
                            String t = c.getText().toString();
                            if (t.startsWith("名称:")) etName.setText(rName);
                            else if (t.startsWith("品牌:")) etBrand.setText(rBrand);
                            else if (t.startsWith("规格:")) etSpec.setText(rSpec);
                            else if (t.startsWith("条码:")) etBarcode.setText(rBarcode);
                            else if (t.startsWith("分类:") && catPos >= 0) spCategory.setSelection(catPos);
                            else if (t.startsWith("存放建议:")) etNote.setText(rStorageTip);
                        }
                        Toast.makeText(this, "✅ 已填入选中项", Toast.LENGTH_SHORT).show();
                    })
                    .setNeutralButton("全部填入", (d, w) -> {
                        if (!rName.isEmpty()) etName.setText(rName);
                        if (!rBarcode.isEmpty()) etBarcode.setText(rBarcode);
                        if (!rBrand.isEmpty()) etBrand.setText(rBrand);
                        if (!rSpec.isEmpty()) etSpec.setText(rSpec);
                        if (!rStorageTip.isEmpty() && etNote.getText().toString().trim().isEmpty()) etNote.setText(rStorageTip);
                        if (catPos >= 0) spCategory.setSelection(catPos);
                        Toast.makeText(this, "✅ 已填入全部", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("重新识别", (d, w) -> {
                        if (photos.size() > 0) recognizeImage(photos.get(photos.size() - 1));
                    })
                    .show();
            } else {
                String message = data.has("message") ? data.get("message").getAsString() : "未能识别物品";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "识别结果解析失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void lookupBarcode(String barcode) {
        HashMap<String, String> params = new HashMap<>();
        params.put("action", "lookup");
        params.put("barcode", barcode);
        ApiClient.get("barcode.php?action=lookup", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (data.has("found") && data.get("found").getAsBoolean()) {
                            // 条码直接来自扫描
                            if (etBarcode.getText().toString().trim().isEmpty()) {
                                etBarcode.setText(barcode);
                            }
                            boolean hasData = false;
                            // name、brand、spec、price、manufacturer等字段直接在data根层级
                            if (data.has("name") && !data.get("name").isJsonNull()) {
                                String name = data.get("name").getAsString();
                                if (!name.isEmpty()) { etName.setText(name); hasData = true; }
                            }
                            if (data.has("brand") && !data.get("brand").isJsonNull()) {
                                String brand = data.get("brand").getAsString();
                                if (!brand.isEmpty()) { etBrand.setText(brand); hasData = true; }
                            }
                            if (data.has("spec") && !data.get("spec").isJsonNull()) {
                                String spec = data.get("spec").getAsString();
                                if (!spec.isEmpty() && etSpec != null) { etSpec.setText(spec); hasData = true; }
                            }
                            if (data.has("manufacturer") && !data.get("manufacturer").isJsonNull()) {
                                String mfr = data.get("manufacturer").getAsString();
                                if (!mfr.isEmpty() && etManufacturer != null) { etManufacturer.setText(mfr); hasData = true; }
                            }
                            if (data.has("price") && !data.get("price").isJsonNull()) {
                                String price = data.get("price").getAsString();
                                if (!price.isEmpty()) {
                                    try {
                                        String cleanPrice = price.replaceAll("[^0-9.]", "");
                                        if (!cleanPrice.isEmpty()) { etPrice.setText(cleanPrice); hasData = true; }
                                    } catch (Exception ignored) {}
                                }
                            }
                            if (hasData) {
                                // 自动选择分类
                                if (data.has("category") && !data.get("category").isJsonNull()) {
                                    String apiCategory = data.get("category").getAsString();
                                    if (!apiCategory.isEmpty()) {
                                        // 映射API分类到APP分类
                                        String mapped = mapCategoryToSpinner(apiCategory);
                                        for (int i = 0; i < spCategory.getCount(); i++) {
                                            if (spCategory.getItemAtPosition(i).toString().equals(mapped)) {
                                                spCategory.setSelection(i);
                                                break;
                                            }
                                        }
                                    }
                                }
                                Toast.makeText(AddItemActivity.this, "✅ 已识别商品", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(AddItemActivity.this, "条码已录入,但商品详情未查到,请手动补充", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            String msg = data.has("msg") ? data.get("msg").getAsString() : "未找到该条码对应的商品";
                            // 友好化处理原始错误信息
                            if (msg.contains("HTTP 404") || msg.contains("未找到")) {
                                msg = "该条码在数据库中未找到,可手动输入物品信息";
                            } else if (msg.contains("HTTP 500") || msg.contains("服务")) {
                                msg = "条码查询服务暂时不可用,请稍后再试";
                            } else if (msg.contains("连接失败") || msg.contains("timeout")) {
                                msg = "网络连接超时,请检查网络后重试";
                            }
                            Toast.makeText(AddItemActivity.this, msg, Toast.LENGTH_LONG).show();
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
            // 没有标签,提示创建
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
            int tagId = tag.has("id") ? tag.get("id").getAsInt() : 0;
            checked[i] = selectedTagIds.contains(tagId);
        }

        new AlertDialog.Builder(this)
            .setTitle("选择标签")
            .setMultiChoiceItems(names, checked, (d, which, isChecked) -> {
                JsonObject tag = tagList.get(which).getAsJsonObject();
                int tagId = tag.has("id") ? tag.get("id").getAsInt() : 0;
                String tagName = tag.has("name") ? tag.get("name").getAsString() : "";
                if (isChecked) {
                    if (!selectedTagIds.contains(tagId)) {
                        selectedTagIds.add(tagId);
                        selectedTagNames.add(tagName);
                    }
                } else {
                    selectedTagIds.remove(Integer.valueOf(tagId));
                    selectedTagNames.remove(tagName);
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
                    int newTagId = data.has("id") ? data.get("id").getAsInt() : 0;
                    if (newTagId > 0 && !selectedTagIds.contains(newTagId)) {
                        selectedTagIds.add(newTagId);
                        selectedTagNames.add(name);
                    }
                    loadTags();
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
        for (int i = 0; i < selectedTagNames.size(); i++) {
            String tag = selectedTagNames.get(i);
            int tagIndex = i;
            TextView tv = new TextView(this);
            tv.setText(tag + " ✕");
            tv.setTextSize(12);
            tv.setTextColor(Color.parseColor("#5B9FED"));
            tv.setBackgroundResource(R.drawable.bg_tag_blue);
            tv.setPadding(dp(12), dp(4), dp(12), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(4);
            tv.setLayoutParams(lp);
            // 问题8: 点击删除标签
            tv.setOnClickListener(v -> {
                if (tagIndex < selectedTagIds.size()) {
                    selectedTagIds.remove(tagIndex);
                }
                if (tagIndex < selectedTagNames.size()) {
                    selectedTagNames.remove(tagIndex);
                }
                updateTagDisplay();
            });
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
            Toast.makeText(this, "暂无空间,请先创建收纳空间", Toast.LENGTH_SHORT).show();
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

    /**
     * 将用户输入的保质期值和单位换算成天数
     */
    /**
     * 年月日快速选择器(先选年,再选月日)
     */
    private void showDatePicker() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH);
        int day = cal.get(java.util.Calendar.DAY_OF_MONTH);

        if (!selectedPurchaseDate.isEmpty()) {
            try {
                String[] parts = selectedPurchaseDate.split("-");
                year = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]) - 1;
                day = Integer.parseInt(parts[2]);
            } catch (Exception ignored) {}
        }

        final int fYear = year, fMonth = month, fDay = day;
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            selectedPurchaseDate = String.format("%04d-%02d-%02d", y, m + 1, d);
            etPurchaseDate.setText(selectedPurchaseDate);
            calcExpiryDate();
        }, fYear, fMonth, fDay);

        // 日期范围：10年前到今天
        java.util.Calendar minDate = java.util.Calendar.getInstance();
        minDate.add(java.util.Calendar.YEAR, -10);
        java.util.Calendar maxDate = java.util.Calendar.getInstance();
        dialog.getDatePicker().setMinDate(minDate.getTimeInMillis());
        dialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());

        dialog.setTitle("选择生产日期（点击年份可快速切换）");
        dialog.show();
    }

    private int convertToDays(int value, String unit) {
        switch (unit) {
            case "月": return value * 30;
            case "年": return value * 365;
            default: return value; // 天
        }
    }

    private void calcExpiryDate() {
        String purchaseStr = etPurchaseDate.getText().toString().trim();
        String daysStr = etExpiryDays.getText().toString().trim();

        if (purchaseStr.isEmpty() || daysStr.isEmpty()) {
            tvExpiryDateAuto.setText("");
            tvExpiryDateAuto.setHint("输入生产日期和保质期后自动计算");
            return;
        }

        try {
            int inputValue = Integer.parseInt(daysStr);
            String unit = "天";
            if (spExpiryUnit != null && spExpiryUnit.getSelectedItem() != null) {
                unit = spExpiryUnit.getSelectedItem().toString();
            }
            int days = convertToDays(inputValue, unit);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            java.util.Date purchaseDate = sdf.parse(purchaseStr);
            if (purchaseDate != null) {
                long expiryMillis = purchaseDate.getTime() + (long) days * 24 * 60 * 60 * 1000;
                String expiryStr = sdf.format(new java.util.Date(expiryMillis));
                tvExpiryDateAuto.setText(expiryStr);

                // 检查是否已过期或即将过期
                long daysLeft = (expiryMillis - System.currentTimeMillis()) / (24 * 60 * 60 * 1000);
                if (daysLeft < 0) {
                    tvExpiryDateAuto.setTextColor(Color.parseColor("#F56565"));
                    tvExpiryDateAuto.setText(expiryStr + " (已过期" + Math.abs(daysLeft) + "天)");
                } else if (daysLeft <= 7) {
                    tvExpiryDateAuto.setTextColor(Color.parseColor("#ED8936"));
                    tvExpiryDateAuto.setText(expiryStr + " (还剩" + daysLeft + "天)");
                } else {
                    tvExpiryDateAuto.setTextColor(Color.parseColor("#2D3748"));
                }
            }
        } catch (Exception e) {
            tvExpiryDateAuto.setText("");
            tvExpiryDateAuto.setHint("日期格式错误");
        }
    }

    /**
     * 问题5: 编辑模式加载物品数据
     */
    private void loadItemForEdit(int goodsId) {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(goodsId));
        ApiClient.get("goods.php?action=detail", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        // ApiClient已提取data层,后端返回goods包裹
                        JsonObject item = null;
                        if (data.has("goods") && !data.get("goods").isJsonNull()) {
                            item = data.getAsJsonObject("goods");
                        } else if (data.has("name")) {
                            item = data; // 兼容直接返回物品数据
                        }
                        if (item == null) {
                            Toast.makeText(AddItemActivity.this, "物品数据为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (item.has("name") && !item.get("name").isJsonNull()) etName.setText(item.get("name").getAsString());
                        if (item.has("barcode") && !item.get("barcode").isJsonNull()) etBarcode.setText(item.get("barcode").getAsString());
                        if (item.has("brand") && !item.get("brand").isJsonNull()) {
                            String brand = item.get("brand").getAsString();
                            if (!brand.isEmpty()) etBrand.setText(brand);
                        }
                        if (item.has("spec") && !item.get("spec").isJsonNull()) {
                            String spec = item.get("spec").getAsString();
                            if (!spec.isEmpty() && etSpec != null) etSpec.setText(spec);
                        }
                        if (item.has("manufacturer") && !item.get("manufacturer").isJsonNull()) {
                            String mfr = item.get("manufacturer").getAsString();
                            if (!mfr.isEmpty() && etManufacturer != null) etManufacturer.setText(mfr);
                        }
                        if (item.has("quantity")) {
                            try { etQuantity.setText(String.valueOf((int) item.get("quantity").getAsDouble())); } catch (Exception ignored) {}
                        }
                        if (item.has("unit") && !item.get("unit").isJsonNull()) {
                            String unit = item.get("unit").getAsString();
                            android.widget.ArrayAdapter<String> adapter = (android.widget.ArrayAdapter<String>) etUnit.getAdapter();
                            if (adapter != null) {
                                int pos = adapter.getPosition(unit);
                                if (pos >= 0) etUnit.setSelection(pos);
                            }
                        }
                        if (item.has("purchase_date") && !item.get("purchase_date").isJsonNull()) {
                            selectedPurchaseDate = item.get("purchase_date").getAsString();
                            etPurchaseDate.setText(selectedPurchaseDate);
                        }
                        if (item.has("expiry_date") && !item.get("expiry_date").isJsonNull()) {
                            try {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                java.util.Date expiry = sdf.parse(item.get("expiry_date").getAsString());
                                if (expiry != null && !selectedPurchaseDate.isEmpty()) {
                                    java.util.Date purchase = sdf.parse(selectedPurchaseDate);
                                    if (purchase != null) {
                                        int days = (int) ((expiry.getTime() - purchase.getTime()) / 86400000L);
                                        if (days > 0) etExpiryDays.setText(String.valueOf(days));
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        if (item.has("purchase_price") && !item.get("purchase_price").isJsonNull()) {
                            try { etPrice.setText(String.valueOf(item.get("purchase_price").getAsDouble())); } catch (Exception ignored) {}
                        }
                        if (item.has("note") && !item.get("note").isJsonNull()) etNote.setText(item.get("note").getAsString());
                        if (item.has("is_private")) swPrivate.setChecked(item.get("is_private").getAsInt() == 1);
                        if (item.has("space_id") && !item.get("space_id").isJsonNull()) {
                            selectedSpaceId = item.get("space_id").getAsInt();
                        }
                        if (item.has("space_name") && !item.get("space_name").isJsonNull()) {
                            tvSpaceName.setText(item.get("space_name").getAsString());
                        }

                        // 加载分类
                        if (item.has("category") && !item.get("category").isJsonNull()) {
                            String cat = item.get("category").getAsString();
                            int catPos = getCategoryPosition(cat);
                            if (catPos >= 0 && spCategory != null) {
                                spCategory.setSelection(catPos);
                            }
                        }

                        // 加载库存阈值
                        if (item.has("stock_threshold") && !item.get("stock_threshold").isJsonNull()) {
                            try { etThreshold.setText(String.valueOf((int) item.get("stock_threshold").getAsDouble())); } catch (Exception ignored) {}
                        }

                        // 加载过期提醒
                        if (item.has("expiry_reminder") && !item.get("expiry_reminder").isJsonNull() && spExpiryReminder != null) {
                            String reminder = item.get("expiry_reminder").getAsString();
                            ArrayAdapter<String> adapter = (ArrayAdapter<String>) spExpiryReminder.getAdapter();
                            if (adapter != null) {
                                int pos = adapter.getPosition(reminder);
                                if (pos >= 0) spExpiryReminder.setSelection(pos);
                            }
                        }

                        // 加载分类扩展字段
                        if (item.has("size") && !item.get("size").isJsonNull() && etSize != null) {
                            etSize.setText(item.get("size").getAsString());
                        }
                        if (item.has("color") && !item.get("color").isJsonNull() && etColor != null) {
                            etColor.setText(item.get("color").getAsString());
                        }
                        if (item.has("material") && !item.get("material").isJsonNull() && etMaterial != null) {
                            etMaterial.setText(item.get("material").getAsString());
                        }
                        if (item.has("shoe_size") && !item.get("shoe_size").isJsonNull() && etShoeSize != null) {
                            etShoeSize.setText(item.get("shoe_size").getAsString());
                        }
                        if (item.has("model") && !item.get("model").isJsonNull() && etModel != null) {
                            etModel.setText(item.get("model").getAsString());
                        }
                        if (item.has("serial_number") && !item.get("serial_number").isJsonNull() && etSerialNumber != null) {
                            etSerialNumber.setText(item.get("serial_number").getAsString());
                        }
                        if (item.has("warranty") && !item.get("warranty").isJsonNull() && etWarranty != null) {
                            etWarranty.setText(item.get("warranty").getAsString());
                        }
                        if (item.has("effect") && !item.get("effect").isJsonNull() && etEffect != null) {
                            etEffect.setText(item.get("effect").getAsString());
                        }
                        if (item.has("skin_type") && !item.get("skin_type").isJsonNull() && etSkinType != null) {
                            etSkinType.setText(item.get("skin_type").getAsString());
                        }

                        // 加载已有标签
                        if (item.has("tags") && !item.get("tags").isJsonNull()) {
                            JsonArray tags = item.getAsJsonArray("tags");
                            for (int i = 0; i < tags.size(); i++) {
                                JsonObject tag = tags.get(i).getAsJsonObject();
                                int tid = tag.has("id") ? tag.get("id").getAsInt() : 0;
                                String tname = tag.has("name") && !tag.get("name").isJsonNull() ? tag.get("name").getAsString() : "";
                                if (tid > 0 && !selectedTagIds.contains(tid)) {
                                    selectedTagIds.add(tid);
                                    selectedTagNames.add(tname);
                                }
                            }
                            updateTagDisplay();
                        }

                        // 加载已有图片
                        if (item.has("images") && !item.get("images").isJsonNull()) {
                            JsonArray images = item.getAsJsonArray("images");
                            for (int i = 0; i < images.size(); i++) {
                                JsonObject img = images.get(i).getAsJsonObject();
                                String imgUrl = img.has("image_path") && !img.get("image_path").isJsonNull() ? img.get("image_path").getAsString() : "";
                                if (!imgUrl.isEmpty()) {
                                    existingImagePaths.add(imgUrl);
                                    addExistingPhotoToView(imgUrl, existingImagePaths.size() - 1);
                                }
                            }
                        }

                        setTitle("编辑物品");
                        btnSave.setText("保存修改");
                    } catch (Exception e) {
                        android.util.Log.e("AddItem", "loadItemForEdit error: " + e.getMessage(), e);
                        Toast.makeText(AddItemActivity.this, "加载物品数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(AddItemActivity.this, "加载失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void saveItem() {
        saveItem(false);
    }

    private void saveItem(boolean continueAfterSave) {
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

        // 检查是否选择了存放位置
        if (selectedSpaceId <= 0) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("未选择存放位置")
                .setMessage("该物品还没有指定存放位置，你希望？")
                .setPositiveButton("选择位置", (d, w) -> showSpacePickerDialog())
                .setNegativeButton("暂不归位", (d, w) -> doSave(continueAfterSave))
                .show();
            return;
        }

        doSave(continueAfterSave);
    }

    private void doSave(boolean continueAfterSave) {
        int houseId = App.getInstance().getCurrentHouseId();
        String name = etName.getText().toString().trim();

        // 保存上次选择的空间
        if (selectedSpaceId > 0) {
            getSharedPreferences("add_item_prefs", MODE_PRIVATE).edit()
                .putInt("last_space_id", selectedSpaceId)
                .putString("last_space_name", tvSpaceName.getText().toString())
                .putString("last_space_path", tvSpacePath.getText().toString())
                .apply();
        }

        // 先上传照片,再创建物品
        if (!photos.isEmpty() && NetworkUtils.isNetworkAvailable(this)) {
            btnSave.setEnabled(false);
            btnSave.setText("上传照片中...");
            btnSaveContinue.setEnabled(false);
            uploadPhotosThenCreate(continueAfterSave);
            return;
        }

        // 没有照片,直接创建
        Goods goods = new Goods();
        goods.houseId = houseId;
        goods.spaceId = selectedSpaceId;
        goods.name = name;
        goods.barcode = etBarcode.getText().toString().trim();
        goods.category = "";
        String qtyStr = etQuantity.getText().toString().trim();
        goods.quantity = qtyStr.isEmpty() ? 1 : Double.parseDouble(qtyStr);
        goods.unit = etUnit.getSelectedItem() != null ? etUnit.getSelectedItem().toString() : "个";
        // 过期日期:使用自动计算的日期
        String autoExpiry = tvExpiryDateAuto.getText().toString().trim();
        if (!autoExpiry.isEmpty() && !autoExpiry.contains("输入")) {
            // 提取日期部分(去掉可能的提示文字)
            goods.expiryDate = autoExpiry.length() >= 10 ? autoExpiry.substring(0, 10) : autoExpiry;
        } else {
            // 回退:从保质期值和单位计算
            String daysStr = etExpiryDays.getText().toString().trim();
            if (!daysStr.isEmpty()) {
                try {
                    int inputValue = Integer.parseInt(daysStr);
                    String unit = "天";
                    if (spExpiryUnit != null && spExpiryUnit.getSelectedItem() != null) {
                        unit = spExpiryUnit.getSelectedItem().toString();
                    }
                    int days = convertToDays(inputValue, unit);
                    long expiryMillis = System.currentTimeMillis() + (long) days * 24 * 60 * 60 * 1000;
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    goods.expiryDate = sdf.format(new java.util.Date(expiryMillis));
                } catch (Exception e) {
                    goods.expiryDate = "";
                }
            }
        }
        goods.note = etNote.getText().toString().trim();
        goods.isPrivate = swPrivate.isChecked() ? 1 : 0;
        String priceStr = etPrice.getText().toString().trim();
        goods.purchasePrice = priceStr.isEmpty() ? 0 : Double.parseDouble(priceStr);

        // 获取选中的分类
        String selectedCategory = "";
        if (spCategory != null && spCategory.getSelectedItem() != null) {
            selectedCategory = spCategory.getSelectedItem().toString();
        }

        // 获取阈值
        String thresholdStr = etThreshold.getText().toString().trim();
        double threshold = thresholdStr.isEmpty() ? 0 : Double.parseDouble(thresholdStr);

        // 获取过期提醒
        String expiryReminder = "";
        if (spExpiryReminder != null && spExpiryReminder.getSelectedItem() != null) {
            expiryReminder = spExpiryReminder.getSelectedItem().toString();
        }

        btnSave.setEnabled(false);
        btnSave.setText("保存中...");
        btnSaveContinue.setEnabled(false);
        btnSaveContinue.setText("保存中...");

        if (NetworkUtils.isNetworkAvailable(this)) {
            JsonObject body = new JsonObject();
            body.addProperty("house_id", goods.houseId);
            body.addProperty("space_id", goods.spaceId);
            body.addProperty("name", goods.name);
            body.addProperty("barcode", goods.barcode);
            body.addProperty("category", selectedCategory);
            body.addProperty("brand", etBrand.getText().toString().trim());
            body.addProperty("manufacturer", etManufacturer != null ? etManufacturer.getText().toString().trim() : "");
            body.addProperty("spec", etSpec != null ? etSpec.getText().toString().trim() : "");
            body.addProperty("quantity", goods.quantity);
            body.addProperty("unit", goods.unit);
            body.addProperty("purchase_date", selectedPurchaseDate);
            body.addProperty("expiry_date", goods.expiryDate);
            body.addProperty("purchase_price", goods.purchasePrice);
            body.addProperty("stock_threshold", threshold);
            body.addProperty("note", goods.note);
            body.addProperty("is_private", goods.isPrivate);

            // 分类特有字段
            if (spExpiryReminder != null) {
                body.addProperty("expiry_reminder", expiryReminder);
            }
            // 服装/鞋帽字段
            if (etSize != null) body.addProperty("size", etSize.getText().toString().trim());
            if (etColor != null) body.addProperty("color", etColor.getText().toString().trim());
            if (etMaterial != null) body.addProperty("material", etMaterial.getText().toString().trim());
            if (etShoeSize != null) body.addProperty("shoe_size", etShoeSize.getText().toString().trim());
            if (spSeason != null && spSeason.getSelectedItemPosition() > 0) body.addProperty("season", spSeason.getSelectedItem().toString());
            // 数码字段
            if (etModel != null) body.addProperty("model", etModel.getText().toString().trim());
            if (etSerialNumber != null) body.addProperty("serial_number", etSerialNumber.getText().toString().trim());
            if (etWarranty != null) body.addProperty("warranty", etWarranty.getText().toString().trim());
            // 化妆品字段
            if (etEffect != null) body.addProperty("effect", etEffect.getText().toString().trim());
            if (etSkinType != null) body.addProperty("skin_type", etSkinType.getText().toString().trim());

            // 添加标签
            if (!selectedTagIds.isEmpty()) {
                com.google.gson.JsonArray tagsArray = new com.google.gson.JsonArray();
                for (int tagId : selectedTagIds) {
                    tagsArray.add(tagId);
                }
                body.add("tags", tagsArray);
            }

            // 编辑模式:附带已有图片路径
            if (isEditMode && !existingImagePaths.isEmpty()) {
                JsonArray imagesArray = new JsonArray();
                for (String path : existingImagePaths) imagesArray.add(path);
                body.add("images", imagesArray);
            }

            String endpoint = isEditMode ? "goods.php?action=update" : "goods.php?action=create";
            if (isEditMode) body.addProperty("id", editGoodsId);

            ApiClient.post(endpoint, body, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    runOnUiThread(() -> {
                        // 如果有待关联的套装，把物品加入
                        if (pendingOutfitId > 0 && !isEditMode) {
                            int goodsId = 0;
                            try {
                                if (data.has("id")) goodsId = data.get("id").getAsInt();
                            } catch (Exception ignored) {}
                            if (goodsId > 0) {
                                addItemToOutfit(pendingOutfitId, goodsId);
                            }
                        }
                        Toast.makeText(AddItemActivity.this, "✅ 保存成功", Toast.LENGTH_SHORT).show();
                        if (continueAfterSave) {
                            resetForm();
                        } else {
                            finish();
                        }
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("保存返回");
                        btnSaveContinue.setEnabled(true);
                        btnSaveContinue.setText("保存继续");
                        Toast.makeText(AddItemActivity.this, "保存失败: " + msg, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            localDb.saveOfflineGoods(goods);
            Toast.makeText(this, "已保存到本地,联网后自动同步", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void resetForm() {
        // 重置表单字段,保留空间选择和模式
        etName.setText("");
        etBarcode.setText("");
        etQuantity.setText("1");
        etUnit.setSelection(0);
        etExpiryDays.setText("");
        etPrice.setText("");
        etThreshold.setText("");
        if (etSpec != null) etSpec.setText("");
        if (etManufacturer != null) etManufacturer.setText("");
        etPurchaseDate.setText("");
        selectedPurchaseDate = "";
        etNote.setText("");
        tvExpiryDateAuto.setText("");
        tvExpiryDateAuto.setHint("输入生产日期和保质期后自动计算");
        tvExpiryDateAuto.setTextColor(Color.parseColor("#2D3748"));
        swPrivate.setChecked(false);

        // 重置分类
        if (spCategory != null) spCategory.setSelection(0);
        if (spExpiryReminder != null) spExpiryReminder.setSelection(0);

        // 重置分类扩展字段
        if (etSize != null) etSize.setText("");
        if (etColor != null) etColor.setText("");
        if (etMaterial != null) etMaterial.setText("");
        if (etShoeSize != null) etShoeSize.setText("");
        if (etModel != null) etModel.setText("");
        if (etSerialNumber != null) etSerialNumber.setText("");
        if (etWarranty != null) etWarranty.setText("");
        if (etEffect != null) etEffect.setText("");
        if (etSkinType != null) etSkinType.setText("");

        // 清空照片和标签
        photos.clear();
        existingImagePaths.clear();
        selectedTagIds.clear();
        selectedTagNames.clear();
        llPhotos.removeAllViews();
        llTags.removeAllViews();

        // 恢复按钮状态
        btnSave.setEnabled(true);
        btnSave.setText("保存返回");
        btnSaveContinue.setEnabled(true);
        btnSaveContinue.setText("保存继续");

        // 滚动到顶部
        ScrollView sv = findViewById(R.id.scroll_view);
        if (sv != null) sv.scrollTo(0, 0);

        // 聚焦到名称输入框
        etName.requestFocus();
    }

    /**
     * 先上传所有照片,获取路径后创建物品
     */
    private void createGoodsWithImages(List<String> imagePaths, boolean continueAfterSave) {
        int houseId = App.getInstance().getCurrentHouseId();

        Goods goods = new Goods();
        goods.houseId = houseId;
        goods.spaceId = selectedSpaceId;
        goods.name = etName.getText().toString().trim();
        goods.barcode = etBarcode.getText().toString().trim();
        goods.category = "";
        String qtyStr = etQuantity.getText().toString().trim();
        goods.quantity = qtyStr.isEmpty() ? 1 : Double.parseDouble(qtyStr);
        goods.unit = etUnit.getSelectedItem() != null ? etUnit.getSelectedItem().toString() : "个";

        String autoExpiry = tvExpiryDateAuto.getText().toString().trim();
        if (!autoExpiry.isEmpty() && !autoExpiry.contains("输入")) {
            goods.expiryDate = autoExpiry.length() >= 10 ? autoExpiry.substring(0, 10) : autoExpiry;
        } else {
            String daysStr = etExpiryDays.getText().toString().trim();
            if (!daysStr.isEmpty()) {
                try {
                    int inputValue = Integer.parseInt(daysStr);
                    String unit = "天";
                    if (spExpiryUnit != null && spExpiryUnit.getSelectedItem() != null) {
                        unit = spExpiryUnit.getSelectedItem().toString();
                    }
                    int days = convertToDays(inputValue, unit);
                    long expiryMillis = System.currentTimeMillis() + (long) days * 24 * 60 * 60 * 1000;
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    goods.expiryDate = sdf.format(new java.util.Date(expiryMillis));
                } catch (Exception e) { goods.expiryDate = ""; }
            }
        }

        goods.note = etNote.getText().toString().trim();
        goods.isPrivate = swPrivate.isChecked() ? 1 : 0;
        String priceStr = etPrice.getText().toString().trim();
        goods.purchasePrice = priceStr.isEmpty() ? 0 : Double.parseDouble(priceStr);

        // 获取选中的分类
        String selectedCategory = "";
        if (spCategory != null && spCategory.getSelectedItem() != null) {
            selectedCategory = spCategory.getSelectedItem().toString();
        }

        String thresholdStr = etThreshold.getText().toString().trim();
        double threshold = thresholdStr.isEmpty() ? 0 : Double.parseDouble(thresholdStr);

        String expiryReminder = "";
        if (spExpiryReminder != null && spExpiryReminder.getSelectedItem() != null) {
            expiryReminder = spExpiryReminder.getSelectedItem().toString();
        }

        JsonObject body = new JsonObject();
        body.addProperty("house_id", goods.houseId);
        body.addProperty("space_id", goods.spaceId);
        body.addProperty("name", goods.name);
        body.addProperty("barcode", goods.barcode);
        body.addProperty("category", selectedCategory);
        body.addProperty("brand", etBrand.getText().toString().trim());
        body.addProperty("manufacturer", etManufacturer != null ? etManufacturer.getText().toString().trim() : "");
        body.addProperty("spec", etSpec != null ? etSpec.getText().toString().trim() : "");
        body.addProperty("quantity", goods.quantity);
        body.addProperty("unit", goods.unit);
        body.addProperty("purchase_date", selectedPurchaseDate);
        body.addProperty("expiry_date", goods.expiryDate);
        body.addProperty("purchase_price", goods.purchasePrice);
        body.addProperty("stock_threshold", threshold);
        body.addProperty("note", goods.note);
        body.addProperty("is_private", goods.isPrivate);

        // 分类特有字段
        if (spExpiryReminder != null) body.addProperty("expiry_reminder", expiryReminder);
        if (etSize != null) body.addProperty("size", etSize.getText().toString().trim());
        if (etColor != null) body.addProperty("color", etColor.getText().toString().trim());
        if (etMaterial != null) body.addProperty("material", etMaterial.getText().toString().trim());
        if (etShoeSize != null) body.addProperty("shoe_size", etShoeSize.getText().toString().trim());
        if (spSeason != null && spSeason.getSelectedItemPosition() > 0) body.addProperty("season", spSeason.getSelectedItem().toString());
        if (etModel != null) body.addProperty("model", etModel.getText().toString().trim());
        if (etSerialNumber != null) body.addProperty("serial_number", etSerialNumber.getText().toString().trim());
        if (etWarranty != null) body.addProperty("warranty", etWarranty.getText().toString().trim());
        if (etEffect != null) body.addProperty("effect", etEffect.getText().toString().trim());
        if (etSkinType != null) body.addProperty("skin_type", etSkinType.getText().toString().trim());

        if (!selectedTagIds.isEmpty()) {
            JsonArray tagsArray = new JsonArray();
            for (int tagId : selectedTagIds) tagsArray.add(tagId);
            body.add("tags", tagsArray);
        }

        // 附带已上传的图片路径
        if (!imagePaths.isEmpty()) {
            JsonArray imagesArray = new JsonArray();
            for (String path : imagePaths) imagesArray.add(path);
            body.add("images", imagesArray);
        }

        // 编辑模式:使用update接口并附带物品ID
        String endpoint = isEditMode ? "goods.php?action=update" : "goods.php?action=create";
        if (isEditMode) body.addProperty("id", editGoodsId);

        ApiClient.post(endpoint, body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    Toast.makeText(AddItemActivity.this, "✅ 保存成功", Toast.LENGTH_SHORT).show();
                    if (continueAfterSave) {
                        resetForm();
                    } else {
                        finish();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("保存返回");
                    btnSaveContinue.setEnabled(true);
                    btnSaveContinue.setText("保存继续");
                    Toast.makeText(AddItemActivity.this, "保存失败: " + msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void uploadPhotosThenCreate(boolean continueAfterSave) {
        new Thread(() -> {
            List<String> imagePaths = new ArrayList<>();
            for (Bitmap photo : photos) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    photo.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    byte[] imageBytes = baos.toByteArray();

                    okhttp3.MultipartBody.Builder builder = new okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("file", "photo_" + System.currentTimeMillis() + ".jpg",
                            okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/jpeg"), imageBytes));

                    String uploadUrl = App.BASE_URL + "upload.php?action=image";
                    okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
                        .url(uploadUrl)
                        .post(builder.build());

                    String token = App.getInstance().getToken();
                    if (token != null && !token.isEmpty()) {
                        reqBuilder.addHeader("Authorization", "Bearer " + token);
                    }

                    okhttp3.Response response = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                        .newCall(reqBuilder.build())
                        .execute();

                    if (response.body() != null) {
                        String body = response.body().string();
                        int jsonStart = body.indexOf('{');
                        if (jsonStart >= 0) body = body.substring(jsonStart);
                        com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(body));
                        reader.setLenient(true);
                        JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("code") && json.get("code").getAsInt() == 0) {
                            JsonObject data = json.getAsJsonObject("data");
                            if (data.has("image_path")) {
                                imagePaths.add(data.get("image_path").getAsString());
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("AddItem", "Photo upload failed: " + e.getMessage());
                }
            }

            // 所有照片上传完毕,创建物品(附带图片路径)
            final List<String> finalPaths = imagePaths;
            runOnUiThread(() -> createGoodsWithImages(finalPaths, continueAfterSave));
        }).start();
    }

    private void uploadPhotos(int goodsId) {
        // 异步上传照片,不阻塞UI
        new Thread(() -> {
            List<String> imagePaths = new java.util.ArrayList<>();
            for (Bitmap photo : photos) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    photo.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    byte[] imageBytes = baos.toByteArray();

                    // 使用multipart上传到 upload.php?action=image
                    okhttp3.MultipartBody.Builder builder = new okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("file", "photo_" + System.currentTimeMillis() + ".jpg",
                            okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/jpeg"), imageBytes));

                    String uploadUrl = App.BASE_URL + "upload.php?action=image";
                    okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
                        .url(uploadUrl)
                        .post(builder.build());

                    String token = App.getInstance().getToken();
                    if (token != null && !token.isEmpty()) {
                        reqBuilder.addHeader("Authorization", "Bearer " + token);
                    }

                    okhttp3.Response response = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                        .newCall(reqBuilder.build())
                        .execute();

                    if (response.body() != null) {
                        String body = response.body().string();
                        // 提取JSON部分
                        int jsonStart = body.indexOf('{');
                        if (jsonStart >= 0) body = body.substring(jsonStart);
                        try {
                            com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(body));
                            reader.setLenient(true);
                            JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                            if (json.has("code") && json.get("code").getAsInt() == 0) {
                                JsonObject data = json.getAsJsonObject("data");
                                if (data.has("image_path")) {
                                    imagePaths.add(data.get("image_path").getAsString());
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }

            // 将图片路径关联到物品
            if (!imagePaths.isEmpty()) {
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("id", goodsId);
                    com.google.gson.JsonArray imagesArray = new com.google.gson.JsonArray();
                    for (String path : imagePaths) {
                        imagesArray.add(path);
                    }
                    body.add("images", imagesArray);
                    // 同步调用更新接口,添加图片
                    okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(
                        okhttp3.MediaType.parse("application/json"), body.toString());
                    String updateUrl = App.BASE_URL + "goods.php?action=update";
                    okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
                        .url(updateUrl)
                        .post(reqBody);
                    String token = App.getInstance().getToken();
                    if (token != null && !token.isEmpty()) {
                        reqBuilder.addHeader("Authorization", "Bearer " + token);
                    }
                    new okhttp3.OkHttpClient()
                        .newCall(reqBuilder.build())
                        .execute();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    /**
     * 加入套装对话框 - 新建或加入已有套装
     */
    private void showOutfitDialog() {
        String goodsName = etName.getText().toString().trim();
        String goodsColor = etColor != null ? etColor.getText().toString().trim() : "";
        String season = spSeason != null && spSeason.getSelectedItemPosition() > 0 ? spSeason.getSelectedItem().toString() : "";
        String category = spCategory != null ? spCategory.getSelectedItem().toString() : "";

        // 弹出选择：新建套装 / 加入已有套装
        String[] options = {"🏷 新建套装", "📋 加入已有套装"};
        new AlertDialog.Builder(this)
            .setTitle("加入套装")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    showCreateOutfitDialog(goodsName, goodsColor, season, category);
                } else {
                    showJoinOutfitDialog(season);
                }
            })
            .show();
    }

    /**
     * 新建套装 - 推荐名称
     */
    private void showCreateOutfitDialog(String goodsName, String goodsColor, String season, String category) {
        // 系统推荐名称
        String suggestedName = "";
        if (!season.isEmpty() && !goodsColor.isEmpty()) {
            suggestedName = season + "季" + goodsColor + "搭配";
        } else if (!season.isEmpty() && !goodsName.isEmpty()) {
            suggestedName = season + "季" + goodsName + "搭配";
        } else if (!goodsName.isEmpty()) {
            suggestedName = goodsName + "套装";
        } else {
            suggestedName = "我的套装";
        }

        EditText input = new EditText(this);
        input.setText(suggestedName);
        input.setHint("输入套装名称");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setTextSize(14);
        // 全选方便用户修改
        input.selectAll();

        new AlertDialog.Builder(this)
            .setTitle("🏷 新建套装")
            .setMessage("系统推荐名称（可修改）：")
            .setView(input)
            .setPositiveButton("创建", (d, w) -> {
                String inputName = input.getText().toString().trim();
                createOutfitAndAddItem(inputName.isEmpty() ? suggestedName : inputName, season);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 创建套装并把当前物品加入
     */
    private void createOutfitAndAddItem(String name, String season) {
        int houseId = App.getInstance().getCurrentHouseId();
        JsonObject body = new JsonObject();
        body.addProperty("house_id", houseId);
        body.addProperty("name", name);
        body.addProperty("season", season);
        // 暂不关联物品，等物品保存后再关联
        body.add("items", new com.google.gson.JsonArray());

        Toast.makeText(this, "正在创建套装...", Toast.LENGTH_SHORT).show();
        ApiClient.post("outfit.php?action=create", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        int outfitId = data.has("id") ? data.get("id").getAsInt() : 0;
                        if (outfitId > 0) {
                            Toast.makeText(AddItemActivity.this, "✅ 套装已创建，保存物品后自动加入", Toast.LENGTH_SHORT).show();
                            // 保存 outfitId，等物品保存后关联
                            pendingOutfitId = outfitId;
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(AddItemActivity.this, "创建失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 加入已有套装 - 按季节匹配
     */
    private void showJoinOutfitDialog(String season) {
        int houseId = App.getInstance().getCurrentHouseId();
        String url = "outfit.php?action=list&house_id=" + houseId;
        if (!season.isEmpty()) url += "&season=" + season;

        Toast.makeText(this, "加载套装列表...", Toast.LENGTH_SHORT).show();
        ApiClient.get(url, null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
                        if (list.size() == 0) {
                            String msg = season.isEmpty() ? "还没有套装" : "没有匹配 " + season + " 的套装";
                            new AlertDialog.Builder(AddItemActivity.this)
                                .setTitle("加入套装")
                                .setMessage(msg + "，是否新建一个？")
                                .setPositiveButton("新建", (d, w) -> showCreateOutfitDialog(
                                    etName.getText().toString().trim(),
                                    etColor != null ? etColor.getText().toString().trim() : "",
                                    season, ""))
                                .setNegativeButton("取消", null)
                                .show();
                            return;
                        }
                        showOutfitPickerDialog(list);
                    } catch (Exception e) {
                        Toast.makeText(AddItemActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(AddItemActivity.this, "加载失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 套装选择列表 - 展示缩略图
     */
    private void showOutfitPickerDialog(JsonArray outfits) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(12), dp(16), dp(8));

        for (int i = 0; i < outfits.size(); i++) {
            JsonObject outfit = outfits.get(i).getAsJsonObject();
            int outfitId = outfit.get("id").getAsInt();
            String name = outfit.has("name") ? outfit.get("name").getAsString() : "";
            String season = outfit.has("season") && !outfit.get("season").isJsonNull() ? outfit.get("season").getAsString() : "";
            JsonArray items = outfit.has("items") && !outfit.get("items").isJsonNull() ? outfit.getAsJsonArray("items") : new JsonArray();

            // 套装卡片
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
            cardBg.setCornerRadius(dp(10));
            cardBg.setColor(0xFFF7FAFC);
            cardBg.setStroke(dp(1), 0xFFE2E8F0);
            card.setBackground(cardBg);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = dp(8);
            card.setLayoutParams(cardLp);

            // 左侧：物品缩略图网格 (2x2)
            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(dp(56), dp(56));
            grid.setLayoutParams(gridLp);

            for (int row = 0; row < 2; row++) {
                LinearLayout gridRow = new LinearLayout(this);
                gridRow.setOrientation(LinearLayout.HORIZONTAL);
                gridRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
                for (int col = 0; col < 2; col++) {
                    int idx = row * 2 + col;
                    android.widget.ImageView img = new android.widget.ImageView(this);
                    img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    img.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    if (idx < items.size()) {
                        JsonObject item = items.get(idx).getAsJsonObject();
                        String cover = item.has("cover_image") && !item.get("cover_image").isJsonNull() ? item.get("cover_image").getAsString() : "";
                        if (!cover.isEmpty()) {
                            try { com.bumptech.glide.Glide.with(this).load(cover).centerCrop().into(img); } catch (Exception ignored) {}
                        } else {
                            img.setBackgroundColor(0xFFEDF2F7);
                        }
                    } else {
                        img.setBackgroundColor(0xFFEDF2F7);
                    }
                    gridRow.addView(img);
                }
                grid.addView(gridRow);
            }
            card.addView(grid);

            // 右侧：名称+季节+数量
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            infoLp.leftMargin = dp(12);
            info.setLayoutParams(infoLp);

            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextSize(14);
            tvName.setTextColor(0xFF2D3748);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            info.addView(tvName);

            String meta = "";
            if (!season.isEmpty()) meta += season + " · ";
            meta += items.size() + " 件";
            TextView tvMeta = new TextView(this);
            tvMeta.setText(meta);
            tvMeta.setTextSize(11);
            tvMeta.setTextColor(0xFF718096);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            metaLp.topMargin = dp(4);
            tvMeta.setLayoutParams(metaLp);
            info.addView(tvMeta);

            card.addView(info);
            container.addView(card);

            // 点击加入
            final int fOutfitId = outfitId;
            final String fName = name;
            card.setOnClickListener(v -> {
                // 关闭对话框后加入
                pendingOutfitId = fOutfitId;
                Toast.makeText(this, "✅ 物品保存后将加入「" + fName + "」", Toast.LENGTH_SHORT).show();
            });
        }

        new AlertDialog.Builder(this)
            .setTitle("选择套装")
            .setView(container)
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 将物品加入套装
     */
    private void addItemToOutfit(int outfitId, int goodsId) {
        // 先获取套装详情，然后更新添加物品
        ApiClient.get("outfit.php?action=detail&id=" + outfitId, null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                try {
                    JsonObject outfit = data.getAsJsonObject("outfit");
                    JsonArray existingItems = outfit.has("items") && !outfit.get("items").isJsonNull()
                        ? outfit.getAsJsonArray("items") : new JsonArray();

                    // 构建新的items列表
                    com.google.gson.JsonArray newItems = new com.google.gson.JsonArray();
                    for (int i = 0; i < existingItems.size(); i++) {
                        JsonObject it = existingItems.get(i).getAsJsonObject();
                        JsonObject item = new JsonObject();
                        item.addProperty("goods_id", it.get("goods_id").getAsInt());
                        item.addProperty("slot", it.has("slot") && !it.get("slot").isJsonNull() ? it.get("slot").getAsString() : "");
                        newItems.add(item);
                    }
                    // 添加新物品
                    JsonObject newItem = new JsonObject();
                    newItem.addProperty("goods_id", goodsId);
                    newItem.addProperty("slot", guessSlot(etName.getText().toString().trim(),
                        spCategory != null ? spCategory.getSelectedItem().toString() : ""));
                    newItems.add(newItem);

                    // 更新套装
                    JsonObject body = new JsonObject();
                    body.addProperty("id", outfitId);
                    body.add("items", newItems);
                    ApiClient.post("outfit.php?action=update", body, new ApiClient.ApiCallback() {
                        @Override public void onSuccess(JsonObject data) {}
                        @Override public void onError(String msg) {}
                    });
                } catch (Exception ignored) {}
            }
            @Override public void onError(String msg) {}
        });
    }

    private String guessSlot(String name, String category) {
        if (category.equals("鞋帽") || name.contains("鞋") || name.contains("靴")) return "shoes";
        if (name.contains("帽")) return "hat";
        if (name.contains("裤") || name.contains("裙")) return "bottom";
        if (name.contains("外套") || name.contains("夹克") || name.contains("大衣") || name.contains("羽绒")) return "outer";
        if (name.contains("包") || name.contains("袋") || name.contains("项链") || name.contains("手表")) return "accessory";
        return "top";
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

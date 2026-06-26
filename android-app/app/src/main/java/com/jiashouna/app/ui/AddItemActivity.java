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
import com.jiashouna.app.ui.BarcodeScanActivity;

import java.io.*;
import java.util.*;

public class AddItemActivity extends AppCompatActivity {
    private EditText etName, etBarcode, etBrand, etQuantity, etExpiryDays, etPrice, etNote, etThreshold;
    private Spinner etUnit;
    private EditText etPurchaseDate;
    private TextView tvExpiryDateAuto;
    private Spinner spExpiryUnit;
    private String selectedPurchaseDate = "";
    private View spacePicker;
    private TextView tvSpaceName, tvSpacePath, tvScanHint;
    private Switch swPrivate;
    private Button btnSave, btnSaveContinue;
    private TextView btnStartScan, btnAddPhoto, btnAddTag;
    private LinearLayout scanContainer, llPhotos, llTags;
    private TextView tabScan, tabPhoto, tabManual, tabAi;
    private int selectedSpaceId = 0;
    private LocalDb localDb;
    private JsonArray spaceList = new JsonArray();
    private JsonArray tagList = new JsonArray();
    private List<Integer> selectedTagIds = new ArrayList<>();
    private List<String> selectedTagNames = new ArrayList<>();
    private List<Bitmap> photos = new ArrayList<>();

    private static final int REQUEST_BARCODE = 100;
    private static final int REQUEST_PHOTO = 101;
    private static final int REQUEST_GALLERY = 102;
    private static final int REQUEST_CAMERA_PERMISSION = 103;
    private static final int REQUEST_AI_PHOTO = 104;

    private boolean isEditMode = false;
    private int editGoodsId = 0;

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
        btnAddTag = findViewById(R.id.btn_add_tag);
        llPhotos = findViewById(R.id.ll_photos);
        llTags = findViewById(R.id.ll_tags);

        // 选项卡
        tabScan.setOnClickListener(v -> switchTab("scan"));
        tabPhoto.setOnClickListener(v -> switchTab("photo"));
        tabManual.setOnClickListener(v -> switchTab("manual"));
        tabAi.setOnClickListener(v -> switchTab("ai"));

        btnStartScan.setOnClickListener(v -> startBarcodeScan());

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

        // 购买日期 - 弹窗日期选择 + 自动计算过期日期
        etPurchaseDate.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                selectedPurchaseDate = String.format("%04d-%02d-%02d", year, month + 1, day);
                etPurchaseDate.setText(selectedPurchaseDate);
                calcExpiryDate();
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

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
        tabScan.setBackgroundResource(R.drawable.bg_tab_normal);
        tabPhoto.setBackgroundResource(R.drawable.bg_tab_normal);
        tabManual.setBackgroundResource(R.drawable.bg_tab_normal);
        tabAi.setBackgroundResource(R.drawable.bg_tab_normal);
        tabScan.setTextColor(Color.parseColor("#718096"));
        tabPhoto.setTextColor(Color.parseColor("#718096"));
        tabManual.setTextColor(Color.parseColor("#718096"));
        tabAi.setTextColor(Color.parseColor("#718096"));
        tabScan.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabPhoto.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabManual.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabAi.setTypeface(null, android.graphics.Typeface.NORMAL);

        switch (mode) {
            case "scan":
                tabScan.setBackgroundResource(R.drawable.bg_tab_selected);
                tabScan.setTextColor(Color.parseColor("#FFFFFF"));
                tabScan.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                tvScanHint.setText("点击下方按钮开始扫码识别条形码");
                btnStartScan.setText("📷 开始扫码");
                btnStartScan.setOnClickListener(v -> startBarcodeScan());
                break;
            case "photo":
                tabPhoto.setBackgroundResource(R.drawable.bg_tab_selected);
                tabPhoto.setTextColor(Color.parseColor("#FFFFFF"));
                tabPhoto.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                tvScanHint.setText("拍照识别物品，自动填充信息");
                btnStartScan.setText("📸 拍照识别");
                btnStartScan.setOnClickListener(v -> startPhotoCapture());
                break;
            case "manual":
                tabManual.setBackgroundResource(R.drawable.bg_tab_selected);
                tabManual.setTextColor(Color.parseColor("#FFFFFF"));
                tabManual.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.GONE);
                break;
            case "ai":
                tabAi.setBackgroundResource(R.drawable.bg_tab_selected);
                tabAi.setTextColor(Color.parseColor("#FFFFFF"));
                tabAi.setTypeface(null, android.graphics.Typeface.BOLD);
                scanContainer.setVisibility(View.VISIBLE);
                tvScanHint.setText("AI 智能识别：拍照自动提取物品信息");
                btnStartScan.setText("AI 智能识别");
                btnStartScan.setOnClickListener(v -> startAiRecognize());
                break;
        }
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
        okhttp3.MultipartBody.Builder builder = new okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("image", "photo.jpg",
                okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/jpeg"), imageBytes));
        int houseId = App.getInstance().getCurrentHouseId();
        if (selectedSpaceId > 0) builder.addFormDataPart("space_id", String.valueOf(selectedSpaceId));
        if (houseId > 0) builder.addFormDataPart("house_id", String.valueOf(houseId));
        okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
            .url(App.BASE_URL + "ai/recognize.php?action=recognize")
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
                    runOnUiThread(() -> handleAiResult(body));
                }
            });
    }

    private void handleAiResult(String responseBody) {
        try {
            JsonObject json = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.get("code").getAsInt() != 0) {
                Toast.makeText(this, "识别失败", Toast.LENGTH_SHORT).show();
                return;
            }
            JsonObject data = json.getAsJsonObject("data");
            String name = data.has("goods_name") ? data.get("goods_name").getAsString() : "";
            String brand = data.has("brand") ? data.get("brand").getAsString() : "";
            String barcode = data.has("barcode") ? data.get("barcode").getAsString() : "";
            String expireDate = data.has("expire_date") ? data.get("expire_date").getAsString() : "";
            double confidence = data.has("confidence") ? data.get("confidence").getAsDouble() : 0;
            if (data.has("suggested_space_id") && !data.get("suggested_space_id").isJsonNull()) {
                int sid = data.get("suggested_space_id").getAsInt();
                if (sid > 0) {
                    selectedSpaceId = sid;
                    String sn = data.has("suggested_space_name") ? data.get("suggested_space_name").getAsString() : "";
                    tvSpaceName.setText(sn);
                }
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
            String msg = "名称: " + name + "\n品牌: " + brand + "\n置信度: " + String.format(Locale.getDefault(), "%.0f%%", confidence * 100);
            new AlertDialog.Builder(this).setTitle("AI 识别结果").setMessage(msg)
                .setPositiveButton("确认填入", null)
                .setNegativeButton("重新识别", (d, w) -> startAiRecognize())
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "识别结果解析失败", Toast.LENGTH_SHORT).show();
        }
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
                    // 回退：尝试获取缩略图
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
                    try { bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), cameraImageUri); } catch (Exception ignored) {}
                }
                if (bitmap == null && data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) bitmap = (Bitmap) extras.get("data");
                }
                if (bitmap != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                    callAiRecognize(baos.toByteArray());
                }
            } catch (Exception ignored) {}
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
        int photoIndex = photos.size() - 1;
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

        // 问题8: 右上角X删除按钮
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
        Toast.makeText(this, "📷 正在识别物品...", Toast.LENGTH_SHORT).show();
        // 自动调用图像识别
        recognizeImage(bitmap);
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
            if (json.get("code").getAsInt() != 0) {
                Toast.makeText(this, "识别失败: " + json.get("msg").getAsString(), Toast.LENGTH_SHORT).show();
                return;
            }

            JsonObject data = json.getAsJsonObject("data");
            boolean recognized = data.has("recognized") && data.get("recognized").getAsBoolean();

            if (recognized) {
                String name = data.has("suggested_name") ? data.get("suggested_name").getAsString() : "";
                String category = data.has("suggested_category") ? data.get("suggested_category").getAsString() : "";
                String brand = data.has("suggested_brand") ? data.get("suggested_brand").getAsString() : "";
                String barcode = data.has("barcode") ? data.get("barcode").getAsString() : "";

                // 显示识别结果确认对话框
                StringBuilder msg = new StringBuilder();
                if (!name.isEmpty()) msg.append("名称: ").append(name).append("\n");
                if (!category.isEmpty()) msg.append("分类: ").append(category).append("\n");
                if (!brand.isEmpty()) msg.append("品牌: ").append(brand).append("\n");
                if (!barcode.isEmpty()) msg.append("条码: ").append(barcode).append("\n");

                if (msg.length() == 0) {
                    Toast.makeText(this, "未能识别物品，请手动输入", Toast.LENGTH_SHORT).show();
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

                new AlertDialog.Builder(this)
                    .setTitle("✅ 识别结果")
                    .setMessage(msg.toString() + "\n是否填入表单？")
                    .setPositiveButton("确认填入", (d, w) -> {
                        if (!name.isEmpty()) etName.setText(name);
                        if (!barcode.isEmpty()) etBarcode.setText(barcode);
                        if (!brand.isEmpty()) etBrand.setText(brand);
                        Toast.makeText(this, "✅ 已填入识别结果", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("重新识别", (d, w) -> {
                        if (photos.size() > 0) {
                            recognizeImage(photos.get(photos.size() - 1));
                        }
                    })
                    .setNeutralButton("手动输入", null)
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
                            if (data.has("data") && !data.get("data").isJsonNull()) {
                                JsonObject info = data.getAsJsonObject("data");
                                if (info.has("name") && !info.get("name").isJsonNull()) {
                                    etName.setText(info.get("name").getAsString());
                                }
                            }
                            Toast.makeText(AddItemActivity.this, "✅ 已识别商品", Toast.LENGTH_SHORT).show();
                        } else {
                            String msg = data.has("msg") ? data.get("msg").getAsString() : "未找到该条码对应的商品";
                            // 友好化处理原始错误信息
                            if (msg.contains("HTTP 404") || msg.contains("未找到")) {
                                msg = "该条码在数据库中未找到，可手动输入物品信息";
                            } else if (msg.contains("HTTP 500") || msg.contains("服务")) {
                                msg = "条码查询服务暂时不可用，请稍后再试";
                            } else if (msg.contains("连接失败") || msg.contains("timeout")) {
                                msg = "网络连接超时，请检查网络后重试";
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

    /**
     * 将用户输入的保质期值和单位换算成天数
     */
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
                        JsonObject item = data.has("goods") ? data.getAsJsonObject("goods") : data;
                        if (item.has("name")) etName.setText(item.get("name").getAsString());
                        if (item.has("barcode")) etBarcode.setText(item.get("barcode").getAsString());
                        if (item.has("brand") && !item.get("brand").isJsonNull()) etBrand.setText(item.get("brand").getAsString());
                        if (item.has("quantity")) etQuantity.setText(String.valueOf(item.get("quantity").getAsInt()));
                        if (item.has("unit")) {
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
                            // 从过期日期反算保质期天数
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
                            etPrice.setText(String.valueOf(item.get("purchase_price").getAsDouble()));
                        }
                        if (item.has("note") && !item.get("note").isJsonNull()) etNote.setText(item.get("note").getAsString());
                        if (item.has("is_private")) swPrivate.setChecked(item.get("is_private").getAsInt() == 1);
                        if (item.has("space_id") && !item.get("space_id").isJsonNull()) {
                            selectedSpaceId = item.get("space_id").getAsInt();
                        }
                        if (item.has("space_name") && !item.get("space_name").isJsonNull()) {
                            tvSpaceName.setText(item.get("space_name").getAsString());
                        }

                        // 更新标题
                        setTitle("编辑物品");
                        btnSave.setText("保存修改");
                    } catch (Exception e) {
                        Toast.makeText(AddItemActivity.this, "加载物品数据失败", Toast.LENGTH_SHORT).show();
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

        // 保存上次选择的空间
        if (selectedSpaceId > 0) {
            getSharedPreferences("add_item_prefs", MODE_PRIVATE).edit()
                .putInt("last_space_id", selectedSpaceId)
                .putString("last_space_name", tvSpaceName.getText().toString())
                .putString("last_space_path", tvSpacePath.getText().toString())
                .apply();
        }

        // 先上传照片，再创建物品
        if (!photos.isEmpty() && NetworkUtils.isNetworkAvailable(this)) {
            btnSave.setEnabled(false);
            btnSave.setText("上传照片中...");
            btnSaveContinue.setEnabled(false);
            uploadPhotosThenCreate(continueAfterSave);
            return;
        }

        // 没有照片，直接创建
        Goods goods = new Goods();
        goods.houseId = houseId;
        goods.spaceId = selectedSpaceId;
        goods.name = name;
        goods.barcode = etBarcode.getText().toString().trim();
        goods.category = "";
        String qtyStr = etQuantity.getText().toString().trim();
        goods.quantity = qtyStr.isEmpty() ? 1 : Double.parseDouble(qtyStr);
        goods.unit = etUnit.getSelectedItem() != null ? etUnit.getSelectedItem().toString() : "个";
        // 过期日期：使用自动计算的日期
        String autoExpiry = tvExpiryDateAuto.getText().toString().trim();
        if (!autoExpiry.isEmpty() && !autoExpiry.contains("输入")) {
            // 提取日期部分（去掉可能的提示文字）
            goods.expiryDate = autoExpiry.length() >= 10 ? autoExpiry.substring(0, 10) : autoExpiry;
        } else {
            // 回退：从保质期值和单位计算
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
            body.addProperty("category", goods.category);
            body.addProperty("brand", etBrand.getText().toString().trim());
            body.addProperty("quantity", goods.quantity);
            body.addProperty("unit", goods.unit);
            body.addProperty("purchase_date", selectedPurchaseDate);
            body.addProperty("expiry_date", goods.expiryDate);
            body.addProperty("purchase_price", goods.purchasePrice);
            body.addProperty("note", goods.note);
            body.addProperty("is_private", goods.isPrivate);

            // 添加标签
            if (!selectedTagIds.isEmpty()) {
                com.google.gson.JsonArray tagsArray = new com.google.gson.JsonArray();
                for (int tagId : selectedTagIds) {
                    tagsArray.add(tagId);
                }
                body.add("tags", tagsArray);
            }

            // 问题5: 编辑模式调用update
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
        } else {
            localDb.saveOfflineGoods(goods);
            Toast.makeText(this, "已保存到本地，联网后自动同步", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void resetForm() {
        // 重置表单字段，保留空间选择和模式
        etName.setText("");
        etBarcode.setText("");
        etQuantity.setText("1");
        etUnit.setSelection(0);
        etExpiryDays.setText("");
        etPrice.setText("");
        etThreshold.setText("");
        etPurchaseDate.setText("");
        selectedPurchaseDate = "";
        etNote.setText("");
        tvExpiryDateAuto.setText("");
        tvExpiryDateAuto.setHint("输入生产日期和保质期后自动计算");
        tvExpiryDateAuto.setTextColor(Color.parseColor("#2D3748"));
        swPrivate.setChecked(false);

        // 清空照片和标签
        photos.clear();
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
     * 先上传所有照片，获取路径后创建物品
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

        JsonObject body = new JsonObject();
        body.addProperty("house_id", goods.houseId);
        body.addProperty("space_id", goods.spaceId);
        body.addProperty("name", goods.name);
        body.addProperty("barcode", goods.barcode);
        body.addProperty("category", goods.category);
        body.addProperty("quantity", goods.quantity);
        body.addProperty("unit", goods.unit);
        body.addProperty("purchase_date", selectedPurchaseDate);
        body.addProperty("expiry_date", goods.expiryDate);
        body.addProperty("purchase_price", goods.purchasePrice);
        body.addProperty("note", goods.note);
        body.addProperty("is_private", goods.isPrivate);

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

        ApiClient.post("goods.php?action=create", body, new ApiClient.ApiCallback() {
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

            // 所有照片上传完毕，创建物品（附带图片路径）
            final List<String> finalPaths = imagePaths;
            runOnUiThread(() -> createGoodsWithImages(finalPaths, continueAfterSave));
        }).start();
    }

    private void uploadPhotos(int goodsId) {
        // 异步上传照片，不阻塞UI
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
                    // 同步调用更新接口，添加图片
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

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

package com.jiashouna.app.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.io.*;
import java.util.*;

public class AddDocumentActivity extends AppCompatActivity {
    private EditText etName, etDocNo, etIssuer, etLocation, etNote;
    private Spinner spCategory, spPrivate;
    private TextView tvIssueDate, tvExpiryDate;
    private LinearLayout llPhotos;
    private Button btnSave;
    private List<Bitmap> photos = new ArrayList<>();
    private String selectedIssueDate = "", selectedExpiryDate = "";
    private int spaceId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_document);

        etName = findViewById(R.id.et_name);
        etDocNo = findViewById(R.id.et_doc_no);
        etIssuer = findViewById(R.id.et_issuer);
        etLocation = findViewById(R.id.et_location);
        etNote = findViewById(R.id.et_note);
        spCategory = findViewById(R.id.sp_category);
        spPrivate = findViewById(R.id.sp_private);
        tvIssueDate = findViewById(R.id.tv_issue_date);
        tvExpiryDate = findViewById(R.id.tv_expiry_date);
        llPhotos = findViewById(R.id.ll_photos);
        btnSave = findViewById(R.id.btn_save);

        // 分类
        String[] cats = {"证件", "合同", "票据", "保单", "房产", "车辆", "教育", "医疗", "其他"};
        spCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));

        // 隐私
        String[] privates = {"私密（仅自己可见）", "共享（家庭成员可见）"};
        spPrivate.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, privates));
        spPrivate.setSelection(0);

        // 日期选择
        tvIssueDate.setOnClickListener(v -> pickDate(tvIssueDate, d -> selectedIssueDate = d));
        tvExpiryDate.setOnClickListener(v -> pickDate(tvExpiryDate, d -> selectedExpiryDate = d));

        // 拍照
        findViewById(R.id.btn_take_photo).setOnClickListener(v -> takePhoto());
        findViewById(R.id.btn_from_album).setOnClickListener(v -> pickFromAlbum());

        // 返回
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 保存
        btnSave.setOnClickListener(v -> saveDocument());
    }

    private void pickDate(TextView target, java.util.function.Consumer<String> callback) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            String date = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
            target.setText(date);
            callback.accept(date);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private Uri cameraUri;

    private void takePhoto() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 100);
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            java.io.File dir = getExternalCacheDir();
            if (dir != null && !dir.exists()) dir.mkdirs();
            java.io.File file = java.io.File.createTempFile("doc_", ".jpg", dir);
            cameraUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {}
        startActivityForResult(intent, 101);
    }

    private void pickFromAlbum() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 102);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        Bitmap bitmap = null;
        if (requestCode == 101 && cameraUri != null) {
            try { bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), cameraUri); } catch (Exception ignored) {}
        } else if (requestCode == 102 && data != null) {
            try {
                Uri uri = data.getData();
                if (uri != null) bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            } catch (Exception ignored) {}
        }
        if (bitmap != null) {
            // 压缩
            int max = 1600;
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            if (w > max || h > max) {
                float scale = Math.min((float) max / w, (float) max / h);
                bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(w * scale), Math.round(h * scale), true);
            }
            photos.add(bitmap);
            addPhotoPreview(bitmap);
        }
    }

    private void addPhotoPreview(Bitmap bitmap) {
        FrameLayout container = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(80), dp(80));
        lp.rightMargin = dp(8);
        container.setLayoutParams(lp);

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bitmap);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        container.addView(iv);

        TextView btnX = new TextView(this);
        btnX.setText("✕");
        btnX.setTextSize(10);
        btnX.setTextColor(Color.WHITE);
        btnX.setGravity(Gravity.CENTER);
        GradientDrawable xBg = new GradientDrawable();
        xBg.setShape(GradientDrawable.OVAL);
        xBg.setColor(0xCC000000);
        btnX.setBackground(xBg);
        FrameLayout.LayoutParams xLp = new FrameLayout.LayoutParams(dp(20), dp(20));
        xLp.gravity = Gravity.TOP | Gravity.END;
        xLp.topMargin = dp(2);
        xLp.rightMargin = dp(2);
        btnX.setLayoutParams(xLp);
        btnX.setOnClickListener(v -> {
            int idx = llPhotos.indexOfChild(container);
            if (idx >= 0 && idx < photos.size()) photos.remove(idx);
            llPhotos.removeView(container);
        });
        container.addView(btnX);
        llPhotos.addView(container);
    }

    private void saveDocument() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入文件名称", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("保存中...");

        if (photos.isEmpty()) {
            doSave(new JsonArray());
        } else {
            uploadPhotosThenSave();
        }
    }

    private void uploadPhotosThenSave() {
        JsonArray imagePaths = new JsonArray();
        final int[] remaining = {photos.size()};

        for (int i = 0; i < photos.size(); i++) {
            Bitmap bmp = photos.get(i);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);

            okhttp3.MultipartBody.Builder builder = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", "doc_" + i + ".jpg",
                    okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/jpeg"), baos.toByteArray()));

            okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder()
                .url(App.BASE_URL + "upload.php?action=image")
                .post(builder.build());
            String token = App.getInstance().getToken();
            if (token != null) reqBuilder.addHeader("Authorization", "Bearer " + token);

            final int idx = i;
            new okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(reqBuilder.build())
                .enqueue(new okhttp3.Callback() {
                    @Override public void onFailure(okhttp3.Call call, IOException e) {
                        runOnUiThread(() -> {
                            remaining[0]--;
                            if (remaining[0] <= 0) doSave(imagePaths);
                        });
                    }
                    @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                        String body = response.body() != null ? response.body().string() : "";
                        try {
                            JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                            if (json.has("data")) {
                                String path = json.getAsJsonObject("data").get("image_path").getAsString();
                                synchronized (imagePaths) { imagePaths.add(path); }
                            }
                        } catch (Exception ignored) {}
                        runOnUiThread(() -> {
                            remaining[0]--;
                            if (remaining[0] <= 0) doSave(imagePaths);
                        });
                    }
                });
        }
    }

    private void doSave(JsonArray imagePaths) {
        JsonObject body = new JsonObject();
        body.addProperty("house_id", App.getInstance().getCurrentHouseId());
        body.addProperty("name", etName.getText().toString().trim());
        body.addProperty("category", spCategory.getSelectedItem().toString());
        body.addProperty("doc_no", etDocNo.getText().toString().trim());
        body.addProperty("issuer", etIssuer.getText().toString().trim());
        body.addProperty("issue_date", selectedIssueDate);
        body.addProperty("expiry_date", selectedExpiryDate);
        body.addProperty("storage_location", etLocation.getText().toString().trim());
        body.addProperty("note", etNote.getText().toString().trim());
        body.addProperty("is_private", spPrivate.getSelectedItemPosition() == 0 ? 1 : 0);
        if (imagePaths.size() > 0) body.add("images", imagePaths);

        ApiClient.post("document.php?action=create", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    Toast.makeText(AddDocumentActivity.this, "✅ 保存成功", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("保存");
                    Toast.makeText(AddDocumentActivity.this, "保存失败: " + msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

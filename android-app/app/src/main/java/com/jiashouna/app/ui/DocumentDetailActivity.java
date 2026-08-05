package com.jiashouna.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import com.bumptech.glide.Glide;
import java.text.SimpleDateFormat;
import java.util.*;

public class DocumentDetailActivity extends AppCompatActivity {
    private int documentId;
    private TextView tvName, tvCategory, tvDocNo, tvIssuer, tvIssueDate, tvExpiryDate;
    private TextView tvLocation, tvNote, tvCreator, tvCreatedTime, tvExpiryBanner;
    private LinearLayout llPhotos, llExpiryBanner;
    private View btnEdit, btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_detail);

        documentId = getIntent().getIntExtra("document_id", 0);
        if (documentId <= 0) { finish(); return; }

        tvName = findViewById(R.id.tv_name);
        tvCategory = findViewById(R.id.tv_category);
        tvDocNo = findViewById(R.id.tv_doc_no);
        tvIssuer = findViewById(R.id.tv_issuer);
        tvIssueDate = findViewById(R.id.tv_issue_date);
        tvExpiryDate = findViewById(R.id.tv_expiry_date);
        tvLocation = findViewById(R.id.tv_location);
        tvNote = findViewById(R.id.tv_note);
        tvCreator = findViewById(R.id.tv_creator);
        tvCreatedTime = findViewById(R.id.tv_created_time);
        tvExpiryBanner = findViewById(R.id.tv_expiry_banner);
        llPhotos = findViewById(R.id.ll_photos);
        llExpiryBanner = findViewById(R.id.layout_expiry_banner);
        btnEdit = findViewById(R.id.btn_edit);
        btnDelete = findViewById(R.id.btn_delete);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnEdit.setOnClickListener(v -> editDocument());
        btnDelete.setOnClickListener(v -> deleteDocument());

        loadDetail();
    }

    private void loadDetail() {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(documentId));

        ApiClient.get("document.php?action=detail", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonObject doc = data.getAsJsonObject("document");
                        displayDocument(doc);
                    } catch (Exception e) {
                        Toast.makeText(DocumentDetailActivity.this, "数据解析失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    Toast.makeText(DocumentDetailActivity.this, "加载失败: " + msg, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void displayDocument(JsonObject doc) {
        tvName.setText(getString(doc, "name"));
        tvCategory.setText(getString(doc, "category"));

        String docNo = getString(doc, "doc_no");
        if (!docNo.isEmpty()) {
            tvDocNo.setText(docNo);
            tvDocNo.setVisibility(View.VISIBLE);
            findViewById(R.id.row_doc_no).setVisibility(View.VISIBLE);
        }

        String issuer = getString(doc, "issuer");
        if (!issuer.isEmpty()) {
            tvIssuer.setText(issuer);
            findViewById(R.id.row_issuer).setVisibility(View.VISIBLE);
        }

        String issueDate = getString(doc, "issue_date");
        if (!issueDate.isEmpty()) {
            tvIssueDate.setText(issueDate);
            findViewById(R.id.row_issue_date).setVisibility(View.VISIBLE);
        }

        String expiryDate = getString(doc, "expiry_date");
        if (!expiryDate.isEmpty()) {
            tvExpiryDate.setText(expiryDate);
            findViewById(R.id.row_expiry_date).setVisibility(View.VISIBLE);

            // 到期状态
            int daysLeft = doc.has("days_left") ? doc.get("days_left").getAsInt() : 0;
            llExpiryBanner.setVisibility(View.VISIBLE);
            if (daysLeft < 0) {
                tvExpiryBanner.setText("🔴 已过期 " + Math.abs(daysLeft) + " 天");
                tvExpiryBanner.setTextColor(0xFFF56565);
            } else if (daysLeft <= 30) {
                tvExpiryBanner.setText("🟠 还剩 " + daysLeft + " 天到期");
                tvExpiryBanner.setTextColor(0xFFED8936);
            } else if (daysLeft <= 90) {
                tvExpiryBanner.setText("🟡 还剩 " + daysLeft + " 天到期");
                tvExpiryBanner.setTextColor(0xFFECC94B);
            } else {
                tvExpiryBanner.setText("🟢 " + expiryDate + " 到期");
                tvExpiryBanner.setTextColor(0xFF48BB78);
            }
        }

        String location = getString(doc, "storage_location");
        if (!location.isEmpty()) {
            tvLocation.setText("📍 " + location);
            tvLocation.setVisibility(View.VISIBLE);
        }

        String note = getString(doc, "note");
        if (!note.isEmpty()) {
            tvNote.setText(note);
            findViewById(R.id.row_note).setVisibility(View.VISIBLE);
        }

        String creator = getString(doc, "creator_name");
        tvCreator.setText("创建者: " + (creator.isEmpty() ? "未知" : creator));

        if (doc.has("created_at") && !doc.get("created_at").isJsonNull()) {
            try {
                long ts = doc.get("created_at").getAsLong();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                tvCreatedTime.setText(sdf.format(new Date(ts * 1000)));
            } catch (Exception ignored) {}
        }

        // 图片
        if (doc.has("images") && !doc.get("images").isJsonNull()) {
            JsonArray images = doc.getAsJsonArray("images");
            llPhotos.removeAllViews();
            for (int i = 0; i < images.size(); i++) {
                JsonObject img = images.get(i).getAsJsonObject();
                String path = getString(img, "image_path");
                if (!path.isEmpty()) {
                    ImageView iv = new ImageView(this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(160));
                    lp.setMarginEnd(dp(8));
                    lp.bottomMargin = dp(8);
                    iv.setLayoutParams(lp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setBackgroundResource(R.drawable.bg_card_16);
                    Glide.with(this).load(path).centerCrop().into(iv);
                    llPhotos.addView(iv);
                }
            }
            llPhotos.setVisibility(View.VISIBLE);
        }
    }

    private String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try { return obj.get(key).getAsString(); } catch (Exception e) {}
        }
        return "";
    }

    private void editDocument() {
        // TODO: 编辑功能
        Toast.makeText(this, "编辑功能开发中", Toast.LENGTH_SHORT).show();
    }

    private void deleteDocument() {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除此文件档案吗？")
            .setPositiveButton("删除", (d, w) -> {
                JsonObject body = new JsonObject();
                body.addProperty("id", documentId);
                ApiClient.post("document.php?action=delete", body, new ApiClient.ApiCallback() {
                    @Override public void onSuccess(JsonObject data) {
                        runOnUiThread(() -> {
                            Toast.makeText(DocumentDetailActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                    @Override public void onError(String msg) {
                        runOnUiThread(() -> Toast.makeText(DocumentDetailActivity.this, "删除失败: " + msg, Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

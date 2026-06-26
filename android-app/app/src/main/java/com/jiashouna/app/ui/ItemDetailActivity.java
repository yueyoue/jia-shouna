package com.jiashouna.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.text.SimpleDateFormat;
import java.util.*;

public class ItemDetailActivity extends AppCompatActivity {
    private int goodsId;
    private TextView tvItemName, tvItemBarcode, tvExpiryBadge, tvPrivacyBadge;
    private TextView tvLocation, tvLocationIcon, tvQuantity, tvUnit;
    private TextView tvPurchaseDate, tvExpiryDate, tvPrice, tvBarcodeValue;
    private TextView tvPrivacyValue, tvNote, tvCreator, tvCreatedTime;
    private TextView tvPhotoCounter, tvBorrowCount;
    private TextView tvDefaultIcon;
    private TextView btnBack, btnShare, btnMore;
    private TextView btnQtyMinus, btnQtyPlus;
    private View btnMove, btnCopy, btnBorrow, btnEdit, btnEditBottom, btnDelete;
    private LinearLayout layoutStatusBadges, layoutTags, layoutBorrowSection, layoutBorrowList;
    private LinearLayout galleryDots;
    private View rowPurchaseDate, rowExpiry, rowPrice, rowBarcode, rowBrand, rowTags, rowNote;
    private View dividerPurchase, dividerExpiry, dividerPrice, dividerBarcode, dividerBrand, dividerTags;
    private TextView tvBrandValue;
    private ViewPager2 viewpagerPhotos;
    private JsonObject currentGoods;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        goodsId = getIntent().getIntExtra("goods_id", 0);
        if (goodsId <= 0) {
            Toast.makeText(this, "物品ID无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadDetail();
    }

    private void initViews() {
        tvItemName = findViewById(R.id.tv_item_name);
        tvItemBarcode = findViewById(R.id.tv_item_barcode);
        tvExpiryBadge = findViewById(R.id.tv_expiry_badge);
        tvPrivacyBadge = findViewById(R.id.tv_privacy_badge);
        layoutStatusBadges = findViewById(R.id.layout_status_badges);
        tvLocationIcon = findViewById(R.id.tv_location_icon);
        tvLocation = findViewById(R.id.tv_location);
        tvQuantity = findViewById(R.id.tv_quantity);
        tvUnit = findViewById(R.id.tv_unit);
        tvPurchaseDate = findViewById(R.id.tv_purchase_date);
        tvExpiryDate = findViewById(R.id.tv_expiry_date);
        tvPrice = findViewById(R.id.tv_price);
        tvBarcodeValue = findViewById(R.id.tv_barcode_value);
        tvPrivacyValue = findViewById(R.id.tv_privacy_value);
        tvNote = findViewById(R.id.tv_note);
        tvCreator = findViewById(R.id.tv_creator);
        tvCreatedTime = findViewById(R.id.tv_created_time);
        tvPhotoCounter = findViewById(R.id.tv_photo_counter);
        tvBorrowCount = findViewById(R.id.tv_borrow_count);
        tvDefaultIcon = findViewById(R.id.tv_default_icon);
        btnBack = findViewById(R.id.btn_back);
        btnShare = findViewById(R.id.btn_share);
        btnMore = findViewById(R.id.btn_more);
        btnQtyMinus = findViewById(R.id.btn_qty_minus);
        btnQtyPlus = findViewById(R.id.btn_qty_plus);
        btnMove = findViewById(R.id.btn_move);
        btnCopy = findViewById(R.id.btn_copy);
        btnBorrow = findViewById(R.id.btn_borrow);
        btnEdit = findViewById(R.id.btn_edit);
        btnEditBottom = findViewById(R.id.btn_edit_bottom);
        btnDelete = findViewById(R.id.btn_delete);
        layoutTags = findViewById(R.id.layout_tags);
        layoutBorrowSection = findViewById(R.id.layout_borrow_section);
        layoutBorrowList = findViewById(R.id.layout_borrow_list);
        galleryDots = findViewById(R.id.gallery_dots);
        viewpagerPhotos = findViewById(R.id.viewpager_photos);

        rowPurchaseDate = findViewById(R.id.row_purchase_date);
        rowExpiry = findViewById(R.id.row_expiry);
        rowPrice = findViewById(R.id.row_price);
        rowBarcode = findViewById(R.id.row_barcode);
        rowBrand = findViewById(R.id.row_brand);
        tvBrandValue = findViewById(R.id.tv_brand_value);
        rowTags = findViewById(R.id.row_tags);
        rowNote = findViewById(R.id.row_note);
        dividerPurchase = findViewById(R.id.divider_purchase);
        dividerExpiry = findViewById(R.id.divider_expiry);
        dividerPrice = findViewById(R.id.divider_price);
        dividerBarcode = findViewById(R.id.divider_barcode);
        dividerBrand = findViewById(R.id.divider_brand);
        dividerTags = findViewById(R.id.divider_tags);

        btnBack.setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> deleteItem());
        btnEdit.setOnClickListener(v -> editItem());
        btnEditBottom.setOnClickListener(v -> editItem());
        btnMove.setOnClickListener(v -> moveItem());
        btnCopy.setOnClickListener(v -> copyItem());
        btnBorrow.setOnClickListener(v -> borrowItem());
        btnShare.setOnClickListener(v -> shareItem());

        // Quantity controls
        btnQtyMinus.setOnClickListener(v -> changeQuantity(-1));
        btnQtyPlus.setOnClickListener(v -> changeQuantity(1));
    }

    private void loadDetail() {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(goodsId));
        ApiClient.get("goods.php?action=detail", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonObject goods = data.getAsJsonObject("goods");
                        currentGoods = goods;
                        displayGoods(goods);
                    } catch (Exception e) {
                        Toast.makeText(ItemDetailActivity.this, "数据解析失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    Toast.makeText(ItemDetailActivity.this, "加载失败: " + msg, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void displayGoods(JsonObject g) {
        // Name
        String name = g.has("name") && !g.get("name").isJsonNull() ? g.get("name").getAsString() : "";
        tvItemName.setText(name);

        // Category icon
        String category = g.has("category") && !g.get("category").isJsonNull() ? g.get("category").getAsString() : "";
        String icon = getCategoryIcon(category);
        tvDefaultIcon.setText(icon);

        // Barcode
        String barcode = g.has("barcode") && !g.get("barcode").isJsonNull() ? g.get("barcode").getAsString() : "";
        if (!barcode.isEmpty()) {
            tvItemBarcode.setText("条码: " + barcode);
            tvItemBarcode.setVisibility(View.VISIBLE);
            tvBarcodeValue.setText(barcode);
            rowBarcode.setVisibility(View.VISIBLE);
            dividerBarcode.setVisibility(View.VISIBLE);
        }

        // Brand
        String brand = g.has("brand") && !g.get("brand").isJsonNull() ? g.get("brand").getAsString() : "";
        if (!brand.isEmpty()) {
            tvBrandValue.setText(brand);
            rowBrand.setVisibility(View.VISIBLE);
            dividerBrand.setVisibility(View.VISIBLE);
        }

        // Expiry status badge
        if (g.has("expiry_date") && !g.get("expiry_date").isJsonNull()) {
            String expiryDate = g.get("expiry_date").getAsString();
            if (!expiryDate.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Date expiry = sdf.parse(expiryDate);
                    if (expiry != null) {
                        long diff = expiry.getTime() - System.currentTimeMillis();
                        int days = (int) (diff / (1000 * 60 * 60 * 24));

                        String statusText;
                        int bgColor;
                        int textColor;
                        if (days < 0) {
                            statusText = "⚠ 已过期" + Math.abs(days) + "天";
                            bgColor = 0x1FF56565;
                            textColor = 0xFF9B2C2C;
                        } else if (days == 0) {
                            statusText = "⚠ 今天过期";
                            bgColor = 0x1FF56565;
                            textColor = 0xFF9B2C2C;
                        } else if (days <= 7) {
                            statusText = "⚠ 还剩" + days + "天过期";
                            bgColor = 0x1FED8936;
                            textColor = 0xFFC25A1E;
                        } else {
                            statusText = "✓ " + expiryDate + " 到期";
                            bgColor = 0x1F48BB78;
                            textColor = 0xFF22543D;
                        }
                        tvExpiryBadge.setText(statusText);
                        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                        bg.setColor(bgColor);
                        bg.setCornerRadius(dp(8));
                        tvExpiryBadge.setBackground(bg);
                        tvExpiryBadge.setTextColor(textColor);
                        tvExpiryBadge.setVisibility(View.VISIBLE);

                        // Expiry info row
                        tvExpiryDate.setText(expiryDate + (days <= 7 ? "（" + (days < 0 ? "已过期" + Math.abs(days) + "天" : days == 0 ? "今天过期" : "还剩" + days + "天") + "）" : ""));
                        tvExpiryDate.setTextColor(days <= 7 ? 0xFFF56565 : 0xFF2D3748);
                        rowExpiry.setVisibility(View.VISIBLE);
                        dividerExpiry.setVisibility(View.VISIBLE);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Privacy badge
        int isPrivate = g.has("is_private") ? g.get("is_private").getAsInt() : 0;
        if (isPrivate == 1) {
            tvPrivacyBadge.setVisibility(View.VISIBLE);
            tvPrivacyValue.setText("仅录入者可见");
            tvPrivacyValue.setTextColor(0xFF805AD5);
        } else {
            tvPrivacyValue.setText("全家可见");
            tvPrivacyValue.setTextColor(0xFF4A5568);
        }

        // Location
        String spaceIcon = g.has("space_icon") && !g.get("space_icon").isJsonNull() ? g.get("space_icon").getAsString() : "🏠";
        tvLocationIcon.setText(spaceIcon);
        if (g.has("space_path") && !g.get("space_path").isJsonNull()) {
            JsonArray path = g.getAsJsonArray("space_path");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) sb.append(" · ");
                sb.append(path.get(i).getAsJsonObject().get("name").getAsString());
            }
            tvLocation.setText(sb.toString());
        } else {
            tvLocation.setText(g.has("space_name") && !g.get("space_name").isJsonNull() ? g.get("space_name").getAsString() : "未分类");
        }

        // Quantity
        double qty = g.has("quantity") ? g.get("quantity").getAsDouble() : 1;
        String unit = g.has("unit") && !g.get("unit").isJsonNull() ? g.get("unit").getAsString() : "个";
        tvQuantity.setText(String.valueOf((int) qty));
        tvUnit.setText(unit);

        // Purchase Date
        String purchaseDate = g.has("purchase_date") && !g.get("purchase_date").isJsonNull() ? g.get("purchase_date").getAsString() : "";
        if (!purchaseDate.isEmpty()) {
            tvPurchaseDate.setText(purchaseDate);
            rowPurchaseDate.setVisibility(View.VISIBLE);
            dividerPurchase.setVisibility(View.VISIBLE);
        }

        // Price
        if (g.has("purchase_price") && !g.get("purchase_price").isJsonNull()) {
            double price = g.get("purchase_price").getAsDouble();
            if (price > 0) {
                tvPrice.setText("¥ " + String.format("%.2f", price));
                rowPrice.setVisibility(View.VISIBLE);
                dividerPrice.setVisibility(View.VISIBLE);
            }
        }

        // Tags
        if (g.has("tags") && !g.get("tags").isJsonNull()) {
            JsonArray tags = g.getAsJsonArray("tags");
            if (tags.size() > 0) {
                layoutTags.removeAllViews();
                for (int i = 0; i < tags.size(); i++) {
                    JsonObject tag = tags.get(i).getAsJsonObject();
                    String tagName = tag.has("name") && !tag.get("name").isJsonNull() ? tag.get("name").getAsString() : "";
                    if (!tagName.isEmpty()) {
                        TextView tv = new TextView(this);
                        tv.setText(tagName);
                        tv.setTextSize(11);
                        tv.setTextColor(0xFFC25A1E);
                        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                        bg.setColor(0x1FFF8C42);
                        bg.setCornerRadius(dp(6));
                        tv.setBackground(bg);
                        tv.setPadding(dp(8), dp(2), dp(8), dp(2));
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.setMarginEnd(dp(4));
                        tv.setLayoutParams(lp);
                        layoutTags.addView(tv);
                    }
                }
                rowTags.setVisibility(View.VISIBLE);
                dividerTags.setVisibility(View.VISIBLE);
            }
        }

        // Note
        String note = g.has("note") && !g.get("note").isJsonNull() ? g.get("note").getAsString() : "";
        if (!note.isEmpty()) {
            tvNote.setText(note);
            rowNote.setVisibility(View.VISIBLE);
        }

        // Creator
        String creator = g.has("creator_name") && !g.get("creator_name").isJsonNull() ? g.get("creator_name").getAsString() : "";
        tvCreator.setText("录入者: " + (creator.isEmpty() ? "未知" : creator));

        // Created time + days since storage
        if (g.has("created_at") && !g.get("created_at").isJsonNull()) {
            try {
                long ts = g.get("created_at").getAsLong();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String dateStr = sdf.format(new Date(ts * 1000));
                // Calculate days since storage
                long daysSince = (System.currentTimeMillis() - ts * 1000) / (24 * 60 * 60 * 1000);
                if (daysSince < 0) daysSince = 0;
                String daysText;
                if (daysSince == 0) {
                    daysText = "今天入库";
                } else if (daysSince == 1) {
                    daysText = "昨天入库";
                } else {
                    daysText = "已入库 " + daysSince + " 天";
                }
                tvCreatedTime.setText(dateStr + "（" + daysText + "）");
            } catch (Exception ignored) {}
        }

        // Photos - show actual images, or cover_image as fallback, or default icon
        List<String> imageUrls = new ArrayList<>();
        if (g.has("images") && !g.get("images").isJsonNull()) {
            JsonArray images = g.getAsJsonArray("images");
            for (int i = 0; i < images.size(); i++) {
                JsonObject img = images.get(i).getAsJsonObject();
                String url = img.has("image_path") && !img.get("image_path").isJsonNull() ? img.get("image_path").getAsString() : "";
                if (!url.isEmpty()) imageUrls.add(url);
            }
        }
        // Fallback: use cover_image from list API if no images array
        if (imageUrls.isEmpty() && g.has("cover_image") && !g.get("cover_image").isJsonNull()) {
            String coverUrl = g.get("cover_image").getAsString();
            if (!coverUrl.isEmpty()) imageUrls.add(coverUrl);
        }
        if (!imageUrls.isEmpty()) {
            setupPhotoGallery(imageUrls);
        } else {
            // No images: show default category-based placeholder
            tvDefaultIcon.setText(icon);
            tvDefaultIcon.setVisibility(View.VISIBLE);
            viewpagerPhotos.setVisibility(View.GONE);
            tvPhotoCounter.setVisibility(View.GONE);
        }

        // Borrow records
        if (g.has("borrow_records") && !g.get("borrow_records").isJsonNull()) {
            JsonArray records = g.getAsJsonArray("borrow_records");
            if (records.size() > 0) {
                layoutBorrowSection.setVisibility(View.VISIBLE);
                tvBorrowCount.setText("查看全部 (" + records.size() + ")");
                layoutBorrowList.removeAllViews();
                for (int i = 0; i < Math.min(records.size(), 3); i++) {
                    addBorrowItem(records.get(i).getAsJsonObject());
                }
            }
        }
    }

    private String getCategoryIcon(String category) {
        if (category == null) return "📦";
        switch (category) {
            case "食品": return "🍪";
            case "药品": return "💊";
            case "衣物": return "👕";
            case "日用品": return "🧴";
            case "数码": return "📱";
            case "证件": return "📄";
            case "厨具": return "🍳";
            default: return "📦";
        }
    }

    private void setupPhotoGallery(List<String> urls) {
        tvDefaultIcon.setVisibility(View.GONE);
        viewpagerPhotos.setVisibility(View.VISIBLE);
        tvPhotoCounter.setVisibility(View.VISIBLE);
        tvPhotoCounter.setText("1/" + urls.size());

        // 点击查看大图
        viewpagerPhotos.setOnClickListener(v -> {
            Intent intent = new Intent(ItemDetailActivity.this, ImageViewerActivity.class);
            intent.putStringArrayListExtra("image_urls", new ArrayList<>(urls));
            intent.putExtra("position", viewpagerPhotos.getCurrentItem());
            startActivity(intent);
        });

        // Setup gallery dots
        galleryDots.removeAllViews();
        for (int i = 0; i < urls.size(); i++) {
            View dot = new View(this);
            int size = dp(6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(4));
            dot.setLayoutParams(lp);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(i == 0 ? 0xFFFFFFFF : 0x80FFFFFF);
            dot.setBackground(bg);
            galleryDots.addView(dot);
        }

        PhotoAdapter adapter = new PhotoAdapter(urls);
        adapter.setOnItemClickListener(v -> {
            Intent intent = new Intent(ItemDetailActivity.this, ImageViewerActivity.class);
            intent.putStringArrayListExtra("image_urls", new ArrayList<>(urls));
            intent.putExtra("position", viewpagerPhotos.getCurrentItem());
            startActivity(intent);
        });
        viewpagerPhotos.setAdapter(adapter);
        viewpagerPhotos.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tvPhotoCounter.setText((position + 1) + "/" + urls.size());
                updateDots(position, urls.size());
            }
        });
    }

    private void updateDots(int selected, int total) {
        for (int i = 0; i < galleryDots.getChildCount(); i++) {
            View dot = galleryDots.getChildAt(i);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            if (i == selected) {
                bg.setColor(0xFFFFFFFF);
                dot.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(6)));
            } else {
                bg.setColor(0x80FFFFFF);
                dot.setLayoutParams(new LinearLayout.LayoutParams(dp(6), dp(6)));
            }
            dot.setBackground(bg);
        }
    }

    private void addBorrowItem(JsonObject record) {
        String userName = record.has("user_name") && !record.get("user_name").isJsonNull() ? record.get("user_name").getAsString() : "用户";
        double qty = record.has("quantity") ? record.get("quantity").getAsDouble() : 1;
        int status = record.has("status") ? record.get("status").getAsInt() : 1;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));

        // Avatar
        TextView avatar = new TextView(this);
        avatar.setText(userName.substring(0, 1));
        avatar.setTextSize(13);
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable avatarBg = new android.graphics.drawable.GradientDrawable();
        avatarBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        avatarBg.setColor(0xFFFF8C42);
        avatar.setBackground(avatarBg);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        avatar.setLayoutParams(avatarLp);

        // Info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.setMarginStart(dp(10));
        info.setLayoutParams(infoLp);

        TextView nameTv = new TextView(this);
        nameTv.setText(userName + " 领取 " + (int) qty + " 件");
        nameTv.setTextSize(13);
        nameTv.setTextColor(0xFF2D3748);
        info.addView(nameTv);

        TextView metaTv = new TextView(this);
        long borrowTime = record.has("borrow_time") ? record.get("borrow_time").getAsLong() : 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateStr = borrowTime > 0 ? sdf.format(new Date(borrowTime * 1000)) : "";
        metaTv.setText(dateStr);
        metaTv.setTextSize(11);
        metaTv.setTextColor(0xFF718096);
        info.addView(metaTv);

        // Status
        TextView statusTv = new TextView(this);
        statusTv.setTextSize(11);
        if (status == 2) {
            statusTv.setText("已归还");
            statusTv.setTextColor(0xFF22543D);
            android.graphics.drawable.GradientDrawable sBg = new android.graphics.drawable.GradientDrawable();
            sBg.setColor(0x1F48BB78);
            sBg.setCornerRadius(dp(4));
            statusTv.setBackground(sBg);
        } else {
            statusTv.setText("借出中");
            statusTv.setTextColor(0xFFC25A1E);
            android.graphics.drawable.GradientDrawable sBg = new android.graphics.drawable.GradientDrawable();
            sBg.setColor(0x1FFF8C42);
            sBg.setCornerRadius(dp(4));
            statusTv.setBackground(sBg);
        }
        statusTv.setPadding(dp(6), dp(2), dp(6), dp(2));

        row.addView(avatar);
        row.addView(info);
        row.addView(statusTv);
        layoutBorrowList.addView(row);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(0xFFEDF2F7);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divLp.setMarginStart(dp(54));
        divider.setLayoutParams(divLp);
        layoutBorrowList.addView(divider);
    }

    private void changeQuantity(int delta) {
        if (currentGoods == null) return;
        double currentQty = currentGoods.has("quantity") ? currentGoods.get("quantity").getAsDouble() : 1;
        double newQty = Math.max(0, currentQty + delta);
        String unit = currentGoods.has("unit") && !currentGoods.get("unit").isJsonNull() ? currentGoods.get("unit").getAsString() : "个";

        JsonObject body = new JsonObject();
        body.addProperty("id", goodsId);
        body.addProperty("quantity", newQty);
        ApiClient.post("goods.php?action=update", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    tvQuantity.setText(String.valueOf((int) newQty));
                    currentGoods.addProperty("quantity", newQty);
                    Toast.makeText(ItemDetailActivity.this, "数量已更新", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(ItemDetailActivity.this, "更新失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteItem() {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除此物品吗？")
            .setPositiveButton("删除", (d, w) -> {
                JsonObject body = new JsonObject();
                body.addProperty("id", goodsId);
                ApiClient.post("goods.php?action=delete", body, new ApiClient.ApiCallback() {
                    @Override public void onSuccess(JsonObject data) {
                        runOnUiThread(() -> {
                            Toast.makeText(ItemDetailActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                    @Override public void onError(String msg) {
                        runOnUiThread(() -> Toast.makeText(ItemDetailActivity.this, "删除失败: " + msg, Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void editItem() {
        Intent intent = new Intent(this, AddItemActivity.class);
        intent.putExtra("edit_mode", true);
        intent.putExtra("goods_id", goodsId);
        startActivity(intent);
    }

    private void moveItem() {
        Toast.makeText(this, "移动功能开发中", Toast.LENGTH_SHORT).show();
    }

    private void copyItem() {
        Toast.makeText(this, "复制功能开发中", Toast.LENGTH_SHORT).show();
    }

    private void borrowItem() {
        if (currentGoods == null) return;
        double currentQty = currentGoods.has("quantity") ? currentGoods.get("quantity").getAsDouble() : 1;
        String unit = currentGoods.has("unit") && !currentGoods.get("unit").isJsonNull() ? currentGoods.get("unit").getAsString() : "个";

        if (currentQty <= 1) {
            // 只有1件，直接领用
            confirmBorrow(1);
            return;
        }

        // 多件时，显示数量选择对话框
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(8));

        TextView hint = new TextView(this);
        hint.setText("当前库存: " + (int) currentQty + unit);
        hint.setTextSize(14);
        hint.setTextColor(0xFF718096);
        container.addView(hint);

        // 数量选择器
        LinearLayout qtyRow = new LinearLayout(this);
        qtyRow.setOrientation(LinearLayout.HORIZONTAL);
        qtyRow.setGravity(Gravity.CENTER_VERTICAL);
        qtyRow.setPadding(0, dp(16), 0, 0);

        TextView btnMinus = new TextView(this);
        btnMinus.setText("−");
        btnMinus.setTextSize(20);
        btnMinus.setTextColor(0xFFFF8C42);
        btnMinus.setGravity(Gravity.CENTER);
        btnMinus.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        btnMinus.setBackgroundResource(R.drawable.bg_icon_btn_rounded);

        EditText etQty = new EditText(this);
        etQty.setText("1");
        etQty.setTextSize(18);
        etQty.setTextColor(0xFF2D3748);
        etQty.setGravity(Gravity.CENTER);
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT);
        etLp.setMarginStart(dp(12));
        etLp.setMarginEnd(dp(12));
        etQty.setLayoutParams(etLp);

        TextView unitTv = new TextView(this);
        unitTv.setText(unit);
        unitTv.setTextSize(14);
        unitTv.setTextColor(0xFF718096);

        TextView btnPlus = new TextView(this);
        btnPlus.setText("+");
        btnPlus.setTextSize(20);
        btnPlus.setTextColor(0xFFFF8C42);
        btnPlus.setGravity(Gravity.CENTER);
        btnPlus.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        btnPlus.setBackgroundResource(R.drawable.bg_icon_btn_rounded);

        btnMinus.setOnClickListener(v -> {
            int val = 1;
            try { val = Integer.parseInt(etQty.getText().toString()); } catch (Exception ignored) {}
            val = Math.max(1, val - 1);
            etQty.setText(String.valueOf(val));
        });
        btnPlus.setOnClickListener(v -> {
            int val = 1;
            try { val = Integer.parseInt(etQty.getText().toString()); } catch (Exception ignored) {}
            val = Math.min((int) currentQty, val + 1);
            etQty.setText(String.valueOf(val));
        });

        qtyRow.addView(btnMinus);
        qtyRow.addView(etQty);
        qtyRow.addView(unitTv);
        qtyRow.addView(btnPlus);
        container.addView(qtyRow);

        new AlertDialog.Builder(this)
            .setTitle("领用物品")
            .setView(container)
            .setPositiveButton("确认领用", (d, w) -> {
                int qty = 1;
                try { qty = Integer.parseInt(etQty.getText().toString()); } catch (Exception ignored) {}
                if (qty < 1) qty = 1;
                if (qty > (int) currentQty) qty = (int) currentQty;
                confirmBorrow(qty);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void confirmBorrow(int quantity) {
        JsonObject body = new JsonObject();
        body.addProperty("goods_id", goodsId);
        body.addProperty("quantity", quantity);
        ApiClient.post("goods.php?action=borrow", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    Toast.makeText(ItemDetailActivity.this, "领用成功", Toast.LENGTH_SHORT).show();
                    loadDetail();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(ItemDetailActivity.this, "领用失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void shareItem() {
        Toast.makeText(this, "分享功能开发中", Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private static class PhotoAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<PhotoAdapter.VH> {
        private final List<String> urls;
        private android.view.View.OnClickListener clickListener;
        PhotoAdapter(List<String> urls) { this.urls = urls; }
        void setOnItemClickListener(android.view.View.OnClickListener listener) { this.clickListener = listener; }

        @Override public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new VH(iv);
        }

        @Override public void onBindViewHolder(VH holder, int position) {
            try {
                com.bumptech.glide.Glide.with(holder.iv.getContext())
                    .load(urls.get(position))
                    .placeholder(R.drawable.bg_quick_item)
                    .into(holder.iv);
            } catch (Exception e) {
                holder.iv.setImageResource(R.drawable.bg_quick_item);
            }
            holder.iv.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onClick(v);
            });
        }

        @Override public int getItemCount() { return urls.size(); }

        static class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView iv;
            VH(android.view.View v) { super(v); iv = (ImageView) v; }
        }
    }
}

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
import com.jiashouna.app.db.LocalDb;
import com.jiashouna.app.utils.NetworkUtils;
import com.jiashouna.app.model.Goods;
import java.text.SimpleDateFormat;
import java.util.*;

public class ItemDetailActivity extends AppCompatActivity {
    private int goodsId;
    private TextView tvItemName, tvExpiryBadge, tvPrivacyBadge;
    private TextView tvLocation, tvQuantity, tvUnit, tvUnitDisplay;
    private TextView tvPurchaseDate, tvExpiryDate, tvPrice, tvBarcodeValue;
    private TextView tvNote, tvCreator, tvCreatedTime;
    private TextView tvPhotoCounter, tvBorrowCount;
    private TextView tvDefaultIcon;
    private TextView btnBack, btnMore;
    private TextView tvCategoryIcon, tvCategoryName;
    private TextView tvExpiryBannerIcon, tvExpiryBannerText;
    // Extended info fields
    private TextView tvBrandValue, tvExpiryDays, tvExpiryReminder;
    private TextView tvSize, tvColor, tvMaterial, tvShoeSize;
    private TextView tvModel, tvSerialNumber, tvWarranty;
    private TextView tvEffect, tvSkinType;
    private TextView tvSpec, tvThreshold;
    // Extended info rows
    private View rowBrand, rowBarcode, rowPurchaseDate, rowExpiry, rowPrice;
    private View rowExpiryDays, rowExpiryReminder;
    private View rowSize, rowColor, rowMaterial, rowShoeSize;
    private View rowModel, rowSerialNumber, rowWarranty;
    private View rowEffect, rowSkinType;
    private View rowSpec, rowThreshold;
    // Containers
    private LinearLayout layoutStatusBadges, layoutTags, layoutBorrowSection, layoutBorrowList;
    private LinearLayout layoutCategoryBadge, layoutExpiryBanner, layoutExtendedInfo;
    private LinearLayout layoutTagsSection, layoutNoteSection;
    private LinearLayout layoutFlowLogSection, layoutFlowLogList;
    private LinearLayout galleryDots;
    private ViewPager2 viewpagerPhotos;
    private View btnEditBottom, btnBorrowBottom, btnLendBottom;
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

        if (!NetworkUtils.isNetworkAvailable(this)) {
            loadFromCache();
        } else {
            loadDetail();
        }
    }

    private void initViews() {
        tvItemName = findViewById(R.id.tv_item_name);
        tvExpiryBadge = findViewById(R.id.tv_expiry_badge);
        tvPrivacyBadge = findViewById(R.id.tv_privacy_badge);
        layoutStatusBadges = findViewById(R.id.layout_status_badges);
        tvLocation = findViewById(R.id.tv_location);
        tvQuantity = findViewById(R.id.tv_quantity);
        tvUnit = findViewById(R.id.tv_unit);
        tvUnitDisplay = findViewById(R.id.tv_unit_display);
        tvPurchaseDate = findViewById(R.id.tv_purchase_date);
        tvExpiryDate = findViewById(R.id.tv_expiry_date);
        tvPrice = findViewById(R.id.tv_price);
        tvBarcodeValue = findViewById(R.id.tv_barcode_value);
        tvNote = findViewById(R.id.tv_note);
        tvCreator = findViewById(R.id.tv_creator);
        tvCreatedTime = findViewById(R.id.tv_created_time);
        tvPhotoCounter = findViewById(R.id.tv_photo_counter);
        tvBorrowCount = findViewById(R.id.tv_borrow_count);
        tvDefaultIcon = findViewById(R.id.tv_default_icon);
        btnBack = findViewById(R.id.btn_back);
        btnMore = findViewById(R.id.btn_more);
        galleryDots = findViewById(R.id.gallery_dots);
        viewpagerPhotos = findViewById(R.id.viewpager_photos);

        // Category badge
        tvCategoryIcon = findViewById(R.id.tv_category_icon);
        tvCategoryName = findViewById(R.id.tv_category_name);
        layoutCategoryBadge = findViewById(R.id.layout_category_badge);

        // Expiry banner
        tvExpiryBannerIcon = findViewById(R.id.tv_expiry_banner_icon);
        tvExpiryBannerText = findViewById(R.id.tv_expiry_banner_text);
        layoutExpiryBanner = findViewById(R.id.layout_expiry_banner);

        // Extended info
        layoutExtendedInfo = findViewById(R.id.layout_extended_info);
        tvBrandValue = findViewById(R.id.tv_brand_value);
        tvExpiryDays = findViewById(R.id.tv_expiry_days);
        tvExpiryReminder = findViewById(R.id.tv_expiry_reminder);
        tvSize = findViewById(R.id.tv_size);
        tvColor = findViewById(R.id.tv_color);
        tvMaterial = findViewById(R.id.tv_material);
        tvShoeSize = findViewById(R.id.tv_shoe_size);
        tvModel = findViewById(R.id.tv_model);
        tvSerialNumber = findViewById(R.id.tv_serial_number);
        tvWarranty = findViewById(R.id.tv_warranty);
        tvEffect = findViewById(R.id.tv_effect);
        tvSkinType = findViewById(R.id.tv_skin_type);
        tvSpec = findViewById(R.id.tv_spec);
        tvThreshold = findViewById(R.id.tv_threshold);

        // Rows
        rowBrand = findViewById(R.id.row_brand);
        rowBarcode = findViewById(R.id.row_barcode);
        rowPurchaseDate = findViewById(R.id.row_purchase_date);
        rowExpiry = findViewById(R.id.row_expiry);
        rowPrice = findViewById(R.id.row_price);
        rowExpiryDays = findViewById(R.id.row_expiry_days);
        rowExpiryReminder = findViewById(R.id.row_expiry_reminder);
        rowSize = findViewById(R.id.row_size);
        rowColor = findViewById(R.id.row_color);
        rowMaterial = findViewById(R.id.row_material);
        rowShoeSize = findViewById(R.id.row_shoe_size);
        rowModel = findViewById(R.id.row_model);
        rowSerialNumber = findViewById(R.id.row_serial_number);
        rowWarranty = findViewById(R.id.row_warranty);
        rowEffect = findViewById(R.id.row_effect);
        rowSkinType = findViewById(R.id.row_skin_type);
        rowSpec = findViewById(R.id.row_spec);
        rowThreshold = findViewById(R.id.row_threshold);

        // Tags & Note sections
        layoutTags = findViewById(R.id.layout_tags);
        layoutTagsSection = findViewById(R.id.layout_tags_section);
        layoutNoteSection = findViewById(R.id.layout_note_section);

        // Borrow
        layoutBorrowSection = findViewById(R.id.layout_borrow_section);
        layoutBorrowList = findViewById(R.id.layout_borrow_list);

        // Flow log
        layoutFlowLogSection = findViewById(R.id.layout_flow_log_section);
        layoutFlowLogList = findViewById(R.id.layout_flow_log_list);

        // Bottom buttons
        btnEditBottom = findViewById(R.id.btn_edit_bottom);
        btnBorrowBottom = findViewById(R.id.btn_borrow_bottom);
        btnLendBottom = findViewById(R.id.btn_lend_bottom);

        btnBack.setOnClickListener(v -> finish());
        btnEditBottom.setOnClickListener(v -> editItem());
        btnBorrowBottom.setOnClickListener(v -> borrowItem());
        btnLendBottom.setOnClickListener(v -> lendItem());
        btnMore.setOnClickListener(v -> showMoreMenu());
    }

    private void showMoreMenu() {
        PopupMenu popup = new PopupMenu(this, btnMore);
        popup.getMenu().add(0, 1, 0, "删除物品");
        popup.getMenu().add(0, 2, 0, "分享");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    deleteItem();
                    return true;
                case 2:
                    shareItem();
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void loadDetail() {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(goodsId));
        ApiClient.get("goods.php?action=detail", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonObject goods = null;
                        if (data.has("goods") && !data.get("goods").isJsonNull()) {
                            goods = data.getAsJsonObject("goods");
                        } else if (data.has("name")) {
                            goods = data;
                        }
                        if (goods == null) {
                            Toast.makeText(ItemDetailActivity.this, "物品数据为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        currentGoods = goods;
                        android.util.Log.d("ItemDetail", "goods keys: " + goods.keySet());
                        displayGoods(goods);
                    } catch (Exception e) {
                        android.util.Log.e("ItemDetail", "loadDetail parse error: " + e.getMessage(), e);
                        Toast.makeText(ItemDetailActivity.this, "数据解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    /**
     * 离线模式：从缓存加载物品详情
     */
    private void loadFromCache() {
        LocalDb localDb = new LocalDb(this);
        Goods cached = localDb.getCachedGoodsDetail(goodsId);
        if (cached == null) {
            Toast.makeText(this, "离线模式：未找到缓存数据", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toast.makeText(this, "📱 离线模式", Toast.LENGTH_SHORT).show();

        JsonObject g = new JsonObject();
        g.addProperty("name", cached.name);
        g.addProperty("barcode", cached.barcode);
        g.addProperty("category", cached.category);
        g.addProperty("brand", cached.brand);
        g.addProperty("quantity", cached.quantity);
        g.addProperty("unit", cached.unit);
        g.addProperty("purchase_date", cached.purchaseDate != null ? cached.purchaseDate : "");
        g.addProperty("expiry_date", cached.expiryDate != null ? cached.expiryDate : "");
        g.addProperty("note", cached.note);
        g.addProperty("space_name", cached.spaceName);
        g.addProperty("is_private", 0);
        g.addProperty("creator_name", "");
        g.add("tags", new com.google.gson.JsonArray());
        g.add("images", new com.google.gson.JsonArray());
        g.add("space_path", new com.google.gson.JsonArray());
        g.add("borrow_records", new com.google.gson.JsonArray());
        currentGoods = g;
        displayGoods(g);
    }

    private String getJsonString(JsonObject g, String key) {
        if (g.has(key) && !g.get(key).isJsonNull()) {
            try { return g.get(key).getAsString(); } catch (Exception e) { return ""; }
        }
        return "";
    }

    private void displayGoods(JsonObject g) {
        android.util.Log.d("ItemDetail", "displayGoods called, keys: " + g.keySet());

        // Name
        String name = getJsonString(g, "name");
        tvItemName.setText(name);

        // Category
        String category = getJsonString(g, "category");
        String icon = getCategoryIcon(category);
        tvDefaultIcon.setText(icon);

        // Category badge
        if (!category.isEmpty()) {
            tvCategoryIcon.setText(icon);
            tvCategoryName.setText(category);
            layoutCategoryBadge.setVisibility(View.VISIBLE);
        } else {
            layoutCategoryBadge.setVisibility(View.GONE);
        }

        // Barcode
        String barcode = getJsonString(g, "barcode");
        if (!barcode.isEmpty()) {
            tvBarcodeValue.setText(barcode);
            rowBarcode.setVisibility(View.VISIBLE);
        }

        // Brand
        String brand = getJsonString(g, "brand");
        if (!brand.isEmpty()) {
            tvBrandValue.setText(brand);
            rowBrand.setVisibility(View.VISIBLE);
        }

        // Expiry status banner
        String expiryDate = getJsonString(g, "expiry_date");
        if (!expiryDate.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date expiry = sdf.parse(expiryDate);
                if (expiry != null) {
                    long diff = expiry.getTime() - System.currentTimeMillis();
                    int days = (int) (diff / (1000 * 60 * 60 * 24));

                    String bannerText;
                    int bgColor, textColor;
                    String bannerIcon;

                    if (days < 0) {
                        bannerIcon = "🔴";
                        bannerText = "已过期" + Math.abs(days) + "天";
                        bgColor = 0x1AF56565;
                        textColor = 0xFF9B2C2C;
                    } else if (days == 0) {
                        bannerIcon = "🔴";
                        bannerText = "今天过期";
                        bgColor = 0x1AF56565;
                        textColor = 0xFF9B2C2C;
                    } else if (days <= 7) {
                        bannerIcon = "🟠";
                        bannerText = "还剩" + days + "天过期";
                        bgColor = 0x1AED8936;
                        textColor = 0xFFC25A1E;
                    } else {
                        bannerIcon = "🟢";
                        bannerText = expiryDate + " 到期";
                        bgColor = 0x1A48BB78;
                        textColor = 0xFF22543D;
                    }

                    tvExpiryBannerIcon.setText(bannerIcon);
                    tvExpiryBannerText.setText(bannerText);
                    tvExpiryBannerText.setTextColor(textColor);
                    android.graphics.drawable.GradientDrawable bannerBg = new android.graphics.drawable.GradientDrawable();
                    bannerBg.setColor(bgColor);
                    bannerBg.setCornerRadius(dp(12));
                    layoutExpiryBanner.setBackground(bannerBg);
                    layoutExpiryBanner.setVisibility(View.VISIBLE);

                    // Also set the old expiry badge for compatibility
                    String statusText;
                    int badgeBgColor, badgeTextColor;
                    if (days < 0) {
                        statusText = "⚠ 已过期" + Math.abs(days) + "天";
                        badgeBgColor = 0x1FF56565;
                        badgeTextColor = 0xFF9B2C2C;
                    } else if (days == 0) {
                        statusText = "⚠ 今天过期";
                        badgeBgColor = 0x1FF56565;
                        badgeTextColor = 0xFF9B2C2C;
                    } else if (days <= 7) {
                        statusText = "⚠ 还剩" + days + "天过期";
                        badgeBgColor = 0x1FED8936;
                        badgeTextColor = 0xFFC25A1E;
                    } else {
                        statusText = "✓ " + expiryDate + " 到期";
                        badgeBgColor = 0x1F48BB78;
                        badgeTextColor = 0xFF22543D;
                    }
                    tvExpiryBadge.setText(statusText);
                    android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
                    badgeBg.setColor(badgeBgColor);
                    badgeBg.setCornerRadius(dp(8));
                    tvExpiryBadge.setBackground(badgeBg);
                    tvExpiryBadge.setTextColor(badgeTextColor);
                    tvExpiryBadge.setVisibility(View.VISIBLE);

                    // Expiry date row in extended info
                    String expiryDisplay = expiryDate;
                    if (days <= 7) {
                        if (days < 0) expiryDisplay += "（已过期" + Math.abs(days) + "天）";
                        else if (days == 0) expiryDisplay += "（今天过期）";
                        else expiryDisplay += "（还剩" + days + "天）";
                    }
                    tvExpiryDate.setText(expiryDisplay);
                    tvExpiryDate.setTextColor(days <= 7 ? 0xFFF56565 : 0xFF2D3748);
                    rowExpiry.setVisibility(View.VISIBLE);
                }
            } catch (Exception ignored) {}
        }

        // Privacy badge
        int isPrivate = g.has("is_private") ? g.get("is_private").getAsInt() : 0;
        if (isPrivate == 1) {
            tvPrivacyBadge.setVisibility(View.VISIBLE);
        }

        // Location
        if (g.has("space_path") && !g.get("space_path").isJsonNull()) {
            JsonArray path = g.getAsJsonArray("space_path");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) sb.append(" · ");
                sb.append(path.get(i).getAsJsonObject().get("name").getAsString());
            }
            tvLocation.setText(sb.toString());
        } else {
            tvLocation.setText(getJsonString(g, "space_name").isEmpty() ? "未分类" : getJsonString(g, "space_name"));
        }

        // Quantity + Unit
        double qty = g.has("quantity") ? g.get("quantity").getAsDouble() : 1;
        String unit = getJsonString(g, "unit");
        if (unit.isEmpty()) unit = "个";
        tvQuantity.setText(String.valueOf((int) qty));
        tvUnit.setText(unit);
        tvUnitDisplay.setText(unit);

        // Purchase Date
        String purchaseDate = getJsonString(g, "purchase_date");
        if (!purchaseDate.isEmpty()) {
            tvPurchaseDate.setText(purchaseDate);
            rowPurchaseDate.setVisibility(View.VISIBLE);
        }

        // Price
        if (g.has("purchase_price") && !g.get("purchase_price").isJsonNull()) {
            try {
                double price = g.get("purchase_price").getAsDouble();
                if (price > 0) {
                    tvPrice.setText("¥ " + String.format("%.2f", price));
                    rowPrice.setVisibility(View.VISIBLE);
                }
            } catch (Exception ignored) {}
        }

        // ========== Category-specific extended fields ==========
        boolean hasExtendedInfo = false;

        // Expiry days (食品/药品)
        String expiryDaysVal = getJsonString(g, "expiry_days");
        String expiryUnitVal = getJsonString(g, "expiry_unit");
        if (!expiryDaysVal.isEmpty()) {
            String display = expiryDaysVal;
            if (!expiryUnitVal.isEmpty()) {
                display += expiryUnitVal;
            }
            tvExpiryDays.setText(display);
            rowExpiryDays.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Expiry reminder
        String expiryReminder = getJsonString(g, "expiry_reminder");
        if (!expiryReminder.isEmpty()) {
            tvExpiryReminder.setText(expiryReminder);
            rowExpiryReminder.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Size (服装/鞋帽)
        String size = getJsonString(g, "size");
        if (!size.isEmpty()) {
            tvSize.setText(size);
            rowSize.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Color (服装/化妆品/日用品/其他)
        String color = getJsonString(g, "color");
        if (!color.isEmpty()) {
            tvColor.setText(color);
            rowColor.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Material (服装/日用品/其他)
        String material = getJsonString(g, "material");
        if (!material.isEmpty()) {
            tvMaterial.setText(material);
            rowMaterial.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Shoe size (鞋帽)
        String shoeSize = getJsonString(g, "shoe_size");
        if (!shoeSize.isEmpty()) {
            tvShoeSize.setText(shoeSize);
            rowShoeSize.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Model (数码)
        String model = getJsonString(g, "model");
        if (!model.isEmpty()) {
            tvModel.setText(model);
            rowModel.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Serial number (数码)
        String serialNumber = getJsonString(g, "serial_number");
        if (!serialNumber.isEmpty()) {
            tvSerialNumber.setText(serialNumber);
            rowSerialNumber.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Warranty (数码)
        String warranty = getJsonString(g, "warranty");
        if (!warranty.isEmpty()) {
            tvWarranty.setText(warranty);
            rowWarranty.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Effect (化妆品)
        String effect = getJsonString(g, "effect");
        if (!effect.isEmpty()) {
            tvEffect.setText(effect);
            rowEffect.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Skin type (化妆品)
        String skinType = getJsonString(g, "skin_type");
        if (!skinType.isEmpty()) {
            tvSkinType.setText(skinType);
            rowSkinType.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Spec
        String spec = getJsonString(g, "spec");
        if (!spec.isEmpty()) {
            tvSpec.setText(spec);
            rowSpec.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Threshold
        String threshold = getJsonString(g, "threshold");
        if (!threshold.isEmpty()) {
            tvThreshold.setText(threshold);
            rowThreshold.setVisibility(View.VISIBLE);
            hasExtendedInfo = true;
        }

        // Show extended info container if any field is visible
        if (hasExtendedInfo) {
            layoutExtendedInfo.setVisibility(View.VISIBLE);
        }

        // Tags
        if (g.has("tags") && !g.get("tags").isJsonNull()) {
            JsonArray tags = g.getAsJsonArray("tags");
            if (tags.size() > 0) {
                layoutTags.removeAllViews();
                for (int i = 0; i < tags.size(); i++) {
                    JsonObject tag = tags.get(i).getAsJsonObject();
                    String tagName = getJsonString(tag, "name");
                    if (!tagName.isEmpty()) {
                        TextView tv = new TextView(this);
                        tv.setText(tagName);
                        tv.setTextSize(11);
                        tv.setTextColor(0xFFC25A1E);
                        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                        bg.setColor(0x1FFF8C42);
                        bg.setCornerRadius(dp(16));
                        tv.setBackground(bg);
                        tv.setPadding(dp(10), dp(4), dp(10), dp(4));
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.setMarginEnd(dp(6));
                        lp.bottomMargin = dp(4);
                        tv.setLayoutParams(lp);
                        layoutTags.addView(tv);
                    }
                }
                layoutTags.setVisibility(View.VISIBLE);
                layoutTagsSection.setVisibility(View.VISIBLE);
            }
        }

        // Note
        String note = getJsonString(g, "note");
        if (!note.isEmpty()) {
            tvNote.setText(note);
            layoutNoteSection.setVisibility(View.VISIBLE);
        }

        // Creator
        String creator = getJsonString(g, "creator_name");
        tvCreator.setText("录入者: " + (creator.isEmpty() ? "未知" : creator));

        // Created time
        if (g.has("created_at") && !g.get("created_at").isJsonNull()) {
            try {
                long ts = g.get("created_at").getAsLong();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String dateStr = sdf.format(new Date(ts * 1000));
                long daysSince = (System.currentTimeMillis() - ts * 1000) / (24 * 60 * 60 * 1000);
                if (daysSince < 0) daysSince = 0;
                String daysText;
                if (daysSince == 0) daysText = "今天入库";
                else if (daysSince == 1) daysText = "昨天入库";
                else daysText = "已入库 " + daysSince + " 天";
                tvCreatedTime.setText(dateStr + "（" + daysText + "）");
            } catch (Exception ignored) {}
        }

        // Photos
        List<String> imageUrls = new ArrayList<>();
        if (g.has("images") && !g.get("images").isJsonNull()) {
            JsonArray images = g.getAsJsonArray("images");
            for (int i = 0; i < images.size(); i++) {
                JsonObject img = images.get(i).getAsJsonObject();
                String url = getJsonString(img, "image_path");
                if (!url.isEmpty()) imageUrls.add(url);
            }
        }
        if (imageUrls.isEmpty() && g.has("cover_image") && !g.get("cover_image").isJsonNull()) {
            String coverUrl = g.get("cover_image").getAsString();
            if (!coverUrl.isEmpty()) imageUrls.add(coverUrl);
        }
        if (!imageUrls.isEmpty()) {
            setupPhotoGallery(imageUrls);
        } else {
            tvDefaultIcon.setText(icon);
            tvDefaultIcon.setVisibility(View.VISIBLE);
            viewpagerPhotos.setVisibility(View.GONE);
            tvPhotoCounter.setVisibility(View.GONE);
        }

        // Flow log
        loadFlowLog();

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
            case "日用品": return "🧴";
            case "数码": return "📱";
            case "化妆品": return "💄";
            case "服装": return "👕";
            case "鞋帽": return "👟";
            case "衣物": return "👕";
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

        viewpagerPhotos.setOnClickListener(v -> {
            Intent intent = new Intent(ItemDetailActivity.this, ImageViewerActivity.class);
            intent.putStringArrayListExtra("image_urls", new ArrayList<>(urls));
            intent.putExtra("position", viewpagerPhotos.getCurrentItem());
            startActivity(intent);
        });

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
        String userName = getJsonString(record, "user_name");
        if (userName.isEmpty()) userName = "用户";
        double qty = record.has("quantity") ? record.get("quantity").getAsDouble() : 1;
        int status = record.has("status") ? record.get("status").getAsInt() : 1;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView avatar = new TextView(this);
        avatar.setText(userName.substring(0, 1));
        avatar.setTextSize(13);
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable avatarBg = new android.graphics.drawable.GradientDrawable();
        avatarBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        avatarBg.setColor(0xFFFF8C42);
        avatar.setBackground(avatarBg);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));

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

        View divider = new View(this);
        divider.setBackgroundColor(0xFFEDF2F7);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divLp.setMarginStart(dp(54));
        divider.setLayoutParams(divLp);
        layoutBorrowList.addView(divider);
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
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "离线模式下无法编辑，请联网后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, AddItemActivity.class);
        intent.putExtra("edit_mode", true);
        intent.putExtra("goods_id", goodsId);
        startActivity(intent);
    }

    private void borrowItem() {
        if (currentGoods == null) return;
        double currentQty = currentGoods.has("quantity") ? currentGoods.get("quantity").getAsDouble() : 1;
        String unit = getJsonString(currentGoods, "unit");
        if (unit.isEmpty()) unit = "个";

        if (currentQty <= 1) {
            confirmBorrow(1);
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(8));

        TextView hint = new TextView(this);
        hint.setText("当前库存: " + (int) currentQty + unit);
        hint.setTextSize(14);
        hint.setTextColor(0xFF718096);
        container.addView(hint);

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

    private void lendItem() {
        if (currentGoods == null) return;
        double currentQty = currentGoods.has("quantity") ? currentGoods.get("quantity").getAsDouble() : 1;
        String unit = getJsonString(currentGoods, "unit");
        if (unit.isEmpty()) unit = "个";

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(8));

        // 借出对象
        TextView labelTo = new TextView(this);
        labelTo.setText("借给谁");
        labelTo.setTextSize(13);
        labelTo.setTextColor(0xFF718096);
        container.addView(labelTo);

        EditText etLendTo = new EditText(this);
        etLendTo.setHint("输入对方姓名");
        etLendTo.setTextSize(15);
        etLendTo.setTextColor(0xFF2D3748);
        etLendTo.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = dp(4);
        etLp.bottomMargin = dp(12);
        etLendTo.setLayoutParams(etLp);
        container.addView(etLendTo);

        // 数量
        if (currentQty > 1) {
            TextView labelQty = new TextView(this);
            labelQty.setText("借出数量");
            labelQty.setTextSize(13);
            labelQty.setTextColor(0xFF718096);
            container.addView(labelQty);

            LinearLayout qtyRow = new LinearLayout(this);
            qtyRow.setOrientation(LinearLayout.HORIZONTAL);
            qtyRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            qtyRow.setPadding(0, dp(4), 0, dp(12));

            TextView btnMinus = new TextView(this);
            btnMinus.setText("−");
            btnMinus.setTextSize(20);
            btnMinus.setTextColor(0xFFFF8C42);
            btnMinus.setGravity(android.view.Gravity.CENTER);
            btnMinus.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
            btnMinus.setBackgroundResource(R.drawable.bg_icon_btn_rounded);

            EditText etQty = new EditText(this);
            etQty.setText("1");
            etQty.setTextSize(16);
            etQty.setTextColor(0xFF2D3748);
            etQty.setGravity(android.view.Gravity.CENTER);
            etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            LinearLayout.LayoutParams qtyLp = new LinearLayout.LayoutParams(dp(50), LinearLayout.LayoutParams.WRAP_CONTENT);
            qtyLp.setMarginStart(dp(8));
            qtyLp.setMarginEnd(dp(8));
            etQty.setLayoutParams(qtyLp);

            TextView unitTv = new TextView(this);
            unitTv.setText(unit);
            unitTv.setTextSize(14);
            unitTv.setTextColor(0xFF718096);

            TextView btnPlus = new TextView(this);
            btnPlus.setText("+");
            btnPlus.setTextSize(20);
            btnPlus.setTextColor(0xFFFF8C42);
            btnPlus.setGravity(android.view.Gravity.CENTER);
            btnPlus.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
            btnPlus.setBackgroundResource(R.drawable.bg_icon_btn_rounded);

            btnMinus.setOnClickListener(v -> {
                int val = 1;
                try { val = Integer.parseInt(etQty.getText().toString()); } catch (Exception ignored) {}
                etQty.setText(String.valueOf(Math.max(1, val - 1)));
            });
            btnPlus.setOnClickListener(v -> {
                int val = 1;
                try { val = Integer.parseInt(etQty.getText().toString()); } catch (Exception ignored) {}
                etQty.setText(String.valueOf(Math.min((int) currentQty, val + 1)));
            });

            qtyRow.addView(btnMinus);
            qtyRow.addView(etQty);
            qtyRow.addView(unitTv);
            qtyRow.addView(btnPlus);
            container.addView(qtyRow);
        }

        // 归还提醒
        TextView labelRemind = new TextView(this);
        labelRemind.setText("归还提醒");
        labelRemind.setTextSize(13);
        labelRemind.setTextColor(0xFF718096);
        container.addView(labelRemind);

        String[] remindOptions = {"不提醒", "7天后", "15天后", "30天后", "60天后"};
        final int[] remindDays = {0, 7, 15, 30, 60};
        android.widget.Spinner spRemind = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> spAdapter = new android.widget.ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, remindOptions);
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRemind.setAdapter(spAdapter);
        spRemind.setSelection(3); // 默认30天
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spLp.topMargin = dp(4);
        spLp.bottomMargin = dp(8);
        spRemind.setLayoutParams(spLp);
        container.addView(spRemind);

        // 备注
        EditText etNote = new EditText(this);
        etNote.setHint("备注（选填）");
        etNote.setTextSize(14);
        etNote.setTextColor(0xFF2D3748);
        etNote.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(8);
        etNote.setLayoutParams(noteLp);
        container.addView(etNote);

        new AlertDialog.Builder(this)
            .setTitle("🤝 借出物品")
            .setView(container)
            .setPositiveButton("确认借出", (d, w) -> {
                String lendTo = etLendTo.getText().toString().trim();
                if (lendTo.isEmpty()) {
                    Toast.makeText(this, "请填写借出对象", Toast.LENGTH_SHORT).show();
                    return;
                }
                int qty = 1;
                if (currentQty > 1) {
                    try { qty = Integer.parseInt(((EditText) ((LinearLayout) container.getChildAt(3)).getChildAt(1)).getText().toString()); } catch (Exception ignored) {}
                }
                if (qty < 1) qty = 1;
                if (qty > (int) currentQty) qty = (int) currentQty;
                int selRemind = remindDays[spRemind.getSelectedItemPosition()];
                String note = etNote.getText().toString().trim();
                confirmLend(lendTo, qty, selRemind, note);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void confirmLend(String lendTo, int quantity, int remindDays, String note) {
        JsonObject body = new JsonObject();
        body.addProperty("goods_id", goodsId);
        body.addProperty("quantity", quantity);
        body.addProperty("lend_to", lendTo);
        body.addProperty("remind_days", remindDays);
        if (!note.isEmpty()) body.addProperty("note", note);

        ApiClient.post("goods.php?action=lend", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    Toast.makeText(ItemDetailActivity.this, "✅ 借出成功", Toast.LENGTH_SHORT).show();
                    loadDetail();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(ItemDetailActivity.this, "借出失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadFlowLog() {
        HashMap<String, String> params = new HashMap<>();
        params.put("goods_id", String.valueOf(goodsId));
        ApiClient.get("goods.php?action=flowLog", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (!data.has("list") || data.get("list").isJsonNull()) return;
                        JsonArray list = data.getAsJsonArray("list");
                        if (list.size() == 0) return;
                        layoutFlowLogSection.setVisibility(View.VISIBLE);
                        layoutFlowLogList.removeAllViews();
                        for (int i = 0; i < list.size(); i++) {
                            JsonObject log = list.get(i).getAsJsonObject();
                            addFlowLogItem(log);
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void addFlowLogItem(JsonObject log) {
        String actionName = getJsonString(log, "action_name");
        String detail = getJsonString(log, "detail");
        String userName = getJsonString(log, "user_name");
        String timeStr = getJsonString(log, "time_str");
        String action = getJsonString(log, "action");

        // 图标映射
        String icon = "📝";
        int iconBgColor = 0xFFEDF2F7;
        switch (action) {
            case "create": icon = "📥"; iconBgColor = 0xFFE6FFFA; break;
            case "edit": icon = "✏️"; iconBgColor = 0xFFFFFAF0; break;
            case "borrow": icon = "📤"; iconBgColor = 0xFFFFFAF0; break;
            case "lend": icon = "🤝"; iconBgColor = 0xFFEBF8FF; break;
            case "return": icon = "📥"; iconBgColor = 0xFFF0FFF4; break;
            case "import": icon = "📦"; iconBgColor = 0xFFFAF5FF; break;
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        // 图标
        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextSize(16);
        tvIcon.setGravity(android.view.Gravity.CENTER);
        tvIcon.setWidth(dp(36));
        tvIcon.setHeight(dp(36));
        android.graphics.drawable.GradientDrawable igBg = new android.graphics.drawable.GradientDrawable();
        igBg.setCornerRadius(dp(8));
        igBg.setColor(iconBgColor);
        tvIcon.setBackground(igBg);
        row.addView(tvIcon);

        // 信息
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.leftMargin = dp(10);
        info.setLayoutParams(infoLp);

        TextView tvAction = new TextView(this);
        tvAction.setText(actionName + (detail.isEmpty() ? "" : " - " + detail));
        tvAction.setTextSize(13);
        tvAction.setTextColor(0xFF2D3748);
        info.addView(tvAction);

        TextView tvMeta = new TextView(this);
        tvMeta.setText((userName.isEmpty() ? "" : userName + " · ") + timeStr);
        tvMeta.setTextSize(11);
        tvMeta.setTextColor(0xFFA0AEC0);
        info.addView(tvMeta);

        row.addView(info);
        layoutFlowLogList.addView(row);

        // 分隔线
        if (layoutFlowLogList.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(0xFFEDF2F7);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            divLp.setMarginStart(dp(60));
            divider.setLayoutParams(divLp);
            layoutFlowLogList.addView(divider);
        }
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

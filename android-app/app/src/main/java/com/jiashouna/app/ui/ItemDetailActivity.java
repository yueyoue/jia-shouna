package com.jiashouna.app.ui;

import android.content.Intent;
import android.os.Bundle;
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
    private TextView tvTitle, tvItemName, tvItemBarcode, tvItemNote, tvCategoryTag;
    private TextView tvLocation, tvQuantity, tvBrand, tvPurchaseDate, tvExpiryDate, tvDaysLeft;
    private TextView tvPrice, tvCreator, tvCreatedTime, tvPrivacyBadge, tvPhotoCounter;
    private TextView btnBack, btnShare, btnDelete, btnEdit, btnMove, btnBorrow;
    private LinearLayout layoutBrand, layoutPurchaseDate, layoutExpiry, layoutPrice;
    private LinearLayout layoutTagsSection, layoutTags, layoutNoPhoto;
    private ViewPager2 viewpagerPhotos;

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
        tvTitle = findViewById(R.id.tv_title);
        tvItemName = findViewById(R.id.tv_item_name);
        tvItemBarcode = findViewById(R.id.tv_item_barcode);
        tvItemNote = findViewById(R.id.tv_item_note);
        tvCategoryTag = findViewById(R.id.tv_category_tag);
        tvLocation = findViewById(R.id.tv_location);
        tvQuantity = findViewById(R.id.tv_quantity);
        tvBrand = findViewById(R.id.tv_brand);
        tvPurchaseDate = findViewById(R.id.tv_purchase_date);
        tvExpiryDate = findViewById(R.id.tv_expiry_date);
        tvDaysLeft = findViewById(R.id.tv_days_left);
        tvPrice = findViewById(R.id.tv_price);
        tvCreator = findViewById(R.id.tv_creator);
        tvCreatedTime = findViewById(R.id.tv_created_time);
        tvPrivacyBadge = findViewById(R.id.tv_privacy_badge);
        tvPhotoCounter = findViewById(R.id.tv_photo_counter);
        btnBack = findViewById(R.id.btn_back);
        btnShare = findViewById(R.id.btn_share);
        btnDelete = findViewById(R.id.btn_delete);
        btnEdit = findViewById(R.id.btn_edit);
        btnMove = findViewById(R.id.btn_move);
        btnBorrow = findViewById(R.id.btn_borrow);
        layoutBrand = findViewById(R.id.layout_brand);
        layoutPurchaseDate = findViewById(R.id.layout_purchase_date);
        layoutExpiry = findViewById(R.id.layout_expiry);
        layoutPrice = findViewById(R.id.layout_price);
        layoutTagsSection = findViewById(R.id.layout_tags_section);
        layoutTags = findViewById(R.id.layout_tags);
        layoutNoPhoto = findViewById(R.id.layout_no_photo);
        viewpagerPhotos = findViewById(R.id.viewpager_photos);

        btnBack.setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> deleteItem());
        btnEdit.setOnClickListener(v -> editItem());
        btnMove.setOnClickListener(v -> moveItem());
        btnBorrow.setOnClickListener(v -> borrowItem());
        btnShare.setOnClickListener(v -> shareItem());
    }

    private void loadDetail() {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(goodsId));
        ApiClient.get("goods.php?action=detail", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonObject goods = data.getAsJsonObject("goods");
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
        String name = g.has("name") ? g.get("name").getAsString() : "";
        tvItemName.setText(name);
        tvTitle.setText(name);

        // Category
        String category = g.has("category") ? g.get("category").getAsString() : "";
        if (!category.isEmpty()) {
            tvCategoryTag.setText("📦 " + category);
            tvCategoryTag.setVisibility(View.VISIBLE);
        }

        // Barcode
        String barcode = g.has("barcode") ? g.get("barcode").getAsString() : "";
        if (!barcode.isEmpty()) {
            tvItemBarcode.setText("条码: " + barcode);
            tvItemBarcode.setVisibility(View.VISIBLE);
        }

        // Note
        String note = g.has("note") ? g.get("note").getAsString() : "";
        if (!note.isEmpty()) {
            tvItemNote.setText(note);
            tvItemNote.setVisibility(View.VISIBLE);
        }

        // Location - space path
        if (g.has("space_path") && !g.get("space_path").isJsonNull()) {
            JsonArray path = g.getAsJsonArray("space_path");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) sb.append(" > ");
                sb.append(path.get(i).getAsJsonObject().get("name").getAsString());
            }
            tvLocation.setText(sb.toString());
        } else {
            tvLocation.setText(g.has("space_name") ? g.get("space_name").getAsString() : "未分类");
        }

        // Quantity
        double qty = g.has("quantity") ? g.get("quantity").getAsDouble() : 1;
        String unit = g.has("unit") ? g.get("unit").getAsString() : "个";
        tvQuantity.setText(qty + " " + unit);

        // Brand
        String brand = g.has("brand") ? g.get("brand").getAsString() : "";
        if (!brand.isEmpty()) {
            tvBrand.setText(brand);
            layoutBrand.setVisibility(View.VISIBLE);
        }

        // Production/Purchase Date
        String purchaseDate = g.has("purchase_date") && !g.get("purchase_date").isJsonNull() ? g.get("purchase_date").getAsString() : "";
        if (!purchaseDate.isEmpty()) {
            tvPurchaseDate.setText(purchaseDate);
            layoutPurchaseDate.setVisibility(View.VISIBLE);
        }

        // Expiry Date
        String expiryDate = g.has("expiry_date") && !g.get("expiry_date").isJsonNull() ? g.get("expiry_date").getAsString() : "";
        if (!expiryDate.isEmpty()) {
            tvExpiryDate.setText(expiryDate);
            layoutExpiry.setVisibility(View.VISIBLE);

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date expiry = sdf.parse(expiryDate);
                if (expiry != null) {
                    long diff = expiry.getTime() - System.currentTimeMillis();
                    int days = (int) (diff / (1000 * 60 * 60 * 24));
                    tvDaysLeft.setVisibility(View.VISIBLE);
                    if (days < 0) {
                        tvDaysLeft.setText("已过期" + Math.abs(days) + "天");
                        tvDaysLeft.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    } else if (days <= 7) {
                        tvDaysLeft.setText("还剩" + days + "天");
                    } else {
                        tvDaysLeft.setText("还剩" + days + "天");
                        tvDaysLeft.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    }
                }
            } catch (Exception ignored) {}
        }

        // Price
        if (g.has("purchase_price") && !g.get("purchase_price").isJsonNull()) {
            double price = g.get("purchase_price").getAsDouble();
            if (price > 0) {
                tvPrice.setText("¥" + String.format("%.2f", price));
                layoutPrice.setVisibility(View.VISIBLE);
            }
        }

        // Creator
        String creator = g.has("creator_name") ? g.get("creator_name").getAsString() : "";
        tvCreator.setText(creator.isEmpty() ? "未知" : creator);

        // Created time
        if (g.has("created_at") && !g.get("created_at").isJsonNull()) {
            long ts = g.get("created_at").getAsLong();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            tvCreatedTime.setText(sdf.format(new Date(ts * 1000)));
        }

        // Privacy
        int isPrivate = g.has("is_private") ? g.get("is_private").getAsInt() : 0;
        if (isPrivate == 1) {
            tvPrivacyBadge.setVisibility(View.VISIBLE);
        }

        // Photos
        if (g.has("images") && !g.get("images").isJsonNull()) {
            JsonArray images = g.getAsJsonArray("images");
            if (images.size() > 0) {
                List<String> imageUrls = new ArrayList<>();
                for (int i = 0; i < images.size(); i++) {
                    JsonObject img = images.get(i).getAsJsonObject();
                    String url = img.has("image_path") ? img.get("image_path").getAsString() : "";
                    if (!url.isEmpty()) imageUrls.add(url);
                }
                if (!imageUrls.isEmpty()) {
                    setupPhotoGallery(imageUrls);
                } else {
                    showNoPhoto();
                }
            } else {
                showNoPhoto();
            }
        } else {
            showNoPhoto();
        }

        // Tags
        if (g.has("tags") && !g.get("tags").isJsonNull()) {
            JsonArray tags = g.getAsJsonArray("tags");
            if (tags.size() > 0) {
                layoutTagsSection.setVisibility(View.VISIBLE);
                layoutTags.removeAllViews();
                for (int i = 0; i < tags.size(); i++) {
                    JsonObject tag = tags.get(i).getAsJsonObject();
                    String tagName = tag.has("name") ? tag.get("name").getAsString() : "";
                    if (!tagName.isEmpty()) {
                        TextView tv = new TextView(this);
                        tv.setText(tagName);
                        tv.setTextSize(12);
                        tv.setTextColor(0xFF5B9FED);
                        tv.setBackgroundResource(R.drawable.bg_tag_blue);
                        tv.setPadding(dp(12), dp(4), dp(12), dp(4));
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.rightMargin = dp(6);
                        lp.bottomMargin = dp(4);
                        tv.setLayoutParams(lp);
                        layoutTags.addView(tv);
                    }
                }
            }
        }
    }

    private void showNoPhoto() {
        layoutNoPhoto.setVisibility(View.VISIBLE);
        viewpagerPhotos.setVisibility(View.GONE);
    }

    private void setupPhotoGallery(List<String> urls) {
        layoutNoPhoto.setVisibility(View.GONE);
        viewpagerPhotos.setVisibility(View.VISIBLE);
        tvPhotoCounter.setVisibility(View.VISIBLE);
        tvPhotoCounter.setText("1/" + urls.size());

        viewpagerPhotos.setAdapter(new PhotoAdapter(urls));
        viewpagerPhotos.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tvPhotoCounter.setText((position + 1) + "/" + urls.size());
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

    private void borrowItem() {
        new AlertDialog.Builder(this)
            .setTitle("领用物品")
            .setMessage("确认领用此物品？")
            .setPositiveButton("确认", (d, w) -> {
                JsonObject body = new JsonObject();
                body.addProperty("goods_id", goodsId);
                body.addProperty("quantity", 1);
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
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void shareItem() {
        Toast.makeText(this, "分享功能开发中", Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // Simple photo adapter
    private static class PhotoAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<PhotoAdapter.VH> {
        private final List<String> urls;
        PhotoAdapter(List<String> urls) { this.urls = urls; }

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
        }

        @Override public int getItemCount() { return urls.size(); }

        static class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView iv;
            VH(android.view.View v) { super(v); iv = (ImageView) v; }
        }
    }
}

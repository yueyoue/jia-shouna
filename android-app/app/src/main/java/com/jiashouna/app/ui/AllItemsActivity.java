package com.jiashouna.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.db.LocalDb;
import com.jiashouna.app.utils.NetworkUtils;
import com.jiashouna.app.model.Goods;
import java.util.*;

public class AllItemsActivity extends AppCompatActivity {
    private static final String TAG = "AllItemsActivity";
    private EditText etSearch;
    private LinearLayout layoutItems, layoutEmpty;
    private ProgressBar progress;
    private TextView tvCount, tvTitle;
    private int houseId = 0;
    private String filterType = "";
    private boolean isLoading = false;
    private LocalDb localDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_items);

        localDb = new LocalDb(this);
        houseId = getIntent().getIntExtra("house_id", App.getInstance().getCurrentHouseId());
        filterType = getIntent().getStringExtra("filter_type") != null ? getIntent().getStringExtra("filter_type") : "";

        etSearch = findViewById(R.id.et_search);
        layoutItems = findViewById(R.id.layout_items);
        layoutEmpty = findViewById(R.id.layout_empty);
        progress = findViewById(R.id.progress);
        tvCount = findViewById(R.id.tv_count);
        tvTitle = findViewById(R.id.tv_title);



        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        String title = getIntent().getStringExtra("title");
        if (title != null) tvTitle.setText(title);

        boolean focusSearch = getIntent().getBooleanExtra("focus_search", false);
        String initKeyword = getIntent().getStringExtra("keyword");
        if (initKeyword != null && !initKeyword.isEmpty()) {
            etSearch.setText(initKeyword);
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String kw = s.toString().trim();
                loadItems(kw);
            }
        });

        if (focusSearch) {
            etSearch.requestFocus();
            etSearch.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearch, 0);
            }, 200);
        }

        String initKw = (initKeyword != null && !initKeyword.isEmpty()) ? initKeyword : "";
        loadItems(initKw);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isLoading) {
            String kw = etSearch.getText().toString().trim();
            loadItems(kw);
        }
    }

    private void loadItems(String keyword) {
        if (isLoading) return;
        isLoading = true;
        progress.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);


        if (houseId <= 0) {
            houseId = App.getInstance().getCurrentHouseId();
        }
        Log.d(TAG, "loadItems: houseId=" + houseId + ", keyword=" + keyword + ", filter=" + filterType);

        HashMap<String, String> params = new HashMap<>();
        if (houseId > 0) {
            params.put("house_id", String.valueOf(houseId));
        }
        params.put("page_size", "200");

        if (keyword != null && !keyword.isEmpty()) {
            params.put("keyword", keyword);
        }

        String endpoint = "goods.php?action=list";
        if ("expiring".equals(filterType)) {
            endpoint = "goods.php?action=expiring";
            params.put("days", "7");
        }

        Log.d(TAG, "Requesting: " + endpoint + " params=" + params);

        // 离线且无搜索关键词时走缓存
        if (!NetworkUtils.isNetworkAvailable(this) && (keyword == null || keyword.isEmpty())) {
            loadFromCache();
            return;
        }

        ApiClient.get(endpoint, params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                String respStr = data.toString();
                Log.d(TAG, "API success: " + respStr.substring(0, Math.min(500, respStr.length())));
                runOnUiThread(() -> {
                    isLoading = false;
                    progress.setVisibility(View.GONE);
                    layoutItems.removeAllViews();
                    try {
                        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
                        int total = data.has("total") ? data.get("total").getAsInt() : list.size();

                        // 联网成功时缓存列表数据
                        try { cacheItems(list); } catch (Exception ignored) {}

                        tvCount.setText(list.size() + " 件");
                        if (list.size() == 0) {
                            layoutEmpty.setVisibility(View.VISIBLE);
                            loadItemsFallback(keyword);
                        } else {
                            layoutEmpty.setVisibility(View.GONE);
                            layoutItems.setPadding(dp(12), dp(8), dp(12), 0);
                            int cols = 2; // 2列网格
                            int rendered = 0;
                            for (int i = 0; i < list.size(); i += cols) {
                                LinearLayout row = new LinearLayout(AllItemsActivity.this);
                                row.setOrientation(LinearLayout.HORIZONTAL);
                                row.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT));
                                LinearLayout.LayoutParams rowLp = (LinearLayout.LayoutParams) row.getLayoutParams();
                                rowLp.bottomMargin = dp(12);
                                row.setLayoutParams(rowLp);

                                for (int j = 0; j < cols; j++) {
                                    if (i + j < list.size()) {
                                        try {
                                            JsonObject item = list.get(i + j).getAsJsonObject();
                                            View card = createGridItem(item);
                                            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                                            if (j > 0) cardLp.setMarginStart(dp(12));
                                            card.setLayoutParams(cardLp);
                                            row.addView(card);
                                            rendered++;
                                        } catch (Exception e) {
                                            Log.e(TAG, "Render error at " + (i+j) + ": " + e.getMessage());
                                        }
                                    } else {
                                        // 单数物品时，添加占位保持半排宽度
                                        View spacer = new View(this);
                                        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1));
                                        row.addView(spacer);
                                    }
                                }
                                layoutItems.addView(row);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage());
                        loadItemsFallback(keyword);
                    }
                });
            }
            @Override public void onError(String msg) {
                Log.e(TAG, "API error: " + msg);
                runOnUiThread(() -> {
                    isLoading = false;
                    progress.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    /**
     * Fallback: try loading all items without house_id filter
     */
    private void loadItemsFallback(String keyword) {
        Log.d(TAG, "Fallback: loading without house_id filter");
        HashMap<String, String> params = new HashMap<>();
        params.put("page_size", "200");
        if (keyword != null && !keyword.isEmpty()) {
            params.put("keyword", keyword);
        }

        ApiClient.get("goods.php?action=list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                Log.d(TAG, "Fallback success: " + data.toString().substring(0, Math.min(200, data.toString().length())));
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    try {
                        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
                        Log.d(TAG, "Fallback items: " + list.size());
                        tvCount.setText(list.size() + " 件");
                        if (list.size() == 0) {
                            layoutEmpty.setVisibility(View.VISIBLE);

                        } else {
                            layoutEmpty.setVisibility(View.GONE);
                            layoutItems.setPadding(dp(12), dp(8), dp(12), 0);
                            int cols = 2;
                            for (int i = 0; i < list.size(); i += cols) {
                                LinearLayout row = new LinearLayout(AllItemsActivity.this);
                                row.setOrientation(LinearLayout.HORIZONTAL);
                                row.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT));
                                LinearLayout.LayoutParams rowLp = (LinearLayout.LayoutParams) row.getLayoutParams();
                                rowLp.bottomMargin = dp(12);
                                row.setLayoutParams(rowLp);
                                for (int j = 0; j < cols; j++) {
                                    if (i + j < list.size()) {
                                        try {
                                            View card = createGridItem(list.get(i + j).getAsJsonObject());
                                            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                                            if (j > 0) cardLp.setMarginStart(dp(12));
                                            card.setLayoutParams(cardLp);
                                            row.addView(card);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Fallback render error: " + e.getMessage());
                                        }
                                    } else {
                                        View spacer = new View(AllItemsActivity.this);
                                        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1));
                                        row.addView(spacer);
                                    }
                                }
                                layoutItems.addView(row);
                            }
                            tvCount.setText(list.size() + " 件");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Fallback parse error: " + e.getMessage());
                        layoutEmpty.setVisibility(View.VISIBLE);
                    }
                });
            }
            @Override public void onError(String msg) {
                Log.e(TAG, "Fallback error: " + msg);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private View createGridItem(JsonObject item) {
        // 网格卡片容器
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_warm);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(cardLp);

        // 图片区域
        String coverUrl = "";
        if (item.has("images") && !item.get("images").isJsonNull()) {
            try {
                JsonArray imgs = item.getAsJsonArray("images");
                if (imgs.size() > 0) coverUrl = imgs.get(0).getAsString();
            } catch (Exception ignored) {}
        }
        if (coverUrl.isEmpty() && item.has("cover_image") && !item.get("cover_image").isJsonNull()) {
            coverUrl = item.get("cover_image").getAsString();
        }

        ImageView imgView = new ImageView(this);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(100));
        imgView.setLayoutParams(imgLp);
        imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgView.setBackgroundColor(0xFFFFE8D6);
        if (!coverUrl.isEmpty()) {
            String fullUrl = coverUrl.startsWith("http") ? coverUrl : "https://sn.tthsdd.top/backend/uploads/" + coverUrl;
            try {
                com.bumptech.glide.Glide.with(this)
                    .load(fullUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(imgView);
            } catch (Exception e) {
                imgView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            // 用分类图标代替
            String category = item.has("category") ? item.get("category").getAsString() : "";
            String emoji = getCategoryIcon(category);
            imgView.setImageBitmap(createEmojiBitmap(emoji, dp(48)));
        }
        card.addView(imgView);

        // 名称
        String name = item.has("name") ? item.get("name").getAsString() : "";
        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(13);
        tvName.setTextColor(0xFF2D3748);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setMaxLines(1);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(8);
        tvName.setLayoutParams(nameLp);
        card.addView(tvName);

        // 数量 + 空间
        int qty = 0;
        try {
            if (item.has("quantity") && !item.get("quantity").isJsonNull()) {
                qty = (int) Double.parseDouble(item.get("quantity").getAsString());
            }
        } catch (Exception ignored) {}
        String unit = item.has("unit") && !item.get("unit").isJsonNull() ? item.get("unit").getAsString() : "件";
        String spaceName = item.has("space_name") && !item.get("space_name").isJsonNull() ? item.get("space_name").getAsString() : "";

        TextView tvMeta = new TextView(this);
        tvMeta.setText("× " + qty + unit + (spaceName.isEmpty() ? "" : " · " + spaceName));
        tvMeta.setTextSize(11);
        tvMeta.setTextColor(0xFF718096);
        tvMeta.setMaxLines(1);
        tvMeta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(4);
        tvMeta.setLayoutParams(metaLp);
        card.addView(tvMeta);

        // 点击事件
        int itemId = item.has("id") ? item.get("id").getAsInt() : 0;
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, ItemDetailActivity.class);
            intent.putExtra("goods_id", itemId);
            startActivity(intent);
        });

        return card;
    }

    private android.graphics.Bitmap createEmojiBitmap(String emoji, int size) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.drawColor(0xFFFFE8D6);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size * 0.6f);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Rect textBounds = new android.graphics.Rect();
        paint.getTextBounds(emoji, 0, emoji.length(), textBounds);
        float x = canvas.getWidth() / 2f;
        float y = canvas.getHeight() / 2f + textBounds.height() / 2f;
        canvas.drawText(emoji, x, y, paint);
        return bitmap;
    }

    private View createItemRow(JsonObject item, boolean showDivider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackgroundColor(Color.WHITE);

        // 物品图片
        String coverImage = item.has("cover_image") && !item.get("cover_image").isJsonNull() ? item.get("cover_image").getAsString() : "";
        android.widget.ImageView imgIcon = new android.widget.ImageView(this);
        imgIcon.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable imgBg = new android.graphics.drawable.GradientDrawable();
        imgBg.setCornerRadius(dp(10));
        imgBg.setColor(0xFFFFE8D6);
        imgIcon.setBackground(imgBg);
        imgIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        if (!coverImage.isEmpty()) {
            try {
                com.bumptech.glide.Glide.with(this)
                    .load(coverImage)
                    .placeholder(R.drawable.bg_quick_item)
                    .error(R.drawable.bg_quick_item)
                    .centerCrop()
                    .into(imgIcon);
            } catch (Exception e) {
                imgIcon.setImageResource(R.drawable.bg_quick_item);
            }
        } else {
            // 使用分类emoji作为默认图
            String category = item.has("category") && !item.get("category").isJsonNull() ? item.get("category").getAsString() : "";
            String emoji = getCategoryIcon(category);
            android.graphics.Bitmap emojiBitmap = android.graphics.Bitmap.createBitmap(dp(48), dp(48), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(emojiBitmap);
            canvas.drawColor(0xFFFFE8D6);
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(dp(24));
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            android.graphics.Rect textBounds = new android.graphics.Rect();
            paint.getTextBounds(emoji, 0, emoji.length(), textBounds);
            float x = canvas.getWidth() / 2f;
            float y = canvas.getHeight() / 2f + textBounds.height() / 2f;
            canvas.drawText(emoji, x, y, paint);
            imgIcon.setImageBitmap(emojiBitmap);
        }
        row.addView(imgIcon);

        // Info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.setMarginStart(dp(12));
        info.setLayoutParams(infoLp);

        TextView name = new TextView(this);
        name.setText(item.has("name") ? item.get("name").getAsString() : "");
        name.setTextSize(14);
        name.setTextColor(0xFF2D3748);
        name.setTypeface(null, Typeface.BOLD);
        info.addView(name);

        // Meta
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        int qty = 0;
        try {
            if (item.has("quantity") && !item.get("quantity").isJsonNull()) {
                qty = (int) Double.parseDouble(item.get("quantity").getAsString());
            }
        } catch (Exception ignored) {}
        String unit = item.has("unit") && !item.get("unit").isJsonNull() ? item.get("unit").getAsString() : "件";
        String spaceName = item.has("space_name") && !item.get("space_name").isJsonNull() ? item.get("space_name").getAsString() : "";

        TextView meta = new TextView(this);
        meta.setText("× " + qty + unit + (spaceName.isEmpty() ? "" : " · " + spaceName));
        meta.setTextSize(11);
        meta.setTextColor(0xFF718096);
        metaRow.addView(meta);

        info.addView(metaRow);
        row.addView(info);

        // Arrow
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(0xFFCBD5E0);
        row.addView(arrow);

        int itemId = item.has("id") ? item.get("id").getAsInt() : 0;
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, ItemDetailActivity.class);
            intent.putExtra("goods_id", itemId);
            startActivity(intent);
        });

        if (showDivider) {
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.addView(row);
            View divider = new View(this);
            divider.setBackgroundColor(0xFFEDF2F7);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            divLp.setMarginStart(dp(76));
            divider.setLayoutParams(divLp);
            wrapper.addView(divider);
            return wrapper;
        }
        return row;
    }

    /**
     * 缓存物品列表到本地
     */
    private void cacheItems(JsonArray list) {
        List<JsonObject> items = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            items.add(list.get(i).getAsJsonObject());
        }
        localDb.refreshCache(items);
    }

    /**
     * 从本地缓存加载物品（离线模式）
     */
    private void loadFromCache() {
        isLoading = true;
        progress.setVisibility(View.GONE);
        layoutItems.removeAllViews();
        layoutEmpty.setVisibility(View.GONE);

        List<Goods> cached;
        String keyword = etSearch.getText().toString().trim();
        if (keyword != null && !keyword.isEmpty()) {
            cached = localDb.searchCachedGoods(keyword);
        } else {
            cached = localDb.getCachedGoods();
        }

        // 离线提示
        if (!cached.isEmpty()) {
            TextView banner = new TextView(this);
            banner.setText("📱 离线模式 · 显示缓存数据（" + cached.size() + "件）");
            banner.setTextSize(12);
            banner.setTextColor(0xFF718096);
            banner.setBackgroundColor(0xFFFFF7F0);
            banner.setPadding(dp(16), dp(8), dp(16), dp(8));
            layoutItems.addView(banner);
        }

        tvCount.setText(cached.size() + " 件");
        layoutItems.setPadding(dp(12), dp(8), dp(12), 0);
        int cols = 2;
        for (int i = 0; i < cached.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams rowLp = (LinearLayout.LayoutParams) row.getLayoutParams();
            rowLp.bottomMargin = dp(12);
            row.setLayoutParams(rowLp);
            for (int j = 0; j < cols; j++) {
                if (i + j < cached.size()) {
                    Goods g = cached.get(i + j);
                    JsonObject item = new JsonObject();
                    item.addProperty("id", g.id);
                    item.addProperty("name", g.name);
                    item.addProperty("barcode", g.barcode);
                    item.addProperty("category", g.category);
                    item.addProperty("quantity", g.quantity);
                    item.addProperty("unit", g.unit);
                    item.addProperty("space_name", g.spaceName);
                    item.addProperty("cover_image", g.coverImage);
                    View card = createGridItem(item);
                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    if (j > 0) cardLp.setMarginStart(dp(12));
                    card.setLayoutParams(cardLp);
                    row.addView(card);
                }
            }
            layoutItems.addView(row);
        }

        if (cached.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
        }

        isLoading = false;
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
            case "饮品": return "🥤";
            case "玩具": return "🧸";
            case "文具": return "✏️";
            default: return "📦";
        }
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

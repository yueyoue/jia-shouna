package com.jiashouna.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.util.HashMap;

public class SpaceDetailActivity extends AppCompatActivity {
    private int spaceId;
    private int houseId;
    private String spaceName;

    private TextView tvTitle;
    private TextView tvSpaceIcon;
    private TextView tvSpaceName;
    private TextView tvSpaceDesc;
    private TextView tvItemCount;
    private TextView tvExpiringCount;
    private TextView tvSharedLabel;
    private TextView tvSubspacesTitle;
    private LinearLayout layoutBreadcrumb;
    private LinearLayout layoutSubspacesHeader;
    private LinearLayout layoutSubspaces;
    private LinearLayout layoutItemsHeader;
    private LinearLayout layoutItems;
    private TextView tvItemsTitle;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;

    // 6 colors for sub-space cards (matching design)
    private final int[] SPACE_COLORS = {
            0xFFFF8C42, 0xFF4ECDC4, 0xFF5B9FED,
            0xFFED8936, 0xFF9F7AEA, 0xFF48BB78
    };
    private final int[] SPACE_COLOR_BGS = {
            R.drawable.bg_subspace_color1, R.drawable.bg_subspace_color2,
            R.drawable.bg_subspace_color3, R.drawable.bg_subspace_color4,
            R.drawable.bg_subspace_color5, R.drawable.bg_subspace_color6
    };
    private final int[] SPACE_ICON_BGS = {
            0x1FFF8C42, 0x1F4ECDC4, 0x1F5B9FED,
            0x1FED8936, 0x1F9F7AEA, 0x1F48BB78
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_space_detail);

        spaceId = getIntent().getIntExtra("space_id", 0);
        spaceName = getIntent().getStringExtra("space_name");
        houseId = getIntent().getIntExtra("house_id", 0);

        tvTitle = findViewById(R.id.tv_title);
        tvSpaceIcon = findViewById(R.id.tv_space_icon);
        tvSpaceName = findViewById(R.id.tv_space_name);
        tvSpaceDesc = findViewById(R.id.tv_space_desc);
        tvItemCount = findViewById(R.id.tv_item_count);
        tvExpiringCount = findViewById(R.id.tv_expiring_count);
        tvSharedLabel = findViewById(R.id.tv_shared_label);
        tvSubspacesTitle = findViewById(R.id.tv_subspaces_title);
        layoutBreadcrumb = findViewById(R.id.layout_breadcrumb);
        layoutSubspacesHeader = findViewById(R.id.layout_subspaces_header);
        layoutSubspaces = findViewById(R.id.layout_subspaces);
        layoutItemsHeader = findViewById(R.id.layout_items_header);
        layoutItems = findViewById(R.id.layout_items);
        tvItemsTitle = findViewById(R.id.tv_items_title);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutEmpty = findViewById(R.id.layout_empty);

        if (spaceName != null) {
            tvTitle.setText(spaceName);
            tvSpaceName.setText(spaceName);
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddItemActivity.class);
            intent.putExtra("space_id", spaceId);
            intent.putExtra("house_id", houseId);
            startActivity(intent);
        });

        loadSpaceDetail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItems();
    }

    private void loadSpaceDetail() {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(spaceId));

        ApiClient.get("space.php?action=detail", params, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonObject space = data.has("space") ? data.getAsJsonObject("space") : data;
                        bindSpaceInfo(space);
                    } catch (Exception e) {
                        Toast.makeText(SpaceDetailActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() ->
                        Toast.makeText(SpaceDetailActivity.this, "加载失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void bindSpaceInfo(JsonObject space) {
        // Icon
        String icon = space.has("icon") && !space.get("icon").isJsonNull()
                ? space.get("icon").getAsString() : "📦";
        tvSpaceIcon.setText(icon);

        // Name
        String name = space.has("name") ? space.get("name").getAsString() : "";
        tvSpaceName.setText(name);
        tvTitle.setText(name);

        // Description
        String desc = space.has("description") && !space.get("description").isJsonNull()
                ? space.get("description").getAsString() : "";
        tvSpaceDesc.setText(desc.isEmpty() ? "收纳空间" : desc);

        // Shared
        boolean shared = space.has("shared") && space.get("shared").getAsInt() == 1;
        tvSharedLabel.setVisibility(shared ? View.VISIBLE : View.GONE);

        // Stats
        int itemCount = space.has("item_count") ? space.get("item_count").getAsInt() : 0;
        int expiringCount = space.has("expiring_count") ? space.get("expiring_count").getAsInt() : 0;
        tvItemCount.setText(String.valueOf(itemCount));
        tvExpiringCount.setText(String.valueOf(expiringCount));

        // Breadcrumb
        buildBreadcrumb(space);

        // Children
        JsonArray children = new JsonArray();
        if (space.has("children") && !space.get("children").isJsonNull()) {
            children = space.getAsJsonArray("children");
        }
        tvSubspacesTitle.setText("📁 子空间（" + children.size() + "）");
        buildSubspaces(children);
    }

    private void buildBreadcrumb(JsonObject space) {
        layoutBreadcrumb.removeAllViews();

        JsonArray path = new JsonArray();
        if (space.has("path") && !space.get("path").isJsonNull()) {
            path = space.getAsJsonArray("path");
        }

        if (path.size() == 0) {
            addBreadcrumbItem("🏠 我的家", false);
            addBreadcrumbSep();
            addBreadcrumbItem(space.has("name") ? space.get("name").getAsString() : "", true);
        } else {
            for (int i = 0; i < path.size(); i++) {
                JsonObject p = path.get(i).getAsJsonObject();
                String pName = p.has("name") ? p.get("name").getAsString() : "";
                boolean isLast = (i == path.size() - 1);
                addBreadcrumbItem(i == 0 ? "🏠 " + pName : pName, isLast);
                if (!isLast) addBreadcrumbSep();
            }
        }
    }

    private void addBreadcrumbItem(String text, boolean isBold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(isBold ? 0xFF2D3748 : 0xFF4A5568);
        if (isBold) tv.setTypeface(null, Typeface.BOLD);
        layoutBreadcrumb.addView(tv);
    }

    private void addBreadcrumbSep() {
        TextView tv = new TextView(this);
        tv.setText("/");
        tv.setTextSize(12);
        tv.setTextColor(0xFFCBD5E0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(4));
        lp.setMarginEnd(dp(4));
        tv.setLayoutParams(lp);
        layoutBreadcrumb.addView(tv);
    }

    private void buildSubspaces(JsonArray children) {
        layoutSubspaces.removeAllViews();

        if (children.size() == 0) {
            layoutSubspacesHeader.setVisibility(View.GONE);
            return;
        }

        layoutSubspacesHeader.setVisibility(View.VISIBLE);

        // Build rows with 2 columns each
        int rows = (children.size() + 1) / 2; // +1 for the "add" card
        int idx = 0;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(10);
            row.setLayoutParams(rowLp);

            for (int c = 0; c < 2; c++) {
                if (idx < children.size()) {
                    JsonObject child = children.get(idx).getAsJsonObject();
                    View card = createSubspaceCard(child, idx);
                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    if (c == 0) cardLp.setMarginEnd(dp(5));
                    else cardLp.setMarginStart(dp(5));
                    card.setLayoutParams(cardLp);
                    row.addView(card);
                    idx++;
                } else if (idx == children.size()) {
                    // "Add" card
                    View addCard = createAddSubspaceCard();
                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    cardLp.setMarginStart(dp(5));
                    addCard.setLayoutParams(cardLp);
                    row.addView(addCard);
                    idx++;
                }
            }
            layoutSubspaces.addView(row);
        }
    }

    private View createSubspaceCard(JsonObject child, int colorIdx) {
        int colorIndex = colorIdx % SPACE_COLORS.length;
        int borderColor = SPACE_COLORS[colorIndex];
        int bgDrawable = SPACE_COLOR_BGS[colorIndex];
        int iconBg = SPACE_ICON_BGS[colorIndex];

        CardView card = new CardView(this);
        card.setRadius(dp(14));
        card.setCardElevation(dp(1));
        card.setCardBackgroundColor(Color.WHITE);

        // Inner layout with left color border
        FrameLayout frame = new FrameLayout(this);

        // Left color bar
        View bar = new View(this);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(dp(3), FrameLayout.LayoutParams.MATCH_PARENT);
        bar.setLayoutParams(barLp);
        bar.setBackgroundColor(borderColor);
        frame.addView(bar);

        // Content
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        contentLp.setMarginStart(dp(3));
        content.setLayoutParams(contentLp);
        content.setPadding(dp(14), dp(14), dp(14), dp(14));

        // Top row: icon + name + arrow
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(this);
        String iconStr = child.has("icon") && !child.get("icon").isJsonNull()
                ? child.get("icon").getAsString() : "📦";
        icon.setText(iconStr);
        icon.setTextSize(18);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        icon.setLayoutParams(iconLp);
        // Set icon background color
        android.graphics.drawable.GradientDrawable iconBgShape = new android.graphics.drawable.GradientDrawable();
        iconBgShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        iconBgShape.setCornerRadius(dp(8));
        iconBgShape.setColor(iconBg);
        icon.setBackground(iconBgShape);

        TextView name = new TextView(this);
        name.setText(child.has("name") ? child.get("name").getAsString() : "");
        name.setTextSize(14);
        name.setTextColor(0xFF2D3748);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameLp.setMarginStart(dp(8));
        name.setLayoutParams(nameLp);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(12);
        arrow.setTextColor(0xFFCBD5E0);

        topRow.addView(icon);
        topRow.addView(name);
        topRow.addView(arrow);

        // Bottom row: item count + expiring
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomLp.topMargin = dp(8);
        bottomRow.setLayoutParams(bottomLp);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);

        int itemCount = child.has("item_count") ? child.get("item_count").getAsInt() : 0;
        int expiringCount = child.has("expiring_count") ? child.get("expiring_count").getAsInt() : 0;

        TextView countTv = new TextView(this);
        countTv.setText("📦 " + itemCount + " 件");
        countTv.setTextSize(11);
        countTv.setTextColor(0xFF718096);

        TextView expiringTv = new TextView(this);
        expiringTv.setText("⏰ " + expiringCount + " 临期");
        expiringTv.setTextSize(11);
        expiringTv.setTextColor(expiringCount > 0 ? 0xFFED8936 : 0xFF718096);
        if (expiringCount > 0) expiringTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        expLp.setMarginStart(dp(12));
        expiringTv.setLayoutParams(expLp);

        bottomRow.addView(countTv);
        bottomRow.addView(expiringTv);

        content.addView(topRow);
        content.addView(bottomRow);
        frame.addView(content);
        card.addView(frame);

        // Click
        int childId = child.has("id") ? child.get("id").getAsInt() : 0;
        String childName = child.has("name") ? child.get("name").getAsString() : "";
        card.setOnClickListener(v -> {
            Intent intent = new Intent(SpaceDetailActivity.this, SpaceDetailActivity.class);
            intent.putExtra("space_id", childId);
            intent.putExtra("space_name", childName);
            intent.putExtra("house_id", houseId);
            intent.putExtra("space_level", child.has("level") ? child.get("level").getAsInt() : 1);
            startActivity(intent);
        });

        return card;
    }

    private View createAddSubspaceCard() {
        CardView card = new CardView(this);
        card.setRadius(dp(14));
        card.setCardElevation(dp(0));
        card.setCardBackgroundColor(0xFFFAFAFA);

        // Dashed border effect via foreground
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        border.setCornerRadius(dp(14));
        border.setColor(0xFFFAFAFA);
        border.setStroke(dp(1), 0xFFCBD5E0, dp(4), dp(4));
        card.setBackground(border);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(16), dp(24), dp(16), dp(24));

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(24);
        plus.setTextColor(0xFFA0AEC0);
        plus.setGravity(Gravity.CENTER);
        plus.setAlpha(0.5f);

        TextView label = new TextView(this);
        label.setText("新建子空间");
        label.setTextSize(11);
        label.setTextColor(0xFFA0AEC0);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(4);
        label.setLayoutParams(labelLp);

        content.addView(plus);
        content.addView(label);
        card.addView(content);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddSpaceActivity.class);
            intent.putExtra("house_id", houseId);
            intent.putExtra("parent_id", spaceId);
            intent.putExtra("parent_space_name", spaceName);
            // 获取当前空间层级
            int currentLevel = 1;
            try {
                HashMap<String, String> p = new HashMap<>();
                p.put("id", String.valueOf(spaceId));
                // 从intent或已加载的数据获取level
            } catch (Exception ignored) {}
            intent.putExtra("parent_level", getIntent().getIntExtra("space_level", 1));
            startActivity(intent);
        });

        return card;
    }

    private void loadItems() {
        if (houseId <= 0) {
            showEmpty();
            return;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("action", "list");
        params.put("space_id", String.valueOf(spaceId));
        params.put("house_id", String.valueOf(houseId));

        ApiClient.get("goods.php", params, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonArray list = new JsonArray();
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            list = data.getAsJsonArray("list");
                        }
                        buildItems(list);
                    } catch (Exception e) {
                        showEmpty();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void buildItems(JsonArray list) {
        layoutItems.removeAllViews();
        layoutLoading.setVisibility(View.GONE);

        if (list.size() == 0) {
            showEmpty();
            tvItemsTitle.setText("📦 物品列表（0）");
            return;
        }

        layoutEmpty.setVisibility(View.GONE);
        tvItemsTitle.setText("📦 物品列表（" + list.size() + "）");

        for (int i = 0; i < list.size(); i++) {
            JsonObject item = list.get(i).getAsJsonObject();
            View row = createItemRow(item);
            layoutItems.addView(row);
        }
    }

    private View createItemRow(JsonObject item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        // Bottom divider
        android.graphics.drawable.GradientDrawable divider = new android.graphics.drawable.GradientDrawable();
        android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{divider});
        // Use a simple approach - set background with bottom border

        // Icon (48dp)
        TextView icon = new TextView(this);
        String iconStr = item.has("icon") && !item.get("icon").isJsonNull()
                ? item.get("icon").getAsString() : "📦";
        icon.setText(iconStr);
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        // Gradient background for icon
        android.graphics.drawable.GradientDrawable iconBg = new android.graphics.drawable.GradientDrawable();
        iconBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(dp(10));
        iconBg.setColor(0xFFFFE8D6);
        icon.setBackground(iconBg);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        icon.setLayoutParams(iconLp);

        // Info column
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.setMarginStart(dp(12));
        info.setLayoutParams(infoLp);

        TextView name = new TextView(this);
        name.setText(item.has("name") ? item.get("name").getAsString() : "");
        name.setTextSize(14);
        name.setTextColor(0xFF2D3748);
        name.setTypeface(null, Typeface.BOLD);

        // Meta row: quantity · status
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(2);
        metaRow.setLayoutParams(metaLp);

        int qty = item.has("quantity") ? item.get("quantity").getAsInt() : 0;
        String unit = item.has("unit") && !item.get("unit").isJsonNull()
                ? item.get("unit").getAsString() : "件";

        TextView qtyTv = new TextView(this);
        qtyTv.setText("× " + qty);
        qtyTv.setTextSize(11);
        qtyTv.setTextColor(0xFF718096);

        TextView sepTv = new TextView(this);
        sepTv.setText(" · ");
        sepTv.setTextSize(11);
        sepTv.setTextColor(0xFFCBD5E0);

        // Status
        TextView statusTv = new TextView(this);
        String status = "在库";
        int statusColor = 0xFF48BB78; // green

        if (item.has("status") && !item.get("status").isJsonNull()) {
            String s = item.get("status").getAsString();
            switch (s) {
                case "borrowed":
                    status = "已领用";
                    statusColor = 0xFF5B9FED;
                    break;
                case "low_stock":
                    status = "⚠ 库存不足";
                    statusColor = 0xFFED8936;
                    break;
                case "expiring":
                    status = "⚠ 临期";
                    statusColor = 0xFFF56565;
                    break;
                default:
                    status = "✓ 在库";
                    statusColor = 0xFF48BB78;
                    break;
            }
        } else {
            // Check if expiring by date
            if (item.has("expiry_date") && !item.get("expiry_date").isJsonNull()) {
                String expiryDate = item.get("expiry_date").getAsString();
                if (!expiryDate.isEmpty()) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                        java.util.Date expiry = sdf.parse(expiryDate);
                        java.util.Date now = new java.util.Date();
                        long diff = expiry.getTime() - now.getTime();
                        long daysLeft = diff / (1000 * 60 * 60 * 24);
                        if (daysLeft < 0) {
                            status = "⚠ 已过期";
                            statusColor = 0xFFF56565;
                        } else if (daysLeft <= 30) {
                            status = "⚠ " + daysLeft + " 天后过期";
                            statusColor = 0xFFF56565;
                        } else {
                            status = "✓ 在库";
                            statusColor = 0xFF48BB78;
                        }
                    } catch (Exception e) {
                        status = "✓ 在库";
                        statusColor = 0xFF48BB78;
                    }
                }
            }
        }

        statusTv.setText(status);
        statusTv.setTextSize(11);
        statusTv.setTextColor(statusColor);

        metaRow.addView(qtyTv);
        metaRow.addView(sepTv);
        metaRow.addView(statusTv);

        info.addView(name);
        info.addView(metaRow);

        // Arrow
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(14);
        arrow.setTextColor(0xFFCBD5E0);

        row.addView(icon);
        row.addView(info);
        row.addView(arrow);

        // Divider line at bottom
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapper.setLayoutParams(wrapperLp);
        wrapper.addView(row);

        View dividerLine = new View(this);
        dividerLine.setBackgroundColor(0xFFEDF2F7);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerLine.setLayoutParams(divLp);
        wrapper.addView(dividerLine);

        // Click
        int goodsId = item.has("id") ? item.get("id").getAsInt() : 0;
        wrapper.setOnClickListener(v -> {
            Intent intent = new Intent(SpaceDetailActivity.this, ItemDetailActivity.class);
            intent.putExtra("goods_id", goodsId);
            startActivity(intent);
        });

        return wrapper;
    }

    private void showEmpty() {
        layoutLoading.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

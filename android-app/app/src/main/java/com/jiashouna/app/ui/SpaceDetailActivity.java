package com.jiashouna.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    private TextView tvSharedBadge;
    private TextView tvItemCount;
    private TextView tvExpiringCount;
    private TextView tvChildrenCount;
    private TextView tvItemsCountLabel;
    private LinearLayout layoutBreadcrumb;
    private LinearLayout layoutSubspacesHeader;
    private LinearLayout layoutSubspaces;
    private LinearLayout layoutItems;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;

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
        tvSharedBadge = findViewById(R.id.tv_shared_badge);
        tvItemCount = findViewById(R.id.tv_item_count);
        tvExpiringCount = findViewById(R.id.tv_expiring_count);
        tvChildrenCount = findViewById(R.id.tv_children_count);
        tvItemsCountLabel = findViewById(R.id.tv_items_count_label);
        layoutBreadcrumb = findViewById(R.id.layout_breadcrumb);
        layoutSubspacesHeader = findViewById(R.id.layout_subspaces_header);
        layoutSubspaces = findViewById(R.id.layout_subspaces);
        layoutItems = findViewById(R.id.layout_items);
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

        // Shared badge
        boolean shared = space.has("shared") && space.get("shared").getAsInt() == 1;
        tvSharedBadge.setVisibility(shared ? View.VISIBLE : View.GONE);

        // Item count
        int itemCount = space.has("item_count") ? space.get("item_count").getAsInt() : 0;
        tvItemCount.setText(String.valueOf(itemCount));

        // Expiring count
        int expiringCount = space.has("expiring_count") ? space.get("expiring_count").getAsInt() : 0;
        tvExpiringCount.setText(String.valueOf(expiringCount));

        // Breadcrumb
        buildBreadcrumb(space);

        // Children
        JsonArray children = new JsonArray();
        if (space.has("children") && !space.get("children").isJsonNull()) {
            children = space.getAsJsonArray("children");
        }
        tvChildrenCount.setText(String.valueOf(children.size()));
        buildSubspaces(children);
    }

    private void buildBreadcrumb(JsonObject space) {
        layoutBreadcrumb.removeAllViews();

        // Try to get path from API, or build simple breadcrumb
        JsonArray path = new JsonArray();
        if (space.has("path") && !space.get("path").isJsonNull()) {
            path = space.getAsJsonArray("path");
        }

        if (path.size() == 0) {
            // Fallback: Home > current space
            addBreadcrumbItem("🏠 我的家", false);
            addBreadcrumbSeparator();
            addBreadcrumbItem(space.has("name") ? space.get("name").getAsString() : "", true);
        } else {
            for (int i = 0; i < path.size(); i++) {
                JsonObject p = path.get(i).getAsJsonObject();
                String pName = p.has("name") ? p.get("name").getAsString() : "";
                boolean isLast = (i == path.size() - 1);
                addBreadcrumbItem(i == 0 ? "🏠 " + pName : pName, isLast);
                if (!isLast) {
                    addBreadcrumbSeparator();
                }
            }
        }
    }

    private void addBreadcrumbItem(String text, boolean isBold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(isBold ? 0xFF2D3748 : 0xFF4A5568);
        if (isBold) {
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        layoutBreadcrumb.addView(tv);
    }

    private void addBreadcrumbSeparator() {
        TextView tv = new TextView(this);
        tv.setText(" / ");
        tv.setTextSize(12);
        tv.setTextColor(0xFFCBD5E0);
        layoutBreadcrumb.addView(tv);
    }

    private void buildSubspaces(JsonArray children) {
        layoutSubspaces.removeAllViews();

        if (children.size() == 0) {
            layoutSubspacesHeader.setVisibility(View.GONE);
            return;
        }

        layoutSubspacesHeader.setVisibility(View.VISIBLE);

        for (int i = 0; i < children.size(); i++) {
            JsonObject child = children.get(i).getAsJsonObject();
            View card = createSubspaceCard(child);
            layoutSubspaces.addView(card);
        }
    }

    private View createSubspaceCard(JsonObject child) {
        CardView card = new CardView(this);
        card.setRadius(dp(12));
        card.setCardElevation(dp(2));
        card.setUseCompatPadding(false);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Icon
        TextView icon = new TextView(this);
        String iconStr = child.has("icon") && !child.get("icon").isJsonNull()
                ? child.get("icon").getAsString() : "📦";
        icon.setText(iconStr);
        icon.setTextSize(20);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setBackgroundResource(R.drawable.bg_icon_circle);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        icon.setLayoutParams(iconLp);

        // Info column
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.setMarginStart(dp(12));
        info.setLayoutParams(infoLp);

        TextView name = new TextView(this);
        name.setText(child.has("name") ? child.get("name").getAsString() : "");
        name.setTextSize(15);
        name.setTextColor(0xFF2D3748);
        name.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout countRow = new LinearLayout(this);
        countRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams countRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countRowLp.topMargin = dp(4);
        countRow.setLayoutParams(countRowLp);

        TextView count = new TextView(this);
        int c = child.has("item_count") ? child.get("item_count").getAsInt() : 0;
        count.setText("📦 " + c + " 件");
        count.setTextSize(12);
        count.setTextColor(0xFF999999);

        countRow.addView(count);
        info.addView(name);
        info.addView(countRow);

        // Arrow
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(0xFFCCCCCC);

        row.addView(icon);
        row.addView(info);
        row.addView(arrow);
        card.addView(row);

        // Click to open child space detail
        int childId = child.has("id") ? child.get("id").getAsInt() : 0;
        String childName = child.has("name") ? child.get("name").getAsString() : "";
        card.setOnClickListener(v -> {
            Intent intent = new Intent(SpaceDetailActivity.this, SpaceDetailActivity.class);
            intent.putExtra("space_id", childId);
            intent.putExtra("space_name", childName);
            intent.putExtra("house_id", houseId);
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
            return;
        }

        layoutEmpty.setVisibility(View.GONE);
        tvItemsCountLabel.setText(list.size() + " 件");

        for (int i = 0; i < list.size(); i++) {
            JsonObject item = list.get(i).getAsJsonObject();
            View card = createItemCard(item);
            layoutItems.addView(card);
        }
    }

    private View createItemCard(JsonObject item) {
        CardView card = new CardView(this);
        card.setRadius(dp(12));
        card.setCardElevation(dp(2));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        // Icon
        TextView icon = new TextView(this);
        String iconStr = item.has("icon") && !item.get("icon").isJsonNull()
                ? item.get("icon").getAsString() : "📦";
        icon.setText(iconStr);
        icon.setTextSize(20);
        icon.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(36), dp(36));
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
        name.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams subRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subRowLp.topMargin = dp(2);
        subRow.setLayoutParams(subRowLp);

        TextView quantity = new TextView(this);
        int qty = item.has("quantity") ? item.get("quantity").getAsInt() : 0;
        String unit = item.has("unit") && !item.get("unit").isJsonNull()
                ? item.get("unit").getAsString() : "件";
        quantity.setText("×" + qty + " " + unit);
        quantity.setTextSize(12);
        quantity.setTextColor(0xFF718096);

        subRow.addView(quantity);

        // Expiry status
        if (item.has("expiry_date") && !item.get("expiry_date").isJsonNull()) {
            String expiryDate = item.get("expiry_date").getAsString();
            if (!expiryDate.isEmpty()) {
                TextView expiry = new TextView(this);
                expiry.setText("  ⏰ " + expiryDate);
                expiry.setTextSize(12);
                expiry.setTextColor(0xFFF56565);
                subRow.addView(expiry);
            }
        }

        info.addView(name);
        info.addView(subRow);

        // Status badge
        if (item.has("status") && !item.get("status").isJsonNull()) {
            String status = item.get("status").getAsString();
            if (!status.isEmpty() && !"normal".equals(status)) {
                TextView badge = new TextView(this);
                String badgeText;
                int badgeColor;
                switch (status) {
                    case "borrowed":
                        badgeText = "已领用";
                        badgeColor = 0xFF5B9FED;
                        break;
                    case "expiring":
                        badgeText = "临期";
                        badgeColor = 0xFFF56565;
                        break;
                    default:
                        badgeText = status;
                        badgeColor = 0xFF718096;
                        break;
                }
                badge.setText(badgeText);
                badge.setTextSize(11);
                badge.setTextColor(badgeColor);
                badge.setPadding(dp(8), dp(2), dp(8), dp(2));

                row.addView(icon);
                row.addView(info);
                row.addView(badge);
                card.addView(row);

                int goodsId = item.has("id") ? item.get("id").getAsInt() : 0;
                card.setOnClickListener(v -> {
                    Intent intent = new Intent(SpaceDetailActivity.this, ItemDetailActivity.class);
                    intent.putExtra("goods_id", goodsId);
                    startActivity(intent);
                });
                return card;
            }
        }

        // Arrow
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(0xFFCCCCCC);

        row.addView(icon);
        row.addView(info);
        row.addView(arrow);
        card.addView(row);

        int goodsId = item.has("id") ? item.get("id").getAsInt() : 0;
        card.setOnClickListener(v -> {
            Intent intent = new Intent(SpaceDetailActivity.this, ItemDetailActivity.class);
            intent.putExtra("goods_id", goodsId);
            startActivity(intent);
        });

        return card;
    }

    private void showEmpty() {
        layoutLoading.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        tvItemsCountLabel.setText("0 件");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

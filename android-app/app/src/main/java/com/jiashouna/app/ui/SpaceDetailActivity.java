package com.jiashouna.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.util.HashMap;

public class SpaceDetailActivity extends AppCompatActivity {
    private int spaceId;
    private int houseId;
    private String spaceName;

    private TextView tvTitle;
    private LinearLayout layoutBreadcrumb;
    private LinearLayout layoutContainerTabs;
    private LinearLayout layoutItems;
    private TextView tvItemsTitle;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;

    private TextView tvSpaceIcon, tvSpaceName, tvSpaceDesc, tvItemCount, tvExpiringCount;

    private JsonArray currentChildren = new JsonArray();
    private int selectedContainerId = 0; // 0=显示全部

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_space_detail);

        spaceId = getIntent().getIntExtra("space_id", 0);
        spaceName = getIntent().getStringExtra("space_name");
        houseId = getIntent().getIntExtra("house_id", 0);
        if (houseId <= 0) {
            houseId = App.getInstance().getCurrentHouseId();
        }

        tvTitle = findViewById(R.id.tv_title);
        layoutBreadcrumb = findViewById(R.id.layout_breadcrumb);
        tvSpaceIcon = findViewById(R.id.tv_space_icon);
        tvSpaceName = findViewById(R.id.tv_space_name);
        tvSpaceDesc = findViewById(R.id.tv_space_desc);
        tvItemCount = findViewById(R.id.tv_item_count);
        tvExpiringCount = findViewById(R.id.tv_expiring_count);
        layoutContainerTabs = findViewById(R.id.layout_subspaces);
        layoutItems = findViewById(R.id.layout_items);
        tvItemsTitle = findViewById(R.id.tv_items_title);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutEmpty = findViewById(R.id.layout_empty);

        if (spaceName != null) tvTitle.setText(spaceName);

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
        // 重新加载当前选中容器的物品
        if (selectedContainerId > 0) {
            loadItemsForSpace(selectedContainerId);
        } else if (spaceId > 0) {
            loadItemsForSpace(spaceId);
        }
    }

    private void loadSpaceDetail() {
        HashMap<String, String> params = new HashMap<>();
        params.put("action", "detail");
        params.put("id", String.valueOf(spaceId));

        ApiClient.get("space.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonObject space = data.has("space") ? data.getAsJsonObject("space") : data;
                        bindSpaceInfo(space);
                    } catch (Exception e) {
                        Toast.makeText(SpaceDetailActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() ->
                    Toast.makeText(SpaceDetailActivity.this, "加载失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void bindSpaceInfo(JsonObject space) {
        String icon = space.has("icon") && !space.get("icon").isJsonNull()
                ? space.get("icon").getAsString() : "📦";
        String name = space.has("name") ? space.get("name").getAsString() : "";
        String desc = space.has("description") && !space.get("description").isJsonNull()
                ? space.get("description").getAsString() : "";
        int itemCount = space.has("item_count") ? space.get("item_count").getAsInt() : 0;
        int expiringCount = space.has("expiring_count") ? space.get("expiring_count").getAsInt() : 0;

        tvTitle.setText(name);
        tvSpaceIcon.setText(icon);
        tvSpaceName.setText(name);
        tvSpaceDesc.setText(desc.isEmpty() ? "收纳空间" : desc);
        tvItemCount.setText(String.valueOf(itemCount));
        tvExpiringCount.setText(String.valueOf(expiringCount));

        // 面包屑
        buildBreadcrumb(space);

        // 子空间(容器)
        JsonArray children = new JsonArray();
        if (space.has("children") && !space.get("children").isJsonNull()) {
            children = space.getAsJsonArray("children");
        }
        currentChildren = children;
        buildContainerTabs(children);

        // 默认加载当前空间物品
        selectedContainerId = 0;
        loadItemsForSpace(spaceId);
    }

    // ==================== 横向容器标签 ====================

    private void buildContainerTabs(JsonArray children) {
        layoutContainerTabs.removeAllViews();

        if (children.size() == 0) {
            // 没有子空间，隐藏标签栏
            return;
        }

        // 标题行
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(4));

        TextView title = new TextView(this);
        title.setText("📁 容器");
        title.setTextSize(14);
        title.setTextColor(0xFF2D3748);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);

        layoutContainerTabs.addView(header);

        // 横向滚动标签
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setFillViewport(true);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(16), dp(8), dp(16), dp(8));

        // "全部"标签
        TextView allTab = createTab("全部", "📋", selectedContainerId == 0);
        allTab.setOnClickListener(v -> {
            selectedContainerId = 0;
            buildContainerTabs(currentChildren);
            loadItemsForSpace(spaceId);
        });
        tabRow.addView(allTab);

        // 各容器标签
        for (int i = 0; i < children.size(); i++) {
            JsonObject child = children.get(i).getAsJsonObject();
            int childId = child.has("id") ? child.get("id").getAsInt() : 0;
            String childName = child.has("name") ? child.get("name").getAsString() : "";
            String childIcon = child.has("icon") && !child.get("icon").isJsonNull()
                ? child.get("icon").getAsString() : "📦";

            int finalId = childId;
            TextView tab = createTab(childName, childIcon, selectedContainerId == childId);
            tab.setOnClickListener(v -> {
                selectedContainerId = finalId;
                buildContainerTabs(currentChildren);
                loadItemsForSpace(finalId);
            });
            tabRow.addView(tab);
        }

        hsv.addView(tabRow);
        layoutContainerTabs.addView(hsv);
    }

    private TextView createTab(String name, String icon, boolean selected) {
        TextView tv = new TextView(this);
        tv.setText(icon + " " + name);
        tv.setTextSize(13);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        tv.setLayoutParams(lp);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(20));
        if (selected) {
            bg.setColor(0xFFFF8C42);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(0xFFF7FAFC);
            bg.setStroke(dp(1), 0xFFE2E8F0);
            tv.setTextColor(0xFF4A5568);
        }
        tv.setBackground(bg);
        return tv;
    }

    // ==================== 加载物品 ====================

    private void loadItemsForSpace(int targetSpaceId) {
        layoutItems.removeAllViews();
        layoutLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        HashMap<String, String> params = new HashMap<>();
        params.put("action", "list");
        params.put("house_id", String.valueOf(houseId));

        if (selectedContainerId == 0) {
            // "全部"模式：加载当前空间及所有子空间的物品
            params.put("space_id", String.valueOf(targetSpaceId));
            params.put("include_children", "1");
        } else {
            // 指定容器：只加载该容器的物品
            params.put("space_id", String.valueOf(targetSpaceId));
        }

        ApiClient.get("goods.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (isFinishing()) return;
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    try {
                        JsonArray list = new JsonArray();
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            list = data.getAsJsonArray("list");
                        }
                        buildItemsList(list);
                    } catch (Exception e) {
                        buildItemsList(new JsonArray());
                    }
                });
            }
            @Override public void onError(String msg) {
                if (isFinishing()) return;
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    buildItemsList(new JsonArray());
                });
            }
        });
    }

    // ==================== 物品列表(HTML样式) ====================

    private void buildItemsList(JsonArray list) {
        layoutItems.removeAllViews();

        if (list.size() == 0) {
            tvItemsTitle.setText("📦 物品列表（0）");
            layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }

        layoutEmpty.setVisibility(View.GONE);
        tvItemsTitle.setText("📦 物品列表（" + list.size() + "）");

        for (int i = 0; i < list.size(); i++) {
            JsonObject item = list.get(i).getAsJsonObject();
            layoutItems.addView(createItemRow(item, i < list.size() - 1));
        }
    }

    private View createItemRow(JsonObject item, boolean showDivider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        // 图标
        TextView icon = new TextView(this);
        String iconStr = item.has("icon") && !item.get("icon").isJsonNull()
                ? item.get("icon").getAsString() : "📦";
        icon.setText(iconStr);
        icon.setTextSize(20);
        icon.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable iconBg = new android.graphics.drawable.GradientDrawable();
        iconBg.setCornerRadius(dp(10));
        iconBg.setColor(0xFFFFE8D6);
        icon.setBackground(iconBg);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        icon.setLayoutParams(iconLp);

        // 信息列
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

        // 元数据行：数量 · 状态
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
        qtyTv.setText("× " + qty + unit);
        qtyTv.setTextSize(11);
        qtyTv.setTextColor(0xFF718096);

        TextView sep = new TextView(this);
        sep.setText(" · ");
        sep.setTextSize(11);
        sep.setTextColor(0xFFCBD5E0);

        TextView statusTv = new TextView(this);
        String statusText = "✓ 在库";
        int statusColor = 0xFF48BB78;

        // 检查是否临期
        if (item.has("expiry_date") && !item.get("expiry_date").isJsonNull()) {
            String expDate = item.get("expiry_date").getAsString();
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                long expMillis = sdf.parse(expDate).getTime();
                long daysLeft = (expMillis - System.currentTimeMillis()) / (24 * 60 * 60 * 1000);
                if (daysLeft < 0) {
                    statusText = "⚠ 已过期";
                    statusColor = 0xFFF56565;
                } else if (daysLeft <= 7) {
                    statusText = "⚠ " + daysLeft + "天后过期";
                    statusColor = 0xFFED8936;
                } else if (daysLeft <= 30) {
                    statusText = "⏰ " + daysLeft + "天后过期";
                    statusColor = 0xFFED8936;
                }
            } catch (Exception ignored) {}
        }

        // 检查库存阈值
        if (item.has("stock_threshold") && !item.get("stock_threshold").isJsonNull()) {
            int threshold = item.get("stock_threshold").getAsInt();
            if (threshold > 0 && qty <= threshold) {
                statusText = "⚠ 库存不足";
                statusColor = 0xFFED8936;
            }
        }

        statusTv.setText(statusText);
        statusTv.setTextSize(11);
        statusTv.setTextColor(statusColor);

        metaRow.addView(qtyTv);
        metaRow.addView(sep);
        metaRow.addView(statusTv);

        info.addView(name);
        info.addView(metaRow);

        // 箭头
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(0xFFCBD5E0);

        row.addView(icon);
        row.addView(info);
        row.addView(arrow);

        // 点击打开详情
        int itemId = item.has("id") ? item.get("id").getAsInt() : 0;
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, ItemDetailActivity.class);
            intent.putExtra("goods_id", itemId);
            startActivity(intent);
        });

        // 分隔线
        if (showDivider) {
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.addView(row);
            View divider = new View(this);
            divider.setBackgroundColor(0xFFEDF2F7);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            divLp.setMarginStart(dp(76));
            divider.setLayoutParams(divLp);
            wrapper.addView(divider);
            return wrapper;
        }

        return row;
    }

    // ==================== 面包屑 ====================

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

    private void showEmpty() {
        layoutEmpty.setVisibility(View.VISIBLE);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

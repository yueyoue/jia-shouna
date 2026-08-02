package com.jiashouna.app.ui;

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
import java.util.*;

public class StatsActivity extends AppCompatActivity {
    private LinearLayout layoutOverview, layoutCategory, layoutSpace, layoutExpiring, layoutLowStock;
    private ProgressBar progress;
    private int houseId;

    // 饼图颜色
    private final int[] COLORS = {
        0xFFFF8C42, 0xFF4A90D9, 0xFF27AE60, 0xFF9B59B6,
        0xFFE74C3C, 0xFFF39C12, 0xFF1ABC9C, 0xFF34495E,
        0xFFE91E63, 0xFF00BCD4
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        houseId = getIntent().getIntExtra("house_id", App.getInstance().getCurrentHouseId());

        layoutOverview = findViewById(R.id.layout_overview);
        layoutCategory = findViewById(R.id.layout_category);
        layoutSpace = findViewById(R.id.layout_space);
        layoutExpiring = findViewById(R.id.layout_expiring);
        layoutLowStock = findViewById(R.id.layout_low_stock);
        progress = findViewById(R.id.progress);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadAllStats();
    }

    private void loadAllStats() {
        progress.setVisibility(View.VISIBLE);
        loadOverview();
        loadByCategory();
        loadBySpace();
        loadExpiringList();
        loadLowStockList();
    }

    private void loadOverview() {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("stats.php?action=overview", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    renderOverview(data);
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> progress.setVisibility(View.GONE));
            }
        });
    }

    private void renderOverview(JsonObject data) {
        layoutOverview.removeAllViews();
        int totalItems = data.has("total_items") ? data.get("total_items").getAsInt() : 0;
        double totalValue = data.has("total_value") ? data.get("total_value").getAsDouble() : 0;
        int expired = data.has("expired") ? data.get("expired").getAsInt() : 0;
        int expiring = data.has("expiring_7days") ? data.get("expiring_7days").getAsInt() : 0;
        int lowStock = data.has("low_stock") ? data.get("low_stock").getAsInt() : 0;
        int pendingValue = data.has("pending_value") ? data.get("pending_value").getAsInt() : 0;
        int totalSpaces = data.has("total_spaces") ? data.get("total_spaces").getAsInt() : 0;

        // 第一行：物品总数 + 物品总价
        LinearLayout row1 = createRow();
        row1.addView(createStatCard("📦", "物品总数", totalItems + "件", 0xFF4A90D9));
        addSpacer(row1);
        String valueStr = totalValue >= 10000 ? String.format("%.0f", totalValue) : String.format("%.2f", totalValue);
        row1.addView(createStatCard("💰", "物品总价", "¥" + valueStr, 0xFF27AE60));
        layoutOverview.addView(row1);

        // 第二行：已过期 + 临期
        LinearLayout row2 = createRow();
        row2.addView(createStatCard("⏰", "已过期", expired + "件", 0xFFE74C3C));
        addSpacer(row2);
        row2.addView(createStatCard("⚠️", "7天内到期", expiring + "件", 0xFFF39C12));
        layoutOverview.addView(row2);

        // 第三行：库存不足 + 待估价
        LinearLayout row3 = createRow();
        row3.addView(createStatCard("📉", "库存不足", lowStock + "件", 0xFFE91E63));
        addSpacer(row3);
        row3.addView(createStatCard("❓", "待估价", pendingValue + "件", 0xFF9B59B6));
        layoutOverview.addView(row3);
    }

    private void loadByCategory() {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("stats.php?action=by_category", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> renderDistribution(layoutCategory, "📊 分类分布", data, "name", "count"));
            }
            @Override public void onError(String msg) {}
        });
    }

    private void loadBySpace() {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("stats.php?action=by_space", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> renderDistribution(layoutSpace, "🏠 空间分布", data, "name", "count"));
            }
            @Override public void onError(String msg) {}
        });
    }

    private void renderDistribution(LinearLayout container, String title, JsonObject data, String nameKey, String countKey) {
        container.removeAllViews();
        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
        if (list.size() == 0) return;

        // 标题
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFF2D3748);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(0, dp(16), 0, dp(12));
        container.addView(tvTitle);

        // 计算总数
        int total = 0;
        for (int i = 0; i < list.size(); i++) {
            total += list.get(i).getAsJsonObject().get(countKey).getAsInt();
        }

        // 饼图（用简单的横条代替）
        for (int i = 0; i < Math.min(list.size(), 8); i++) {
            JsonObject item = list.get(i).getAsJsonObject();
            String name = item.get(nameKey).getAsString();
            int count = item.get(countKey).getAsInt();
            float percent = total > 0 ? (float) count / total * 100 : 0;
            int color = COLORS[i % COLORS.length];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            // 色块
            View colorBlock = new View(this);
            LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(dp(12), dp(12));
            blockLp.setMarginEnd(dp(8));
            colorBlock.setLayoutParams(blockLp);
            colorBlock.setBackgroundColor(color);
            row.addView(colorBlock);

            // 名称
            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextSize(13);
            tvName.setTextColor(0xFF4A5568);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tvName.setLayoutParams(nameLp);
            row.addView(tvName);

            // 数量
            TextView tvCount = new TextView(this);
            tvCount.setText(count + "件");
            tvCount.setTextSize(13);
            tvCount.setTextColor(0xFF718096);
            tvCount.setPadding(dp(8), 0, 0, 0);
            row.addView(tvCount);

            // 百分比
            TextView tvPercent = new TextView(this);
            tvPercent.setText(String.format("%.0f%%", percent));
            tvPercent.setTextSize(13);
            tvPercent.setTextColor(color);
            tvPercent.setTypeface(null, Typeface.BOLD);
            tvPercent.setPadding(dp(8), 0, 0, 0);
            row.addView(tvPercent);

            container.addView(row);

            // 进度条
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress((int) percent);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
            barLp.setMarginStart(dp(20));
            barLp.bottomMargin = dp(4);
            bar.setLayoutParams(barLp);
            // 设置进度条颜色
            try {
                android.graphics.drawable.Drawable progressDrawable = bar.getProgressDrawable().mutate();
                progressDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                bar.setProgressDrawable(progressDrawable);
            } catch (Exception ignored) {}
            container.addView(bar);
        }
    }

    private void loadExpiringList() {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        params.put("days", "7");
        ApiClient.get("stats.php?action=expiring_list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> renderExpiringList(data));
            }
            @Override public void onError(String msg) {}
        });
    }

    private void renderExpiringList(JsonObject data) {
        layoutExpiring.removeAllViews();
        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
        if (list.size() == 0) return;

        TextView tvTitle = new TextView(this);
        tvTitle.setText("⏰ 临期物品");
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFF2D3748);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(0, dp(16), 0, dp(8));
        layoutExpiring.addView(tvTitle);

        for (int i = 0; i < list.size(); i++) {
            JsonObject item = list.get(i).getAsJsonObject();
            String name = item.get("name").getAsString();
            int daysLeft = item.get("days_left").getAsInt();
            String space = item.has("space_name") ? item.get("space_name").getAsString() : "";

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card_warm);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);
            row.setLayoutParams(rowLp);

            // 名称
            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextSize(14);
            tvName.setTextColor(0xFF2D3748);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tvName.setLayoutParams(nameLp);
            row.addView(tvName);

            // 剩余天数
            TextView tvDays = new TextView(this);
            tvDays.setText(daysLeft <= 0 ? "已过期" : daysLeft + "天");
            tvDays.setTextSize(13);
            tvDays.setTextColor(daysLeft <= 0 ? 0xFFF56565 : daysLeft <= 3 ? 0xFFED8936 : 0xFFF39C12);
            tvDays.setTypeface(null, Typeface.BOLD);
            row.addView(tvDays);

            layoutExpiring.addView(row);
        }
    }

    private void loadLowStockList() {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("stats.php?action=low_stock_list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> renderLowStockList(data));
            }
            @Override public void onError(String msg) {}
        });
    }

    private void renderLowStockList(JsonObject data) {
        layoutLowStock.removeAllViews();
        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
        if (list.size() == 0) return;

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📉 库存不足");
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFF2D3748);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(0, dp(16), 0, dp(8));
        layoutLowStock.addView(tvTitle);

        for (int i = 0; i < list.size(); i++) {
            JsonObject item = list.get(i).getAsJsonObject();
            String name = item.get("name").getAsString();
            double qty = item.get("quantity").getAsDouble();
            String unit = item.get("unit").getAsString();
            double threshold = item.get("stock_threshold").getAsDouble();

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card_warm);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);
            row.setLayoutParams(rowLp);

            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextSize(14);
            tvName.setTextColor(0xFF2D3748);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tvName.setLayoutParams(nameLp);
            row.addView(tvName);

            TextView tvStock = new TextView(this);
            tvStock.setText(String.format("%.0f/%.0f%s", qty, threshold, unit));
            tvStock.setTextSize(13);
            tvStock.setTextColor(0xFFE91E63);
            tvStock.setTypeface(null, Typeface.BOLD);
            row.addView(tvStock);

            layoutLowStock.addView(row);
        }
    }

    // ===== 工具方法 =====
    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        row.setLayoutParams(lp);
        return row;
    }

    private void addSpacer(LinearLayout row) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 0));
        row.addView(spacer);
    }

    private View createStatCard(String emoji, String label, String value, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_warm);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        card.setLayoutParams(cardLp);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(emoji);
        tvEmoji.setTextSize(20);
        card.addView(tvEmoji);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(20);
        tvValue.setTextColor(color);
        tvValue.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valLp.topMargin = dp(6);
        tvValue.setLayoutParams(valLp);
        card.addView(tvValue);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(0xFF718096);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(4);
        tvLabel.setLayoutParams(labelLp);
        card.addView(tvLabel);

        return card;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}

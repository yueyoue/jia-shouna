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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.text.SimpleDateFormat;
import java.util.*;

public class DocumentListActivity extends AppCompatActivity {
    private RecyclerView rvDocuments;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutCategories, layoutStats, layoutEmpty;
    private TextView tvStatTotal, tvStatExpiring, tvStatExpired;
    private String currentCategory = "";
    private JsonArray documents = new JsonArray();
    private DocumentAdapter adapter;

    private static final String[] CATEGORIES = {"全部", "证件", "合同", "票据", "保单", "房产", "车辆", "教育", "医疗", "其他"};
    private static final String[] CAT_ICONS = {"📁", "🪪", "📝", "🧾", "🛡️", "🏠", "🚗", "🎓", "🏥", "📦"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_list);

        rvDocuments = findViewById(R.id.rv_documents);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        layoutCategories = findViewById(R.id.layout_categories);
        layoutStats = findViewById(R.id.layout_stats);
        layoutEmpty = findViewById(R.id.layout_empty);
        tvStatTotal = findViewById(R.id.tv_stat_total);
        tvStatExpiring = findViewById(R.id.tv_stat_expiring);
        tvStatExpired = findViewById(R.id.tv_stat_expired);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add).setOnClickListener(v -> addDocument());

        swipeRefresh.setColorSchemeColors(0xFFC48F4E);
        swipeRefresh.setOnRefreshListener(this::loadDocuments);

        rvDocuments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DocumentAdapter();
        rvDocuments.setAdapter(adapter);

        setupCategories();
        loadDocuments();
        loadStats();
    }

    private void setupCategories() {
        layoutCategories.removeAllViews();
        for (int i = 0; i < CATEGORIES.length; i++) {
            final String cat = i == 0 ? "" : CATEGORIES[i];
            TextView tab = new TextView(this);
            tab.setText(CAT_ICONS[i] + " " + CATEGORIES[i]);
            tab.setTextSize(13);
            tab.setPadding(dp(14), dp(8), dp(14), dp(8));
            tab.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            tab.setLayoutParams(lp);

            boolean isActive = (cat.equals(currentCategory));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            if (isActive) {
                bg.setColor(0xFFC48F4E);
                tab.setTextColor(Color.WHITE);
            } else {
                bg.setColor(0xFFF7FAFC);
                bg.setStroke(dp(1), 0xFFE2E8F0);
                tab.setTextColor(0xFF718096);
            }
            tab.setBackground(bg);

            tab.setOnClickListener(v -> {
                currentCategory = cat;
                setupCategories();
                loadDocuments();
            });

            layoutCategories.addView(tab);
        }
    }

    private void loadDocuments() {
        int houseId = App.getInstance().getCurrentHouseId();
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        if (!currentCategory.isEmpty()) params.put("category", currentCategory);

        ApiClient.get("document.php?action=list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    try {
                        documents = data.has("list") && !data.get("list").isJsonNull()
                            ? data.getAsJsonArray("list") : new JsonArray();
                        adapter.notifyDataSetChanged();
                        boolean hasData = documents.size() > 0;
                        layoutEmpty.setVisibility(hasData ? View.GONE : View.VISIBLE);
                        swipeRefresh.setVisibility(hasData ? View.VISIBLE : View.GONE);
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(DocumentListActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadStats() {
        int houseId = App.getInstance().getCurrentHouseId();
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        ApiClient.get("document.php?action=stats", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        int total = data.has("total") ? data.get("total").getAsInt() : 0;
                        int expiring = data.has("expiring") ? data.get("expiring").getAsInt() : 0;
                        int expired = data.has("expired") ? data.get("expired").getAsInt() : 0;
                        tvStatTotal.setText("共 " + total + " 份");
                        tvStatExpiring.setText("⚠ 即将到期 " + expiring);
                        tvStatExpired.setText("🔴 已过期 " + expired);
                        layoutStats.setVisibility(View.VISIBLE);
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void addDocument() {
        Intent intent = new Intent(this, AddDocumentActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDocuments();
        loadStats();
    }

    // ===== RecyclerView Adapter =====
    private class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.VH> {
        @Override public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(DocumentListActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), 0xFFE2E8F0);
            card.setBackground(bg);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            card.setLayoutParams(lp);
            return new VH(card);
        }

        @Override public void onBindViewHolder(VH holder, int position) {
            JsonObject doc = documents.get(position).getAsJsonObject();
            LinearLayout card = (LinearLayout) holder.itemView;
            card.removeAllViews();

            String name = doc.has("name") ? doc.get("name").getAsString() : "";
            String category = doc.has("category") ? doc.get("category").getAsString() : "";
            String location = doc.has("storage_location") && !doc.get("storage_location").isJsonNull()
                ? doc.get("storage_location").getAsString() : "";
            String expiryStatus = doc.has("expiry_status") ? doc.get("expiry_status").getAsString() : "";
            int imageCount = doc.has("image_count") ? doc.get("image_count").getAsInt() : 0;

            // 左侧图标
            TextView icon = new TextView(DocumentListActivity.this);
            icon.setWidth(dp(48));
            icon.setHeight(dp(48));
            icon.setGravity(Gravity.CENTER);
            icon.setTextSize(22);
            icon.setText(getCategoryIcon(category));
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(dp(10));
            iconBg.setColor(getCategoryBgColor(category));
            icon.setBackground(iconBg);
            card.addView(icon);

            // 中间信息
            LinearLayout info = new LinearLayout(DocumentListActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            infoLp.leftMargin = dp(12);
            info.setLayoutParams(infoLp);

            TextView tvName = new TextView(DocumentListActivity.this);
            tvName.setText(name);
            tvName.setTextSize(14);
            tvName.setTextColor(0xFF2D3748);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setMaxLines(1);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(tvName);

            LinearLayout metaRow = new LinearLayout(DocumentListActivity.this);
            metaRow.setOrientation(LinearLayout.HORIZONTAL);
            metaRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvCat = new TextView(DocumentListActivity.this);
            tvCat.setText(category);
            tvCat.setTextSize(11);
            tvCat.setTextColor(0xFF718096);
            tvCat.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable catBg = new GradientDrawable();
            catBg.setCornerRadius(dp(4));
            catBg.setColor(0xFFF7FAFC);
            tvCat.setBackground(catBg);
            metaRow.addView(tvCat);

            if (!location.isEmpty()) {
                TextView tvLoc = new TextView(DocumentListActivity.this);
                tvLoc.setText(" 📍" + location);
                tvLoc.setTextSize(11);
                tvLoc.setTextColor(0xFFA0AEC0);
                metaRow.addView(tvLoc);
            }

            info.addView(metaRow);

            // 到期状态
            if (!expiryStatus.isEmpty() && !expiryStatus.equals("valid")) {
                TextView tvExpiry = new TextView(DocumentListActivity.this);
                int daysLeft = doc.has("days_left") ? doc.get("days_left").getAsInt() : 0;
                if (expiryStatus.equals("expired")) {
                    tvExpiry.setText("🔴 已过期 " + Math.abs(daysLeft) + " 天");
                    tvExpiry.setTextColor(0xFFF56565);
                } else if (expiryStatus.equals("expiring")) {
                    tvExpiry.setText("🟠 " + daysLeft + " 天后到期");
                    tvExpiry.setTextColor(0xFFED8936);
                } else {
                    tvExpiry.setText("🟡 " + daysLeft + " 天后到期");
                    tvExpiry.setTextColor(0xFFECC94B);
                }
                tvExpiry.setTextSize(11);
                tvExpiry.setTypeface(null, Typeface.BOLD);
                info.addView(tvExpiry);
            }

            card.addView(info);

            // 右侧
            if (imageCount > 0) {
                TextView tvImg = new TextView(DocumentListActivity.this);
                tvImg.setText("📎" + imageCount);
                tvImg.setTextSize(12);
                tvImg.setTextColor(0xFFA0AEC0);
                card.addView(tvImg);
            }

            // 点击跳转详情
            int docId = doc.has("id") ? doc.get("id").getAsInt() : 0;
            final int fDocId = docId;
            card.setOnClickListener(v -> {
                Intent intent = new Intent(DocumentListActivity.this, DocumentDetailActivity.class);
                intent.putExtra("document_id", fDocId);
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return documents.size(); }

        class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }

    private String getCategoryIcon(String cat) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(cat)) return CAT_ICONS[i];
        }
        return "📦";
    }

    private int getCategoryBgColor(String cat) {
        switch (cat) {
            case "证件": return 0xFFEDF2F7;
            case "合同": return 0xFFFFFAF0;
            case "票据": return 0xFFF0FFF4;
            case "保单": return 0xFFEBF8FF;
            case "房产": return 0xFFFAF5FF;
            case "车辆": return 0xFFFFF5F5;
            case "教育": return 0xFFFFFAF0;
            case "医疗": return 0xFFF0FFF4;
            default: return 0xFFF7FAFC;
        }
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

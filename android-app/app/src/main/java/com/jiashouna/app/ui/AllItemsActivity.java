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
import java.util.*;

public class AllItemsActivity extends AppCompatActivity {
    private static final String TAG = "AllItemsActivity";
    private EditText etSearch;
    private LinearLayout layoutItems, layoutEmpty;
    private ProgressBar progress;
    private TextView tvCount, tvTitle, tvDebugHint;
    private int houseId = 0;
    private String filterType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_items);

        houseId = getIntent().getIntExtra("house_id", App.getInstance().getCurrentHouseId());
        filterType = getIntent().getStringExtra("filter_type") != null ? getIntent().getStringExtra("filter_type") : "";

        etSearch = findViewById(R.id.et_search);
        layoutItems = findViewById(R.id.layout_items);
        layoutEmpty = findViewById(R.id.layout_empty);
        progress = findViewById(R.id.progress);
        tvCount = findViewById(R.id.tv_count);
        tvTitle = findViewById(R.id.tv_title);

        // Debug hint (hidden by default)
        tvDebugHint = new TextView(this);
        tvDebugHint.setTextSize(11);
        tvDebugHint.setTextColor(0xFFA0AEC0);
        tvDebugHint.setPadding(dp(16), dp(4), dp(16), dp(4));
        tvDebugHint.setVisibility(View.GONE);

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
        String kw = etSearch.getText().toString().trim();
        loadItems(kw);
    }

    private void loadItems(String keyword) {
        progress.setVisibility(View.VISIBLE);
        layoutItems.removeAllViews();
        layoutEmpty.setVisibility(View.GONE);
        tvDebugHint.setVisibility(View.GONE);

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

        ApiClient.get(endpoint, params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                String respStr = data.toString();
                Log.d(TAG, "API success: " + respStr.substring(0, Math.min(300, respStr.length())));
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    try {
                        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
                        int total = data.has("total") ? data.get("total").getAsInt() : list.size();
                        Log.d(TAG, "Items loaded: list.size=" + list.size() + ", total=" + total);
                        tvCount.setText(list.size() + " 件");
                        if (list.size() == 0) {
                            // 显示API返回的调试信息
                            String debugText = "list.size=0, total=" + total;
                            if (data.has("debug") && !data.get("debug").isJsonNull()) {
                                JsonObject dbg = data.getAsJsonObject("debug");
                                debugText += "\nhouseId=" + (dbg.has("resolved_house_id") ? dbg.get("resolved_house_id").getAsInt() : "?");
                                debugText += " userId=" + (dbg.has("user_id") ? dbg.get("user_id").getAsInt() : "?");
                                if (dbg.has("all_goods_count")) debugText += "\n全库物品=" + dbg.get("all_goods_count").getAsInt();
                                if (dbg.has("user_house_count")) debugText += " 用户房屋=" + dbg.get("user_house_count").getAsInt();
                                if (dbg.has("user_created_house_count")) debugText += " 创建房屋=" + dbg.get("user_created_house_count").getAsInt();
                            }
                            tvDebugHint.setText(debugText);
                            tvDebugHint.setVisibility(View.VISIBLE);
                            loadItemsFallback(keyword);
                        } else {
                            for (int i = 0; i < list.size(); i++) {
                                layoutItems.addView(createItemRow(list.get(i).getAsJsonObject(), i < list.size() - 1));
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
                    progress.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                    tvDebugHint.setText("⚠ 加载失败: " + msg + "\nURL: goods.php?action=list&house_id=" + houseId);
                    tvDebugHint.setVisibility(View.VISIBLE);
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
                            tvDebugHint.setText("提示: 当前家庭暂无物品，请先录入");
                            tvDebugHint.setVisibility(View.VISIBLE);
                        } else {
                            for (int i = 0; i < list.size(); i++) {
                                layoutItems.addView(createItemRow(list.get(i).getAsJsonObject(), i < list.size() - 1));
                            }
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
                    tvDebugHint.setText("⚠ 加载失败: " + msg + "\n请检查网络连接或重新登录");
                    tvDebugHint.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private View createItemRow(JsonObject item, boolean showDivider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackgroundColor(Color.WHITE);

        // Icon
        TextView icon = new TextView(this);
        String category = item.has("category") && !item.get("category").isJsonNull() ? item.get("category").getAsString() : "";
        String iconStr = getCategoryIcon(category);
        icon.setText(iconStr);
        icon.setTextSize(20);
        icon.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable iconBg = new android.graphics.drawable.GradientDrawable();
        iconBg.setCornerRadius(dp(10));
        iconBg.setColor(0xFFFFE8D6);
        icon.setBackground(iconBg);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        row.addView(icon);

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

        int qty = item.has("quantity") ? item.get("quantity").getAsInt() : 0;
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

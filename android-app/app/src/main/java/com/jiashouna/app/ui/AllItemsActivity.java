package com.jiashouna.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
    private EditText etSearch;
    private LinearLayout layoutItems, layoutEmpty;
    private ProgressBar progress;
    private TextView tvCount, tvTitle;
    private int houseId = 0;
    private String filterType = ""; // "expiring", "low_stock", etc.

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
                if (kw.length() >= 1 || kw.isEmpty()) {
                    loadItems(kw);
                }
            }
        });

        if (focusSearch) {
            etSearch.requestFocus();
            etSearch.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearch, 0);
            }, 200);
        }

        if (initKeyword != null && !initKeyword.isEmpty()) {
            loadItems(initKeyword);
        } else {
            loadItems("");
        }
    }

    private void loadItems(String keyword) {
        progress.setVisibility(View.VISIBLE);
        layoutItems.removeAllViews();
        layoutEmpty.setVisibility(View.GONE);

        if (houseId <= 0) {
            houseId = App.getInstance().getCurrentHouseId();
        }
        if (houseId <= 0) {
            progress.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        params.put("page_size", "100");
        if (keyword != null && !keyword.isEmpty()) {
            try {
                params.put("keyword", java.net.URLEncoder.encode(keyword, "UTF-8"));
            } catch (Exception e) {
                params.put("keyword", keyword);
            }
        }

        String endpoint = "goods.php?action=list";
        if ("expiring".equals(filterType)) {
            endpoint = "goods.php?action=expiring";
            params.put("days", "7");
        }

        ApiClient.get(endpoint, params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    try {
                        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
                        tvCount.setText(list.size() + " 件");
                        if (list.size() == 0) {
                            layoutEmpty.setVisibility(View.VISIBLE);
                        } else {
                            for (int i = 0; i < list.size(); i++) {
                                layoutItems.addView(createItemRow(list.get(i).getAsJsonObject(), i < list.size() - 1));
                            }
                        }
                    } catch (Exception e) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
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
        String iconStr = item.has("icon") && !item.get("icon").isJsonNull() ? item.get("icon").getAsString() : "📦";
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

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

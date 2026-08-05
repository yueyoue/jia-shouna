package com.jiashouna.app.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.util.HashMap;

public class RemindersFragment extends Fragment {

    // Filter tabs
    private TextView tabAll, tabExpiring, tabExpired, tabLowstock, tabCustom, tabLend;
    private int currentTab = 0; // 0=all, 1=expiring, 2=expired, 3=lowstock, 4=custom, 5=lend

    // Sections
    private LinearLayout layoutExpiringSection, layoutExpiredSection, layoutLowstockSection, layoutCustomSection, layoutLendSection;

    // Lists
    private LinearLayout llExpiringList, llExpiredList, llLowstockList, llCustomList, llLendList;

    // Count badge
    private TextView tvExpiringCount;

    // Data
    private JsonArray allExpiring = new JsonArray();
    private JsonArray allExpired = new JsonArray();
    private JsonArray allLowstock = new JsonArray();
    private JsonArray allCustom = new JsonArray();
    private JsonArray allLend = new JsonArray();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_reminders, container, false);

        // Filter tabs
        tabAll = v.findViewById(R.id.tab_all);
        tabExpiring = v.findViewById(R.id.tab_expiring);
        tabExpired = v.findViewById(R.id.tab_expired);
        tabLowstock = v.findViewById(R.id.tab_lowstock);
        tabCustom = v.findViewById(R.id.tab_custom);

        // Sections
        layoutExpiringSection = v.findViewById(R.id.layout_expiring_section);
        layoutExpiredSection = v.findViewById(R.id.layout_expired_section);
        layoutLowstockSection = v.findViewById(R.id.layout_lowstock_section);
        layoutCustomSection = v.findViewById(R.id.layout_custom_section);

        // Lists
        llExpiringList = v.findViewById(R.id.ll_expiring_list);
        llExpiredList = v.findViewById(R.id.ll_expired_list);
        llLowstockList = v.findViewById(R.id.ll_lowstock_list);
        llCustomList = v.findViewById(R.id.ll_custom_list);

        // Lend section
        tabLend = v.findViewById(R.id.tab_lend);
        layoutLendSection = v.findViewById(R.id.layout_lend_section);
        llLendList = v.findViewById(R.id.ll_lend_list);

        // Count badge
        tvExpiringCount = v.findViewById(R.id.tv_expiring_count);

        // Tab click listeners
        tabAll.setOnClickListener(view -> switchTab(0));
        tabExpiring.setOnClickListener(view -> switchTab(1));
        tabExpired.setOnClickListener(view -> switchTab(2));
        tabLowstock.setOnClickListener(view -> switchTab(3));
        tabCustom.setOnClickListener(view -> switchTab(4));
        tabLend.setOnClickListener(view -> switchTab(5));

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    // ===== Tab Switching =====

    private void switchTab(int tab) {
        currentTab = tab;
        updateTabStyles();
        updateSectionVisibility();
    }

    private void updateTabStyles() {
        TextView[] tabs = {tabAll, tabExpiring, tabExpired, tabLowstock, tabCustom, tabLend};
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == null) continue;
            if (i == currentTab) {
                tabs[i].setBackgroundResource(R.drawable.bg_filter_chip_active);
                tabs[i].setTextColor(Color.WHITE);
            } else {
                tabs[i].setBackgroundResource(R.drawable.bg_filter_chip_inactive);
                tabs[i].setTextColor(Color.parseColor("#718096"));
            }
        }
    }

    private void updateSectionVisibility() {
        layoutExpiringSection.setVisibility(currentTab == 0 || currentTab == 1 ? View.VISIBLE : View.GONE);
        layoutExpiredSection.setVisibility(currentTab == 0 || currentTab == 2 ? View.VISIBLE : View.GONE);
        layoutLowstockSection.setVisibility(currentTab == 0 || currentTab == 3 ? View.VISIBLE : View.GONE);
        layoutCustomSection.setVisibility(currentTab == 0 || currentTab == 4 ? View.VISIBLE : View.GONE);
        if (layoutLendSection != null) layoutLendSection.setVisibility(currentTab == 5 ? View.VISIBLE : View.GONE);
    }

    // ===== Data Loading =====

    private void loadData() {
        int houseId = App.getInstance().getCurrentHouseId();
        android.util.Log.d("Reminders", "loadData houseId=" + houseId + " token=" + (App.getInstance().getToken().isEmpty() ? "EMPTY" : "OK"));
        if (houseId <= 0) {
            android.util.Log.w("Reminders", "houseId<=0, showing empty");
            showEmptyInAll("请先创建或加入一个家庭");
            return;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        // Load stats
        ApiClient.get("reminder.php?action=stats", params, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                android.util.Log.d("Reminders", "Stats OK: " + data.toString().substring(0, Math.min(200, data.toString().length())));
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        int expiry = 0;
                        if (data.has("expiring_count")) {
                            expiry = data.get("expiring_count").getAsInt();
                        } else if (data.has("stats") && !data.get("stats").isJsonNull()) {
                            JsonObject stats = data.getAsJsonObject("stats");
                            expiry = stats.has("expiring_7days") ? stats.get("expiring_7days").getAsInt() : 0;
                        }
                        tvExpiringCount.setText(String.valueOf(expiry));
                    } catch (Exception e) {
                        android.util.Log.e("Reminders", "Stats parse error", e);
                        tvExpiringCount.setText("0");
                    }
                });
            }

            @Override
            public void onError(String msg) {
                android.util.Log.e("Reminders", "Stats error: " + msg);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> tvExpiringCount.setText("0"));
                }
            }
        });

        // Load reminder list
        showLoading();
        ApiClient.get("reminder.php?action=list", params, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                android.util.Log.d("Reminders", "List OK keys=" + data.keySet() + " raw=" + data.toString().substring(0, Math.min(300, data.toString().length())));
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        allExpiring = new JsonArray();
                        allExpired = new JsonArray();
                        allLowstock = new JsonArray();
                        allCustom = new JsonArray();

                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            JsonArray list = data.getAsJsonArray("list");
                            android.util.Log.d("Reminders", "List size=" + list.size());
                            for (int i = 0; i < list.size(); i++) {
                                JsonObject item = list.get(i).getAsJsonObject();
                                String type = item.has("type") ? item.get("type").getAsString() : "custom";
                                // 兼容：如果API返回space_name但没有location，自动补充
                                if (!item.has("location") && item.has("space_name") && !item.get("space_name").isJsonNull()) {
                                    item.addProperty("location", item.get("space_name").getAsString());
                                }
                                // 兼容：如果API返回quantity但没有current_qty，自动补充
                                if (!item.has("current_qty") && item.has("quantity") && !item.get("quantity").isJsonNull()) {
                                    item.add("current_qty", item.get("quantity"));
                                }
                                switch (type) {
                                    case "expiry":
                                        // 区分已过期和临期
                                        int daysLeft = item.has("days_left") ? item.get("days_left").getAsInt() : 999;
                                        if (daysLeft < 0) {
                                            allExpired.add(item);
                                        } else {
                                            allExpiring.add(item);
                                        }
                                        break;
                                    case "low_stock":
                                        allLowstock.add(item);
                                        break;
                                    default:
                                        allCustom.add(item);
                                        break;
                                }
                            }
                        } else {
                            android.util.Log.w("Reminders", "No 'list' key in data. Keys: " + data.keySet());
                        }

                        android.util.Log.d("Reminders", "Final counts: expiring=" + allExpiring.size() + " lowstock=" + allLowstock.size() + " custom=" + allCustom.size());
                        renderExpiring();
                        renderExpired();
                        renderLowstock();
                        renderCustom();
                        loadLendData();
                        updateSectionVisibility();
                    } catch (Exception e) {
                        android.util.Log.e("Reminders", "List parse error", e);
                        showEmptyInAll("数据解析异常，请重试");
                    }
                });
            }

            @Override
            public void onError(String msg) {
                android.util.Log.e("Reminders", "List error: " + msg);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String displayMsg = "加载失败，请重试";
                        if (msg != null && msg.contains("请先登录")) {
                            displayMsg = "登录已过期，请重新登录";
                        } else if (msg != null && msg.contains("网络")) {
                            displayMsg = "网络连接失败，请检查网络";
                        }
                        showEmptyInAll(displayMsg);
                    });
                }
            }
        });
    }

    private void showLoading() {
        llExpiringList.removeAllViews();
        llExpiredList.removeAllViews();
        llLowstockList.removeAllViews();
        llCustomList.removeAllViews();
        if (llLendList != null) llLendList.removeAllViews();
        addLoadingHint(llExpiringList);
        addLoadingHint(llExpiredList);
        addLoadingHint(llLowstockList);
        addLoadingHint(llCustomList);
    }

    private void showEmptyInAll(String msg) {
        llExpiringList.removeAllViews();
        llExpiredList.removeAllViews();
        llLowstockList.removeAllViews();
        llCustomList.removeAllViews();
        if (llLendList != null) llLendList.removeAllViews();
        addEmptyHint(llExpiringList, msg);
        addEmptyHint(llExpiredList, msg);
        addEmptyHint(llLowstockList, msg);
        addEmptyHint(llCustomList, msg);
    }

    // ===== Render Expiring Section =====

    private void renderExpiring() {
        llExpiringList.removeAllViews();
        if (allExpiring.size() == 0) {
            addEmptyHint(llExpiringList, "暂无临期物品 👍");
            return;
        }
        for (int i = 0; i < allExpiring.size(); i++) {
            JsonObject item = allExpiring.get(i).getAsJsonObject();
            llExpiringList.addView(createExpiringCard(item));
        }
    }

    private void renderExpired() {
        llExpiredList.removeAllViews();
        if (allExpired.size() == 0) {
            addEmptyHint(llExpiredList, "暂无已过期物品 👍");
            return;
        }
        for (int i = 0; i < allExpired.size(); i++) {
            JsonObject item = allExpired.get(i).getAsJsonObject();
            llExpiredList.addView(createExpiringCard(item));
        }
    }

    private View createExpiringCard(JsonObject item) {
        Context ctx = getActivity();
        if (ctx == null) return new View(getContext());

        int daysLeft = item.has("days_left") && !item.get("days_left").isJsonNull()
                ? item.get("days_left").getAsInt() : 0;
        boolean isExpired = daysLeft < 0;

        // Card container with left border
        LinearLayout cardWrapper = new LinearLayout(ctx);
        cardWrapper.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapperLp.bottomMargin = dp(8);
        cardWrapper.setLayoutParams(wrapperLp);
        cardWrapper.setElevation(dp(2));
        GradientDrawable wrapperBg = new GradientDrawable();
        wrapperBg.setColor(Color.WHITE);
        wrapperBg.setCornerRadius(dp(12));
        cardWrapper.setBackground(wrapperBg);

        // Left border strip
        View leftBorder = new View(ctx);
        LinearLayout.LayoutParams borderLp = new LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT);
        leftBorder.setLayoutParams(borderLp);
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setColor(isExpired ? Color.parseColor("#F56565") : Color.parseColor("#ED8936"));
        float[] radii = {dp(12), dp(12), 0, 0, 0, 0, dp(12), dp(12)};
        borderDrawable.setCornerRadii(radii);
        leftBorder.setBackground(borderDrawable);
        cardWrapper.addView(leftBorder);

        // Inner content
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        card.setLayoutParams(cardLp);

        // Thumbnail / Emoji placeholder
        TextView thumb = new TextView(ctx);
        thumb.setWidth(dp(48));
        thumb.setHeight(dp(48));
        thumb.setGravity(Gravity.CENTER);
        thumb.setTextSize(22);
        String goodsName = item.has("goods_name") && !item.get("goods_name").isJsonNull()
                ? item.get("goods_name").getAsString() : "";
        String emoji = getCategoryEmoji(item);
        thumb.setText(emoji);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(Color.parseColor("#FFF5F5"));
        thumbBg.setCornerRadius(dp(8));
        thumb.setBackground(thumbBg);
        card.addView(thumb);

        // Info column
        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.leftMargin = dp(10);
        info.setLayoutParams(infoLp);

        // Name
        TextView name = new TextView(ctx);
        name.setText(goodsName);
        name.setTextSize(14);
        name.setTextColor(Color.parseColor("#2D3748"));
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(name);

        // Status tag
        TextView status = new TextView(ctx);
        if (isExpired) {
            status.setText("已过期 " + Math.abs(daysLeft) + " 天");
            status.setTextColor(Color.parseColor("#F56565"));
        } else if (daysLeft == 0) {
            status.setText("今天到期");
            status.setTextColor(Color.parseColor("#F56565"));
        } else {
            status.setText(daysLeft + " 天后过期");
            status.setTextColor(Color.parseColor("#ED8936"));
        }
        status.setTextSize(12);
        status.setTypeface(null, Typeface.BOLD);
        info.addView(status);

        // Location
        if (item.has("location") && !item.get("location").isJsonNull()) {
            String loc = item.get("location").getAsString();
            if (!loc.isEmpty()) {
                TextView locTv = new TextView(ctx);
                locTv.setText("📍 " + loc);
                locTv.setTextSize(11);
                locTv.setTextColor(Color.parseColor("#A0AEC0"));
                info.addView(locTv);
            }
        }

        card.addView(info);

        // Handle button
        TextView btnHandle = new TextView(ctx);
        btnHandle.setText("标记已处理");
        btnHandle.setTextSize(11);
        btnHandle.setTextColor(Color.parseColor("#9B4500"));
        btnHandle.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(Color.TRANSPARENT);
        handleBg.setStroke(dp(1), Color.parseColor("#9B4500"));
        handleBg.setCornerRadius(dp(16));
        btnHandle.setBackground(handleBg);
        card.addView(btnHandle);

        // Click to detail
        if (item.has("goods_id") && !item.get("goods_id").isJsonNull()) {
            try {
                int goodsId = item.get("goods_id").getAsInt();
                if (goodsId > 0) {
                    cardWrapper.setClickable(true);
                    cardWrapper.setFocusable(true);
                    cardWrapper.setOnClickListener(v -> {
                        Intent intent = new Intent(getActivity(), com.jiashouna.app.ui.ItemDetailActivity.class);
                        intent.putExtra("goods_id", goodsId);
                        startActivity(intent);
                    });
                }
            } catch (Exception ignored) {}
        }

        cardWrapper.addView(card);

        // Handle button click - auto-generated reminders have string IDs like "exp_3"
        int parsedItemId = 0;
        try {
            String idStr = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : "0";
            parsedItemId = Integer.parseInt(idStr);
        } catch (NumberFormatException ignored) {}
        final int itemId = parsedItemId;
        if (itemId > 0) {
            btnHandle.setOnClickListener(v -> markHandled(itemId, cardWrapper, llExpiringList));
        } else {
            btnHandle.setVisibility(View.GONE);
        }

        return cardWrapper;
    }

    // ===== Render Low Stock Section =====

    private void renderLowstock() {
        llLowstockList.removeAllViews();
        if (allLowstock.size() == 0) {
            addEmptyHint(llLowstockList, "库存充足 👍");
            return;
        }
        for (int i = 0; i < allLowstock.size(); i++) {
            JsonObject item = allLowstock.get(i).getAsJsonObject();
            llLowstockList.addView(createLowstockCard(item));
        }
    }

    private View createLowstockCard(JsonObject item) {
        Context ctx = getActivity();
        if (ctx == null) return new View(getContext());

        // Card container
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_lowstock_card);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(8);
        card.setLayoutParams(cardLp);

        // Emoji icon
        TextView icon = new TextView(ctx);
        icon.setWidth(dp(48));
        icon.setHeight(dp(48));
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(22);
        icon.setText(getCategoryEmoji(item));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.parseColor("#EBF8FF"));
        iconBg.setCornerRadius(dp(8));
        icon.setBackground(iconBg);
        card.addView(icon);

        // Info column
        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.leftMargin = dp(10);
        info.setLayoutParams(infoLp);

        // Name
        String goodsName = item.has("goods_name") && !item.get("goods_name").isJsonNull()
                ? item.get("goods_name").getAsString() : "";
        TextView name = new TextView(ctx);
        name.setText(goodsName);
        name.setTextSize(14);
        name.setTextColor(Color.parseColor("#2D3748"));
        name.setTypeface(null, Typeface.BOLD);
        info.addView(name);

        // Progress bar - current_qty/threshold are DECIMAL, may be "1.00" string
        int current = 0;
        int threshold = 1;
        try {
            if (item.has("current_qty") && !item.get("current_qty").isJsonNull()) {
                current = (int) item.get("current_qty").getAsDouble();
            }
        } catch (Exception ignored) {}
        try {
            if (item.has("threshold") && !item.get("threshold").isJsonNull()) {
                threshold = (int) item.get("threshold").getAsDouble();
            }
        } catch (Exception ignored) {}
        float ratio = threshold > 0 ? Math.min(1f, (float) current / threshold) : 0;

        FrameLayout progressContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressLp.topMargin = dp(6);
        progressContainer.setLayoutParams(progressLp);

        // Background bar
        View bgBar = new View(ctx);
        bgBar.setBackgroundResource(R.drawable.bg_progress_bar);
        progressContainer.addView(bgBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Fill bar
        View fillBar = new View(ctx);
        fillBar.setBackgroundResource(R.drawable.bg_progress_fill);
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT);
        // We need to set width after layout, so use a runnable
        final float finalRatio = ratio;
        progressContainer.post(() -> {
            int totalWidth = progressContainer.getWidth();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fillBar.getLayoutParams();
            lp.width = (int) (totalWidth * finalRatio);
            fillBar.setLayoutParams(lp);
        });
        progressContainer.addView(fillBar);

        info.addView(progressContainer);

        // Stock text
        TextView stockText = new TextView(ctx);
        stockText.setText("当前库存: " + current + " / 阈值: " + threshold);
        stockText.setTextSize(11);
        stockText.setTextColor(Color.parseColor("#718096"));
        LinearLayout.LayoutParams stockLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stockLp.topMargin = dp(4);
        stockText.setLayoutParams(stockLp);
        info.addView(stockText);

        card.addView(info);

        // Cart button
        TextView btnCart = new TextView(ctx);
        btnCart.setText("🛒");
        btnCart.setTextSize(18);
        btnCart.setGravity(Gravity.CENTER);
        btnCart.setWidth(dp(40));
        btnCart.setHeight(dp(40));
        GradientDrawable cartBg = new GradientDrawable();
        cartBg.setColor(Color.parseColor("#9B4500"));
        cartBg.setCornerRadius(dp(20));
        btnCart.setBackground(cartBg);
        card.addView(btnCart);

        // Click to detail
        if (item.has("goods_id") && !item.get("goods_id").isJsonNull()) {
            try {
                int goodsId = item.get("goods_id").getAsInt();
                if (goodsId > 0) {
                    card.setClickable(true);
                    card.setFocusable(true);
                    card.setOnClickListener(v -> {
                        Intent intent = new Intent(getActivity(), com.jiashouna.app.ui.ItemDetailActivity.class);
                        intent.putExtra("goods_id", goodsId);
                        startActivity(intent);
                    });
                }
            } catch (Exception ignored) {}
        }

        return card;
    }

    // ===== Render Custom Section =====

    private void renderCustom() {
        llCustomList.removeAllViews();
        if (allCustom.size() == 0) {
            addEmptyHint(llCustomList, "暂无自定义提醒\n点击「+ 新增提醒」创建");
            return;
        }
        for (int i = 0; i < allCustom.size(); i++) {
            JsonObject item = allCustom.get(i).getAsJsonObject();
            llCustomList.addView(createCustomCard(item));
        }
    }

    private View createCustomCard(JsonObject item) {
        Context ctx = getActivity();
        if (ctx == null) return new View(getContext());

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_lowstock_card);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(8);
        card.setLayoutParams(cardLp);
        // Border
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.WHITE);
        border.setCornerRadius(dp(12));
        border.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(border);

        // Info column
        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        info.setLayoutParams(infoLp);

        // Title
        String title = item.has("title") && !item.get("title").isJsonNull()
                ? item.get("title").getAsString() : "";
        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextSize(14);
        titleTv.setTextColor(Color.parseColor("#2D3748"));
        titleTv.setTypeface(null, Typeface.BOLD);
        info.addView(titleTv);

        // Time / repeat rule
        String timeText = "";
        if (item.has("remind_time") && !item.get("remind_time").isJsonNull()) {
            timeText = item.get("remind_time").getAsString();
        }
        if (item.has("repeat_rule") && !item.get("repeat_rule").isJsonNull()) {
            String rule = item.get("repeat_rule").getAsString();
            if (!rule.isEmpty()) {
                timeText = timeText.isEmpty() ? rule : timeText + " · " + rule;
            }
        }
        if (timeText.isEmpty() && item.has("content") && !item.get("content").isJsonNull()) {
            timeText = item.get("content").getAsString();
        }
        if (!timeText.isEmpty()) {
            TextView timeTv = new TextView(ctx);
            timeTv.setText("⏰ " + timeText);
            timeTv.setTextSize(12);
            timeTv.setTextColor(Color.parseColor("#718096"));
            info.addView(timeTv);
        }

        card.addView(info);

        // Arrow
        TextView arrow = new TextView(ctx);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(Color.parseColor("#A0AEC0"));
        card.addView(arrow);

        // Click to detail if goods_id exists
        if (item.has("goods_id") && !item.get("goods_id").isJsonNull()) {
            try {
                int goodsId = item.get("goods_id").getAsInt();
                if (goodsId > 0) {
                    card.setClickable(true);
                    card.setFocusable(true);
                    card.setOnClickListener(v -> {
                        Intent intent = new Intent(getActivity(), com.jiashouna.app.ui.ItemDetailActivity.class);
                        intent.putExtra("goods_id", goodsId);
                        startActivity(intent);
                    });
                }
            } catch (Exception ignored) {}
        }

        return card;
    }

    // ===== Load Lend Data =====

    private void loadLendData() {
        if (llLendList == null) return;
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) return;

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        ApiClient.get("goods.php?action=borrowList", params, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        allLend = new JsonArray();
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            JsonArray list = data.getAsJsonArray("list");
                            for (int i = 0; i < list.size(); i++) {
                                JsonObject item = list.get(i).getAsJsonObject();
                                // 只显示有借出对象的记录
                                String lendTo = item.has("lend_to") && !item.get("lend_to").isJsonNull()
                                    ? item.get("lend_to").getAsString() : "";
                                if (!lendTo.isEmpty()) {
                                    allLend.add(item);
                                }
                            }
                        }
                        renderLend();
                    } catch (Exception e) {
                        android.util.Log.e("Reminders", "Lend parse error", e);
                    }
                });
            }

            @Override
            public void onError(String msg) {
                android.util.Log.e("Reminders", "Lend error: " + msg);
            }
        });
    }

    private void renderLend() {
        if (llLendList == null) return;
        llLendList.removeAllViews();
        if (allLend.size() == 0) {
            addEmptyHint(llLendList, "暂无借出物品 👍");
            return;
        }
        for (int i = 0; i < allLend.size(); i++) {
            JsonObject item = allLend.get(i).getAsJsonObject();
            llLendList.addView(createLendCard(item));
        }
    }

    private View createLendCard(JsonObject item) {
        Context ctx = getActivity();
        if (ctx == null) return new View(getContext());

        String goodsName = item.has("goods_name") && !item.get("goods_name").isJsonNull()
            ? item.get("goods_name").getAsString() : "";
        String lendTo = item.has("lend_to") && !item.get("lend_to").isJsonNull()
            ? item.get("lend_to").getAsString() : "";
        double qty = item.has("quantity") ? item.get("quantity").getAsDouble() : 1;
        long borrowTime = item.has("borrow_time") ? item.get("borrow_time").getAsLong() : 0;
        int daysSince = borrowTime > 0 ? (int) ((System.currentTimeMillis() / 1000 - borrowTime) / 86400) : 0;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(8);
        card.setLayoutParams(cardLp);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(12));
        cardBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        card.setBackground(cardBg);

        // 左侧图标
        TextView icon = new TextView(ctx);
        icon.setWidth(dp(48));
        icon.setHeight(dp(48));
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(22);
        icon.setText("📤");
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.parseColor("#FFFAF0"));
        iconBg.setCornerRadius(dp(8));
        icon.setBackground(iconBg);
        card.addView(icon);

        // 信息
        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.leftMargin = dp(10);
        info.setLayoutParams(infoLp);

        TextView nameTv = new TextView(ctx);
        nameTv.setText(goodsName);
        nameTv.setTextSize(14);
        nameTv.setTextColor(Color.parseColor("#2D3748"));
        nameTv.setTypeface(null, Typeface.BOLD);
        info.addView(nameTv);

        TextView lendToTv = new TextView(ctx);
        lendToTv.setText("借给: " + lendTo + " · " + (int) qty + "件");
        lendToTv.setTextSize(12);
        lendToTv.setTextColor(Color.parseColor("#718096"));
        info.addView(lendToTv);

        TextView timeTv = new TextView(ctx);
        timeTv.setText("已借 " + daysSince + " 天");
        timeTv.setTextSize(11);
        timeTv.setTextColor(daysSince > 30 ? Color.parseColor("#F56565") : Color.parseColor("#A0AEC0"));
        info.addView(timeTv);

        card.addView(info);

        // 归还按钮
        TextView btnReturn = new TextView(ctx);
        btnReturn.setText("归还");
        btnReturn.setTextSize(12);
        btnReturn.setTextColor(Color.parseColor("#38A169"));
        btnReturn.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable retBg = new GradientDrawable();
        retBg.setColor(Color.TRANSPARENT);
        retBg.setStroke(dp(1), Color.parseColor("#38A169"));
        retBg.setCornerRadius(dp(16));
        btnReturn.setBackground(retBg);
        card.addView(btnReturn);

        // 归还点击
        int borrowId = item.has("id") ? item.get("id").getAsInt() : 0;
        final int goodsId = item.has("goods_id") ? item.get("goods_id").getAsInt() : 0;
        if (borrowId > 0) {
            btnReturn.setOnClickListener(v -> {
                JsonObject body = new JsonObject();
                body.addProperty("borrow_id", borrowId);
                ApiClient.post("goods.php?action=return", body, new ApiClient.ApiCallback() {
                    @Override public void onSuccess(JsonObject data) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getActivity(), "✅ 已归还", Toast.LENGTH_SHORT).show();
                            loadLendData();
                        });
                    }
                    @Override public void onError(String msg) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "归还失败: " + msg, Toast.LENGTH_SHORT).show());
                        }
                    }
                });
            });
        }

        // 点击跳转详情
        if (goodsId > 0) {
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), com.jiashouna.app.ui.ItemDetailActivity.class);
                intent.putExtra("goods_id", goodsId);
                startActivity(intent);
            });
        }

        return card;
    }

    // ===== Mark Handled =====

    private void markHandled(int id, View card, LinearLayout parentList) {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);

        ApiClient.post("reminder.php?action=handle", body, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    // Fade out animation
                    card.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction(() -> {
                                parentList.removeView(card);
                                // Check if list is now empty
                                if (parentList.getChildCount() == 0) {
                                    if (parentList == llExpiringList) {
                                        addEmptyHint(parentList, "暂无临期物品 👍");
                                    }
                                }
                            })
                            .start();
                });
            }

            @Override
            public void onError(String msg) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getActivity(), "操作失败，请重试", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    // ===== Helpers =====

    private String getCategoryEmoji(JsonObject item) {
        String type = item.has("type") ? item.get("type").getAsString() : "";
        switch (type) {
            case "expiry": return "⏰";
            case "low_stock": return "📉";
            case "tidy": return "🧹";
            default: return "📌";
        }
    }

    private void addEmptyHint(LinearLayout parent, String text) {
        if (getActivity() == null) return;
        TextView tv = new TextView(getActivity());
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.parseColor("#A0AEC0"));
        tv.setTextSize(14);
        tv.setPadding(0, dp(40), 0, dp(40));
        parent.addView(tv);
    }

    private void addLoadingHint(LinearLayout parent) {
        if (getActivity() == null) return;
        LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(0, dp(30), 0, dp(30));
        ProgressBar pb = new ProgressBar(getActivity());
        layout.addView(pb);
        TextView tv = new TextView(getActivity());
        tv.setText("加载中...");
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.parseColor("#A0AEC0"));
        tv.setTextSize(13);
        tv.setPadding(0, dp(8), 0, 0);
        layout.addView(tv);
        parent.addView(layout);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

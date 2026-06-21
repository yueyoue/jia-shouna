package com.jiashouna.app.ui.fragment;

import android.content.Intent;
import android.graphics.Color;
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
    private LinearLayout llList;
    private TextView tvStats, tvExpiryWarning, tvWeekRange;
    private TextView tvStatExpiry, tvStatLowstock, tvStatHandled;
    private ProgressBar pbLoading;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_reminders, container, false);
        llList = v.findViewById(R.id.ll_reminder_list);
        tvStats = v.findViewById(R.id.tv_stats);
        tvExpiryWarning = v.findViewById(R.id.tv_expiry_warning);
        tvWeekRange = v.findViewById(R.id.tv_week_range);
        tvStatExpiry = v.findViewById(R.id.tv_stat_expiry);
        tvStatLowstock = v.findViewById(R.id.tv_stat_lowstock);
        tvStatHandled = v.findViewById(R.id.tv_stat_handled);
        loadReminders();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadReminders();
    }

    private void loadReminders() {
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            tvStats.setText("暂无数据");
            if (tvExpiryWarning != null) tvExpiryWarning.setText("请先创建或加入一个家庭");
            llList.removeAllViews();
            addEmptyHint("请先创建或加入一个家庭");
            return;
        }

        // 设置本周日期范围
        if (tvWeekRange != null) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM.dd", java.util.Locale.getDefault());
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                String start = sdf.format(cal.getTime());
                cal.add(java.util.Calendar.DAY_OF_WEEK, 6);
                String end = sdf.format(cal.getTime());
                tvWeekRange.setText(start + " - " + end);
            } catch (Exception ignored) {}
        }

        // 显示加载状态
        llList.removeAllViews();
        addLoadingHint();

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        // 获取统计
        ApiClient.get("/reminder/stats", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        int expiry = 0;
                        int lowStock = 0;
                        int handled = 0;
                        if (data.has("stats") && !data.get("stats").isJsonNull()) {
                            JsonObject stats = data.getAsJsonObject("stats");
                            expiry = stats.has("expiring_7days") ? stats.get("expiring_7days").getAsInt() : 0;
                            lowStock = stats.has("low_stock") ? stats.get("low_stock").getAsInt() : 0;
                            handled = stats.has("handled") ? stats.get("handled").getAsInt() : 0;
                        }
                        if (data.has("expiring_count")) {
                            try { expiry = data.get("expiring_count").getAsInt(); } catch (Exception ignored) {}
                        }
                        tvStats.setText(expiry + " 件临期 · " + lowStock + " 件库存不足");
                        if (tvExpiryWarning != null) {
                            if (expiry > 0) {
                                tvExpiryWarning.setText(expiry + " 件物品 7 天内过期");
                            } else {
                                tvExpiryWarning.setText("暂无临期物品 👍");
                            }
                        }
                        if (tvStatExpiry != null) tvStatExpiry.setText(String.valueOf(expiry));
                        if (tvStatLowstock != null) tvStatLowstock.setText(String.valueOf(lowStock));
                        if (tvStatHandled != null) tvStatHandled.setText(String.valueOf(handled));
                    } catch (Exception e) {
                        tvStats.setText("0 件临期 · 0 件库存不足");
                        if (tvExpiryWarning != null) tvExpiryWarning.setText("暂无临期物品");
                        if (tvStatExpiry != null) tvStatExpiry.setText("0");
                        if (tvStatLowstock != null) tvStatLowstock.setText("0");
                        if (tvStatHandled != null) tvStatHandled.setText("0");
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    tvStats.setText("0 件临期 · 0 件库存不足");
                    if (tvExpiryWarning != null) tvExpiryWarning.setText("暂无临期物品");
                    if (tvStatExpiry != null) tvStatExpiry.setText("0");
                    if (tvStatLowstock != null) tvStatLowstock.setText("0");
                    if (tvStatHandled != null) tvStatHandled.setText("0");
                });
            }
        });

        // 获取提醒列表
        ApiClient.get("/reminder/list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    llList.removeAllViews();
                    try {
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            JsonArray list = data.getAsJsonArray("list");
                            if (list.size() == 0) {
                                addEmptyHint("暂无提醒\n系统会在物品临期时自动提醒您");
                            } else {
                                for (int i = 0; i < list.size(); i++) {
                                    JsonObject item = list.get(i).getAsJsonObject();
                                    addReminderItem(item);
                                }
                            }
                        } else {
                            addEmptyHint("暂无提醒\n系统会在物品临期时自动提醒您");
                        }
                    } catch (Exception e) {
                        addEmptyHint("暂无提醒");
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    llList.removeAllViews();
                    addEmptyHint("暂无提醒\n系统会在物品临期时自动提醒您");
                });
            }
        });
    }

    private void addReminderItem(JsonObject item) {
        if (getActivity() == null) return;

        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card_16);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(8);
        row.setLayoutParams(rowLp);

        // 图标
        TextView icon = new TextView(getActivity());
        String type = item.has("type") ? item.get("type").getAsString() : "custom";
        switch (type) {
            case "expiry": icon.setText("⏰"); break;
            case "low_stock": icon.setText("📉"); break;
            case "tidy": icon.setText("🧹"); break;
            default: icon.setText("📌"); break;
        }
        icon.setTextSize(20);
        row.addView(icon);

        // 内容
        LinearLayout content = new LinearLayout(getActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        contentLp.leftMargin = dp(12);
        content.setLayoutParams(contentLp);

        TextView title = new TextView(getActivity());
        String titleText = item.has("title") && !item.get("title").isJsonNull() ? item.get("title").getAsString() : "";
        title.setText(titleText);
        title.setTextSize(14);
        title.setTextColor(Color.parseColor("#2D3748"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        if (item.has("content") && !item.get("content").isJsonNull() && !item.get("content").getAsString().isEmpty()) {
            TextView desc = new TextView(getActivity());
            desc.setText(item.get("content").getAsString());
            desc.setTextSize(12);
            desc.setTextColor(Color.parseColor("#718096"));
            desc.setMaxLines(2);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            content.addView(desc);
        }

        if (item.has("goods_name") && !item.get("goods_name").isJsonNull()) {
            String goodsName = item.get("goods_name").getAsString();
            if (!goodsName.isEmpty()) {
                TextView goods = new TextView(getActivity());
                goods.setText("📦 " + goodsName);
                goods.setTextSize(11);
                goods.setTextColor(Color.parseColor("#A0AEC0"));
                content.addView(goods);
            }
        }

        // 显示剩余天数（临期提醒）
        if ("expiry".equals(type) && item.has("days_left") && !item.get("days_left").isJsonNull()) {
            try {
                int daysLeft = item.get("days_left").getAsInt();
                TextView daysTv = new TextView(getActivity());
                if (daysLeft < 0) {
                    daysTv.setText("已过期 " + Math.abs(daysLeft) + " 天");
                    daysTv.setTextColor(Color.parseColor("#F56565"));
                } else if (daysLeft == 0) {
                    daysTv.setText("今天到期");
                    daysTv.setTextColor(Color.parseColor("#F56565"));
                } else if (daysLeft <= 3) {
                    daysTv.setText("还剩 " + daysLeft + " 天");
                    daysTv.setTextColor(Color.parseColor("#ED8936"));
                } else {
                    daysTv.setText("还剩 " + daysLeft + " 天");
                    daysTv.setTextColor(Color.parseColor("#48BB78"));
                }
                daysTv.setTextSize(11);
                daysTv.setTypeface(null, android.graphics.Typeface.BOLD);
                content.addView(daysTv);
            } catch (Exception ignored) {}
        }

        row.addView(content);

        // 状态 - 未读标记
        boolean isRead = item.has("is_read") && !item.get("is_read").isJsonNull() && item.get("is_read").getAsInt() == 1;
        if (!isRead) {
            TextView dot = new TextView(getActivity());
            dot.setText("●");
            dot.setTextSize(12);
            dot.setTextColor(Color.parseColor("#FF8C42"));
            row.addView(dot);
        }

        // 点击跳转到物品详情
        if (item.has("goods_id") && !item.get("goods_id").isJsonNull()) {
            try {
                int goodsId = item.get("goods_id").getAsInt();
                if (goodsId > 0) {
                    row.setClickable(true);
                    row.setFocusable(true);
                    final int finalGoodsId = goodsId;
                    row.setOnClickListener(v -> {
                        Intent intent = new Intent(getActivity(), com.jiashouna.app.ui.ItemDetailActivity.class);
                        intent.putExtra("goods_id", finalGoodsId);
                        startActivity(intent);
                    });
                }
            } catch (Exception ignored) {}
        }

        llList.addView(row);
    }

    private void addEmptyHint(String text) {
        if (getActivity() == null) return;
        TextView tv = new TextView(getActivity());
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.parseColor("#A0AEC0"));
        tv.setTextSize(14);
        tv.setPadding(0, dp(60), 0, dp(60));
        llList.addView(tv);
    }

    private void addLoadingHint() {
        if (getActivity() == null) return;
        LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(0, dp(40), 0, dp(40));
        ProgressBar pb = new ProgressBar(getActivity());
        layout.addView(pb);
        TextView tv = new TextView(getActivity());
        tv.setText("加载中...");
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.parseColor("#A0AEC0"));
        tv.setTextSize(13);
        tv.setPadding(0, dp(8), 0, 0);
        layout.addView(tv);
        llList.addView(layout);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

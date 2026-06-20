package com.jiashouna.app.ui.fragment;

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
    private TextView tvStats;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_reminders, container, false);
        llList = v.findViewById(R.id.ll_reminder_list);
        tvStats = v.findViewById(R.id.tv_stats);
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
            llList.removeAllViews();
            addEmptyHint("请先创建或加入一个家庭");
            return;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        // 获取统计
        ApiClient.get("reminder/stats", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        int expiry = 0;
                        int lowStock = 0;
                        if (data.has("stats") && !data.get("stats").isJsonNull()) {
                            JsonObject stats = data.getAsJsonObject("stats");
                            expiry = stats.has("expiring_7days") ? stats.get("expiring_7days").getAsInt() : 0;
                            lowStock = stats.has("low_stock") ? stats.get("low_stock").getAsInt() : 0;
                        }
                        // 也检查直接字段
                        if (data.has("expiring_count")) {
                            expiry = data.get("expiring_count").getAsInt();
                        }
                        tvStats.setText(expiry + " 件临期 · " + lowStock + " 件库存不足");
                    } catch (Exception e) {
                        tvStats.setText("0 件临期 · 0 件库存不足");
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    tvStats.setText("0 件临期 · 0 件库存不足"));
            }
        });

        // 获取提醒列表
        ApiClient.get("reminder/list", params, new ApiClient.ApiCallback() {
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
                    addEmptyHint("加载失败: " + msg);
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
        title.setText(item.has("title") ? item.get("title").getAsString() : "");
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
            TextView goods = new TextView(getActivity());
            goods.setText("📦 " + item.get("goods_name").getAsString());
            goods.setTextSize(11);
            goods.setTextColor(Color.parseColor("#A0AEC0"));
            content.addView(goods);
        }

        row.addView(content);

        // 状态
        boolean isRead = item.has("is_read") && item.get("is_read").getAsInt() == 1;
        if (!isRead) {
            TextView dot = new TextView(getActivity());
            dot.setText("●");
            dot.setTextSize(12);
            dot.setTextColor(Color.parseColor("#FF8C42"));
            row.addView(dot);
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

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

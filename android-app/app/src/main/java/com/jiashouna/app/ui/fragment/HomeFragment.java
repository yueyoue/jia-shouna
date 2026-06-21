package com.jiashouna.app.ui.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.ui.AddItemActivity;
import com.jiashouna.app.ui.AddSpaceActivity;
import com.jiashouna.app.ui.AllItemsActivity;
import com.jiashouna.app.ui.FamilyShareActivity;

import java.text.SimpleDateFormat;
import java.util.*;

public class HomeFragment extends Fragment {
    private TextView tvGreeting, tvHouseInfo, tvItemCount, tvSpaceCount, tvExpiringCount, tvMemberCount;
    private TextView tvExpiringTag;
    private LinearLayout llExpiringList, llRecentList, llContributions;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        tvGreeting = v.findViewById(R.id.tv_greeting);
        tvHouseInfo = v.findViewById(R.id.tv_house_info);
        tvItemCount = v.findViewById(R.id.tv_item_count);
        tvItemCount.setOnClickListener(e -> startActivity(new Intent(getActivity(), AllItemsActivity.class)));
        tvSpaceCount = v.findViewById(R.id.tv_space_count);
        tvExpiringCount = v.findViewById(R.id.tv_expiring_count);
        tvMemberCount = v.findViewById(R.id.tv_member_count);
        tvExpiringTag = v.findViewById(R.id.tv_expiring_tag);
        llExpiringList = v.findViewById(R.id.ll_expiring_list);
        llRecentList = v.findViewById(R.id.ll_recent_list);
        llContributions = v.findViewById(R.id.ll_contributions);

        // 快捷入口
        v.findViewById(R.id.btn_scan).setOnClickListener(e -> {
            Intent i = new Intent(getActivity(), AddItemActivity.class);
            i.putExtra("mode", "scan");
            startActivity(i);
        });
        v.findViewById(R.id.btn_photo).setOnClickListener(e -> {
            Intent i = new Intent(getActivity(), AddItemActivity.class);
            i.putExtra("mode", "photo");
            startActivity(i);
        });
        v.findViewById(R.id.btn_manual).setOnClickListener(e -> {
            Intent i = new Intent(getActivity(), AddItemActivity.class);
            i.putExtra("mode", "manual");
            startActivity(i);
        });
        v.findViewById(R.id.btn_browse_spaces).setOnClickListener(e -> {
            // 切换到空间Tab
            if (getActivity() != null) {
                com.google.android.material.bottomnavigation.BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
                if (nav != null) nav.setSelectedItemId(R.id.nav_spaces);
            }
        });
        v.findViewById(R.id.btn_add_space).setOnClickListener(e -> startActivity(new Intent(getActivity(), AddSpaceActivity.class)));
        v.findViewById(R.id.btn_family).setOnClickListener(e -> startActivity(new Intent(getActivity(), FamilyShareActivity.class)));

        // 搜索栏 - 点击进入所有物品页面搜索
        v.findViewById(R.id.btn_search).setOnClickListener(e -> {
            Intent intent = new Intent(getActivity(), AllItemsActivity.class);
            intent.putExtra("focus_search", true);
            startActivity(intent);
        });
        v.findViewById(R.id.btn_scan_search).setOnClickListener(e -> {
            Intent i = new Intent(getActivity(), AddItemActivity.class);
            i.putExtra("mode", "scan");
            startActivity(i);
        });

        // 查看全部
        v.findViewById(R.id.tv_view_all_reminders).setOnClickListener(e -> {
            if (getActivity() != null) {
                com.google.android.material.bottomnavigation.BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
                if (nav != null) nav.setSelectedItemId(R.id.nav_reminders);
            }
        });
        v.findViewById(R.id.tv_view_all_recent).setOnClickListener(e -> {
            startActivity(new Intent(getActivity(), AllItemsActivity.class));
        });

        loadData();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        App app = App.getInstance();

        // 设置问候语
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 6) greeting = "凌晨好 🌙";
        else if (hour < 9) greeting = "早上好 🌅";
        else if (hour < 12) greeting = "上午好 ☀️";
        else if (hour < 14) greeting = "中午好 🌞";
        else if (hour < 18) greeting = "下午好 👋";
        else if (hour < 22) greeting = "晚上好 🌆";
        else greeting = "夜深了 🌙";
        tvGreeting.setText(greeting);

        int houseId = app.getCurrentHouseId();
        if (houseId <= 0) {
            tvHouseInfo.setText("暂无家庭，请先创建");
            tvItemCount.setText("0");
            tvSpaceCount.setText("0");
            tvExpiringCount.setText("0");
            tvMemberCount.setText("0");
            tvExpiringTag.setText("0 件");
            llExpiringList.removeAllViews();
            addEmptyHint(llExpiringList, "暂无临期物品");
            llRecentList.removeAllViews();
            addEmptyHint(llRecentList, "暂无物品");
            return;
        }

        tvHouseInfo.setText(app.getCurrentHouseName());

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        // 获取家庭成员数
        ApiClient.get("house.php?action=list", null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            JsonArray houses = data.getAsJsonArray("list");
                            for (int i = 0; i < houses.size(); i++) {
                                JsonObject house = houses.get(i).getAsJsonObject();
                                if (house.has("id") && house.get("id").getAsInt() == houseId) {
                                    int members = house.has("member_count") ? house.get("member_count").getAsInt() : 1;
                                    tvMemberCount.setText(members + "");
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });

        // 加载家庭贡献
        loadContributions(houseId);

        // 获取物品统计
        HashMap<String, String> params2 = new HashMap<>();
        params2.put("action", "list");
        params2.put("house_id", String.valueOf(houseId));
        ApiClient.get("goods.php?action=list", params2, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        int total = data.has("total") ? data.get("total").getAsInt() : 0;
                        tvItemCount.setText(String.valueOf(total));
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });

        // 获取空间统计
        HashMap<String, String> spaceParams = new HashMap<>();
        spaceParams.put("action", "list");
        spaceParams.put("house_id", String.valueOf(houseId));
        ApiClient.get("space.php?action=list", spaceParams, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        if (data.has("list")) {
                            tvSpaceCount.setText(String.valueOf(data.getAsJsonArray("list").size()));
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });

        // 获取提醒统计
        HashMap<String, String> statsParams = new HashMap<>();
        statsParams.put("house_id", String.valueOf(houseId));
        ApiClient.get("reminder.php?action=stats", statsParams, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        if (data.has("stats") && !data.get("stats").isJsonNull()) {
                            JsonObject stats = data.getAsJsonObject("stats");
                            int expiring = stats.has("expiring_7days") ? stats.get("expiring_7days").getAsInt() : 0;
                            tvExpiringCount.setText(String.valueOf(expiring));
                            tvExpiringTag.setText(expiring + " 件");
                        }
                        // 也从goods接口获取临期数
                        if (data.has("expiring_count")) {
                            int expiring = data.get("expiring_count").getAsInt();
                            tvExpiringCount.setText(String.valueOf(expiring));
                            tvExpiringTag.setText(expiring + " 件");
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });

        // 获取临期物品列表
        HashMap<String, String> expParams = new HashMap<>();
        expParams.put("house_id", String.valueOf(houseId));
        expParams.put("action", "expiring");
        expParams.put("days", "7");
        ApiClient.get("goods.php?action=expiring", expParams, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    llExpiringList.removeAllViews();
                    try {
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            JsonArray list = data.getAsJsonArray("list");
                            if (list.size() == 0) {
                                addEmptyHint(llExpiringList, "暂无临期物品 👍");
                            } else {
                                for (int i = 0; i < Math.min(list.size(), 5); i++) {
                                    JsonObject item = list.get(i).getAsJsonObject();
                                    addExpiringItem(item);
                                }
                            }
                        } else {
                            addEmptyHint(llExpiringList, "暂无临期物品 👍");
                        }
                    } catch (Exception e) {
                        addEmptyHint(llExpiringList, "暂无临期物品 👍");
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    llExpiringList.removeAllViews();
                    addEmptyHint(llExpiringList, "暂无临期物品 👍");
                });
            }
        });

        // 获取最近添加的物品
        HashMap<String, String> recentParams = new HashMap<>();
        recentParams.put("action", "list");
        recentParams.put("house_id", String.valueOf(houseId));
        recentParams.put("page_size", "4");
        ApiClient.get("goods.php?action=list", recentParams, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    llRecentList.removeAllViews();
                    try {
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            JsonArray list = data.getAsJsonArray("list");
                            if (list.size() == 0) {
                                addEmptyHint(llRecentList, "暂无物品，快去添加吧");
                            } else {
                                for (int i = 0; i < list.size(); i += 2) {
                                    LinearLayout row = new LinearLayout(getActivity());
                                    row.setOrientation(LinearLayout.HORIZONTAL);
                                    row.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                                    if (i > 0) {
                                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
                                        lp.topMargin = dp(10);
                                    }
                                    addRecentItem(row, list.get(i).getAsJsonObject());
                                    if (i + 1 < list.size()) {
                                        View spacer = new View(getActivity());
                                        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 0));
                                        row.addView(spacer);
                                        addRecentItem(row, list.get(i + 1).getAsJsonObject());
                                    } else {
                                        View spacer = new View(getActivity());
                                        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1));
                                        row.addView(spacer);
                                    }
                                    llRecentList.addView(row);
                                }
                            }
                        } else {
                            addEmptyHint(llRecentList, "暂无物品，快去添加吧");
                        }
                    } catch (Exception e) {
                        addEmptyHint(llRecentList, "暂无物品，快去添加吧");
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    llRecentList.removeAllViews();
                    addEmptyHint(llRecentList, "暂无物品，快去添加吧");
                });
            }
        });
    }

    private void addExpiringItem(JsonObject item) {
        if (getActivity() == null) return;
        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView icon = new TextView(getActivity());
        icon.setText("⚠️");
        icon.setTextSize(14);
        row.addView(icon);

        TextView name = new TextView(getActivity());
        name.setText(item.has("name") ? item.get("name").getAsString() : "");
        name.setTextSize(13);
        name.setTextColor(Color.parseColor("#2D3748"));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameLp.leftMargin = dp(8);
        name.setLayoutParams(nameLp);
        row.addView(name);

        TextView date = new TextView(getActivity());
        String expiry = item.has("expiry_date") && !item.get("expiry_date").isJsonNull() ? item.get("expiry_date").getAsString() : "";
        date.setText(expiry);
        date.setTextSize(11);
        date.setTextColor(Color.parseColor("#ED8936"));
        row.addView(date);

        row.setOnClickListener(e -> {
            // 跳转到物品详情
            Toast.makeText(getContext(), "查看: " + (item.has("name") ? item.get("name").getAsString() : ""), Toast.LENGTH_SHORT).show();
        });

        llExpiringList.addView(row);
    }

    private void addRecentItem(LinearLayout parent, JsonObject item) {
        if (getActivity() == null) return;
        LinearLayout card = new LinearLayout(getActivity());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_quick_item);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        card.setLayoutParams(cardLp);

        TextView icon = new TextView(getActivity());
        String iconStr = item.has("space_icon") && !item.get("space_icon").isJsonNull() ? item.get("space_icon").getAsString() : "📦";
        if (iconStr.isEmpty()) iconStr = "📦";
        icon.setText(iconStr);
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        icon.setLayoutParams(iconLp);
        icon.setBackgroundResource(R.drawable.bg_quick_orange);
        card.addView(icon);

        LinearLayout info = new LinearLayout(getActivity());
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.leftMargin = dp(10);
        info.setLayoutParams(infoLp);

        TextView name = new TextView(getActivity());
        name.setText(item.has("name") ? item.get("name").getAsString() : "");
        name.setTextSize(12);
        name.setTextColor(Color.parseColor("#2D3748"));
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(name);

        String spaceName = item.has("space_name") && !item.get("space_name").isJsonNull() ? item.get("space_name").getAsString() : "未分类";
        TextView space = new TextView(getActivity());
        space.setText(spaceName);
        space.setTextSize(10);
        space.setTextColor(Color.parseColor("#718096"));
        info.addView(space);

        card.addView(info);
        // 点击打开物品详情
        int itemId = 0;
        try {
            if (item.has("id") && !item.get("id").isJsonNull()) {
                itemId = item.get("id").getAsInt();
            }
        } catch (Exception ignored) {}
        if (itemId > 0) {
            card.setClickable(true);
            card.setFocusable(true);
            final int finalItemId = itemId;
            card.setOnClickListener(e -> {
                Intent intent = new Intent(getActivity(), com.jiashouna.app.ui.ItemDetailActivity.class);
                intent.putExtra("goods_id", finalItemId);
                startActivity(intent);
            });
        }
        parent.addView(card);
    }

    private void loadContributions(int houseId) {
        if (llContributions == null || houseId <= 0) return;
        llContributions.removeAllViews();

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("house.php?action=contribution", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        llContributions.removeAllViews();
                        JsonArray members = data.has("members") ? data.getAsJsonArray("members") : new JsonArray();
                        if (members.size() == 0) {
                            TextView tv = new TextView(getActivity());
                            tv.setText("暂无数据");
                            tv.setTextSize(11);
                            tv.setTextColor(0xFFA0AEC0);
                            llContributions.addView(tv);
                            return;
                        }
                        int[] colors = {0xFFFFF0E6, 0xFFE6F0FF, 0xFFE6FFE6, 0xFFFFE6E6, 0xFFF0E6FF};
                        int[] textColors = {0xFFC25A1E, 0xFF2C5282, 0xFF22543D, 0xFF9B2C2C, 0xFF553C9A};
                        for (int i = 0; i < members.size(); i++) {
                            JsonObject m = members.get(i).getAsJsonObject();
                            String name = m.has("nickname") && !m.get("nickname").isJsonNull() ? m.get("nickname").getAsString() : "用户";
                            int count = m.has("item_count") ? m.get("item_count").getAsInt() : 0;

                            TextView tag = new TextView(getActivity());
                            tag.setText(name + " " + count);
                            tag.setTextSize(11);
                            tag.setTextColor(textColors[i % textColors.length]);
                            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                            bg.setColor(colors[i % colors.length]);
                            bg.setCornerRadius(dp(4));
                            tag.setBackground(bg);
                            tag.setPadding(dp(8), dp(3), dp(8), dp(3));
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            lp.setMarginEnd(dp(4));
                            tag.setLayoutParams(lp);
                            llContributions.addView(tag);
                        }
                    } catch (Exception e) {}
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    llContributions.removeAllViews();
                    TextView tv = new TextView(getActivity());
                    tv.setText("暂无数据");
                    tv.setTextSize(11);
                    tv.setTextColor(0xFFA0AEC0);
                    llContributions.addView(tv);
                });
            }
        });
    }

    private void addEmptyHint(LinearLayout container, String text) {
        if (getActivity() == null) return;
        TextView tv = new TextView(getActivity());
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.parseColor("#A0AEC0"));
        tv.setTextSize(13);
        tv.setPadding(0, dp(20), 0, dp(20));
        container.addView(tv);
    }

    private void showSearchDialog() {
        if (getActivity() == null) return;

        LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(8));

        EditText etSearch = new EditText(getActivity());
        etSearch.setHint("输入物品名称、条码、标签...");
        etSearch.setTextSize(14);
        etSearch.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(0xFFF7FAFC);
        bg.setStroke(dp(1), 0xFFE2E8F0);
        etSearch.setBackground(bg);
        layout.addView(etSearch);

        new androidx.appcompat.app.AlertDialog.Builder(getActivity())
            .setTitle("🔍 搜索物品")
            .setView(layout)
            .setPositiveButton("搜索", (d, w) -> {
                String keyword = etSearch.getText().toString().trim();
                if (!keyword.isEmpty()) {
                    doSearch(keyword);
                }
            })
            .setNegativeButton("取消", null)
            .show();

        etSearch.requestFocus();
    }

    private void doSearch(String keyword) {
        int houseId = App.getInstance().getCurrentHouseId();
        java.util.HashMap<String, String> params = new java.util.HashMap<>();
        params.put("action", "search");
        params.put("keyword", keyword);
        if (houseId > 0) params.put("house_id", String.valueOf(houseId));

        ApiClient.get("goods.php?action=search", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        JsonArray list = data.has("list") && !data.get("list").isJsonNull()
                            ? data.getAsJsonArray("list") : new JsonArray();
                        showSearchResults(list, keyword);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "搜索失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "搜索失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showSearchResults(JsonArray results, String keyword) {
        if (getActivity() == null) return;

        StringBuilder sb = new StringBuilder();
        if (results.size() == 0) {
            sb.append("未找到与「").append(keyword).append("」相关的物品");
        } else {
            sb.append("找到 ").append(results.size()).append(" 件物品：\n\n");
            for (int i = 0; i < results.size(); i++) {
                JsonObject item = results.get(i).getAsJsonObject();
                String name = item.has("name") ? item.get("name").getAsString() : "";
                String space = item.has("space_name") && !item.get("space_name").isJsonNull()
                    ? item.get("space_name").getAsString() : "未分类";
                int qty = item.has("quantity") ? item.get("quantity").getAsInt() : 0;
                String unit = item.has("unit") && !item.get("unit").isJsonNull()
                    ? item.get("unit").getAsString() : "个";
                sb.append("• ").append(name).append("  ×").append(qty).append(unit)
                  .append("  📍").append(space).append("\n");
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(getActivity())
            .setTitle("搜索结果")
            .setMessage(sb.toString())
            .setPositiveButton("确定", null)
            .show();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

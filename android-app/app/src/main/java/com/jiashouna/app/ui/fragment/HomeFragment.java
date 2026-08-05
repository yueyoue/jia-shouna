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
import com.jiashouna.app.db.LocalDb;
import com.jiashouna.app.utils.NetworkUtils;
import com.jiashouna.app.model.Goods;
import com.jiashouna.app.ui.AddItemActivity;
import com.jiashouna.app.ui.AddSpaceActivity;
import com.jiashouna.app.ui.AllItemsActivity;
import com.jiashouna.app.ui.FamilyShareActivity;
import com.jiashouna.app.ui.ItemDetailActivity;

import java.text.SimpleDateFormat;
import java.util.*;

public class HomeFragment extends Fragment {
    private TextView tvGreeting, tvHouseInfo, tvDate;
    private boolean guideDialogShown = false;
    private TextView tvItemCount, tvSpaceCount, tvExpiringCount, tvMemberCount;
    private LinearLayout llExpiringList, llRecentList;
    private LinearLayout layoutCategoryChips;
    private String selectedCategory = "";
    private int houseId = 0;
    private LinearLayout layoutAnnouncement;
    private ViewFlipper viewFlipperAnnouncement;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Top bar
        tvHouseInfo = v.findViewById(R.id.tv_house_info);
        tvDate = v.findViewById(R.id.tv_date);

        // 点击家庭名称可切换家庭
        tvHouseInfo.setOnClickListener(v1 -> showHouseSwitcher());
        tvGreeting = v.findViewById(R.id.tv_greeting);

        // Stats
        tvItemCount = v.findViewById(R.id.tv_item_count);
        tvSpaceCount = v.findViewById(R.id.tv_space_count);
        tvExpiringCount = v.findViewById(R.id.tv_expiring_count);
        tvMemberCount = v.findViewById(R.id.tv_member_count);

        // Lists
        llExpiringList = v.findViewById(R.id.ll_expiring_list);
        llRecentList = v.findViewById(R.id.ll_recent_list);

        // Category chips
        layoutCategoryChips = v.findViewById(R.id.layout_category_chips);
        setupCategoryChips();

        // Announcement banner
        layoutAnnouncement = v.findViewById(R.id.layout_announcement);
        viewFlipperAnnouncement = v.findViewById(R.id.view_flipper_announcement);

        // === Card clicks ===
        // 物品总数 → 全部物品
        v.findViewById(R.id.card_item_count).setOnClickListener(e ->
            startActivity(new Intent(getActivity(), AllItemsActivity.class)));

        // 即将过期 → 临期物品列表
        v.findViewById(R.id.card_expiring).setOnClickListener(e -> {
            Intent intent = new Intent(getActivity(), AllItemsActivity.class);
            intent.putExtra("filter_type", "expiring");
            intent.putExtra("title", "临期物品");
            startActivity(intent);
        });

        // 收纳空间 → 切换到空间Tab
        v.findViewById(R.id.card_space_count).setOnClickListener(e -> {
            if (getActivity() != null) {
                com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    getActivity().findViewById(R.id.bottom_nav);
                if (nav != null) nav.setSelectedItemId(R.id.nav_spaces);
            }
        });

        // 家庭成员 → 家庭共享
        v.findViewById(R.id.card_member_count).setOnClickListener(e ->
            startActivity(new Intent(getActivity(), FamilyShareActivity.class)));

        // === Quick Actions ===
        // 扫码
        v.findViewById(R.id.btn_scan).setOnClickListener(e -> {
            Intent i = new Intent(getActivity(), AddItemActivity.class);
            i.putExtra("mode", "scan");
            startActivity(i);
        });

        // 拍照
        v.findViewById(R.id.btn_photo).setOnClickListener(e -> {
            Intent i = new Intent(getActivity(), AddItemActivity.class);
            i.putExtra("mode", "photo");
            startActivity(i);
        });

        // 手动
        v.findViewById(R.id.btn_manual).setOnClickListener(e -> {
            Intent i = new Intent(getActivity(), AddItemActivity.class);
            i.putExtra("mode", "manual");
            startActivity(i);
        });

        // AI识别
        v.findViewById(R.id.btn_ai_scan).setOnClickListener(e -> {
            Intent i = new Intent(getActivity(), AddItemActivity.class);
            i.putExtra("mode", "ai");
            startActivity(i);
        });

        // === Search ===
        v.findViewById(R.id.btn_search).setOnClickListener(e -> {
            Intent intent = new Intent(getActivity(), AllItemsActivity.class);
            intent.putExtra("focus_search", true);
            startActivity(intent);
        });

        // === Home selector ===
        v.findViewById(R.id.btn_home_selector).setOnClickListener(e -> {
            // TODO: Show house picker dialog
            Toast.makeText(getContext(), "切换家庭", Toast.LENGTH_SHORT).show();
        });

        // === View All ===
        v.findViewById(R.id.tv_view_all_recent).setOnClickListener(e ->
            startActivity(new Intent(getActivity(), AllItemsActivity.class)));

        // === 文件档案 ===
        v.findViewById(R.id.btn_document).setOnClickListener(e ->
            startActivity(new Intent(getActivity(), com.jiashouna.app.ui.DocumentListActivity.class)));

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

        // 设置日期
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);
        tvDate.setText(sdf.format(new Date()));

        // 设置问候语
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 6) greeting = "凌晨好，欢迎回来 🌙";
        else if (hour < 9) greeting = "早上好，欢迎回来 🌅";
        else if (hour < 12) greeting = "上午好，欢迎回来 ☀️";
        else if (hour < 14) greeting = "中午好，欢迎回来 🌞";
        else if (hour < 18) greeting = "下午好，欢迎回来 👋";
        else if (hour < 22) greeting = "晚上好，欢迎回来 🌆";
        else greeting = "夜深了，欢迎回来 🌙";
        tvGreeting.setText(greeting);

        houseId = app.getCurrentHouseId();
        if (houseId <= 0) {
            tvHouseInfo.setText("暂无家庭");
            tvItemCount.setText("0");
            tvSpaceCount.setText("0");
            tvExpiringCount.setText("0");
            tvMemberCount.setText("0");
            llExpiringList.removeAllViews();
            addEmptyHint(llExpiringList, "暂无临期物品");
            llRecentList.removeAllViews();
            addEmptyHint(llRecentList, "暂无物品");

            // 首次使用引导提示（只弹一次）
            if (!guideDialogShown && isAdded()) {
                guideDialogShown = true;
                new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("👋 欢迎使用家收纳")
                    .setMessage("您还没有创建家庭，请先前往「我的 → 我的家庭」创建一个家，然后在「空间」中创建收纳空间，就可以开始录入物品啦！")
                    .setPositiveButton("我知道了", null)
                    .show();
            }
            return;
        }

        tvHouseInfo.setText(app.getCurrentHouseName());
        // 添加下拉箭头提示
        tvHouseInfo.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        tvHouseInfo.setCompoundDrawablePadding(dp(4));

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
                                    tvMemberCount.setText(String.valueOf(members));
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });

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
                        }
                        if (data.has("expiring_count")) {
                            int expiring = data.get("expiring_count").getAsInt();
                            tvExpiringCount.setText(String.valueOf(expiring));
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
        android.util.Log.d("HomeExp", "Loading expiring, houseId=" + houseId);
        ApiClient.get("goods.php?action=expiring", expParams, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                android.util.Log.d("HomeExp", "OK keys=" + data.keySet() + " raw=" + data.toString().substring(0, Math.min(300, data.toString().length())));
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    llExpiringList.removeAllViews();
                    try {
                        if (data.has("list") && !data.get("list").isJsonNull()) {
                            JsonArray list = data.getAsJsonArray("list");
                            android.util.Log.d("HomeExp", "list.size=" + list.size());
                            if (list.size() == 0) {
                                addEmptyHint(llExpiringList, "暂无临期物品 👍");
                            } else {
                                for (int i = 0; i < Math.min(list.size(), 5); i++) {
                                    addExpiringItem(list.get(i).getAsJsonObject());
                                }
                            }
                        } else {
                            android.util.Log.w("HomeExp", "No list key. Keys: " + data.keySet());
                            addEmptyHint(llExpiringList, "暂无临期物品 👍");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("HomeExp", "Parse error", e);
                        addEmptyHint(llExpiringList, "暂无临期物品 👍");
                    }
                });
            }
            @Override public void onError(String msg) {
                android.util.Log.e("HomeExp", "Error: " + msg);
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    llExpiringList.removeAllViews();
                    addEmptyHint(llExpiringList, "暂无临期物品 👍");
                });
            }
        });

        // 获取公告提醒（临期+库存不足）
        loadAnnouncements();

        // 获取最近添加的物品
        if (!NetworkUtils.isNetworkAvailable(getActivity())) {
            loadRecentFromCache();
        } else {
            loadRecentItems();
        }
    }

    /**
     * 加载公告提醒（临期物品+库存不足）
     */
    private void loadAnnouncements() {
        if (layoutAnnouncement == null || viewFlipperAnnouncement == null) return;
        if (houseId <= 0) return;
        android.util.Log.d("HomeAnn", "Loading announcements, houseId=" + houseId);

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        ApiClient.get("reminder.php?action=stats", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                android.util.Log.d("HomeAnn", "Stats OK: " + data.toString().substring(0, Math.min(200, data.toString().length())));
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        int expiring = 0, lowStock = 0;
                        if (data.has("stats") && !data.get("stats").isJsonNull()) {
                            JsonObject stats = data.getAsJsonObject("stats");
                            expiring = stats.has("expiring_7days") ? stats.get("expiring_7days").getAsInt() : 0;
                            lowStock = stats.has("low_stock") ? stats.get("low_stock").getAsInt() : 0;
                        }
                        android.util.Log.d("HomeAnn", "expiring=" + expiring + " lowStock=" + lowStock);
                        updateAnnouncementBanner(expiring, lowStock);
                        updateReminderBadge(expiring + lowStock);
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) { android.util.Log.e("HomeAnn", "Stats error: " + msg); }
        });
    }

    private void updateAnnouncementBanner(int expiring, int lowStock) {
        if (layoutAnnouncement == null || viewFlipperAnnouncement == null) return;
        viewFlipperAnnouncement.removeAllViews();

        List<String> messages = new ArrayList<>();
        if (expiring > 0) messages.add("⏰ 有 " + expiring + " 件物品即将过期，请及时处理");
        if (lowStock > 0) messages.add("📉 有 " + lowStock + " 件物品库存不足，需要补货");

        if (messages.isEmpty()) {
            layoutAnnouncement.setVisibility(View.GONE);
            return;
        }

        layoutAnnouncement.setVisibility(View.VISIBLE);
        for (String msg : messages) {
            TextView tv = new TextView(getActivity());
            tv.setText(msg);
            tv.setTextSize(13);
            tv.setTextColor(Color.parseColor("#C25A1E"));
            viewFlipperAnnouncement.addView(tv);
        }

        if (messages.size() > 1) {
            viewFlipperAnnouncement.setFlipInterval(3000);
            viewFlipperAnnouncement.startFlipping();
        }

        layoutAnnouncement.setOnClickListener(e -> {
            Intent intent = new Intent(getActivity(), com.jiashouna.app.ui.MainActivity.class);
            intent.putExtra("open_tab", "reminders");
            startActivity(intent);
        });
    }

    private void updateReminderBadge(int count) {
        // 提醒已合并到首页公告横幅，底部导航不再显示提醒角标
    }

    /**
     * 离线模式：从缓存加载最近物品
     */
    private void showHouseSwitcher() {
        if (getActivity() == null) return;
        ApiClient.get("house.php?action=list", null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
                        if (list.size() == 0) {
                            Toast.makeText(getActivity(), "暂无家庭", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        int currentId = App.getInstance().getCurrentHouseId();
                        String[] names = new String[list.size()];
                        int[] ids = new int[list.size()];
                        int checkedIdx = 0;

                        for (int i = 0; i < list.size(); i++) {
                            JsonObject h = list.get(i).getAsJsonObject();
                            ids[i] = h.get("id").getAsInt();
                            names[i] = h.get("name").getAsString();
                            if (ids[i] == currentId) checkedIdx = i;
                        }

                        new android.app.AlertDialog.Builder(getActivity())
                            .setTitle("选择家庭")
                            .setSingleChoiceItems(names, checkedIdx, (dialog, which) -> {
                                int newId = ids[which];
                                String newName = names[which];
                                App.getInstance().setCurrentHouseId(newId);
                                App.getInstance().setCurrentHouseName(newName);
                                tvHouseInfo.setText(newName);
                                houseId = newId;
                                dialog.dismiss();
                                // 重新加载数据
                                loadData();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), "加载家庭列表失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    Toast.makeText(getActivity(), "加载失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void setupCategoryChips() {
        if (layoutCategoryChips == null || getActivity() == null) return;
        layoutCategoryChips.removeAllViews();
        String[] categories = {"全部", "食品", "药品", "日用品", "衣物", "数码", "厨具", "文具", "其他"};
        for (String cat : categories) {
            TextView chip = new TextView(getActivity());
            chip.setText(cat);
            chip.setTextSize(13);
            chip.setGravity(android.view.Gravity.CENTER);
            int px = dp(16);
            chip.setPadding(px, dp(8), px, dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
            if (layoutCategoryChips.indexOfChild(chip) > 0) lp.setMarginStart(dp(8));
            chip.setLayoutParams(lp);

            boolean isActive = (cat.equals("全部") && selectedCategory.isEmpty())
                || cat.equals(selectedCategory);
            chip.setBackgroundResource(isActive ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
            chip.setTextColor(isActive ? 0xFFFFFFFF : 0xFF718096);

            chip.setOnClickListener(v -> {
                selectedCategory = cat.equals("全部") ? "" : cat;
                setupCategoryChips();
                loadRecentItems();
            });
            layoutCategoryChips.addView(chip);
        }
    }

    private void loadRecentItems() {
        if (getActivity() == null) return;
        HashMap<String, String> params = new HashMap<>();
        params.put("action", "list");
        params.put("house_id", String.valueOf(houseId));
        params.put("page_size", "4");
        if (!selectedCategory.isEmpty()) params.put("category", selectedCategory);
        ApiClient.get("goods.php?action=list", params, new ApiClient.ApiCallback() {
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
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT));
                                    if (i > 0) {
                                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
                                        lp.topMargin = dp(12);
                                    }
                                    addBentoItem(row, list.get(i).getAsJsonObject());
                                    if (i + 1 < list.size()) {
                                        View spacer = new View(getActivity());
                                        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 0));
                                        row.addView(spacer);
                                        addBentoItem(row, list.get(i + 1).getAsJsonObject());
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

    private void loadRecentFromCache() {
        if (getActivity() == null) return;
        LocalDb localDb = new LocalDb(getActivity());
        List<Goods> cached = localDb.getCachedGoods();
        llRecentList.removeAllViews();
        if (cached.isEmpty()) {
            addEmptyHint(llRecentList, "暂无缓存数据");
            return;
        }
        int count = Math.min(cached.size(), 4);
        for (int i = 0; i < count; i += 2) {
            LinearLayout row = new LinearLayout(getActivity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            if (i > 0) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
                lp.topMargin = dp(12);
            }
            Goods g1 = cached.get(i);
            JsonObject item1 = new JsonObject();
            item1.addProperty("id", g1.id);
            item1.addProperty("name", g1.name);
            item1.addProperty("space_name", g1.spaceName);
            item1.addProperty("cover_image", g1.coverImage);
            addBentoItem(row, item1);
            if (i + 1 < count) {
                View spacer = new View(getActivity());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 0));
                row.addView(spacer);
                Goods g2 = cached.get(i + 1);
                JsonObject item2 = new JsonObject();
                item2.addProperty("id", g2.id);
                item2.addProperty("name", g2.name);
                item2.addProperty("space_name", g2.spaceName);
                item2.addProperty("cover_image", g2.coverImage);
                addBentoItem(row, item2);
            } else {
                View spacer = new View(getActivity());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1));
                row.addView(spacer);
            }
            llRecentList.addView(row);
        }
    }

    private void addExpiringItem(JsonObject item) {
        if (getActivity() == null) return;

        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(16), 0, dp(16));
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Image thumbnail
        FrameLayout imgFrame = new FrameLayout(getActivity());
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(64), dp(64));
        imgFrame.setLayoutParams(imgLp);

        android.widget.ImageView imgView = new android.widget.ImageView(getActivity());
        imgView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        imgView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        android.graphics.drawable.GradientDrawable imgBg = new android.graphics.drawable.GradientDrawable();
        imgBg.setCornerRadius(dp(8));
        imgBg.setColor(0xFFE7EEFF);
        imgView.setBackground(imgBg);

        String coverImage = item.has("cover_image") && !item.get("cover_image").isJsonNull()
            ? item.get("cover_image").getAsString() : "";
        if (!coverImage.isEmpty()) {
            try {
                com.bumptech.glide.Glide.with(getActivity())
                    .load(coverImage)
                    .centerCrop()
                    .into(imgView);
            } catch (Exception ignored) {}
        } else {
            TextView emoji = new TextView(getActivity());
            emoji.setText("📦");
            emoji.setTextSize(24);
            emoji.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams emojiLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            emoji.setLayoutParams(emojiLp);
            imgFrame.addView(imgView);
            imgFrame.addView(emoji);
        }
        if (coverImage.isEmpty()) {
            imgFrame.addView(imgView);
        } else {
            imgFrame.addView(imgView);
        }
        row.addView(imgFrame);

        // Info
        LinearLayout info = new LinearLayout(getActivity());
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.leftMargin = dp(16);
        info.setLayoutParams(infoLp);

        // Name + expiry tag
        LinearLayout topRow = new LinearLayout(getActivity());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(getActivity());
        name.setText(item.has("name") ? item.get("name").getAsString() : "");
        name.setTextSize(16);
        name.setTextColor(Color.parseColor("#121C2C"));
        name.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        name.setLayoutParams(nameLp);
        topRow.addView(name);

        // Expiry status
        String expiry = item.has("expiry_date") && !item.get("expiry_date").isJsonNull()
            ? item.get("expiry_date").getAsString() : "";
        TextView expiryTag = new TextView(getActivity());
        if (!expiry.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date expiryDate = sdf.parse(expiry);
                long diff = expiryDate.getTime() - System.currentTimeMillis();
                long daysLeft = diff / (1000 * 60 * 60 * 24);
                if (daysLeft < 0) {
                    expiryTag.setText("已过期");
                    expiryTag.setTextColor(Color.parseColor("#F56565"));
                    expiryTag.setTypeface(null, Typeface.BOLD);
                } else if (daysLeft <= 2) {
                    expiryTag.setText("还有 " + daysLeft + " 天过期");
                    expiryTag.setTextColor(Color.parseColor("#ED8936"));
                } else {
                    expiryTag.setText("还有 " + daysLeft + " 天过期");
                    expiryTag.setTextColor(Color.parseColor("#ED8936"));
                }
            } catch (Exception e) {
                expiryTag.setText(expiry);
                expiryTag.setTextColor(Color.parseColor("#ED8936"));
            }
        }
        expiryTag.setTextSize(12);
        topRow.addView(expiryTag);
        info.addView(topRow);

        // Location
        String spaceName = item.has("space_name") && !item.get("space_name").isJsonNull()
            ? item.get("space_name").getAsString() : "未分类";
        TextView location = new TextView(getActivity());
        location.setText("📍 " + spaceName);
        location.setTextSize(12);
        location.setTextColor(Color.parseColor("#564338"));
        LinearLayout.LayoutParams locLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        locLp.topMargin = dp(4);
        location.setLayoutParams(locLp);
        info.addView(location);

        row.addView(info);

        // Click to detail
        int itemId = 0;
        try {
            if (item.has("id") && !item.get("id").isJsonNull()) {
                itemId = item.get("id").getAsInt();
            }
        } catch (Exception ignored) {}
        if (itemId > 0) {
            row.setClickable(true);
            row.setFocusable(true);
            final int finalItemId = itemId;
            row.setOnClickListener(e -> {
                Intent intent = new Intent(getActivity(), ItemDetailActivity.class);
                intent.putExtra("goods_id", finalItemId);
                startActivity(intent);
            });
        }

        llExpiringList.addView(row);

        // Divider (except last item)
        View divider = new View(getActivity());
        divider.setBackgroundColor(Color.parseColor("#E2E8F0"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divider.setLayoutParams(divLp);
        llExpiringList.addView(divider);
    }

    private void addBentoItem(LinearLayout parent, JsonObject item) {
        if (getActivity() == null) return;

        LinearLayout card = new LinearLayout(getActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_bento_item);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        card.setLayoutParams(cardLp);
        card.setElevation(dp(2));

        // Image
        FrameLayout imgFrame = new FrameLayout(getActivity());
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(120));
        imgFrame.setLayoutParams(imgLp);

        android.widget.ImageView imgView = new android.widget.ImageView(getActivity());
        imgView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        imgView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        android.graphics.drawable.GradientDrawable imgBg = new android.graphics.drawable.GradientDrawable();
        imgBg.setCornerRadius(dp(8));
        imgBg.setColor(0xFFE7EEFF);
        imgView.setBackground(imgBg);

        String coverImage = item.has("cover_image") && !item.get("cover_image").isJsonNull()
            ? item.get("cover_image").getAsString() : "";
        if (!coverImage.isEmpty()) {
            try {
                com.bumptech.glide.Glide.with(getActivity())
                    .load(coverImage)
                    .centerCrop()
                    .into(imgView);
            } catch (Exception ignored) {}
        }
        imgFrame.addView(imgView);
        card.addView(imgFrame);

        // Name
        TextView name = new TextView(getActivity());
        name.setText(item.has("name") ? item.get("name").getAsString() : "");
        name.setTextSize(16);
        name.setTextColor(Color.parseColor("#121C2C"));
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(12);
        name.setLayoutParams(nameLp);
        card.addView(name);

        // Location
        String spaceName = item.has("space_name") && !item.get("space_name").isJsonNull()
            ? item.get("space_name").getAsString() : "未分类";
        TextView location = new TextView(getActivity());
        location.setText("📍 " + spaceName);
        location.setTextSize(12);
        location.setTextColor(Color.parseColor("#564338"));
        LinearLayout.LayoutParams locLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        locLp.topMargin = dp(4);
        location.setLayoutParams(locLp);
        card.addView(location);

        // Click to detail
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
                Intent intent = new Intent(getActivity(), ItemDetailActivity.class);
                intent.putExtra("goods_id", finalItemId);
                startActivity(intent);
            });
        }

        parent.addView(card);
    }

    private void addEmptyHint(LinearLayout container, String text) {
        if (getActivity() == null) return;
        TextView tv = new TextView(getActivity());
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.parseColor("#A0AEC0"));
        tv.setTextSize(14);
        tv.setPadding(0, dp(32), 0, dp(32));
        container.addView(tv);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

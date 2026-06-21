package com.jiashouna.app.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.ui.FamilyShareActivity;
import com.jiashouna.app.ui.LoginActivity;

public class ProfileFragment extends Fragment {

    private TextView tvAvatar, tvNickname, tvUserId;
    private TextView tvItemCount, tvSpaceCount, tvOperations;
    private TextView tvHouseName, tvMemberCount, tvInviteCode;
    private TextView tvVersion;
    private LinearLayout layoutMembers;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            View v = inflater.inflate(R.layout.fragment_profile, container, false);
            setupViews(v);
            loadProfile();
            return v;
        } catch (Exception e) {
            TextView tv = new TextView(getActivity());
            tv.setText("加载中...");
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(50, 50, 50, 50);
            return tv;
        }
    }

    private void setupViews(View v) {
        tvAvatar = v.findViewById(R.id.tv_avatar);
        tvNickname = v.findViewById(R.id.tv_nickname);
        tvUserId = v.findViewById(R.id.tv_user_id);
        tvItemCount = v.findViewById(R.id.tv_item_count);
        tvSpaceCount = v.findViewById(R.id.tv_space_count);
        tvOperations = v.findViewById(R.id.tv_operations);
        tvHouseName = v.findViewById(R.id.tv_house_name);
        tvMemberCount = v.findViewById(R.id.tv_member_count);
        tvInviteCode = v.findViewById(R.id.tv_invite_code);
        tvVersion = v.findViewById(R.id.tv_version);
        layoutMembers = v.findViewById(R.id.layout_members);

        // 退出登录
        View btnLogout = v.findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(e -> {
                new android.app.AlertDialog.Builder(requireActivity())
                    .setTitle("退出登录")
                    .setMessage("确定要退出登录吗？")
                    .setPositiveButton("退出", (d, w) -> {
                        App.getInstance().logout();
                        startActivity(new Intent(getActivity(), LoginActivity.class));
                        if (getActivity() != null) getActivity().finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            });
        }

        // 家庭共享/管理
        View btnFamily = v.findViewById(R.id.btn_family_share);
        if (btnFamily != null) {
            btnFamily.setOnClickListener(e -> {
                try {
                    startActivity(new Intent(getActivity(), FamilyShareActivity.class));
                } catch (Exception ex) {
                    Toast.makeText(getContext(), "打开失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 物品盘点
        setupMenuClick(v, R.id.menu_inventory, () -> {
            // 跳转到空间页面查看物品
            Toast.makeText(getContext(), "请在空间页面查看物品列表", Toast.LENGTH_SHORT).show();
        });

        // 数据统计
        setupMenuClick(v, R.id.menu_stats, () -> {
            loadStats();
        });

        // 数据导出
        setupMenuClick(v, R.id.menu_export, () -> {
            exportData();
        });

        // 家庭成员
        setupMenuClick(v, R.id.menu_members, () -> {
            try {
                startActivity(new Intent(getActivity(), FamilyShareActivity.class));
            } catch (Exception ex) {
                Toast.makeText(getContext(), "打开失败", Toast.LENGTH_SHORT).show();
            }
        });

        // 分享邀请
        setupMenuClick(v, R.id.menu_invite, () -> {
            shareInvite();
        });

        // 标签管理
        setupMenuClick(v, R.id.menu_tags, () -> {
            openTagManager();
        });

        // 操作日志
        setupMenuClick(v, R.id.menu_logs, () -> {
            loadOperationLogs();
        });

        // 提醒设置
        setupMenuClick(v, R.id.menu_reminder_settings, () -> {
            Toast.makeText(getContext(), "提醒设置：请在提醒页面管理", Toast.LENGTH_SHORT).show();
        });

        // 账号安全
        setupMenuClick(v, R.id.menu_security, () -> {
            Toast.makeText(getContext(), "账号安全功能开发中", Toast.LENGTH_SHORT).show();
        });

        // 版本更新
        setupMenuClick(v, R.id.menu_version_update, () -> {
            checkVersionUpdate();
        });

        // 帮助与反馈
        setupMenuClick(v, R.id.menu_help, () -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://sn.tthsdd.top/help"));
            try {
                startActivity(browserIntent);
            } catch (Exception ex) {
                Toast.makeText(getContext(), "无法打开帮助页面", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupMenuClick(View v, int id, Runnable action) {
        View menu = v.findViewById(id);
        if (menu != null) {
            menu.setOnClickListener(e -> {
                try { action.run(); } catch (Exception ex) {
                    Toast.makeText(getContext(), "操作失败: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void loadProfile() {
        ApiClient.get("/user/profile", null, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        // User info
                        if (data.has("user") && !data.get("user").isJsonNull()) {
                            JsonObject user = data.getAsJsonObject("user");
                            String name = "";
                            if (user.has("nickname") && !user.get("nickname").isJsonNull()) {
                                name = user.get("nickname").getAsString();
                            }
                            if (name.isEmpty() && user.has("username") && !user.get("username").isJsonNull()) {
                                name = user.get("username").getAsString();
                            }
                            if (name.isEmpty()) name = "用户";
                            if (tvNickname != null) tvNickname.setText(name);
                            if (tvAvatar != null) tvAvatar.setText(name.substring(0, 1));

                            // User ID / phone
                            if (tvUserId != null) {
                                String phone = "";
                                if (user.has("phone") && !user.get("phone").isJsonNull()) {
                                    phone = user.get("phone").getAsString();
                                }
                                if (phone.isEmpty() && user.has("username") && !user.get("username").isJsonNull()) {
                                    phone = user.get("username").getAsString();
                                }
                                if (!phone.isEmpty() && phone.length() >= 11) {
                                    tvUserId.setText("ID: " + phone.substring(0, 3) + "****" + phone.substring(7));
                                } else if (!phone.isEmpty()) {
                                    tvUserId.setText("ID: " + phone);
                                }
                            }
                        }

                        // Stats
                        if (data.has("stats") && !data.get("stats").isJsonNull()) {
                            JsonObject stats = data.getAsJsonObject("stats");
                            if (tvItemCount != null)
                                tvItemCount.setText(String.valueOf(stats.has("items") ? stats.get("items").getAsInt() : 0));
                            if (tvSpaceCount != null)
                                tvSpaceCount.setText(String.valueOf(stats.has("spaces") ? stats.get("spaces").getAsInt() : 0));
                            if (tvOperations != null)
                                tvOperations.setText(String.valueOf(stats.has("operations") ? stats.get("operations").getAsInt() : 0));
                        }

                        // House info
                        if (data.has("houses") && !data.get("houses").isJsonNull()) {
                            JsonArray houses = data.getAsJsonArray("houses");
                            if (houses.size() > 0) {
                                JsonObject house = houses.get(0).getAsJsonObject();
                                String hName = house.has("name") ? house.get("name").getAsString() : "我的家";
                                if (tvHouseName != null) tvHouseName.setText(hName);

                                int memberCount = house.has("member_count") ? house.get("member_count").getAsInt() : 1;
                                if (tvMemberCount != null) tvMemberCount.setText(memberCount + " 位成员");

                                // Invite code
                                if (tvInviteCode != null) {
                                    String code = house.has("invite_code") && !house.get("invite_code").isJsonNull()
                                            ? house.get("invite_code").getAsString() : "";
                                    tvInviteCode.setText(code.isEmpty() ? "无邀请码" : code);
                                }

                                // Save house info
                                int houseId = house.has("id") ? house.get("id").getAsInt() : 0;
                                if (houseId > 0) {
                                    App.getInstance().setCurrentHouseId(houseId);
                                    App.getInstance().setCurrentHouseName(hName);
                                }

                                // Members
                                if (layoutMembers != null && house.has("members") && !house.get("members").isJsonNull()) {
                                    buildMemberAvatars(house.getAsJsonArray("members"));
                                }
                            } else {
                                if (tvHouseName != null) tvHouseName.setText("暂无家庭");
                                if (tvMemberCount != null) tvMemberCount.setText("点击创建家庭");
                                if (tvInviteCode != null) tvInviteCode.setText("");
                            }
                        }

                        // App version
                        if (tvVersion != null) {
                            if (data.has("app_version") && !data.get("app_version").isJsonNull()
                                    && data.get("app_version").isJsonObject()) {
                                JsonObject ver = data.getAsJsonObject("app_version");
                                tvVersion.setText("v" + ver.get("version_name").getAsString() + " · 已是最新版本");
                            }
                        }
                    } catch (Exception e) {
                        // silently ignore parse errors
                    }
                });
            }

            @Override
            public void onError(String msg) {
                // API failed - show login prompt if 401
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (msg.contains("401") || msg.contains("登录")) {
                        Toast.makeText(getContext(), "请重新登录", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void buildMemberAvatars(JsonArray members) {
        if (layoutMembers == null) return;
        layoutMembers.removeAllViews();

        int[] colors = {0xFFD4A574, 0xFFFF8C42, 0xFF4ECDC4, 0xFF9F7AEA, 0xFF718096};
        String[] colorNames = {"bg_circle_gold", "bg_circle_orange", "bg_circle_teal", "bg_circle_purple", "bg_circle_gray"};
        int[] colorRes = {R.drawable.bg_circle_gold, R.drawable.bg_circle_orange,
                R.drawable.bg_circle_teal, R.drawable.bg_circle_purple, R.drawable.bg_circle_gray};

        int count = Math.min(members.size(), 5);
        for (int i = 0; i < count; i++) {
            JsonObject member = members.get(i).getAsJsonObject();
            String name = member.has("nickname") && !member.get("nickname").isJsonNull()
                    ? member.get("nickname").getAsString() : "成";

            TextView avatar = new TextView(requireContext());
            avatar.setText(name.substring(0, 1));
            avatar.setTextSize(13);
            avatar.setTextColor(0xFFFFFFFF);
            avatar.setTypeface(null, android.graphics.Typeface.BOLD);
            avatar.setGravity(android.view.Gravity.CENTER);
            avatar.setBackgroundResource(colorRes[i % colorRes.length]);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            if (i > 0) lp.setMarginStart(dp(-8));
            avatar.setLayoutParams(lp);
            layoutMembers.addView(avatar);
        }

        // Add "+" button
        TextView addBtn = new TextView(requireContext());
        addBtn.setText("+");
        addBtn.setTextSize(12);
        addBtn.setTextColor(0xFF718096);
        addBtn.setGravity(android.view.Gravity.CENTER);
        addBtn.setBackgroundResource(R.drawable.bg_icon_btn_rounded);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        addLp.setMarginStart(dp(-8));
        addBtn.setLayoutParams(addLp);
        addBtn.setOnClickListener(e -> {
            try {
                startActivity(new Intent(getActivity(), FamilyShareActivity.class));
            } catch (Exception ex) {}
        });
        layoutMembers.addView(addBtn);

        // Count label
        TextView countLabel = new TextView(requireContext());
        countLabel.setText("等 " + members.size() + " 位成员");
        countLabel.setTextSize(11);
        countLabel.setTextColor(0xFF718096);
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clLp.setMarginStart(dp(14));
        countLabel.setLayoutParams(clLp);
        layoutMembers.addView(countLabel);

        // Update member count text
        if (tvMemberCount != null) {
            tvMemberCount.setText(members.size() + " 位成员");
        }
    }

    private void loadStats() {
        Toast.makeText(getContext(), "正在加载统计数据...", Toast.LENGTH_SHORT).show();
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            Toast.makeText(getContext(), "暂无家庭数据", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.HashMap<String, String> params = new java.util.HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("/stats", params, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder("📊 数据统计\n\n");
                    if (data.has("categories") && !data.get("categories").isJsonNull()) {
                        JsonArray cats = data.getAsJsonArray("categories");
                        sb.append("按分类：\n");
                        for (int i = 0; i < cats.size(); i++) {
                            JsonObject cat = cats.get(i).getAsJsonObject();
                            sb.append("  ").append(cat.has("name") ? cat.get("name").getAsString() : "")
                              .append(": ").append(cat.has("count") ? cat.get("count").getAsInt() : 0)
                              .append(" 件\n");
                        }
                    }
                    if (data.has("spaces") && !data.get("spaces").isJsonNull()) {
                        JsonArray sp = data.getAsJsonArray("spaces");
                        sb.append("\n按空间：\n");
                        for (int i = 0; i < sp.size(); i++) {
                            JsonObject s = sp.get(i).getAsJsonObject();
                            sb.append("  ").append(s.has("name") ? s.get("name").getAsString() : "")
                              .append(": ").append(s.has("count") ? s.get("count").getAsInt() : 0)
                              .append(" 件\n");
                        }
                    }
                    new android.app.AlertDialog.Builder(requireActivity())
                        .setTitle("数据统计")
                        .setMessage(sb.toString())
                        .setPositiveButton("确定", null)
                        .show();
                });
            }

            @Override
            public void onError(String msg) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "加载统计失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void exportData() {
        Toast.makeText(getContext(), "正在准备导出...", Toast.LENGTH_SHORT).show();
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            Toast.makeText(getContext(), "暂无数据可导出", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.HashMap<String, String> params = new java.util.HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("/export", params, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    String url = data.has("url") ? data.get("url").getAsString() : "";
                    if (!url.isEmpty()) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                        try {
                            startActivity(browserIntent);
                        } catch (Exception ex) {
                            Toast.makeText(getContext(), "下载链接: " + url, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "导出完成，请在网页端下载", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "导出失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void shareInvite() {
        String houseName = App.getInstance().getCurrentHouseName();
        int houseId = App.getInstance().getCurrentHouseId();

        if (tvInviteCode != null && !tvInviteCode.getText().toString().isEmpty()
                && !tvInviteCode.getText().toString().equals("无邀请码")) {
            String code = tvInviteCode.getText().toString();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "来加入我的家庭「" + houseName + "」吧！\n邀请码: " + code + "\n下载家收纳APP: https://sn.tthsdd.top");
            startActivity(Intent.createChooser(shareIntent, "分享邀请码"));
        } else {
            // Try to get invite code from API
            java.util.HashMap<String, String> params = new java.util.HashMap<>();
            params.put("house_id", String.valueOf(houseId));
            ApiClient.get("/house/invite_code", params, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JsonObject data) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        String code = data.has("code") ? data.get("code").getAsString() : "";
                        if (!code.isEmpty()) {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_TEXT,
                                    "来加入我的家庭「" + houseName + "」吧！\n邀请码: " + code + "\n下载家收纳APP: https://sn.tthsdd.top");
                            startActivity(Intent.createChooser(shareIntent, "分享邀请码"));
                        } else {
                            Toast.makeText(getContext(), "暂无邀请码，请先创建家庭", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(String msg) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "获取邀请码失败: " + msg, Toast.LENGTH_SHORT).show()
                    );
                }
            });
        }
    }

    private void loadOperationLogs() {
        Toast.makeText(getContext(), "正在加载操作日志...", Toast.LENGTH_SHORT).show();
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            Toast.makeText(getContext(), "暂无操作记录", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.HashMap<String, String> params = new java.util.HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        params.put("limit", "20");
        ApiClient.get("/log", params, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder();
                    if (data.has("list") && !data.get("list").isJsonNull()) {
                        JsonArray logs = data.getAsJsonArray("list");
                        if (logs.size() == 0) {
                            sb.append("暂无操作记录");
                        }
                        for (int i = 0; i < logs.size(); i++) {
                            JsonObject log = logs.get(i).getAsJsonObject();
                            sb.append(log.has("created_at") ? log.get("created_at").getAsString() : "")
                              .append("\n")
                              .append(log.has("action") ? log.get("action").getAsString() : "")
                              .append(" - ")
                              .append(log.has("detail") ? log.get("detail").getAsString() : "")
                              .append("\n\n");
                        }
                    } else {
                        sb.append("暂无操作记录");
                    }
                    new android.app.AlertDialog.Builder(requireActivity())
                        .setTitle("操作日志")
                        .setMessage(sb.toString())
                        .setPositiveButton("确定", null)
                        .show();
                });
            }

            @Override
            public void onError(String msg) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "加载日志失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void openTagManager() {
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            Toast.makeText(getContext(), "暂无家庭", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), "正在加载标签...", Toast.LENGTH_SHORT).show();
        java.util.HashMap<String, String> params = new java.util.HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("/tag/list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        JsonArray tags = data.has("list") && !data.get("list").isJsonNull()
                            ? data.getAsJsonArray("list") : new JsonArray();
                        showTagManagerDialog(tags, houseId);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "加载标签失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showTagManagerDialog(JsonArray tags, int houseId) {
        if (getActivity() == null) return;

        LinearLayout container = new LinearLayout(getActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(8));

        // Current tags list
        LinearLayout tagListLayout = new LinearLayout(getActivity());
        tagListLayout.setOrientation(LinearLayout.VERTICAL);
        tagListLayout.setId(android.R.id.content);

        if (tags.size() == 0) {
            TextView empty = new TextView(getActivity());
            empty.setText("暂无标签，点击下方按钮添加");
            empty.setTextColor(0xFFA0AEC0);
            empty.setTextSize(13);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            tagListLayout.addView(empty);
        } else {
            for (int i = 0; i < tags.size(); i++) {
                JsonObject tag = tags.get(i).getAsJsonObject();
                int tagId = tag.has("id") ? tag.get("id").getAsInt() : 0;
                String tagName = tag.has("name") ? tag.get("name").getAsString() : "";
                int usageCount = tag.has("usage_count") ? tag.get("usage_count").getAsInt() : 0;

                LinearLayout row = new LinearLayout(getActivity());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));

                // Tag color dot
                View dot = new View(getActivity());
                android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
                dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dotBg.setColor(0xFFFF8C42);
                dot.setBackground(dotBg);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
                dotLp.setMarginEnd(dp(10));
                dot.setLayoutParams(dotLp);
                row.addView(dot);

                // Tag name
                TextView name = new TextView(getActivity());
                name.setText(tagName);
                name.setTextSize(14);
                name.setTextColor(0xFF2D3748);
                LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                name.setLayoutParams(nameLp);
                row.addView(name);

                // Usage count
                TextView count = new TextView(getActivity());
                count.setText(usageCount + " 件");
                count.setTextSize(11);
                count.setTextColor(0xFFA0AEC0);
                LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                countLp.setMarginEnd(dp(12));
                count.setLayoutParams(countLp);
                row.addView(count);

                // Delete button
                TextView deleteBtn = new TextView(getActivity());
                deleteBtn.setText("×");
                deleteBtn.setTextSize(18);
                deleteBtn.setTextColor(0xFFF56565);
                deleteBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
                final int finalTagId = tagId;
                final String finalTagName = tagName;
                deleteBtn.setOnClickListener(v -> {
                    new android.app.AlertDialog.Builder(getActivity())
                        .setTitle("删除标签")
                        .setMessage("确定删除标签「" + finalTagName + "」？\n关联的物品不会被删除。")
                        .setPositiveButton("删除", (d, w) -> deleteTag(finalTagId, houseId))
                        .setNegativeButton("取消", null)
                        .show();
                });
                row.addView(deleteBtn);

                tagListLayout.addView(row);

                // Divider
                if (i < tags.size() - 1) {
                    View divider = new View(getActivity());
                    divider.setBackgroundColor(0xFFEDF2F7);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
                    tagListLayout.addView(divider);
                }
            }
        }
        container.addView(tagListLayout);

        new android.app.AlertDialog.Builder(getActivity())
            .setTitle("🏷 标签管理")
            .setView(container)
            .setPositiveButton("关闭", null)
            .setNeutralButton("+ 新建标签", (d, w) -> showCreateTagDialog(houseId))
            .show();
    }

    private void showCreateTagDialog(int houseId) {
        if (getActivity() == null) return;

        EditText input = new EditText(getActivity());
        input.setHint("输入标签名称，如：食品、常用、紧急");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

        new android.app.AlertDialog.Builder(getActivity())
            .setTitle("新建标签")
            .setView(input)
            .setPositiveButton("创建", (d, w) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    createTag(name, houseId);
                } else {
                    Toast.makeText(getContext(), "请输入标签名称", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void createTag(String name, int houseId) {
        JsonObject body = new JsonObject();
        body.addProperty("house_id", houseId);
        body.addProperty("name", name);
        ApiClient.post("/tag/create", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "✅ 标签已创建", Toast.LENGTH_SHORT).show();
                    openTagManager(); // Refresh
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "创建失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void deleteTag(int tagId, int houseId) {
        java.util.HashMap<String, String> params = new java.util.HashMap<>();
        params.put("id", String.valueOf(tagId));
        ApiClient.get("/tag/delete", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "✅ 已删除", Toast.LENGTH_SHORT).show();
                    openTagManager(); // Refresh
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "删除失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void checkVersionUpdate() {
        Toast.makeText(getContext(), "正在检查更新...", Toast.LENGTH_SHORT).show();
        try {
            int currentCode = requireContext().getPackageManager()
                .getPackageInfo(requireContext().getPackageName(), 0).versionCode;
            String currentName = requireContext().getPackageManager()
                .getPackageInfo(requireContext().getPackageName(), 0).versionName;

            java.util.HashMap<String, String> params = new java.util.HashMap<>();
            params.put("action", "check");
            params.put("version_code", String.valueOf(currentCode));

            ApiClient.get("/version/check", params, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        try {
                            boolean hasUpdate = data.has("has_update") && data.get("has_update").getAsBoolean();
                            if (hasUpdate && data.has("latest")) {
                                JsonObject latest = data.getAsJsonObject("latest");
                                String verName = latest.get("version_name").getAsString();
                                String changelog = latest.has("changelog") ? latest.get("changelog").getAsString() : "";
                                String apkUrl = latest.has("apk_url") ? latest.get("apk_url").getAsString() : "";
                                boolean isForce = latest.has("is_force") && latest.get("is_force").getAsInt() == 1;

                                new android.app.AlertDialog.Builder(requireActivity())
                                    .setTitle("发现新版本 v" + verName)
                                    .setMessage("当前版本: v" + currentName + "\n\n更新内容:\n" + changelog)
                                    .setPositiveButton("立即更新", (d, w) -> {
                                        if (!apkUrl.isEmpty()) {
                                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(apkUrl));
                                            try { startActivity(browserIntent); } catch (Exception e) {
                                                Toast.makeText(getContext(), "无法打开下载链接", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                            } else {
                                Toast.makeText(getContext(), "已是最新版本 v" + currentName, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "检查更新失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                @Override public void onError(String msg) {
                    if (getActivity() != null) getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "检查更新失败: " + msg, Toast.LENGTH_SHORT).show()
                    );
                }
            });
        } catch (Exception e) {
            Toast.makeText(getContext(), "获取版本信息失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

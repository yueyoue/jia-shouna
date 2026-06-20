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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            View v = inflater.inflate(R.layout.fragment_profile, container, false);
            setupViews(v);
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
        try {
            TextView tvNickname = v.findViewById(R.id.tv_nickname);
            TextView tvItemCount = v.findViewById(R.id.tv_item_count);
            TextView tvSpaceCount = v.findViewById(R.id.tv_space_count);
            TextView tvOperations = v.findViewById(R.id.tv_operations);
            TextView tvHouseName = v.findViewById(R.id.tv_house_name);
            TextView tvMemberCount = v.findViewById(R.id.tv_member_count);
            TextView tvVersion = v.findViewById(R.id.tv_version);
            Button btnLogout = v.findViewById(R.id.btn_logout);

            // 退出登录
            if (btnLogout != null) {
                btnLogout.setOnClickListener(e -> {
                    try {
                        App.getInstance().logout();
                        startActivity(new Intent(getActivity(), LoginActivity.class));
                        if (getActivity() != null) getActivity().finish();
                    } catch (Exception ex) {}
                });
            }

            // 家庭共享
            View btnFamily = v.findViewById(R.id.btn_family_share);
            if (btnFamily != null) {
                btnFamily.setOnClickListener(e -> {
                    try {
                        startActivity(new Intent(getActivity(), FamilyShareActivity.class));
                    } catch (Exception ex) {
                        Toast.makeText(getContext(), "功能开发中", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // 物品盘点 - 跳转到物品列表
            setupMenuClick(v, R.id.menu_inventory, () -> {
                Toast.makeText(getContext(), "物品盘点：请在首页查看", Toast.LENGTH_SHORT).show();
            });

            // 数据统计
            setupMenuClick(v, R.id.menu_stats, () -> {
                Toast.makeText(getContext(), "数据统计功能开发中", Toast.LENGTH_SHORT).show();
            });

            // 数据导出
            setupMenuClick(v, R.id.menu_export, () -> {
                Toast.makeText(getContext(), "数据导出功能开发中", Toast.LENGTH_SHORT).show();
            });

            // 家庭成员
            setupMenuClick(v, R.id.menu_members, () -> {
                try {
                    startActivity(new Intent(getActivity(), FamilyShareActivity.class));
                } catch (Exception ex) {
                    Toast.makeText(getContext(), "功能开发中", Toast.LENGTH_SHORT).show();
                }
            });

            // 分享邀请
            setupMenuClick(v, R.id.menu_invite, () -> {
                try {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "来加入我的家庭收纳吧！家收纳APP");
                    startActivity(Intent.createChooser(shareIntent, "分享"));
                } catch (Exception ex) {
                    Toast.makeText(getContext(), "分享失败", Toast.LENGTH_SHORT).show();
                }
            });

            // 操作日志
            setupMenuClick(v, R.id.menu_logs, () -> {
                Toast.makeText(getContext(), "操作日志功能开发中", Toast.LENGTH_SHORT).show();
            });

            // 提醒设置
            setupMenuClick(v, R.id.menu_reminder_settings, () -> {
                Toast.makeText(getContext(), "提醒设置功能开发中", Toast.LENGTH_SHORT).show();
            });

            // 账号安全
            setupMenuClick(v, R.id.menu_security, () -> {
                Toast.makeText(getContext(), "账号安全功能开发中", Toast.LENGTH_SHORT).show();
            });

            // 帮助与反馈
            setupMenuClick(v, R.id.menu_help, () -> {
                Toast.makeText(getContext(), "帮助与反馈功能开发中", Toast.LENGTH_SHORT).show();
            });

            loadProfile(tvNickname, tvItemCount, tvSpaceCount, tvOperations, tvHouseName, tvMemberCount, tvVersion);
        } catch (Exception e) {}
    }

    private void setupMenuClick(View v, int id, Runnable action) {
        try {
            View menu = v.findViewById(id);
            if (menu != null) {
                menu.setOnClickListener(e -> {
                    try { action.run(); } catch (Exception ex) {}
                });
            }
        } catch (Exception ignored) {}
    }

    private void loadProfile(TextView tvNickname, TextView tvItemCount, TextView tvSpaceCount,
                             TextView tvOperations, TextView tvHouseName, TextView tvMemberCount, TextView tvVersion) {
        try {
            ApiClient.get("user.php?action=profile", null, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        try {
                            if (tvNickname != null && data.has("user") && !data.get("user").isJsonNull()) {
                                JsonObject user = data.getAsJsonObject("user");
                                String name = "";
                                if (user.has("nickname") && !user.get("nickname").isJsonNull()) {
                                    name = user.get("nickname").getAsString();
                                }
                                if (name.isEmpty() && user.has("username") && !user.get("username").isJsonNull()) {
                                    name = user.get("username").getAsString();
                                }
                                if (name.isEmpty()) name = "用户";
                                tvNickname.setText(name);
                            }
                            if (data.has("stats") && !data.get("stats").isJsonNull()) {
                                JsonObject stats = data.getAsJsonObject("stats");
                                if (tvItemCount != null) tvItemCount.setText(String.valueOf(stats.has("items") ? stats.get("items").getAsInt() : 0));
                                if (tvSpaceCount != null) tvSpaceCount.setText(String.valueOf(stats.has("spaces") ? stats.get("spaces").getAsInt() : 0));
                                if (tvOperations != null) tvOperations.setText(String.valueOf(stats.has("operations") ? stats.get("operations").getAsInt() : 0));
                            }
                            if (data.has("houses") && !data.get("houses").isJsonNull()) {
                                JsonArray houses = data.getAsJsonArray("houses");
                                if (houses.size() > 0) {
                                    JsonObject house = houses.get(0).getAsJsonObject();
                                    if (tvHouseName != null) tvHouseName.setText(house.has("name") ? house.get("name").getAsString() : "我的家");
                                    if (tvMemberCount != null) tvMemberCount.setText((house.has("member_count") ? house.get("member_count").getAsInt() : 1) + " 位成员");
                                } else {
                                    if (tvHouseName != null) tvHouseName.setText("暂无家庭");
                                    if (tvMemberCount != null) tvMemberCount.setText("点击创建家庭");
                                }
                            }
                            if (tvVersion != null) {
                                if (data.has("app_version") && !data.get("app_version").isJsonNull() && data.get("app_version").isJsonObject()) {
                                    JsonObject ver = data.getAsJsonObject("app_version");
                                    tvVersion.setText("v" + ver.get("version_name").getAsString());
                                } else {
                                    tvVersion.setText("家收纳 v1.0.0");
                                }
                            }
                        } catch (Exception e) {}
                    });
                }
                @Override public void onError(String msg) {}
            });
        } catch (Exception e) {}
    }
}

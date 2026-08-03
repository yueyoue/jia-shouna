package com.jiashouna.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.util.HashMap;

public class FamilyShareActivity extends AppCompatActivity {
    private TextView tvHouseName, tvInviteCode, tvMemberCount;
    private LinearLayout llMembers;
    private TextView btnCopyCode, btnJoin, btnCreateHouse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family_share);

        tvHouseName = findViewById(R.id.tv_house_name);
        tvInviteCode = findViewById(R.id.tv_invite_code);
        tvMemberCount = findViewById(R.id.tv_member_count);
        llMembers = findViewById(R.id.ll_members);
        llHousesList = findViewById(R.id.ll_houses_list);
        btnCopyCode = findViewById(R.id.btn_copy_code);
        btnJoin = findViewById(R.id.btn_join);
        btnCreateHouse = findViewById(R.id.btn_create_house);

        // 返回按钮
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (btnCopyCode != null) {
            btnCopyCode.setOnClickListener(v -> {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    String code = tvInviteCode != null ? tvInviteCode.getText().toString() : "";
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("invite_code", code));
                    Toast.makeText(this, "已复制邀请码", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnJoin != null) btnJoin.setOnClickListener(v -> joinHouse());
        if (btnCreateHouse != null) btnCreateHouse.setOnClickListener(v -> showCreateHouseDialog());

        try {
            loadMembers();
        } catch (Exception e) {
            android.util.Log.e("FamilyShare", "loadMembers crash", e);
            Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout llHousesList;

    private void loadAllHouses(int currentHouseId) {
        ApiClient.get("house.php?action=list", null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
                        if (llHousesList == null) return;
                        llHousesList.removeAllViews();

                        if (list.size() == 0) {
                            TextView hint = new TextView(FamilyShareActivity.this);
                            hint.setText("暂无家庭，点击上方按钮创建");
                            hint.setTextSize(13);
                            hint.setTextColor(0xFF718096);
                            hint.setPadding(dp(4), dp(8), 0, 0);
                            llHousesList.addView(hint);
                            return;
                        }

                        for (int i = 0; i < list.size(); i++) {
                            JsonObject house = list.get(i).getAsJsonObject();
                            int hid = house.get("id").getAsInt();
                            String hname = house.get("name").getAsString();
                            int members = house.has("member_count") ? house.get("member_count").getAsInt() : 1;
                            String inviteCode = house.has("invite_code") ? house.get("invite_code").getAsString() : "";
                            boolean isCurrent = (hid == currentHouseId);

                            LinearLayout row = new LinearLayout(FamilyShareActivity.this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                            row.setPadding(dp(12), dp(10), dp(12), dp(10));
                            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            rowLp.bottomMargin = dp(6);
                            row.setLayoutParams(rowLp);
                            row.setBackgroundResource(isCurrent ? R.drawable.bg_card_warm : R.drawable.bg_card);

                            // 家庭图标
                            TextView icon = new TextView(FamilyShareActivity.this);
                            icon.setText(isCurrent ? "🏠" : "🏡");
                            icon.setTextSize(20);
                            icon.setPadding(0, 0, dp(8), 0);
                            row.addView(icon);

                            // 家庭名+成员数+邀请码
                            LinearLayout info = new LinearLayout(FamilyShareActivity.this);
                            info.setOrientation(LinearLayout.VERTICAL);
                            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                            info.setLayoutParams(infoLp);

                            TextView nameTv = new TextView(FamilyShareActivity.this);
                            nameTv.setText(hname + (isCurrent ? " (当前)" : ""));
                            nameTv.setTextSize(15);
                            nameTv.setTextColor(0xFF2D3748);
                            nameTv.setTypeface(null, isCurrent ? Typeface.BOLD : Typeface.NORMAL);
                            info.addView(nameTv);

                            TextView metaTv = new TextView(FamilyShareActivity.this);
                            metaTv.setText(members + " 位成员 · 邀请码: " + (inviteCode.isEmpty() ? "无" : inviteCode));
                            metaTv.setTextSize(12);
                            metaTv.setTextColor(0xFF718096);
                            info.addView(metaTv);

                            row.addView(info);

                            // 切换按钮
                            if (!isCurrent) {
                                TextView switchBtn = new TextView(FamilyShareActivity.this);
                                switchBtn.setText("切换");
                                switchBtn.setTextSize(13);
                                switchBtn.setTextColor(0xFFFF8C42);
                                switchBtn.setPadding(dp(8), 0, 0, 0);
                                switchBtn.setOnClickListener(v -> {
                                    App.getInstance().setCurrentHouseId(hid);
                                    App.getInstance().setCurrentHouseName(hname);
                                    loadMembers();
                                });
                                row.addView(switchBtn);
                            }

                            llHousesList.addView(row);
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void loadMembers() {
        int houseId = App.getInstance().getCurrentHouseId();

        // 先加载所有家庭列表
        loadAllHouses(houseId);

        if (houseId <= 0) {
            if (tvHouseName != null) tvHouseName.setText("暂无家庭");
            if (tvMemberCount != null) tvMemberCount.setText("0 位成员");
            if (tvInviteCode != null) tvInviteCode.setText("");
            if (llMembers != null) {
                llMembers.removeAllViews();
                TextView hint = new TextView(this);
                hint.setText("请先创建或加入一个家庭");
                hint.setTextSize(14);
                hint.setTextColor(0xFF718096);
                hint.setGravity(android.view.Gravity.CENTER);
                hint.setPadding(0, dp(20), 0, 0);
                llMembers.addView(hint);
            }
            return;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        ApiClient.get("house.php?action=members", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    if (data.has("list")) {
                        JsonArray list = data.getAsJsonArray("list");
                        tvMemberCount.setText(list.size() + " 位成员");
                        llMembers.removeAllViews();
                        String[] roles = {"", "管理员", "编辑", "只读"};
                        for (int i = 0; i < list.size(); i++) {
                            JsonObject m = list.get(i).getAsJsonObject();
                            View row = getLayoutInflater().inflate(R.layout.item_member, llMembers, false);
                            ((TextView) row.findViewById(R.id.tv_name)).setText(m.has("nickname") ? m.get("nickname").getAsString() : m.get("username").getAsString());
                            int role = m.get("role").getAsInt();
                            TextView tvRole = row.findViewById(R.id.tv_role);
                            tvRole.setText(roles[role]);
                            llMembers.addView(row);
                        }
                    }
                });
            }
            @Override public void onError(String msg) {}
        });

        // 加载邀请码
        HashMap<String, String> codeParams = new HashMap<>();
        codeParams.put("house_id", String.valueOf(houseId));
        ApiClient.get("house.php?action=invite_code", codeParams, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        if (data.has("code")) {
                            String code = data.get("code").getAsString();
                            if (tvInviteCode != null) tvInviteCode.setText(code);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                });
            }
            @Override public void onError(String msg) {
                // 邀请码加载失败时保持默认显示
            }
        });
    }

    private void joinHouse() {
        EditText et = new EditText(this);
        et.setHint("请输入邀请码");
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("加入房屋")
            .setView(et)
            .setPositiveButton("加入", (d, w) -> {
                String code = et.getText().toString().trim();
                if (code.isEmpty()) return;
                JsonObject body = new JsonObject();
                body.addProperty("invite_code", code);
                ApiClient.post("house.php?action=join", body, new ApiClient.ApiCallback() {
                    @Override public void onSuccess(JsonObject data) {
                        runOnUiThread(() -> {
                            Toast.makeText(FamilyShareActivity.this, "加入成功", Toast.LENGTH_SHORT).show();
                            loadMembers();
                        });
                    }
                    @Override public void onError(String msg) {
                        runOnUiThread(() -> Toast.makeText(FamilyShareActivity.this, msg, Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showCreateHouseDialog() {
        EditText et = new EditText(this);
        et.setHint("例如：我的家");
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("创建家庭")
            .setMessage("给你的家庭取个名字吧")
            .setView(et)
            .setPositiveButton("创建", (d, w) -> {
                String input = et.getText().toString().trim();
                final String houseName = input.isEmpty() ? "我的家" : input;
                JsonObject body = new JsonObject();
                body.addProperty("name", houseName);
                Toast.makeText(this, "正在创建...", Toast.LENGTH_SHORT).show();
                ApiClient.post("house.php?action=create", body, new ApiClient.ApiCallback() {
                    @Override public void onSuccess(JsonObject data) {
                        runOnUiThread(() -> {
                            try {
                                int newHouseId = data.has("id") ? data.get("id").getAsInt() : 0;
                                App.getInstance().setCurrentHouseId(newHouseId);
                                App.getInstance().setCurrentHouseName(houseName);
                                Toast.makeText(FamilyShareActivity.this, "创建成功！", Toast.LENGTH_SHORT).show();
                                loadMembers();
                            } catch (Exception e) {
                                Toast.makeText(FamilyShareActivity.this, "创建成功", Toast.LENGTH_SHORT).show();
                                loadMembers();
                            }
                        });
                    }
                    @Override public void onError(String msg) {
                        runOnUiThread(() -> {
                            Toast.makeText(FamilyShareActivity.this, "创建失败: " + msg, Toast.LENGTH_LONG).show();
                            android.util.Log.e("FamilyShare", "创建家庭失败: " + msg);
                        });
                    }
                });
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

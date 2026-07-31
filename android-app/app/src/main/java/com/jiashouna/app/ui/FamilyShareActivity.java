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
    private Button btnCopyCode, btnJoin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family_share);

        tvHouseName = findViewById(R.id.tv_house_name);
        tvInviteCode = findViewById(R.id.tv_invite_code);
        tvMemberCount = findViewById(R.id.tv_member_count);
        llMembers = findViewById(R.id.ll_members);
        btnCopyCode = findViewById(R.id.btn_copy_code);
        btnJoin = findViewById(R.id.btn_join);

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

        try {
            loadMembers();
        } catch (Exception e) {
            android.util.Log.e("FamilyShare", "loadMembers crash", e);
            Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadMembers() {
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            if (tvHouseName != null) tvHouseName.setText("暂无家庭");
            if (tvMemberCount != null) tvMemberCount.setText("0 位成员");
            if (tvInviteCode != null) tvInviteCode.setText("");
            if (llMembers != null) {
                llMembers.removeAllViews();

                // 显示创建家庭按钮
                TextView createBtn = new TextView(this);
                createBtn.setText("+ 创建家庭");
                createBtn.setTextSize(15);
                createBtn.setTextColor(0xFFFFFFFF);
                createBtn.setGravity(android.view.Gravity.CENTER);
                createBtn.setBackgroundResource(R.drawable.bg_button_primary_warm);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
                lp.setMargins(dp(16), dp(20), dp(16), 0);
                createBtn.setLayoutParams(lp);
                createBtn.setOnClickListener(v -> showCreateHouseDialog());
                llMembers.addView(createBtn);
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

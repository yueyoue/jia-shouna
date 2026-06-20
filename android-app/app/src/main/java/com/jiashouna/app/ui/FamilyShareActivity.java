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

        btnCopyCode.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("invite_code", tvInviteCode.getText().toString()));
            Toast.makeText(this, "已复制邀请码", Toast.LENGTH_SHORT).show();
        });

        btnJoin.setOnClickListener(v -> joinHouse());
        loadMembers();
    }

    private void loadMembers() {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(App.getInstance().getCurrentHouseId()));

        ApiClient.get("house/members", params, new ApiClient.ApiCallback() {
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
                ApiClient.post("house/join", body, new ApiClient.ApiCallback() {
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
}

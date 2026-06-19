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
    private TextView tvNickname, tvItemCount, tvSpaceCount, tvOperations;
    private TextView tvHouseName, tvMemberCount, tvVersion;
    private Button btnLogout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        tvNickname = v.findViewById(R.id.tv_nickname);
        tvItemCount = v.findViewById(R.id.tv_item_count);
        tvSpaceCount = v.findViewById(R.id.tv_space_count);
        tvOperations = v.findViewById(R.id.tv_operations);
        tvHouseName = v.findViewById(R.id.tv_house_name);
        tvMemberCount = v.findViewById(R.id.tv_member_count);
        tvVersion = v.findViewById(R.id.tv_version);
        btnLogout = v.findViewById(R.id.btn_logout);

        btnLogout.setOnClickListener(e -> {
            App.getInstance().logout();
            startActivity(new Intent(getActivity(), LoginActivity.class));
            getActivity().finish();
        });

        v.findViewById(R.id.btn_family_share).setOnClickListener(e -> 
            startActivity(new Intent(getActivity(), FamilyShareActivity.class)));

        loadProfile();
        return v;
    }

    private void loadProfile() {
        ApiClient.get("user.php", null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (data.has("user")) {
                        JsonObject user = data.getAsJsonObject("user");
                        tvNickname.setText(user.has("nickname") ? user.get("nickname").getAsString() : user.get("username").getAsString());
                    }
                    if (data.has("stats")) {
                        JsonObject stats = data.getAsJsonObject("stats");
                        tvItemCount.setText(String.valueOf(stats.get("items").getAsInt()));
                        tvSpaceCount.setText(String.valueOf(stats.get("spaces").getAsInt()));
                        tvOperations.setText(String.valueOf(stats.get("operations").getAsInt()));
                    }
                    if (data.has("houses")) {
                        JsonArray houses = data.getAsJsonArray("houses");
                        if (houses.size() > 0) {
                            JsonObject house = houses.get(0).getAsJsonObject();
                            tvHouseName.setText(house.get("name").getAsString());
                            tvMemberCount.setText(house.get("member_count").getAsInt() + " 位成员");
                        }
                    }
                    if (data.has("app_version")) {
                        JsonObject ver = data.getAsJsonObject("app_version");
                        tvVersion.setText("v" + ver.get("version_name").getAsString());
                    }
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void checkUpdate() {
        try {
            int currentCode = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionCode;
            java.util.HashMap<String, String> params = new java.util.HashMap<>();
            params.put("version_code", String.valueOf(currentCode));
            ApiClient.get("version.php", params, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (data.has("has_update") && data.get("has_update").getAsBoolean()) {
                            JsonObject latest = data.getAsJsonObject("latest");
                            new androidx.appcompat.app.AlertDialog.Builder(getActivity())
                                .setTitle("发现新版本 v" + latest.get("version_name").getAsString())
                                .setMessage("更新内容：\n" + (latest.has("changelog") ? latest.get("changelog").getAsString() : ""))
                                .setPositiveButton("立即更新", (d, w) -> {
                                    if (latest.has("apk_url") && !latest.get("apk_url").getAsString().isEmpty()) {
                                        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(latest.get("apk_url").getAsString()));
                                        startActivity(i);
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                        } else {
                            Toast.makeText(getContext(), "已是最新版本", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                @Override public void onError(String msg) {
                    if (getActivity() != null) getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(getContext(), "检查更新失败", Toast.LENGTH_SHORT).show();
        }
    }
}

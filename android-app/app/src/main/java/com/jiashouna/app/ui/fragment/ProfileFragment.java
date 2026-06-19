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
            if (getActivity() != null) getActivity().finish();
        });

        v.findViewById(R.id.btn_family_share).setOnClickListener(e -> 
            startActivity(new Intent(getActivity(), FamilyShareActivity.class)));

        loadProfile();
        return v;
    }

    private void loadProfile() {
        ApiClient.get("user.php?action=profile", null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        if (data.has("user") && !data.get("user").isJsonNull()) {
                            JsonObject user = data.getAsJsonObject("user");
                            String name = user.has("nickname") && !user.get("nickname").getAsString().isEmpty()
                                ? user.get("nickname").getAsString()
                                : (user.has("username") ? user.get("username").getAsString() : "用户");
                            tvNickname.setText(name);
                        }
                        if (data.has("stats") && !data.get("stats").isJsonNull()) {
                            JsonObject stats = data.getAsJsonObject("stats");
                            tvItemCount.setText(String.valueOf(stats.has("items") ? stats.get("items").getAsInt() : 0));
                            tvSpaceCount.setText(String.valueOf(stats.has("spaces") ? stats.get("spaces").getAsInt() : 0));
                            tvOperations.setText(String.valueOf(stats.has("operations") ? stats.get("operations").getAsInt() : 0));
                        }
                        if (data.has("houses") && !data.get("houses").isJsonNull()) {
                            JsonArray houses = data.getAsJsonArray("houses");
                            if (houses.size() > 0) {
                                JsonObject house = houses.get(0).getAsJsonObject();
                                tvHouseName.setText(house.has("name") ? house.get("name").getAsString() : "我的家");
                                tvMemberCount.setText((house.has("member_count") ? house.get("member_count").getAsInt() : 1) + " 位成员");
                            } else {
                                tvHouseName.setText("暂无家庭");
                                tvMemberCount.setText("0 位成员");
                            }
                        }
                        if (data.has("app_version") && !data.get("app_version").isJsonNull() && data.get("app_version").isJsonObject()) {
                            JsonObject ver = data.getAsJsonObject("app_version");
                            tvVersion.setText("v" + ver.get("version_name").getAsString());
                        } else {
                            tvVersion.setText("家收纳 v1.0.0");
                        }
                    } catch (Exception e) {
                        // 防止解析异常导致崩溃
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "加载失败: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }
}

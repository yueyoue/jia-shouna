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
import com.jiashouna.app.ui.AddItemActivity;
import com.jiashouna.app.ui.AddSpaceActivity;
import com.jiashouna.app.ui.FamilyShareActivity;

import java.util.HashMap;

public class HomeFragment extends Fragment {
    private TextView tvGreeting, tvHouseInfo, tvItemCount, tvSpaceCount, tvExpiringCount, tvMemberCount;
    private LinearLayout llExpiringList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);
        
        tvGreeting = v.findViewById(R.id.tv_greeting);
        tvHouseInfo = v.findViewById(R.id.tv_house_info);
        tvItemCount = v.findViewById(R.id.tv_item_count);
        tvSpaceCount = v.findViewById(R.id.tv_space_count);
        tvExpiringCount = v.findViewById(R.id.tv_expiring_count);
        tvMemberCount = v.findViewById(R.id.tv_member_count);
        llExpiringList = v.findViewById(R.id.ll_expiring_list);

        // 快捷入口
        v.findViewById(R.id.btn_scan).setOnClickListener(e -> startActivity(new Intent(getActivity(), AddItemActivity.class)));
        v.findViewById(R.id.btn_manual).setOnClickListener(e -> {
            Intent i = new Intent(getActivity(), AddItemActivity.class);
            i.putExtra("mode", "manual");
            startActivity(i);
        });
        v.findViewById(R.id.btn_add_space).setOnClickListener(e -> startActivity(new Intent(getActivity(), AddSpaceActivity.class)));
        v.findViewById(R.id.btn_family).setOnClickListener(e -> startActivity(new Intent(getActivity(), FamilyShareActivity.class)));

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
        tvGreeting.setText("下午好 👋");
        tvHouseInfo.setText(app.getCurrentHouseName());

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(app.getCurrentHouseId()));

        // 获取提醒统计
        ApiClient.get("reminder.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (data.has("stats")) {
                        JsonObject stats = data.getAsJsonObject("stats");
                        tvExpiringCount.setText(stats.has("expiring_7days") ? stats.get("expiring_7days").getAsString() : "0");
                    }
                });
            }
            @Override public void onError(String msg) {}
        });

        // 获取物品列表(简要)
        ApiClient.get("goods.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (data.has("total")) {
                        tvItemCount.setText(String.valueOf(data.get("total").getAsInt()));
                    }
                });
            }
            @Override public void onError(String msg) {}
        });

        // 获取临期物品
        HashMap<String, String> expParams = new HashMap<>();
        expParams.put("house_id", String.valueOf(app.getCurrentHouseId()));
        expParams.put("days", "7");
        ApiClient.get("goods.php?action=expiring", expParams, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    llExpiringList.removeAllViews();
                    if (data.has("list")) {
                        JsonArray list = data.getAsJsonArray("list");
                        for (int i = 0; i < Math.min(list.size(), 5); i++) {
                            JsonObject item = list.get(i).getAsJsonObject();
                            TextView tv = new TextView(getActivity());
                            tv.setText("⚠ " + item.get("name").getAsString() + " - " + (item.has("expiry_date") ? item.get("expiry_date").getAsString() : ""));
                            tv.setTextSize(13);
                            tv.setPadding(0, 8, 0, 8);
                            llExpiringList.addView(tv);
                        }
                    }
                });
            }
            @Override public void onError(String msg) {}
        });
    }
}

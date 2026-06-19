package com.jiashouna.app.ui.fragment;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.util.HashMap;

public class RemindersFragment extends Fragment {
    private LinearLayout llList;
    private TextView tvStats;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_reminders, container, false);
        llList = v.findViewById(R.id.ll_reminder_list);
        tvStats = v.findViewById(R.id.tv_stats);
        loadReminders();
        return v;
    }

    private void loadReminders() {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(App.getInstance().getCurrentHouseId()));

        // 获取统计
        ApiClient.get("reminder.php?action=stats", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (data.has("stats")) {
                        JsonObject stats = data.getAsJsonObject("stats");
                        int expiry = stats.has("expiring_7days") ? stats.get("expiring_7days").getAsInt() : 0;
                        int lowStock = stats.has("low_stock") ? stats.get("low_stock").getAsInt() : 0;
                        tvStats.setText(expiry + " 件临期 · " + lowStock + " 件库存不足");
                    }
                });
            }
            @Override public void onError(String msg) {}
        });

        // 获取提醒列表
        ApiClient.get("reminder.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    llList.removeAllViews();
                    if (data.has("list")) {
                        JsonArray list = data.getAsJsonArray("list");
                        for (int i = 0; i < list.size(); i++) {
                            JsonObject item = list.get(i).getAsJsonObject();
                            View row = LayoutInflater.from(getContext()).inflate(R.layout.item_reminder, llList, false);
                            ((TextView) row.findViewById(R.id.tv_title)).setText(item.get("title").getAsString());
                            ((TextView) row.findViewById(R.id.tv_content)).setText(
                                item.has("content") ? item.get("content").getAsString() : "");
                            llList.addView(row);
                        }
                        if (list.size() == 0) {
                            TextView empty = new TextView(getContext());
                            empty.setText("暂无提醒");
                            empty.setGravity(Gravity.CENTER);
                            empty.setPadding(0, 60, 0, 60);
                            llList.addView(empty);
                        }
                    }
                });
            }
            @Override public void onError(String msg) {}
        });
    }
}

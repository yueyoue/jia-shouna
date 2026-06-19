package com.jiashouna.app.ui.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.ui.AddSpaceActivity;
import java.util.HashMap;

public class SpacesFragment extends Fragment {
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private TextView tvError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_spaces, container, false);
        recyclerView = v.findViewById(R.id.recycler_spaces);
        emptyView = v.findViewById(R.id.empty_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        v.findViewById(R.id.btn_add_space).setOnClickListener(e -> 
            startActivity(new Intent(getActivity(), AddSpaceActivity.class)));

        loadSpaces();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSpaces();
    }

    private void loadSpaces() {
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            showEmpty("暂无家庭，请先创建一个家庭");
            return;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        ApiClient.get("space.php?action=tree", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        JsonArray list = null;
                        if (data.has("tree") && !data.get("tree").isJsonNull()) {
                            list = data.getAsJsonArray("tree");
                        } else if (data.has("list") && !data.get("list").isJsonNull()) {
                            list = data.getAsJsonArray("list");
                        }
                        if (list != null && list.size() > 0) {
                            emptyView.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                            recyclerView.setAdapter(new SpaceAdapter(list));
                        } else {
                            showEmpty("暂无收纳空间\n点击下方按钮创建");
                        }
                    } catch (Exception e) {
                        showEmpty("数据解析错误");
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    if (msg.contains("403") || msg.contains("不是该房屋成员")) {
                        showEmpty("暂无权限访问\n请联系家庭管理员");
                    } else {
                        showEmpty("加载失败: " + msg);
                    }
                });
            }
        });
    }

    private void showEmpty(String message) {
        emptyView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        // 在emptyView中显示提示
        if (emptyView.getChildCount() > 0) {
            View child = emptyView.getChildAt(0);
            if (child instanceof TextView) {
                ((TextView) child).setText(message);
            }
        }
    }

    class SpaceAdapter extends RecyclerView.Adapter<SpaceAdapter.VH> {
        JsonArray items;
        SpaceAdapter(JsonArray items) { this.items = items; }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_space_card, parent, false));
        }

        @Override public void onBindViewHolder(VH holder, int position) {
            try {
                JsonObject item = items.get(position).getAsJsonObject();
                holder.tvIcon.setText(item.has("icon") && !item.get("icon").isJsonNull() ? item.get("icon").getAsString() : "🏠");
                holder.tvName.setText(item.has("name") ? item.get("name").getAsString() : "");
                int itemCount = item.has("item_count") ? item.get("item_count").getAsInt() : 0;
                int expiringCount = item.has("expiring_count") ? item.get("expiring_count").getAsInt() : 0;
                holder.tvCount.setText("📦 " + itemCount + " 件");
                holder.tvExpiring.setText("⏰ " + expiringCount + " 临期");

                // 显示子空间
                if (item.has("children") && !item.get("children").isJsonNull()) {
                    JsonArray children = item.getAsJsonArray("children");
                    if (children.size() > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < Math.min(children.size(), 3); i++) {
                            JsonObject child = children.get(i).getAsJsonObject();
                            if (sb.length() > 0) sb.append(" · ");
                            sb.append(child.has("name") ? child.get("name").getAsString() : "");
                        }
                        holder.tvExpiring.setText(holder.tvExpiring.getText() + "\n" + sb.toString());
                    }
                }

                holder.itemView.setOnClickListener(e -> {
                    Toast.makeText(getContext(), "查看空间: " + item.get("name").getAsString(), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                holder.tvName.setText("数据错误");
            }
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvName, tvCount, tvExpiring;
            VH(View v) {
                super(v);
                tvIcon = v.findViewById(R.id.tv_icon);
                tvName = v.findViewById(R.id.tv_name);
                tvCount = v.findViewById(R.id.tv_count);
                tvExpiring = v.findViewById(R.id.tv_expiring);
            }
        }
    }
}

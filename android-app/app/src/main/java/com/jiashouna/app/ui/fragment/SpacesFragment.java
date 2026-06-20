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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.ui.AddSpaceActivity;
import java.util.HashMap;

public class SpacesFragment extends Fragment {
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private LinearLayout loadingView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_spaces, container, false);
        recyclerView = v.findViewById(R.id.recycler_spaces);
        emptyView = v.findViewById(R.id.empty_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        v.findViewById(R.id.btn_add_space).setOnClickListener(e -> 
            startActivity(new Intent(getActivity(), AddSpaceActivity.class)));

        loadData();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        int houseId = App.getInstance().getCurrentHouseId();

        // 先加载家列表
        ApiClient.get("house.php?action=list", null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                JsonArray houses = new JsonArray();
                try {
                    if (data.has("list") && !data.get("list").isJsonNull()) {
                        houses = data.getAsJsonArray("list");
                    }
                } catch (Exception ignored) {}
                final JsonArray houseList = houses;

                if (houseId > 0) {
                    // 有当前家，加载空间
                    loadSpaces(houseId, houseList);
                } else if (houseList.size() > 0) {
                    // 没有当前家但有家列表，使用第一个
                    JsonObject firstHouse = houseList.get(0).getAsJsonObject();
                    int newHouseId = firstHouse.get("id").getAsInt();
                    String houseName = firstHouse.has("name") ? firstHouse.get("name").getAsString() : "我的家";
                    App.getInstance().setCurrentHouseId(newHouseId);
                    App.getInstance().setCurrentHouseName(houseName);
                    loadSpaces(newHouseId, houseList);
                } else {
                    // 完全没有家
                    if (getActivity() != null) getActivity().runOnUiThread(() -> showEmpty("暂无家庭\n点击下方按钮创建一个家"));
                }
            }
            @Override public void onError(String msg) {
                // 即使加载家失败，也尝试加载空间
                if (houseId > 0) {
                    loadSpaces(houseId, new JsonArray());
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> showEmpty("暂无家庭\n点击下方按钮创建一个家"));
                }
            }
        });
    }

    private void loadSpaces(int houseId, JsonArray houseList) {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        ApiClient.get("space.php?action=tree", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        JsonArray allItems = new JsonArray();

                        // 添加"家"条目
                        for (int i = 0; i < houseList.size(); i++) {
                            JsonObject house = houseList.get(i).getAsJsonObject();
                            JsonObject houseItem = new JsonObject();
                            houseItem.addProperty("id", house.get("id").getAsInt());
                            houseItem.addProperty("name", house.has("name") ? house.get("name").getAsString() : "我的家");
                            houseItem.addProperty("icon", "🏠");
                            houseItem.addProperty("is_house", true);
                            houseItem.addProperty("item_count", house.has("item_count") ? house.get("item_count").getAsInt() : 0);
                            houseItem.addProperty("expiring_count", 0);
                            allItems.add(houseItem);
                        }

                        // 添加空间条目
                        JsonArray spaces = new JsonArray();
                        if (data.has("tree") && !data.get("tree").isJsonNull()) {
                            spaces = data.getAsJsonArray("tree");
                        } else if (data.has("list") && !data.get("list").isJsonNull()) {
                            spaces = data.getAsJsonArray("list");
                        }
                        for (int i = 0; i < spaces.size(); i++) {
                            allItems.add(spaces.get(i));
                        }

                        if (allItems.size() > 0) {
                            emptyView.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                            recyclerView.setAdapter(new SpaceAdapter(allItems));
                        } else {
                            showEmpty("暂无收纳空间\n点击下方按钮创建");
                        }
                    } catch (Exception e) {
                        showEmpty("数据解析错误");
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    // 即使空间加载失败，也显示家列表
                    if (houseList.size() > 0) {
                        JsonArray allItems = new JsonArray();
                        for (int i = 0; i < houseList.size(); i++) {
                            JsonObject house = houseList.get(i).getAsJsonObject();
                            JsonObject houseItem = new JsonObject();
                            houseItem.addProperty("id", house.get("id").getAsInt());
                            houseItem.addProperty("name", house.has("name") ? house.get("name").getAsString() : "我的家");
                            houseItem.addProperty("icon", "🏠");
                            houseItem.addProperty("is_house", true);
                            houseItem.addProperty("item_count", 0);
                            houseItem.addProperty("expiring_count", 0);
                            allItems.add(houseItem);
                        }
                        emptyView.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        recyclerView.setAdapter(new SpaceAdapter(allItems));
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

                // 如果是"家"条目，显示特殊样式
                boolean isHouse = item.has("is_house") && item.get("is_house").getAsBoolean();
                if (isHouse) {
                    holder.tvCount.setText("🏠 家庭");
                    holder.tvExpiring.setText("");
                }

                // 显示子空间
                if (!isHouse && item.has("children") && !item.get("children").isJsonNull()) {
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
                    if (isHouse) {
                        Toast.makeText(getContext(), "查看家庭: " + item.get("name").getAsString(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "查看空间: " + item.get("name").getAsString(), Toast.LENGTH_SHORT).show();
                    }
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

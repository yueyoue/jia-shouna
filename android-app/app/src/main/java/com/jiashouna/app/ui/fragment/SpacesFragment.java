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
import com.jiashouna.app.ui.SpaceDetailActivity;
import java.util.HashMap;

public class SpacesFragment extends Fragment {
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private LinearLayout loadingView;
    private JsonArray houseList = new JsonArray();
    private int currentViewingHouseId = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_spaces, container, false);
        recyclerView = v.findViewById(R.id.recycler_spaces);
        emptyView = v.findViewById(R.id.empty_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        v.findViewById(R.id.btn_add_space).setOnClickListener(e -> {
            Intent intent = new Intent(getActivity(), AddSpaceActivity.class);
            intent.putExtra("parent_id", 0);
            intent.putExtra("house_id", currentViewingHouseId > 0 ? currentViewingHouseId : App.getInstance().getCurrentHouseId());
            startActivity(intent);
        });

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
        if (currentViewingHouseId > 0) {
            houseId = currentViewingHouseId;
        }

        // 先加载家列表
        ApiClient.get("house/list", null, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                try {
                    if (data.has("list") && !data.get("list").isJsonNull()) {
                        houseList = data.getAsJsonArray("list");
                    }
                } catch (Exception ignored) {}

                if (houseId > 0) {
                    loadSpaces(houseId);
                } else if (houseList.size() > 0) {
                    JsonObject firstHouse = houseList.get(0).getAsJsonObject();
                    int newHouseId = firstHouse.get("id").getAsInt();
                    String houseName = firstHouse.has("name") ? firstHouse.get("name").getAsString() : "我的家";
                    App.getInstance().setCurrentHouseId(newHouseId);
                    App.getInstance().setCurrentHouseName(houseName);
                    currentViewingHouseId = newHouseId;
                    loadSpaces(newHouseId);
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> showEmpty("暂无家庭\n点击下方按钮创建一个家"));
                }
            }
            @Override public void onError(String msg) {
                if (houseId > 0) {
                    loadSpaces(houseId);
                } else {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> showEmpty("暂无家庭\n点击下方按钮创建一个家"));
                }
            }
        });
    }

    private void loadSpaces(int houseId) {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));

        ApiClient.get("space/tree", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        JsonArray allItems = new JsonArray();

                        // 添加"家"条目
                        for (int i = 0; i < houseList.size(); i++) {
                            JsonObject house = houseList.get(i).getAsJsonObject();
                            int hid = house.get("id").getAsInt();
                            JsonObject houseItem = new JsonObject();
                            houseItem.addProperty("id", hid);
                            houseItem.addProperty("name", house.has("name") ? house.get("name").getAsString() : "我的家");
                            houseItem.addProperty("icon", "🏠");
                            houseItem.addProperty("is_house", true);
                            houseItem.addProperty("item_count", house.has("item_count") ? house.get("item_count").getAsInt() : 0);
                            houseItem.addProperty("expiring_count", 0);
                            houseItem.addProperty("is_current", hid == houseId);
                            allItems.add(houseItem);

                            // 如果是当前查看的家，展开显示其子空间(房间)
                            if (hid == houseId) {
                                JsonArray tree = new JsonArray();
                                if (data.has("tree") && !data.get("tree").isJsonNull()) {
                                    tree = data.getAsJsonArray("tree");
                                } else if (data.has("list") && !data.get("list").isJsonNull()) {
                                    tree = data.getAsJsonArray("list");
                                }
                                for (int j = 0; j < tree.size(); j++) {
                                    JsonObject space = tree.get(j).getAsJsonObject();
                                    space.addProperty("is_child_of_current_house", true);
                                    allItems.add(space);
                                }
                            }
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

                boolean isHouse = item.has("is_house") && item.get("is_house").getAsBoolean();
                boolean isCurrentHouse = item.has("is_current") && item.get("is_current").getAsBoolean();

                if (isHouse) {
                    holder.tvCount.setText(isCurrentHouse ? "🏠 当前家庭" : "🏠 家庭");
                    holder.tvExpiring.setText("");
                    // 当前查看的家高亮
                    holder.itemView.setBackgroundColor(isCurrentHouse ? 0x0AFF8C42 : Color.WHITE);
                }

                // 子空间(房间)显示缩进
                boolean isChild = item.has("is_child_of_current_house") && item.get("is_child_of_current_house").getAsBoolean();
                if (isChild) {
                    holder.tvIcon.setText(item.has("icon") ? item.get("icon").getAsString() : "🛋");
                    // 显示子空间信息
                    int childCount = 0;
                    if (item.has("children") && !item.get("children").isJsonNull()) {
                        childCount = item.getAsJsonArray("children").size();
                    }
                    if (childCount > 0) {
                        holder.tvExpiring.setText("📂 " + childCount + " 个子空间");
                    }
                }

                holder.itemView.setOnClickListener(e -> {
                    int itemId = item.has("id") ? item.get("id").getAsInt() : 0;
                    String itemName = item.has("name") ? item.get("name").getAsString() : "";
                    if (isHouse) {
                        // 切换到该家并刷新空间列表
                        currentViewingHouseId = itemId;
                        App.getInstance().setCurrentHouseId(itemId);
                        App.getInstance().setCurrentHouseName(itemName);
                        loadData();
                    } else {
                        // 打开空间详情
                        Intent intent = new Intent(getActivity(), SpaceDetailActivity.class);
                        intent.putExtra("space_id", itemId);
                        intent.putExtra("space_name", itemName);
                        intent.putExtra("house_id", currentViewingHouseId > 0 ? currentViewingHouseId : App.getInstance().getCurrentHouseId());
                        intent.putExtra("space_level", item.has("level") ? item.get("level").getAsInt() : 1);
                        startActivity(intent);
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

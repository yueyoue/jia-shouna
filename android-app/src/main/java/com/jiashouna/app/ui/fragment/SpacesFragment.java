package com.jiashouna.app.ui.fragment;

import android.content.Intent;
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
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(App.getInstance().getCurrentHouseId()));

        ApiClient.get("space.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (data.has("list")) {
                        JsonArray list = data.getAsJsonArray("list");
                        if (list.size() == 0) {
                            emptyView.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                            recyclerView.setAdapter(new SpaceAdapter(list));
                        }
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    class SpaceAdapter extends RecyclerView.Adapter<SpaceAdapter.VH> {
        JsonArray items;
        SpaceAdapter(JsonArray items) { this.items = items; }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_space_card, parent, false));
        }

        @Override public void onBindViewHolder(VH holder, int position) {
            JsonObject item = items.get(position).getAsJsonObject();
            holder.tvIcon.setText(item.has("icon") ? item.get("icon").getAsString() : "🏠");
            holder.tvName.setText(item.get("name").getAsString());
            holder.tvCount.setText("📦 " + item.get("item_count").getAsInt() + " 件");
            holder.tvExpiring.setText("⏰ " + (item.has("expiring_count") ? item.get("expiring_count").getAsInt() : 0) + " 临期");
            holder.itemView.setOnClickListener(e -> {
                // 进入空间详情
                Toast.makeText(getContext(), "查看空间: " + item.get("name").getAsString(), Toast.LENGTH_SHORT).show();
            });
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

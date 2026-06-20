package com.jiashouna.app.ui.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.gson.*;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.ui.AddSpaceActivity;
import com.jiashouna.app.ui.SpaceDetailActivity;
import java.util.*;

public class SpacesFragment extends Fragment {
    private LinearLayout layoutHouses, layoutSpacesSection, layoutSpaces, emptyView;
    private TextView tvSelectedHouse, tvSpaceCount;
    private int selectedHouseId = 0;
    private JsonArray houses = new JsonArray();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_spaces, container, false);
        layoutHouses = v.findViewById(R.id.layout_houses);
        layoutSpacesSection = v.findViewById(R.id.layout_spaces_section);
        layoutSpaces = v.findViewById(R.id.layout_spaces);
        emptyView = v.findViewById(R.id.empty_view);
        tvSelectedHouse = v.findViewById(R.id.tv_selected_house);
        tvSpaceCount = v.findViewById(R.id.tv_space_count);

        v.findViewById(R.id.btn_add_space).setOnClickListener(x -> {
            startActivity(new Intent(getActivity(), AddSpaceActivity.class));
        });

        loadHouses();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHouses();
    }

    private void loadHouses() {
        int userId = App.getInstance().getUserId();
        if (userId <= 0) return;

        HashMap<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(userId));
        ApiClient.get("house.php?action=list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        if (data.has("list")) {
                            houses = data.getAsJsonArray("list");
                            renderHouses();
                            if (houses.size() > 0) {
                                JsonObject first = houses.get(0).getAsJsonObject();
                                int hid = first.get("id").getAsInt();
                                selectHouse(hid);
                            }
                        }
                    } catch (Exception e) {
                        showEmpty();
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> showEmpty());
                }
            }
        });
    }

    private void renderHouses() {
        layoutHouses.removeAllViews();
        if (houses.size() == 0) {
            showEmpty();
            return;
        }
        emptyView.setVisibility(View.GONE);

        // Render houses in 2-column grid
        for (int i = 0; i < houses.size(); i += 2) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            row.addView(createHouseCard(houses.get(i).getAsJsonObject()));

            if (i + 1 < houses.size()) {
                View spacer = new View(getContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 0));
                row.addView(spacer);
                row.addView(createHouseCard(houses.get(i + 1).getAsJsonObject()));
            } else {
                // Empty space for alignment
                View empty = new View(getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 0);
                lp.weight = 1;
                empty.setLayoutParams(lp);
                row.addView(empty);
            }

            layoutHouses.addView(row);

            // Add vertical spacing between rows
            if (i + 2 < houses.size()) {
                View spacer = new View(getContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, dp(12)));
                layoutHouses.addView(spacer);
            }
        }
    }

    private View createHouseCard(JsonObject house) {
        int id = house.get("id").getAsInt();
        String name = house.has("name") ? house.get("name").getAsString() : "我的家";
        int itemCount = house.has("item_count") ? house.get("item_count").getAsInt() : 0;
        int spaceCount = house.has("space_count") ? house.get("space_count").getAsInt() : 0;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_16);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.weight = 1;
        card.setLayoutParams(lp);

        // Icon
        TextView icon = new TextView(getContext());
        icon.setText("🏠");
        icon.setTextSize(28);
        icon.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        iconLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(iconLp);
        icon.setBackgroundResource(R.drawable.bg_gradient_orange);
        card.addView(icon);

        // Name
        TextView tvName = new TextView(getContext());
        tvName.setText(name);
        tvName.setTextSize(14);
        tvName.setTextColor(Color.parseColor("#2D3748"));
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setGravity(android.view.Gravity.CENTER);
        tvName.setPadding(0, dp(8), 0, 0);
        card.addView(tvName);

        // Stats
        TextView tvStats = new TextView(getContext());
        tvStats.setText(itemCount + "件物品 · " + spaceCount + "个空间");
        tvStats.setTextSize(11);
        tvStats.setTextColor(Color.parseColor("#718096"));
        tvStats.setGravity(android.view.Gravity.CENTER);
        tvStats.setPadding(0, dp(4), 0, 0);
        card.addView(tvStats);

        card.setOnClickListener(v -> selectHouse(id));

        // Highlight if selected
        if (id == selectedHouseId) {
            card.setBackgroundResource(R.drawable.bg_icon_orange_light);
        }

        return card;
    }

    private void selectHouse(int houseId) {
        selectedHouseId = houseId;
        App.getInstance().setCurrentHouseId(houseId);
        renderHouses(); // Re-render to update highlight
        loadSpaces(houseId);
    }

    private void loadSpaces(int houseId) {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(houseId));
        ApiClient.get("space.php?action=tree", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        JsonArray tree = data.has("tree") ? data.getAsJsonArray("tree") : new JsonArray();
                        renderSpaces(tree, houseId);
                    } catch (Exception e) {
                        layoutSpacesSection.setVisibility(View.GONE);
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> layoutSpacesSection.setVisibility(View.GONE));
                }
            }
        });
    }

    private void renderSpaces(JsonArray tree, int houseId) {
        layoutSpacesSection.setVisibility(View.VISIBLE);
        layoutSpaces.removeAllViews();

        // Find house name
        for (int i = 0; i < houses.size(); i++) {
            JsonObject h = houses.get(i).getAsJsonObject();
            if (h.get("id").getAsInt() == houseId) {
                tvSelectedHouse.setText("📁 " + h.get("name").getAsString() + " 的收纳空间");
                break;
            }
        }

        if (tree.size() == 0) {
            tvSpaceCount.setText("暂无空间");
            TextView empty = new TextView(getContext());
            empty.setText("点击右上角 + 新建 创建收纳空间");
            empty.setTextColor(Color.parseColor("#A0AEC0"));
            empty.setTextSize(12);
            empty.setPadding(0, dp(16), 0, 0);
            empty.setGravity(android.view.Gravity.CENTER);
            layoutSpaces.addView(empty);
            return;
        }

        int totalSpaces = countSpaces(tree);
        tvSpaceCount.setText(totalSpaces + " 个空间");

        for (int i = 0; i < tree.size(); i++) {
            JsonObject space = tree.get(i).getAsJsonObject();
            layoutSpaces.addView(createSpaceItem(space, 0));

            // Add children
            if (space.has("children") && !space.get("children").isJsonNull()) {
                JsonArray children = space.getAsJsonArray("children");
                for (int j = 0; j < children.size(); j++) {
                    layoutSpaces.addView(createSpaceItem(children.get(j).getAsJsonObject(), 1));
                }
            }
        }
    }

    private View createSpaceItem(JsonObject space, int level) {
        int id = space.get("id").getAsInt();
        String name = space.has("name") ? space.get("name").getAsString() : "";
        String icon = space.has("icon") && !space.get("icon").isJsonNull() ? space.get("icon").getAsString() : "📦";
        int itemCount = space.has("item_count") ? space.get("item_count").getAsInt() : 0;

        LinearLayout item = new LinearLayout(getContext());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setBackgroundResource(R.drawable.bg_card_16);
        item.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        if (level > 0) {
            lp.setMarginStart(dp(20));
        }
        item.setLayoutParams(lp);

        // Icon
        TextView tvIcon = new TextView(getContext());
        tvIcon.setText(icon);
        tvIcon.setTextSize(18);
        tvIcon.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        tvIcon.setLayoutParams(iconLp);
        tvIcon.setBackgroundResource(level == 0 ? R.drawable.bg_gradient_orange : R.drawable.bg_quick_green);
        item.addView(tvIcon);

        // Info
        LinearLayout info = new LinearLayout(getContext());
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.weight = 1;
        infoLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        infoLp.setMarginStart(dp(10));
        info.setLayoutParams(infoLp);

        TextView tvName = new TextView(getContext());
        tvName.setText(name);
        tvName.setTextSize(14);
        tvName.setTextColor(Color.parseColor("#2D3748"));
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        info.addView(tvName);

        TextView tvDesc = new TextView(getContext());
        tvDesc.setText(level == 0 ? "房间" : "容器");
        tvDesc.setTextSize(11);
        tvDesc.setTextColor(Color.parseColor("#A0AEC0"));
        info.addView(tvDesc);

        item.addView(info);

        // Count
        TextView tvCount = new TextView(getContext());
        tvCount.setText(itemCount + "");
        tvCount.setTextSize(13);
        tvCount.setTextColor(Color.parseColor("#FF8C42"));
        tvCount.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCount.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        countLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        tvCount.setLayoutParams(countLp);
        item.addView(tvCount);

        // Arrow
        TextView arrow = new TextView(getContext());
        arrow.setText("›");
        arrow.setTextSize(16);
        arrow.setTextColor(Color.parseColor("#CBD5E0"));
        arrow.setGravity(android.view.Gravity.CENTER);
        item.addView(arrow);

        item.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SpaceDetailActivity.class);
            intent.putExtra("space_id", id);
            intent.putExtra("space_name", name);
            intent.putExtra("house_id", houseId);
            startActivity(intent);
        });

        return item;
    }

    private int countSpaces(JsonArray tree) {
        int count = tree.size();
        for (int i = 0; i < tree.size(); i++) {
            JsonObject s = tree.get(i).getAsJsonObject();
            if (s.has("children") && !s.get("children").isJsonNull()) {
                count += s.getAsJsonArray("children").size();
            }
        }
        return count;
    }

    private void showEmpty() {
        emptyView.setVisibility(View.VISIBLE);
        layoutHouses.removeAllViews();
        layoutSpacesSection.setVisibility(View.GONE);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

package com.jiashouna.app.ui.fragment;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private LinearLayout layoutSpaces;
    private LinearLayout emptyView;
    private LinearLayout addSpaceHint;
    private TextView tvSelectedHouse;
    private int selectedHouseId = 0;
    private JsonArray houses = new JsonArray();

    // Track expanded state of rooms
    private HashMap<Integer, Boolean> expandedState = new HashMap<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_spaces, container, false);

        layoutSpaces = v.findViewById(R.id.layout_spaces);
        emptyView = v.findViewById(R.id.empty_view);
        addSpaceHint = v.findViewById(R.id.btn_add_space_hint);
        tvSelectedHouse = v.findViewById(R.id.tv_selected_house);

        // 新建按钮
        v.findViewById(R.id.btn_add_space).setOnClickListener(x ->
            startActivity(new Intent(getActivity(), AddSpaceActivity.class)));

        // 搜索按钮
        v.findViewById(R.id.btn_search).setOnClickListener(x -> {
            // TODO: search spaces
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
        if (userId <= 0) {
            showEmpty();
            return;
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(userId));
        ApiClient.get("house.php?action=list", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        if (data.has("list")) {
                            houses = data.getAsJsonArray("list");
                            if (houses.size() > 0) {
                                JsonObject first = houses.get(0).getAsJsonObject();
                                int hid = first.get("id").getAsInt();
                                selectHouse(hid);
                            } else {
                                showEmpty();
                            }
                        } else {
                            showEmpty();
                        }
                    } catch (Exception e) {
                        showEmpty();
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void selectHouse(int houseId) {
        selectedHouseId = houseId;
        App.getInstance().setCurrentHouseId(houseId);

        // Update house name
        for (int i = 0; i < houses.size(); i++) {
            JsonObject h = houses.get(i).getAsJsonObject();
            if (h.get("id").getAsInt() == houseId) {
                tvSelectedHouse.setText(h.get("name").getAsString());
                break;
            }
        }

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
                        renderSpaces(tree);
                    } catch (Exception e) {
                        showEmpty();
                    }
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void renderSpaces(JsonArray tree) {
        layoutSpaces.removeAllViews();
        emptyView.setVisibility(View.GONE);

        if (tree.size() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            addSpaceHint.setVisibility(View.GONE);
            return;
        }

        addSpaceHint.setVisibility(View.VISIBLE);

        for (int i = 0; i < tree.size(); i++) {
            JsonObject room = tree.get(i).getAsJsonObject();
            int roomId = room.get("id").getAsInt();

            // Check if this room has children
            boolean hasChildren = room.has("children") && !room.get("children").isJsonNull()
                && room.getAsJsonArray("children").size() > 0;

            // Room card container (white card with border)
            LinearLayout roomCard = new LinearLayout(getActivity());
            roomCard.setOrientation(LinearLayout.VERTICAL);
            roomCard.setBackgroundResource(R.drawable.bg_room_card);
            LinearLayout.LayoutParams roomLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) roomLp.topMargin = dp(12);
            roomCard.setLayoutParams(roomLp);

            // Room header (clickable row)
            LinearLayout roomHeader = createRoomHeader(room, hasChildren);
            roomCard.addView(roomHeader);

            // Sub-items container (initially hidden if expanded state says so)
            LinearLayout subItemsContainer = new LinearLayout(getActivity());
            subItemsContainer.setOrientation(LinearLayout.VERTICAL);
            subItemsContainer.setBackgroundResource(R.drawable.bg_sub_items_container);
            subItemsContainer.setPadding(dp(0), dp(0), dp(0), dp(8));

            boolean isExpanded = expandedState.containsKey(roomId) ? expandedState.get(roomId) : true;
            subItemsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            if (hasChildren) {
                JsonArray children = room.getAsJsonArray("children");
                for (int j = 0; j < children.size(); j++) {
                    JsonObject child = children.get(j).getAsJsonObject();
                    View childItem = createSubItem(child, j == children.size() - 1);
                    subItemsContainer.addView(childItem);
                }
            }

            roomCard.addView(subItemsContainer);

            // Toggle expand/collapse on header click
            final int finalRoomId = roomId;
            final LinearLayout finalSubItems = subItemsContainer;
            final TextView[] arrowRef = new TextView[1];
            // Find the arrow in header
            roomHeader.post(() -> {
                for (int k = 0; k < ((LinearLayout) roomHeader).getChildCount(); k++) {
                    View child = ((LinearLayout) roomHeader).getChildAt(k);
                    if (child instanceof TextView) {
                        String text = ((TextView) child).getText().toString();
                        if (text.equals("▼") || text.equals("▶")) {
                            arrowRef[0] = (TextView) child;
                            break;
                        }
                    }
                }
            });

            // Single click: navigate to space detail
            roomHeader.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), SpaceDetailActivity.class);
                intent.putExtra("space_id", finalRoomId);
                intent.putExtra("space_name", room.has("name") ? room.get("name").getAsString() : "");
                intent.putExtra("house_id", selectedHouseId);
                startActivity(intent);
            });

            if (hasChildren) {
                // Long click: toggle expand/collapse
                roomHeader.setOnLongClickListener(v -> {
                    boolean currentlyExpanded = finalSubItems.getVisibility() == View.VISIBLE;
                    if (currentlyExpanded) {
                        finalSubItems.setVisibility(View.GONE);
                        expandedState.put(finalRoomId, false);
                        if (arrowRef[0] != null) arrowRef[0].setText("▶");
                    } else {
                        finalSubItems.setVisibility(View.VISIBLE);
                        expandedState.put(finalRoomId, true);
                        if (arrowRef[0] != null) arrowRef[0].setText("▼");
                    }
                    return true;
                });
            }

            layoutSpaces.addView(roomCard);
        }
    }

    private LinearLayout createRoomHeader(JsonObject room, boolean hasChildren) {
        int id = room.get("id").getAsInt();
        String name = room.has("name") ? room.get("name").getAsString() : "";
        String icon = room.has("icon") && !room.get("icon").isJsonNull() ? room.get("icon").getAsString() : "🏠";
        int itemCount = room.has("item_count") ? room.get("item_count").getAsInt() : 0;
        boolean isExpanded = expandedState.containsKey(id) ? expandedState.get(id) : true;

        LinearLayout header = new LinearLayout(getActivity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(16));
        header.setClickable(true);
        header.setFocusable(true);

        // Add ripple effect
        android.util.TypedValue outValue = new android.util.TypedValue();
        getActivity().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        header.setBackgroundResource(outValue.resourceId);

        // Icon
        TextView tvIcon = new TextView(getActivity());
        tvIcon.setText(icon);
        tvIcon.setTextSize(24);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconLp.gravity = Gravity.CENTER_VERTICAL;
        tvIcon.setLayoutParams(iconLp);
        header.addView(tvIcon);

        // Info
        LinearLayout info = new LinearLayout(getActivity());
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.gravity = Gravity.CENTER_VERTICAL;
        infoLp.leftMargin = dp(12);
        info.setLayoutParams(infoLp);

        TextView tvName = new TextView(getActivity());
        tvName.setText(name);
        tvName.setTextSize(18);
        tvName.setTextColor(Color.parseColor("#121C2C"));
        tvName.setTypeface(null, Typeface.BOLD);
        info.addView(tvName);

        TextView tvDesc = new TextView(getActivity());
        tvDesc.setText(itemCount + " 件物品");
        tvDesc.setTextSize(12);
        tvDesc.setTextColor(Color.parseColor("#564338"));
        info.addView(tvDesc);

        header.addView(info);

        // Expand arrow (only if has children)
        if (hasChildren) {
            TextView arrow = new TextView(getActivity());
            arrow.setText(isExpanded ? "▼" : "▶");
            arrow.setTextSize(14);
            arrow.setTextColor(Color.parseColor("#564338"));
            LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            arrowLp.gravity = Gravity.CENTER_VERTICAL;
            arrow.setLayoutParams(arrowLp);
            header.addView(arrow);
        }

        return header;
    }

    private View createSubItem(JsonObject space, boolean isLast) {
        int id = space.get("id").getAsInt();
        String name = space.has("name") ? space.get("name").getAsString() : "";
        String icon = space.has("icon") && !space.get("icon").isJsonNull() ? space.get("icon").getAsString() : "📦";
        int itemCount = space.has("item_count") ? space.get("item_count").getAsInt() : 0;

        // Outer container with tree line effect
        FrameLayout treeContainer = new FrameLayout(getActivity());
        LinearLayout.LayoutParams treeLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        treeContainer.setLayoutParams(treeLp);

        // Vertical tree line
        View verticalLine = new View(getActivity());
        verticalLine.setBackgroundColor(Color.parseColor("#E2E8F0"));
        FrameLayout.LayoutParams vertLp = new FrameLayout.LayoutParams(dp(2), FrameLayout.LayoutParams.MATCH_PARENT);
        vertLp.leftMargin = dp(23);
        // For last item, don't extend to bottom
        if (isLast) {
            // Use a shorter line
            FrameLayout.LayoutParams shortLineLp = new FrameLayout.LayoutParams(dp(2), dp(24));
            shortLineLp.leftMargin = dp(23);
            shortLineLp.topMargin = dp(0);
            verticalLine.setLayoutParams(shortLineLp);
        } else {
            verticalLine.setLayoutParams(vertLp);
        }
        treeContainer.addView(verticalLine);

        // Horizontal tree line
        View horizontalLine = new View(getActivity());
        horizontalLine.setBackgroundColor(Color.parseColor("#E2E8F0"));
        FrameLayout.LayoutParams horizLp = new FrameLayout.LayoutParams(dp(14), dp(2));
        horizLp.leftMargin = dp(23);
        horizLp.topMargin = dp(23);
        horizontalLine.setLayoutParams(horizLp);
        treeContainer.addView(horizontalLine);

        // Content row
        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(44), dp(12), dp(16), dp(12));
        row.setClickable(true);
        row.setFocusable(true);

        android.util.TypedValue outValue = new android.util.TypedValue();
        getActivity().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        // Container icon
        TextView tvIcon = new TextView(getActivity());
        tvIcon.setText(icon);
        tvIcon.setTextSize(20);
        row.addView(tvIcon);

        // Info
        LinearLayout info = new LinearLayout(getActivity());
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoLp.leftMargin = dp(12);
        info.setLayoutParams(infoLp);

        TextView tvName = new TextView(getActivity());
        tvName.setText(name);
        tvName.setTextSize(16);
        tvName.setTextColor(Color.parseColor("#121C2C"));
        info.addView(tvName);

        TextView tvCount = new TextView(getActivity());
        tvCount.setText(itemCount + " 件");
        tvCount.setTextSize(12);
        tvCount.setTextColor(Color.parseColor("#564338"));
        info.addView(tvCount);

        row.addView(info);

        // More menu button
        TextView moreBtn = new TextView(getActivity());
        moreBtn.setText("⋮");
        moreBtn.setTextSize(18);
        moreBtn.setTextColor(Color.parseColor("#564338"));
        moreBtn.setGravity(Gravity.CENTER);
        moreBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        moreBtn.setOnClickListener(v -> showSpaceContextMenu(v, id, name));
        row.addView(moreBtn);

        treeContainer.addView(row);

        // Click to navigate
        row.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SpaceDetailActivity.class);
            intent.putExtra("space_id", id);
            intent.putExtra("space_name", name);
            intent.putExtra("house_id", selectedHouseId);
            startActivity(intent);
        });

        return treeContainer;
    }

    private void showSpaceContextMenu(View anchor, int spaceId, String spaceName) {
        PopupMenu popup = new PopupMenu(getActivity(), anchor);
        popup.getMenu().add(0, 1, 0, "查看详情");
        popup.getMenu().add(0, 2, 0, "删除");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    Intent intent = new Intent(getActivity(), SpaceDetailActivity.class);
                    intent.putExtra("space_id", spaceId);
                    intent.putExtra("space_name", spaceName);
                    intent.putExtra("house_id", selectedHouseId);
                    startActivity(intent);
                    return true;
                case 2:
                    confirmDeleteSpace(spaceId, spaceName);
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDeleteSpace(int spaceId, String spaceName) {
        new android.app.AlertDialog.Builder(getActivity())
            .setTitle("删除空间")
            .setMessage("确定要删除「" + spaceName + "」吗？\n该空间下的所有子空间和物品将被移除。")
            .setPositiveButton("删除", (d, w) -> deleteSpace(spaceId))
            .setNegativeButton("取消", null)
            .show();
    }

    private void deleteSpace(int spaceId) {
        JsonObject body = new JsonObject();
        body.addProperty("id", spaceId);
        ApiClient.post("space.php?action=delete", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                    loadSpaces(selectedHouseId);
                });
            }
            @Override public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "删除失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showEmpty() {
        emptyView.setVisibility(View.VISIBLE);
        layoutSpaces.removeAllViews();
        addSpaceHint.setVisibility(View.GONE);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

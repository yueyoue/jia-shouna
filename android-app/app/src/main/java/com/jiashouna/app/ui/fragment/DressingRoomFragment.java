package com.jiashouna.app.ui.fragment;

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
import com.jiashouna.app.ui.OutfitCreateActivity;
import java.util.*;

/**
 * 衣帽间 - 套装浏览
 */
public class DressingRoomFragment extends Fragment {
    private LinearLayout layoutOutfits;
    private LinearLayout emptyView;
    private EditText etSearch;
    private LinearLayout layoutSeasonChips;
    private String selectedSeason = "";
    private String selectedOccasion = "";
    private TextView tvSelectedHouse;

    private static final String[] SEASONS = {"全部", "春", "夏", "秋", "冬", "四季"};
    private static final String[] OCCASIONS = {"全部", "通勤", "运动", "约会", "居家", "正装", "休闲"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_dressing_room, container, false);

        layoutOutfits = v.findViewById(R.id.layout_outfits);
        emptyView = v.findViewById(R.id.empty_view);
        etSearch = v.findViewById(R.id.et_search);
        layoutSeasonChips = v.findViewById(R.id.layout_season_chips);
        tvSelectedHouse = v.findViewById(R.id.tv_selected_house);

        // 新建套装按钮
        v.findViewById(R.id.btn_create_outfit).setOnClickListener(x ->
            startActivity(new Intent(getActivity(), OutfitCreateActivity.class)));

        // 搜索
        etSearch.setOnEditorActionListener((v1, actionId, event) -> {
            loadOutfits();
            return true;
        });

        setupSeasonChips();
        setupOccasionChips(v);
        loadOutfits();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOutfits();
    }

    private void setupSeasonChips() {
        layoutSeasonChips.removeAllViews();
        for (String season : SEASONS) {
            TextView chip = new TextView(getActivity());
            chip.setText(season.equals("全部") ? "全部" : season);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            int px = dp(14);
            chip.setPadding(px, dp(8), px, dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
            if (layoutSeasonChips.indexOfChild(chip) > 0) lp.setMarginStart(dp(8));
            chip.setLayoutParams(lp);

            boolean isActive = (season.equals("全部") && selectedSeason.isEmpty())
                || season.equals(selectedSeason);
            chip.setBackgroundResource(isActive ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
            chip.setTextColor(isActive ? 0xFFFFFFFF : 0xFF718096);

            chip.setOnClickListener(v -> {
                selectedSeason = season.equals("全部") ? "" : season;
                setupSeasonChips();
                loadOutfits();
            });
            layoutSeasonChips.addView(chip);
        }
    }

    private void setupOccasionChips(View root) {
        LinearLayout layoutOccasion = root.findViewById(R.id.layout_occasion_chips);
        if (layoutOccasion == null) return;
        layoutOccasion.removeAllViews();
        for (String occ : OCCASIONS) {
            TextView chip = new TextView(getActivity());
            chip.setText(occ.equals("全部") ? "全部场合" : occ);
            chip.setTextSize(12);
            chip.setGravity(Gravity.CENTER);
            int px = dp(12);
            chip.setPadding(px, dp(6), px, dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
            if (layoutOccasion.indexOfChild(chip) > 0) lp.setMarginStart(dp(6));
            chip.setLayoutParams(lp);

            boolean isActive = (occ.equals("全部") && selectedOccasion.isEmpty())
                || occ.equals(selectedOccasion);
            chip.setBackgroundResource(isActive ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
            chip.setTextColor(isActive ? 0xFFFFFFFF : 0xFF718096);

            chip.setOnClickListener(v -> {
                selectedOccasion = occ.equals("全部") ? "" : occ;
                setupOccasionChips(root);
                loadOutfits();
            });
            layoutOccasion.addView(chip);
        }
    }

    private void loadOutfits() {
        int houseId = App.getInstance().getCurrentHouseId();
        if (houseId <= 0) {
            showEmpty("暂无家庭", "请先在「我的」中创建或加入家庭");
            return;
        }

        tvSelectedHouse.setText(App.getInstance().getCurrentHouseName());

        StringBuilder url = new StringBuilder("outfit.php?action=list&house_id=" + houseId);
        if (!selectedSeason.isEmpty()) url.append("&season=").append(selectedSeason);
        if (!selectedOccasion.isEmpty()) url.append("&occasion=").append(selectedOccasion);
        String keyword = etSearch.getText().toString().trim();
        if (!keyword.isEmpty()) url.append("&keyword=").append(keyword);

        ApiClient.get(url.toString(), null, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JsonObject data) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        JsonArray list = data.has("list") ? data.getAsJsonArray("list") : new JsonArray();
                        if (list.size() == 0) {
                            showEmpty("还没有套装", "点击右上角创建你的第一个穿搭组合");
                        } else {
                            renderOutfits(list);
                        }
                    } catch (Exception e) {
                        showEmpty("加载失败", "请稍后重试");
                    }
                });
            }

            @Override
            public void onError(String msg) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                    showEmpty("加载失败", msg));
            }
        });
    }

    private void renderOutfits(JsonArray list) {
        layoutOutfits.removeAllViews();
        emptyView.setVisibility(View.GONE);

        // 2列网格
        for (int i = 0; i < list.size(); i += 2) {
            LinearLayout row = new LinearLayout(getActivity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            if (i > 0) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
                lp.topMargin = dp(12);
            }

            addOutfitCard(row, list.get(i).getAsJsonObject());
            if (i + 1 < list.size()) {
                View spacer = new View(getActivity());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 0));
                row.addView(spacer);
                addOutfitCard(row, list.get(i + 1).getAsJsonObject());
            } else {
                View spacer = new View(getActivity());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1));
                row.addView(spacer);
            }

            layoutOutfits.addView(row);
        }
    }

    private void addOutfitCard(LinearLayout parent, JsonObject outfit) {
        LinearLayout card = new LinearLayout(getActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_round20);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        card.setLayoutParams(cardLp);

        // 2x2 物品缩略图网格
        JsonArray items = outfit.has("items") && !outfit.get("items").isJsonNull()
            ? outfit.getAsJsonArray("items") : new JsonArray();

        LinearLayout grid = new LinearLayout(getActivity());
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackgroundColor(0xFFF7FAFC);

        for (int row = 0; row < 2; row++) {
            LinearLayout gridRow = new LinearLayout(getActivity());
            gridRow.setOrientation(LinearLayout.HORIZONTAL);
            gridRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

            for (int col = 0; col < 2; col++) {
                int idx = row * 2 + col;
                FrameLayout cell = new FrameLayout(getActivity());
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, dp(80), 1));

                if (idx < items.size()) {
                    JsonObject item = items.get(idx).getAsJsonObject();
                    String imgUrl = item.has("cover_image") && !item.get("cover_image").isJsonNull()
                        ? item.get("cover_image").getAsString() : "";

                    android.widget.ImageView img = new android.widget.ImageView(getActivity());
                    img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    img.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                    if (!imgUrl.isEmpty()) {
                        try {
                            com.bumptech.glide.Glide.with(getActivity()).load(imgUrl).centerCrop().into(img);
                        } catch (Exception ignored) {}
                    } else {
                        img.setBackgroundColor(0xFFEDF2F7);
                    }
                    cell.addView(img);
                } else {
                    cell.setBackgroundColor(0xFFEDF2F7);
                    TextView plus = new TextView(getActivity());
                    plus.setText("+");
                    plus.setTextSize(20);
                    plus.setTextColor(0xFFCBD5E0);
                    plus.setGravity(Gravity.CENTER);
                    cell.addView(plus);
                }
                gridRow.addView(cell);
            }
            grid.addView(gridRow);
        }
        card.addView(grid);

        // 套装信息
        LinearLayout info = new LinearLayout(getActivity());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), dp(10), dp(12), dp(12));

        TextView name = new TextView(getActivity());
        name.setText(outfit.has("name") ? outfit.get("name").getAsString() : "");
        name.setTextSize(15);
        name.setTextColor(0xFF2D3748);
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(name);

        // 标签行
        LinearLayout tags = new LinearLayout(getActivity());
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tagsLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tagsLp.topMargin = dp(6);
        tags.setLayoutParams(tagsLp);

        String season = outfit.has("season") && !outfit.get("season").isJsonNull()
            ? outfit.get("season").getAsString() : "";
        String occasion = outfit.has("occasion") && !outfit.get("occasion").isJsonNull()
            ? outfit.get("occasion").getAsString() : "";

        if (!season.isEmpty()) {
            tags.addView(makeTag(season, 0xFF0E9F8E, 0xFFE0F7F4));
        }
        if (!occasion.isEmpty()) {
            if (!season.isEmpty()) {
                View spacer = new View(getActivity());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 0));
                tags.addView(spacer);
            }
            tags.addView(makeTag(occasion, 0xFF6B46C1, 0xFFE9D8FD));
        }

        int itemCount = items.size();
        View spacer2 = new View(getActivity());
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 0));
        tags.addView(spacer2);
        tags.addView(makeTag(itemCount + " 件", 0xFF718096, 0xFFEDF2F7));

        info.addView(tags);
        card.addView(info);

        // 点击进入详情
        int outfitId = outfit.has("id") ? outfit.get("id").getAsInt() : 0;
        if (outfitId > 0) {
            card.setClickable(true);
            card.setFocusable(true);
            final int fid = outfitId;
            card.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), OutfitCreateActivity.class);
                intent.putExtra("outfit_id", fid);
                intent.putExtra("edit_mode", true);
                startActivity(intent);
            });
        }

        parent.addView(card);
    }

    private TextView makeTag(String text, int textColor, int bgColor) {
        TextView tag = new TextView(getActivity());
        tag.setText(text);
        tag.setTextSize(11);
        tag.setTextColor(textColor);
        tag.setPadding(dp(8), dp(3), dp(8), dp(3));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(bgColor);
        tag.setBackground(bg);
        return tag;
    }

    private void showEmpty(String title, String desc) {
        layoutOutfits.removeAllViews();
        emptyView.setVisibility(View.VISIBLE);
        TextView tvTitle = emptyView.findViewById(R.id.tv_empty_title);
        TextView tvDesc = emptyView.findViewById(R.id.tv_empty_desc);
        if (tvTitle != null) tvTitle.setText(title);
        if (tvDesc != null) tvDesc.setText(desc);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

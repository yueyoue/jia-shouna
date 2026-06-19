package com.jiashouna.app.ui;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import java.util.HashMap;

public class ItemDetailActivity extends AppCompatActivity {
    private TextView tvName, tvSpace, tvCategory, tvQuantity, tvExpiry, tvPrice, tvBarcode, tvNote, tvPrivate;
    private Button btnBorrow, btnMove, btnEdit, btnDelete;
    private int goodsId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        goodsId = getIntent().getIntExtra("goods_id", 0);

        tvName = findViewById(R.id.tv_name);
        tvSpace = findViewById(R.id.tv_space);
        tvCategory = findViewById(R.id.tv_category);
        tvQuantity = findViewById(R.id.tv_quantity);
        tvExpiry = findViewById(R.id.tv_expiry);
        tvPrice = findViewById(R.id.tv_price);
        tvBarcode = findViewById(R.id.tv_barcode);
        tvNote = findViewById(R.id.tv_note);
        tvPrivate = findViewById(R.id.tv_private);
        btnBorrow = findViewById(R.id.btn_borrow);
        btnMove = findViewById(R.id.btn_move);
        btnEdit = findViewById(R.id.btn_edit);
        btnDelete = findViewById(R.id.btn_delete);

        btnDelete.setOnClickListener(v -> deleteItem());

        loadDetail();
    }

    private void loadDetail() {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(goodsId));
        ApiClient.get("goods.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    if (data.has("goods")) {
                        JsonObject g = data.getAsJsonObject("goods");
                        tvName.setText(g.get("name").getAsString());
                        tvSpace.setText(g.has("space_name") ? g.get("space_name").getAsString() : "");
                        tvCategory.setText(g.has("category") ? g.get("category").getAsString() : "");
                        tvQuantity.setText(g.get("quantity").getAsString() + " " + g.get("unit").getAsString());
                        tvExpiry.setText(g.has("expiry_date") && !g.get("expiry_date").isJsonNull() ? g.get("expiry_date").getAsString() : "-");
                        tvPrice.setText(g.has("purchase_price") && !g.get("purchase_price").isJsonNull() ? "¥" + g.get("purchase_price").getAsString() : "-");
                        tvBarcode.setText(g.has("barcode") ? g.get("barcode").getAsString() : "");
                        tvNote.setText(g.has("note") ? g.get("note").getAsString() : "");
                        tvPrivate.setVisibility(g.has("is_private") && g.get("is_private").getAsInt() == 1 ? android.view.View.VISIBLE : android.view.View.GONE);
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(ItemDetailActivity.this, msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteItem() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除此物品吗？")
            .setPositiveButton("删除", (d, w) -> {
                JsonObject body = new JsonObject();
                body.addProperty("id", goodsId);
                ApiClient.post("goods.php?action=delete", body, new ApiClient.ApiCallback() {
                    @Override public void onSuccess(JsonObject data) {
                        runOnUiThread(() -> {
                            Toast.makeText(ItemDetailActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                    @Override public void onError(String msg) {
                        runOnUiThread(() -> Toast.makeText(ItemDetailActivity.this, msg, Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .setNegativeButton("取消", null)
            .show();
    }
}

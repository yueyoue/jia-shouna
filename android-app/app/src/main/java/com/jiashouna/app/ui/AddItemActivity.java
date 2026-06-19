package com.jiashouna.app.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.db.LocalDb;
import com.jiashouna.app.model.Goods;
import com.jiashouna.app.utils.NetworkUtils;
import java.util.Calendar;

public class AddItemActivity extends AppCompatActivity {
    private EditText etName, etBarcode, etQuantity, etUnit, etExpiry, etPrice, etNote;
    private View spacePicker;
    private Switch swPrivate;
    private Button btnSave;
    private int selectedSpaceId = 0;
    private LocalDb localDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        localDb = new LocalDb(this);

        etName = findViewById(R.id.et_name);
        etBarcode = findViewById(R.id.et_barcode);
        etQuantity = findViewById(R.id.et_quantity);
        etUnit = findViewById(R.id.et_unit);
        etExpiry = findViewById(R.id.et_expiry);
        etPrice = findViewById(R.id.et_price);
        etNote = findViewById(R.id.et_note);
        spacePicker = findViewById(R.id.space_picker);
        swPrivate = findViewById(R.id.sw_private);
        btnSave = findViewById(R.id.btn_save);

        etExpiry.setOnClickListener(v -> showDatePicker());
        btnSave.setOnClickListener(v -> saveItem());
        spacePicker.setOnClickListener(v -> showSpacePicker());

        loadSpaces();
    }

    private void loadSpaces() {
        HashMap<String, String> params = new HashMap<>();
        params.put("house_id", String.valueOf(App.getInstance().getCurrentHouseId()));
        ApiClient.get("space.php", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    if (data.has("list")) {
                        // 填充空间选择器
                        Toast.makeText(AddItemActivity.this, "空间已加载", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            etExpiry.setText(year + "-" + String.format("%02d", month + 1) + "-" + String.format("%02d", day));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showSpacePicker() {
        // TODO: 显示空间选择弹窗
        Toast.makeText(this, "空间选择功能开发中", Toast.LENGTH_SHORT).show();
    }

    private void saveItem() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("请输入物品名称");
            return;
        }

        Goods goods = new Goods();
        goods.houseId = App.getInstance().getCurrentHouseId();
        goods.spaceId = selectedSpaceId;
        goods.name = name;
        goods.barcode = etBarcode.getText().toString().trim();
        goods.category = "";
        goods.quantity = Double.parseDouble(etQuantity.getText().toString().trim().isEmpty() ? "1" : etQuantity.getText().toString().trim());
        goods.unit = etUnit.getText().toString().trim().isEmpty() ? "个" : etUnit.getText().toString().trim();
        goods.expiryDate = etExpiry.getText().toString().trim();
        goods.note = etNote.getText().toString().trim();
        goods.isPrivate = swPrivate.isChecked() ? 1 : 0;

        if (NetworkUtils.isNetworkAvailable(this)) {
            // 在线模式 - 直接提交到服务器
            JsonObject body = new JsonObject();
            body.addProperty("house_id", goods.houseId);
            body.addProperty("space_id", goods.spaceId);
            body.addProperty("name", goods.name);
            body.addProperty("barcode", goods.barcode);
            body.addProperty("category", goods.category);
            body.addProperty("quantity", goods.quantity);
            body.addProperty("unit", goods.unit);
            body.addProperty("expiry_date", goods.expiryDate);
            body.addProperty("note", goods.note);
            body.addProperty("is_private", goods.isPrivate);

            ApiClient.post("goods.php?action=create", body, new ApiClient.ApiCallback() {
                @Override public void onSuccess(JsonObject data) {
                    runOnUiThread(() -> {
                        Toast.makeText(AddItemActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> Toast.makeText(AddItemActivity.this, msg, Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            // 离线模式 - 保存到本地
            localDb.saveOfflineGoods(goods);
            Toast.makeText(this, "已保存到本地，联网后自动同步", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}

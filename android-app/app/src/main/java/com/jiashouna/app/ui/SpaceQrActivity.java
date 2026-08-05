package com.jiashouna.app.ui;

import android.content.ContentValues;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.jiashouna.app.App;
import com.jiashouna.app.R;
import com.jiashouna.app.api.ApiClient;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.util.HashMap;
import java.util.Hashtable;

/**
 * 空间二维码展示页面
 * 生成并显示空间的二维码，支持保存到相册
 */
public class SpaceQrActivity extends AppCompatActivity {
    private int spaceId;
    private String spaceName;
    private String spaceCode;
    private ImageView ivQrCode;
    private TextView tvSpaceName, tvSpaceCode, tvLoading;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        spaceId = getIntent().getIntExtra("space_id", 0);
        spaceName = getIntent().getStringExtra("space_name");
        if (spaceName == null) spaceName = "";

        setContentView(buildLayout());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadSpaceCode();
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF7FAFC);

        // 顶栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(12), dp(16), dp(12));
        topBar.setBackgroundColor(Color.WHITE);
        topBar.setElevation(dp(2));

        TextView btnBack = new TextView(this);
        btnBack.setId(R.id.btn_back);
        btnBack.setText("←");
        btnBack.setTextSize(20);
        btnBack.setTextColor(0xFF2D3748);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setPadding(dp(12), dp(8), dp(12), dp(8));
        topBar.addView(btnBack);

        TextView title = new TextView(this);
        title.setText("空间二维码");
        title.setTextSize(17);
        title.setTextColor(0xFF2D3748);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        title.setLayoutParams(titleLp);
        topBar.addView(title);

        root.addView(topBar);

        // 内容区
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(32), dp(40), dp(32), dp(32));

        // 空间名
        tvSpaceName = new TextView(this);
        tvSpaceName.setText(spaceName);
        tvSpaceName.setTextSize(22);
        tvSpaceName.setTextColor(0xFF2D3748);
        tvSpaceName.setTypeface(null, Typeface.BOLD);
        tvSpaceName.setGravity(Gravity.CENTER);
        content.addView(tvSpaceName);

        // 空间编码
        tvSpaceCode = new TextView(this);
        tvSpaceCode.setText("加载中...");
        tvSpaceCode.setTextSize(14);
        tvSpaceCode.setTextColor(0xFF718096);
        tvSpaceCode.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        codeLp.topMargin = dp(8);
        tvSpaceCode.setLayoutParams(codeLp);
        content.addView(tvSpaceCode);

        // 二维码
        ivQrCode = new ImageView(this);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(dp(240), dp(240));
        qrLp.topMargin = dp(32);
        ivQrCode.setLayoutParams(qrLp);
        content.addView(ivQrCode);

        // 提示
        tvLoading = new TextView(this);
        tvLoading.setText("正在生成二维码...");
        tvLoading.setTextSize(13);
        tvLoading.setTextColor(0xFFA0AEC0);
        tvLoading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        loadLp.topMargin = dp(16);
        tvLoading.setLayoutParams(loadLp);
        content.addView(tvLoading);

        // 保存按钮
        btnSave = new Button(this);
        btnSave.setText("💾 保存二维码到相册");
        btnSave.setTextSize(15);
        btnSave.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setCornerRadius(dp(12));
        btnBg.setColor(0xFFC48F4E);
        btnSave.setBackground(btnBg);
        btnSave.setPadding(dp(24), dp(14), dp(24), dp(14));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(32);
        btnSave.setLayoutParams(btnLp);
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(v -> saveQrToGallery());
        content.addView(btnSave);

        // 使用说明
        TextView tip = new TextView(this);
        tip.setText("💡 打印此二维码贴到对应空间位置\n扫码可快速查看该空间内所有物品");
        tip.setTextSize(12);
        tip.setTextColor(0xFFA0AEC0);
        tip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tipLp.topMargin = dp(16);
        tip.setLayoutParams(tipLp);
        content.addView(tip);

        root.addView(content);
        return root;
    }

    private void loadSpaceCode() {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(spaceId));

        ApiClient.get("space.php?action=generate_code", params, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                runOnUiThread(() -> {
                    try {
                        spaceCode = data.has("space_code") ? data.get("space_code").getAsString() : "";
                        String name = data.has("space_name") ? data.get("space_name").getAsString() : spaceName;
                        if (!name.isEmpty()) spaceName = name;

                        tvSpaceName.setText(spaceName);
                        tvSpaceCode.setText("编码: " + spaceCode);
                        tvLoading.setText("");

                        // 生成二维码
                        String qrContent = "JSN:SPACE:" + spaceCode;
                        Bitmap qrBitmap = generateQrBitmap(qrContent, dp(240));
                        if (qrBitmap != null) {
                            // 合成带文字的完整图片
                            Bitmap fullBitmap = createQrCard(qrBitmap, spaceName, spaceCode);
                            ivQrCode.setImageBitmap(fullBitmap);
                            btnSave.setEnabled(true);
                        } else {
                            tvLoading.setText("二维码生成失败");
                        }
                    } catch (Exception e) {
                        tvLoading.setText("解析失败: " + e.getMessage());
                    }
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> tvLoading.setText("加载失败: " + msg));
            }
        });
    }

    private Bitmap generateQrBitmap(String content, int size) {
        try {
            Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 创建带空间名和编码的完整二维码卡片图片
     */
    private Bitmap createQrCard(Bitmap qrBitmap, String name, String code) {
        int cardW = dp(320);
        int cardH = dp(400);
        Bitmap card = Bitmap.createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(card);

        // 白色背景
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(0, 0, cardW, cardH, dp(16), dp(16), bgPaint);

        // 空间名
        Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTextSize(dp(22));
        namePaint.setColor(0xFF2D3748);
        namePaint.setTypeface(Typeface.DEFAULT_BOLD);
        namePaint.setTextAlign(Paint.Align.CENTER);
        float nameY = dp(48);
        canvas.drawText(name, cardW / 2f, nameY, namePaint);

        // 编码
        Paint codePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        codePaint.setTextSize(dp(14));
        codePaint.setColor(0xFF718096);
        codePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("编码: " + code, cardW / 2f, nameY + dp(24), codePaint);

        // 二维码居中
        int qrSize = dp(200);
        float qrX = (cardW - qrSize) / 2f;
        float qrY = nameY + dp(40);
        canvas.drawBitmap(qrBitmap, null, new RectF(qrX, qrY, qrX + qrSize, qrY + qrSize), null);

        // 底部提示
        Paint tipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tipPaint.setTextSize(dp(11));
        tipPaint.setColor(0xFFA0AEC0);
        tipPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("扫码查看空间物品", cardW / 2f, qrY + qrSize + dp(24), tipPaint);

        // 应用名
        Paint appPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        appPaint.setTextSize(dp(10));
        appPaint.setColor(0xFFCBD5E0);
        appPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("家收纳", cardW / 2f, qrY + qrSize + dp(42), appPaint);

        return card;
    }

    private void saveQrToGallery() {
        try {
            // 重新生成完整卡片
            String qrContent = "JSN:SPACE:" + spaceCode;
            Bitmap qrBitmap = generateQrBitmap(qrContent, dp(240));
            if (qrBitmap == null) {
                Toast.makeText(this, "生成失败", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap fullBitmap = createQrCard(qrBitmap, spaceName, spaceCode);

            String fileName = "space_qr_" + spaceCode + ".jpg";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/家收纳");

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    fullBitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
                    if (os != null) os.close();
                    Toast.makeText(this, "✅ 已保存到相册「家收纳」文件夹", Toast.LENGTH_LONG).show();
                }
            } else {
                String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    + "/家收纳/" + fileName;
                java.io.File dir = new java.io.File(path).getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                FileOutputStream fos = new FileOutputStream(path);
                fullBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.close();
                Toast.makeText(this, "✅ 已保存: " + path, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

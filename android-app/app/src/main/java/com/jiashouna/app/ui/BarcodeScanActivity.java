package com.jiashouna.app.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * 条码扫描Activity - 使用ZXing IntentIntegrator
 */
public class BarcodeScanActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 强制竖屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
            integrator.setPrompt("将条形码/二维码对准框内\n自动识别");
            integrator.setCameraId(0);
            integrator.setBeepEnabled(true);
            integrator.setBarcodeImageEnabled(false);
            integrator.setOrientationLocked(true);
            integrator.initiateScan();
        } catch (Exception e) {
            Toast.makeText(this, "扫码启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                Intent intent = new Intent();
                intent.putExtra("SCAN_RESULT", result.getContents());
                setResult(RESULT_OK, intent);
            } else {
                setResult(RESULT_CANCELED);
            }
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
            finish();
        }
    }
}

package com.jiashouna.app.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.jiashouna.app.R;

/**
 * 条码扫描Activity - 使用ZXing库
 */
public class BarcodeScanActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ZXing集成需要实际的扫码库
        // 这里提供基本框架，实际使用时需要集成ZXing或ML Kit
        Toast.makeText(this, "扫码功能需要集成扫码SDK", Toast.LENGTH_LONG).show();
        finish();
    }
}

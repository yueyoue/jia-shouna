package com.jiashouna.app.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.jiashouna.app.R;
import com.jiashouna.app.ui.fragment.HomeFragment;
import com.jiashouna.app.ui.fragment.SpacesFragment;
import com.jiashouna.app.ui.fragment.RemindersFragment;
import com.jiashouna.app.ui.fragment.ProfileFragment;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);

        // 默认显示首页
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
                return true;
            } else if (id == R.id.nav_spaces) {
                loadFragment(new SpacesFragment());
                return true;
            } else if (id == R.id.nav_scan) {
                // 扫码 - 跳转到添加物品页面
                Intent intent = new Intent(this, AddItemActivity.class);
                intent.putExtra("mode", "scan");
                startActivity(intent);
                // 不切换fragment，保持当前页面
                bottomNav.setSelectedItemId(R.id.nav_home);
                return false;
            } else if (id == R.id.nav_reminders) {
                loadFragment(new RemindersFragment());
                return true;
            } else if (id == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
                return true;
            }
            return false;
        });

        // FAB按钮
        findViewById(R.id.fab_add).setOnClickListener(v -> {
            startActivity(new Intent(this, AddItemActivity.class));
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}

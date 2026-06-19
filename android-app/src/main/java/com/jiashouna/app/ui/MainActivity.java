package com.jiashouna.app.ui;

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
            Fragment fragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_home) fragment = new HomeFragment();
            else if (id == R.id.nav_spaces) fragment = new SpacesFragment();
            else if (id == R.id.nav_reminders) fragment = new RemindersFragment();
            else if (id == R.id.nav_profile) fragment = new ProfileFragment();
            
            if (fragment != null) loadFragment(fragment);
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}

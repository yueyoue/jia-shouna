package com.jiashouna.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class App extends Application {
    private static App instance;
    public static final String BASE_URL = "http://j.tthsdd.top/backend/api/";
    public static final String IMAGE_BASE = "http://j.tthsdd.top/backend/uploads/";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static App getInstance() { return instance; }

    public SharedPreferences getPrefs() {
        return getSharedPreferences("jiashouna", Context.MODE_PRIVATE);
    }

    public String getToken() {
        return getPrefs().getString("token", "");
    }

    public void setToken(String token) {
        getPrefs().edit().putString("token", token).apply();
    }

    public int getUserId() {
        return getPrefs().getInt("user_id", 0);
    }

    public void setUserId(int id) {
        getPrefs().edit().putInt("user_id", id).apply();
    }

    public int getCurrentHouseId() {
        return getPrefs().getInt("current_house_id", 0);
    }

    public void setCurrentHouseId(int id) {
        getPrefs().edit().putInt("current_house_id", id).apply();
    }

    public String getCurrentHouseName() {
        return getPrefs().getString("current_house_name", "我的家");
    }

    public void setCurrentHouseName(String name) {
        getPrefs().edit().putString("current_house_name", name).apply();
    }

    public boolean isLoggedIn() {
        return !getToken().isEmpty() && getUserId() > 0;
    }

    public void logout() {
        getPrefs().edit().clear().apply();
    }
}

package com.jiashouna.app.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.jiashouna.app.utils.NetworkUtils;

/**
 * 后台同步服务 - 联网后自动同步离线数据
 */
public class SyncService extends Service {
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NetworkUtils.syncOfflineData(this, (success, msg) -> {
            // 同步完成，停止服务
            stopSelf();
        });
        return START_NOT_STICKY;
    }
}

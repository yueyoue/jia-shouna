package com.jiashouna.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.jiashouna.app.App;
import com.jiashouna.app.api.ApiClient;
import com.jiashouna.app.db.LocalDb;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jiashouna.app.model.Goods;
import com.jiashouna.app.model.Space;
import java.util.List;

public class NetworkUtils {

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    /**
     * 同步离线数据到服务器
     */
    public static void syncOfflineData(Context context, SyncCallback callback) {
        if (!isNetworkAvailable(context)) {
            if (callback != null) callback.onResult(false, "无网络连接");
            return;
        }

        LocalDb localDb = new LocalDb(context);
        List<Goods> unsyncedGoods = localDb.getUnsyncedGoods();
        List<Space> unsyncedSpaces = localDb.getUnsyncedSpaces();

        if (unsyncedGoods.isEmpty() && unsyncedSpaces.isEmpty()) {
            if (callback != null) callback.onResult(true, "无待同步数据");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("house_id", App.getInstance().getCurrentHouseId());

        // 构建离线空间数据
        JsonArray spacesArr = new JsonArray();
        for (Space s : unsyncedSpaces) {
            JsonObject sp = new JsonObject();
            sp.addProperty("offline_id", s.offlineId);
            sp.addProperty("name", s.name);
            sp.addProperty("parent_id", s.parentId);
            sp.addProperty("icon", s.icon);
            sp.addProperty("color", s.color);
            sp.addProperty("shared", s.shared);
            spacesArr.add(sp);
        }
        body.add("spaces", spacesArr);

        // 构建离线物品数据
        JsonArray itemsArr = new JsonArray();
        for (Goods g : unsyncedGoods) {
            JsonObject item = new JsonObject();
            item.addProperty("offline_id", g.offlineId);
            item.addProperty("name", g.name);
            item.addProperty("barcode", g.barcode);
            item.addProperty("category", g.category);
            item.addProperty("quantity", g.quantity);
            item.addProperty("unit", g.unit);
            item.addProperty("expiry_date", g.expiryDate);
            item.addProperty("note", g.note);
            item.addProperty("is_private", g.isPrivate);
            item.addProperty("space_id", g.spaceId);
            itemsArr.add(item);
        }
        body.add("items", itemsArr);

        ApiClient.post("sync/push", body, new ApiClient.ApiCallback() {
            @Override public void onSuccess(JsonObject data) {
                localDb.markSynced();
                int syncedItems = data.has("synced_items") ? data.get("synced_items").getAsInt() : 0;
                if (callback != null) callback.onResult(true, "同步成功: " + syncedItems + "件物品");
            }
            @Override public void onError(String msg) {
                if (callback != null) callback.onResult(false, "同步失败: " + msg);
            }
        });
    }

    public interface SyncCallback {
        void onResult(boolean success, String msg);
    }
}

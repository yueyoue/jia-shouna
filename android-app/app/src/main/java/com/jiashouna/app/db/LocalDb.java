package com.jiashouna.app.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.jiashouna.app.model.Goods;
import com.jiashouna.app.model.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocalDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "jiashouna_local.db";
    private static final int DB_VERSION = 1;

    public LocalDb(Context context) {
        super(context, DB_NAME, null, CURRENT_DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS local_goods (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "offline_id TEXT UNIQUE," +
                "server_id INTEGER DEFAULT 0," +
                "house_id INTEGER," +
                "space_id INTEGER," +
                "offline_space_id TEXT," +
                "name TEXT," +
                "barcode TEXT," +
                "category TEXT," +
                "brand TEXT," +
                "quantity REAL DEFAULT 1," +
                "unit TEXT DEFAULT '个'," +
                "purchase_date TEXT," +
                "expiry_date TEXT," +
                "purchase_price REAL," +
                "note TEXT," +
                "is_private INTEGER DEFAULT 0," +
                "is_synced INTEGER DEFAULT 0," +
                "created_at INTEGER," +
                "updated_at INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS local_spaces (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "offline_id TEXT UNIQUE," +
                "server_id INTEGER DEFAULT 0," +
                "house_id INTEGER," +
                "parent_id INTEGER DEFAULT 0," +
                "name TEXT," +
                "level INTEGER DEFAULT 1," +
                "icon TEXT DEFAULT '🏠'," +
                "color TEXT DEFAULT '#FF8C42'," +
                "is_synced INTEGER DEFAULT 0," +
                "created_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v2: 添加只读缓存表
            db.execSQL("CREATE TABLE IF NOT EXISTS cache_goods (" +
                    "id INTEGER PRIMARY KEY," +
                    "house_id INTEGER," +
                    "space_id INTEGER," +
                    "name TEXT," +
                    "barcode TEXT," +
                    "category TEXT," +
                    "brand TEXT," +
                    "quantity REAL DEFAULT 1," +
                    "unit TEXT DEFAULT '个'," +
                    "purchase_date TEXT," +
                    "expiry_date TEXT," +
                    "purchase_price REAL," +
                    "note TEXT," +
                    "is_private INTEGER DEFAULT 0," +
                    "space_name TEXT," +
                    "space_icon TEXT," +
                    "cover_image TEXT," +
                    "cached_at INTEGER)");
        }
    }

    // v2: 创建缓存表
    public static final int CURRENT_DB_VERSION = 2;

    // 重新设置版本号
    { /* instance initializer - handled in constructor */ }

    // 保存离线物品
    public String saveOfflineGoods(Goods goods) {
        SQLiteDatabase db = getWritableDatabase();
        String offlineId = UUID.randomUUID().toString();
        ContentValues cv = new ContentValues();
        cv.put("offline_id", offlineId);
        cv.put("house_id", goods.houseId);
        cv.put("space_id", goods.spaceId);
        cv.put("name", goods.name);
        cv.put("barcode", goods.barcode);
        cv.put("category", goods.category);
        cv.put("quantity", goods.quantity);
        cv.put("unit", goods.unit);
        cv.put("expiry_date", goods.expiryDate);
        cv.put("note", goods.note);
        cv.put("is_private", goods.isPrivate);
        cv.put("is_synced", 0);
        cv.put("created_at", System.currentTimeMillis() / 1000);
        db.insert("local_goods", null, cv);
        return offlineId;
    }

    // 保存离线空间
    public String saveOfflineSpace(Space space) {
        SQLiteDatabase db = getWritableDatabase();
        String offlineId = UUID.randomUUID().toString();
        ContentValues cv = new ContentValues();
        cv.put("offline_id", offlineId);
        cv.put("house_id", space.houseId);
        cv.put("parent_id", space.parentId);
        cv.put("name", space.name);
        cv.put("level", space.level);
        cv.put("icon", space.icon);
        cv.put("color", space.color);
        cv.put("is_synced", 0);
        cv.put("created_at", System.currentTimeMillis() / 1000);
        db.insert("local_spaces", null, cv);
        return offlineId;
    }

    // 获取未同步的物品
    public List<Goods> getUnsyncedGoods() {
        List<Goods> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM local_goods WHERE is_synced = 0", null);
        while (c.moveToNext()) {
            Goods g = new Goods();
            g.offlineId = c.getString(c.getColumnIndexOrThrow("offline_id"));
            g.houseId = c.getInt(c.getColumnIndexOrThrow("house_id"));
            g.spaceId = c.getInt(c.getColumnIndexOrThrow("space_id"));
            g.name = c.getString(c.getColumnIndexOrThrow("name"));
            g.barcode = c.getString(c.getColumnIndexOrThrow("barcode"));
            g.category = c.getString(c.getColumnIndexOrThrow("category"));
            g.quantity = c.getDouble(c.getColumnIndexOrThrow("quantity"));
            g.unit = c.getString(c.getColumnIndexOrThrow("unit"));
            g.expiryDate = c.getString(c.getColumnIndexOrThrow("expiry_date"));
            g.note = c.getString(c.getColumnIndexOrThrow("note"));
            g.isPrivate = c.getInt(c.getColumnIndexOrThrow("is_private"));
            list.add(g);
        }
        c.close();
        return list;
    }

    // 获取未同步的空间
    public List<Space> getUnsyncedSpaces() {
        List<Space> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM local_spaces WHERE is_synced = 0", null);
        while (c.moveToNext()) {
            Space s = new Space();
            s.offlineId = c.getString(c.getColumnIndexOrThrow("offline_id"));
            s.houseId = c.getInt(c.getColumnIndexOrThrow("house_id"));
            s.parentId = c.getInt(c.getColumnIndexOrThrow("parent_id"));
            s.name = c.getString(c.getColumnIndexOrThrow("name"));
            s.level = c.getInt(c.getColumnIndexOrThrow("level"));
            s.icon = c.getString(c.getColumnIndexOrThrow("icon"));
            s.color = c.getString(c.getColumnIndexOrThrow("color"));
            list.add(s);
        }
        c.close();
        return list;
    }

    // 标记已同步
    public void markSynced() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE local_goods SET is_synced = 1 WHERE is_synced = 0");
        db.execSQL("UPDATE local_spaces SET is_synced = 1 WHERE is_synced = 0");
    }

    // ========== 只读缓存 (离线查看) ==========

    /**
     * 清空并替换缓存数据
     */
    public void refreshCache(List<JsonObject> items) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("cache_goods", null, null);
            long now = System.currentTimeMillis() / 1000;
            for (JsonObject item : items) {
                ContentValues cv = new ContentValues();
                cv.put("id", item.has("id") ? item.get("id").getAsInt() : 0);
                cv.put("house_id", item.has("house_id") ? item.get("house_id").getAsInt() : 0);
                cv.put("space_id", item.has("space_id") ? item.get("space_id").getAsInt() : 0);
                cv.put("name", item.has("name") ? item.get("name").getAsString() : "");
                cv.put("barcode", item.has("barcode") && !item.get("barcode").isJsonNull() ? item.get("barcode").getAsString() : "");
                cv.put("category", item.has("category") && !item.get("category").isJsonNull() ? item.get("category").getAsString() : "");
                cv.put("brand", item.has("brand") && !item.get("brand").isJsonNull() ? item.get("brand").getAsString() : "");
                cv.put("quantity", item.has("quantity") ? item.get("quantity").getAsDouble() : 1);
                cv.put("unit", item.has("unit") && !item.get("unit").isJsonNull() ? item.get("unit").getAsString() : "个");
                cv.put("purchase_date", item.has("purchase_date") && !item.get("purchase_date").isJsonNull() ? item.get("purchase_date").getAsString() : "");
                cv.put("expiry_date", item.has("expiry_date") && !item.get("expiry_date").isJsonNull() ? item.get("expiry_date").getAsString() : "");
                cv.put("purchase_price", item.has("purchase_price") && !item.get("purchase_price").isJsonNull() ? item.get("purchase_price").getAsDouble() : 0);
                cv.put("note", item.has("note") && !item.get("note").isJsonNull() ? item.get("note").getAsString() : "");
                cv.put("is_private", item.has("is_private") ? item.get("is_private").getAsInt() : 0);
                cv.put("space_name", item.has("space_name") && !item.get("space_name").isJsonNull() ? item.get("space_name").getAsString() : "");
                cv.put("space_icon", item.has("space_icon") && !item.get("space_icon").isJsonNull() ? item.get("space_icon").getAsString() : "");
                cv.put("cover_image", item.has("cover_image") && !item.get("cover_image").isJsonNull() ? item.get("cover_image").getAsString() : "");
                cv.put("cached_at", now);
                db.insertWithOnConflict("cache_goods", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            android.util.Log.e("LocalDb", "refreshCache error: " + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 获取缓存物品列表
     */
    public List<Goods> getCachedGoods() {
        List<Goods> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM cache_goods ORDER BY cached_at DESC", null);
        while (c.moveToNext()) {
            Goods g = new Goods();
            g.id = c.getInt(c.getColumnIndexOrThrow("id"));
            g.houseId = c.getInt(c.getColumnIndexOrThrow("house_id"));
            g.spaceId = c.getInt(c.getColumnIndexOrThrow("space_id"));
            g.name = c.getString(c.getColumnIndexOrThrow("name"));
            g.barcode = c.getString(c.getColumnIndexOrThrow("barcode"));
            g.category = c.getString(c.getColumnIndexOrThrow("category"));
            g.brand = c.getString(c.getColumnIndexOrThrow("brand"));
            g.quantity = c.getDouble(c.getColumnIndexOrThrow("quantity"));
            g.unit = c.getString(c.getColumnIndexOrThrow("unit"));
            g.purchaseDate = c.getString(c.getColumnIndexOrThrow("purchase_date"));
            g.expiryDate = c.getString(c.getColumnIndexOrThrow("expiry_date"));
            g.note = c.getString(c.getColumnIndexOrThrow("note"));
            g.spaceName = c.getString(c.getColumnIndexOrThrow("space_name"));
            g.coverImage = c.getString(c.getColumnIndexOrThrow("cover_image"));
            list.add(g);
        }
        c.close();
        return list;
    }

    /**
     * 搜索缓存物品
     */
    public List<Goods> searchCachedGoods(String keyword) {
        List<Goods> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String kw = "%" + keyword + "%";
        Cursor c = db.rawQuery("SELECT * FROM cache_goods WHERE name LIKE ? OR barcode LIKE ? OR brand LIKE ? OR note LIKE ? ORDER BY cached_at DESC",
                new String[]{kw, kw, kw, kw});
        while (c.moveToNext()) {
            Goods g = new Goods();
            g.id = c.getInt(c.getColumnIndexOrThrow("id"));
            g.name = c.getString(c.getColumnIndexOrThrow("name"));
            g.barcode = c.getString(c.getColumnIndexOrThrow("barcode"));
            g.category = c.getString(c.getColumnIndexOrThrow("category"));
            g.brand = c.getString(c.getColumnIndexOrThrow("brand"));
            g.quantity = c.getDouble(c.getColumnIndexOrThrow("quantity"));
            g.unit = c.getString(c.getColumnIndexOrThrow("unit"));
            g.spaceName = c.getString(c.getColumnIndexOrThrow("space_name"));
            g.coverImage = c.getString(c.getColumnIndexOrThrow("cover_image"));
            list.add(g);
        }
        c.close();
        return list;
    }

    /**
     * 获取缓存物品详情
     */
    public Goods getCachedGoodsDetail(int goodsId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM cache_goods WHERE id = ?", new String[]{String.valueOf(goodsId)});
        Goods g = null;
        if (c.moveToFirst()) {
            g = new Goods();
            g.id = c.getInt(c.getColumnIndexOrThrow("id"));
            g.houseId = c.getInt(c.getColumnIndexOrThrow("house_id"));
            g.spaceId = c.getInt(c.getColumnIndexOrThrow("space_id"));
            g.name = c.getString(c.getColumnIndexOrThrow("name"));
            g.barcode = c.getString(c.getColumnIndexOrThrow("barcode"));
            g.category = c.getString(c.getColumnIndexOrThrow("category"));
            g.brand = c.getString(c.getColumnIndexOrThrow("brand"));
            g.quantity = c.getDouble(c.getColumnIndexOrThrow("quantity"));
            g.unit = c.getString(c.getColumnIndexOrThrow("unit"));
            g.purchaseDate = c.getString(c.getColumnIndexOrThrow("purchase_date"));
            g.expiryDate = c.getString(c.getColumnIndexOrThrow("expiry_date"));
            g.note = c.getString(c.getColumnIndexOrThrow("note"));
            g.spaceName = c.getString(c.getColumnIndexOrThrow("space_name"));
            g.coverImage = c.getString(c.getColumnIndexOrThrow("cover_image"));
        }
        c.close();
        return g;
    }

    /**
     * 获取缓存总数
     */
    public int getCachedGoodsCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM cache_goods", null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    // 搜索本地物品(离线模式)
    public List<Goods> searchLocal(String keyword) {
        List<Goods> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM local_goods WHERE name LIKE ? OR barcode LIKE ?",
                new String[]{"%" + keyword + "%", "%" + keyword + "%"});
        while (c.moveToNext()) {
            Goods g = new Goods();
            g.id = c.getInt(c.getColumnIndexOrThrow("id"));
            g.name = c.getString(c.getColumnIndexOrThrow("name"));
            g.barcode = c.getString(c.getColumnIndexOrThrow("barcode"));
            g.category = c.getString(c.getColumnIndexOrThrow("category"));
            g.quantity = c.getDouble(c.getColumnIndexOrThrow("quantity"));
            g.unit = c.getString(c.getColumnIndexOrThrow("unit"));
            list.add(g);
        }
        c.close();
        return list;
    }
}

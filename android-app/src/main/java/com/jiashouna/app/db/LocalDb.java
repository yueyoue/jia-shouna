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
        super(context, DB_NAME, null, DB_VERSION);
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
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

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

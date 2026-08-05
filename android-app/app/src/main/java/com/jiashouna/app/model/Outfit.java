package com.jiashouna.app.model;

/**
 * 套装模型
 */
public class Outfit {
    public int id;
    public int houseId;
    public int creatorId;
    public String name;
    public String season;
    public String occasion;
    public String coverImage;
    public String note;
    public int status;
    public long createdAt;
    public long updatedAt;

    // 关联物品数量（列表接口返回）
    public int itemCount;
}

package com.jiashouna.app.model;

import java.io.Serializable;
import java.util.List;

public class Space implements Serializable {
    public int id;
    public int houseId;
    public int parentId;
    public String name = "";
    public int level = 1;
    public String icon = "🏠";
    public String color = "#FF8C42";
    public int sortOrder;
    public int itemCount;
    public int expiringCount;
    public int shared = 1;
    public int creatorId;
    public String offlineId;
    public List<Space> children;
}

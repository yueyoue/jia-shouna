package com.jiashouna.app.model;

import java.io.Serializable;

public class Goods implements Serializable {
    public int id;
    public int houseId;
    public int spaceId;
    public int creatorId;
    public String name = "";
    public String barcode = "";
    public String category = "";
    public String brand = "";
    public String spec = "";
    public double quantity = 1;
    public String unit = "个";
    public String purchaseDate;
    public String expiryDate;
    public Double purchasePrice;
    public Double stockThreshold;
    public String note = "";
    public int isPrivate;
    public int status = 1;
    public String spaceName = "";
    public String spaceIcon = "";
    public String coverImage = "";
    public String offlineId;
    public String createdAt;
    public String updatedAt;
}

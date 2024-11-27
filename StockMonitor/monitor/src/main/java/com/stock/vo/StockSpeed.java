package com.stock.vo;

public class StockSpeed {
    private String code;
    private String name;
    private double speed;

    public StockSpeed(String code, String name, double speed) {
        this.code = code;
        this.name = name;
        this.speed = speed;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public double getSpeed() { return speed; }
}

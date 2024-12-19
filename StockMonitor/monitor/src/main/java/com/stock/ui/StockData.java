package com.stock.ui;

import javafx.beans.property.*;

public class StockData {
    private final StringProperty code;
    private final StringProperty name;
    private final DoubleProperty currentPrice;
    private final DoubleProperty netInflow;
    private final DoubleProperty changePercent;
    private final DoubleProperty mainForcePercent;
    private final DoubleProperty momentumScore;
    private final DoubleProperty upTrendScore;
    private final DoubleProperty downTrendScore;
    private final DoubleProperty volume;
    private final DoubleProperty lurkingValue;
    private final StringProperty createTime;
    
    // 新增属性
    private final DoubleProperty inflowDiff;         // 较上条记录净流入差值
    private final DoubleProperty mainForceInflow;    // 主力净流入
    private final DoubleProperty superLargeInflow;   // 超大单净流入
    private final DoubleProperty otherInflow;        // 其他净流入

    public StockData() {
        this.code = new SimpleStringProperty();
        this.name = new SimpleStringProperty();
        this.currentPrice = new SimpleDoubleProperty();
        this.netInflow = new SimpleDoubleProperty();
        this.changePercent = new SimpleDoubleProperty();
        this.mainForcePercent = new SimpleDoubleProperty();
        this.momentumScore = new SimpleDoubleProperty();
        this.upTrendScore = new SimpleDoubleProperty();
        this.downTrendScore = new SimpleDoubleProperty();
        this.volume = new SimpleDoubleProperty();
        this.lurkingValue = new SimpleDoubleProperty();
        this.createTime = new SimpleStringProperty();
        
        // 初始化新增属性
        this.inflowDiff = new SimpleDoubleProperty();
        this.mainForceInflow = new SimpleDoubleProperty();
        this.superLargeInflow = new SimpleDoubleProperty();
        this.otherInflow = new SimpleDoubleProperty();
    }

    // Getters and Setters for all properties
    public String getCode() {
        return code.get();
    }

    public StringProperty codeProperty() {
        return code;
    }

    public void setCode(String code) {
        this.code.set(code);
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public double getCurrentPrice() {
        return currentPrice.get();
    }

    public DoubleProperty currentPriceProperty() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice.set(currentPrice);
    }

    public double getNetInflow() {
        return netInflow.get();
    }

    public DoubleProperty netInflowProperty() {
        return netInflow;
    }

    public void setNetInflow(double netInflow) {
        this.netInflow.set(netInflow);
    }

    public double getChangePercent() {
        return changePercent.get();
    }

    public DoubleProperty changePercentProperty() {
        return changePercent;
    }

    public void setChangePercent(double changePercent) {
        this.changePercent.set(changePercent);
    }

    public double getMainForcePercent() {
        return mainForcePercent.get();
    }

    public DoubleProperty mainForcePercentProperty() {
        return mainForcePercent;
    }

    public void setMainForcePercent(double mainForcePercent) {
        this.mainForcePercent.set(mainForcePercent);
    }

    public double getMomentumScore() {
        return momentumScore.get();
    }

    public DoubleProperty momentumScoreProperty() {
        return momentumScore;
    }

    public void setMomentumScore(double momentumScore) {
        this.momentumScore.set(momentumScore);
    }

    public double getUpTrendScore() {
        return upTrendScore.get();
    }

    public DoubleProperty upTrendScoreProperty() {
        return upTrendScore;
    }

    public void setUpTrendScore(double upTrendScore) {
        this.upTrendScore.set(upTrendScore);
    }

    public double getDownTrendScore() {
        return downTrendScore.get();
    }

    public DoubleProperty downTrendScoreProperty() {
        return downTrendScore;
    }

    public void setDownTrendScore(double downTrendScore) {
        this.downTrendScore.set(downTrendScore);
    }

    public double getVolume() {
        return volume.get();
    }

    public DoubleProperty volumeProperty() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume.set(volume);
    }

    public double getLurkingValue() {
        return lurkingValue.get();
    }

    public DoubleProperty lurkingValueProperty() {
        return lurkingValue;
    }

    public void setLurkingValue(double lurkingValue) {
        this.lurkingValue.set(lurkingValue);
    }

    public String getCreateTime() {
        return createTime.get();
    }

    public StringProperty createTimeProperty() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime.set(createTime);
    }

    // 新增属性的getter和setter方法
    public double getInflowDiff() {
        return inflowDiff.get();
    }

    public DoubleProperty inflowDiffProperty() {
        return inflowDiff;
    }

    public void setInflowDiff(double inflowDiff) {
        this.inflowDiff.set(inflowDiff);
    }

    public double getMainForceInflow() {
        return mainForceInflow.get();
    }

    public DoubleProperty mainForceInflowProperty() {
        return mainForceInflow;
    }

    public void setMainForceInflow(double mainForceInflow) {
        this.mainForceInflow.set(mainForceInflow);
    }

    public double getSuperLargeInflow() {
        return superLargeInflow.get();
    }

    public DoubleProperty superLargeInflowProperty() {
        return superLargeInflow;
    }

    public void setSuperLargeInflow(double superLargeInflow) {
        this.superLargeInflow.set(superLargeInflow);
    }

    public double getOtherInflow() {
        return otherInflow.get();
    }

    public DoubleProperty otherInflowProperty() {
        return otherInflow;
    }

    public void setOtherInflow(double otherInflow) {
        this.otherInflow.set(otherInflow);
    }
} 
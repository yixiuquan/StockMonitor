package com.stock.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockSpeed {
    private String code;// 股票代码
    private String name;// 股票名称
    private double inSpeed;// 股票资金流入实时速度
    // 股票资金流出实时速度
    private double outSpeed;
    private double change;                  // 涨跌额
    private double changePercent;           // 涨跌幅
    private double volume;                  // 成交量
    //排名上涨趋势分数
    private double rankUpTrendScore;
    //排名下跌趋势分数
    private double rankDownTrendScore;
}


package com.stock.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockSpeed {
    // 基础信息
    private String code;                    // 股票代码
    private String name;                    // 股票名称
    private double currentPrice;            // 当前价格
    
    // 涨跌相关
    private double change;                  // 涨跌额
    private double changePercent;           // 涨跌幅
    
    // 资金流向速度指标
    private double inSpeed;                 // 资金流入实时速度（万元/秒）
    private double outSpeed;                // 资金流出实时速度（万元/秒）
    private double netSpeed;                // 净流入速度（inSpeed - outSpeed）
    private double volumeSpeed;             // 成交量变化速度
    
    // 主力资金指标
    private double zhuliNetInflow;          // 主力净流入（万元）
    private double zhuliNetInflowPercent;   // 主力净流入占比
    private double zhuliNetInflowSpeed;     // 主力净流入速度（万元/秒）
    
    // 大单资金指标
    private double bigOrderNetInflow;       // 大单净流入（万元）
    private double bigOrderNetInflowPercent;// 大单净流入占比
    private double bigOrderNetInflowSpeed;  // 大单净流入速度（万元/秒）
    
    // 趋势评分
    private double rankUpTrendScore;        // 上涨趋势得分
    private double rankDownTrendScore;      // 下跌趋势得分
    private double momentumScore;           // 动量评分
    
    // 市场活跃度指标
    private double volume;                  // 成交量
    private double turnoverRate;            // 换手率
    private double avgPrice;                // 均价
    
    // 技术指标
    private double macd;                    // MACD值
    private double rsi;                     // RSI指标
    private double kdj;                     // KDJ指标
    
    private double minuteTrendScore;    // 一分钟趋势得分
    
    // 新增构造函数，用于基础数据初始化
    public StockSpeed(String code, String name, double inSpeed, double outSpeed,
                     double change, double changePercent, double volumeSpeed,
                     double rankUpTrendScore, double rankDownTrendScore) {
        this.code = code;
        this.name = name;
        this.inSpeed = inSpeed;
        this.outSpeed = outSpeed;
        this.change = change;
        this.changePercent = changePercent;
        this.volumeSpeed = volumeSpeed;
        this.rankUpTrendScore = rankUpTrendScore;
        this.rankDownTrendScore = rankDownTrendScore;
        this.netSpeed = inSpeed - outSpeed;
    }
}


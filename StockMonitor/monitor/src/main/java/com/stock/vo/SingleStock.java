package com.stock.vo;

import lombok.Data;

@Data
public class SingleStock {
    private String code;                    // 股票代码
    private String name;                    // 股票名称
    private double currentPrice;            // 当前价格
    private double zhuliNetInflow;         // 今日主力净流入
    private double zhuliNetInflowPercent;  // 今日主力净流入百分比
    private double totalNetInflow;          // 总净流入
    private double totalNetInflowPercent;   // 总净流入百分比
    private double chaodadanNetInflow;       // 超大大单净流入
    private double chaodadanNetInflowPercent; // 超大大单净流入百分比
    private double bigdanNetInflow;         // 大单净流入
    private double bigdanNetInflowPercent;  // 大单净流入百分比
    private double zhongdanNetInflow;       // 中单净流入
    private double zhongdanNetInflowPercent; // 中单净流入百分比
    private double xiaodanNetInflow;        // 小单净流入
    private double xiaodanNetInflowPercent; // 小单净流入百分比
    private double change;                  // 涨跌额
    private double changePercent;           // 涨跌幅
//    private double openPrice;               // 开盘价
//    private double highPrice;               // 最高价
//    private double lowPrice;                // 最低价
//    private double previousClose;           // 昨日收盘价
//    private double volume;                  // 成交量
//    private double avgVolume;               // 平均成交量
//    private double marketCap;               // 市值
//    private double peRatio;                 // 市盈率
//    private double dividendYield;           // 股息收益率
//    private double beta;                    // 贝塔系数
//    private double eps;                     // 每股收益
//    private double earningsDate;            // 收益日期
//    private double epsEstimateNextYear;     // 明年每股收益预估
//    private double epsEstimateNextQuarter;  // 下季度每股收益预估
//    private double epsRevisions;            // 每股收益修正
//    private double sharesOutstanding;       // 流通股本
//    private double bookValue;               // 账面价值
//    private double priceToBook;             // 市净率
//    private double priceToSales;            // 市销率
//    private double dayLow;                  // 当日最低价
//    private double dayHigh;                 // 当日最高价
//    private double yearLow;                 // 年度最低价
//    private double yearHigh;                // 年度最高价
//    private double yearTarget;              // 年度目标价
//    private double priceEarningsRatio;      // 市盈率（动态）
//    private double pegRatio;                // 市盈增长比率
//    private double priceToEarningsRatio;    // 市盈率（静态）
//    private double shortRatio;              // 空头比率
//    private double forwardPE;               // 预期市盈率
//    private double forwardEps;              // 预期每股收益
//    private double trailingPE;              // 滚动市盈率
//    private double trailingEps;             // 滚动每股收益
//    private double fiftyTwoWeekLow;         // 52周最低价
//    private double fiftyTwoWeekHigh;        // 52周最高价
//    private double priceSalesRatio;         // 市销率（PS）
//    private double priceBookRatio;          // 市净率（PB）
//    private double enterpriseValue;         // 企业价值
//    private double revenuePerShare;         // 每股营收
//    private double revenuePerEmployee;      // 人均营收
//    private double ebitda;                  // 息税折旧摊销前利润
//    private double netIncomePerShare;       // 每股净收入
//    private double profitMargin;            // 利润率
//    private double operatingMargin;         // 营业利润率
//    private double returnOnAssets;          // 资产收益率
//    private double returnOnEquity;          // 净资产收益率
//    private double revenueGrowth;           // 营收增长率
//    private double grossProfitGrowth;       // 毛利润增长率
//    private double ebitdaGrowth;            // EBITDA增长率
//    private double netIncomeGrowth;         // 净利润增长率
//    private double totalCash;               // 总现金
//    private double totalDebt;               // 总负债
//    private double totalCashPerShare;       // 每股现金
//    private double totalDebtPerShare;       // 每股负债
//    private double currentRatio;            // 流动比率
//    private double quickRatio;              // 速动比率
//    private double cashRatio;               // 现金比率
//    private double totalAssets;             // 总资产
//    private double totalLiabilities;        // 总负债
//    private double totalStockholderEquity;  // 股东权益
//    private double totalRevenue;            // 总营收
//    private double totalOperatingIncome;    // 营业总收入
//    private double totalNetIncome;          // 净利润总额
//    private double totalCashFromOperatingActivities;           // 经营活动现金流
//    private double totalCashFromFinancingActivities;          // 筹资活动现金流
//    private double totalCashFromInvestingActivities;          // 投资活动现金流
//    private double totalCashFromOperatingActivitiesPerShare;  // 每股经营现金流
//    private double totalCashFromFinancingActivitiesPerShare;  // 每股筹资现金流
//    private double totalCashFromInvestingActivitiesPerShare;  // 每股投资现金流
//    private double earningsGrowth;          // 收益增长率
//    private double revenueGrowthQ;          // 季度营收增长率
//    private double revenueGrowthY;          // 年度营收增长率
//    private double netIncomeGrowthQ;        // 季度净利润增长率
//    private double netIncomeGrowthY;        // 年度净利润增长率
//    private double epsGrowthQ;              // 季度每股收益增长率
//    private double epsGrowthY;              // 年度每股收益增长率
//    private double dividendPerShare;        // 每股股息
//    private double dividendYieldQ;          // 季度股息收益率
//    private double dividendYieldY;          // 年度股息收益率
//    private double beta3Y;                  // 3年贝塔系数
//    private double maxAge;                  // 最大年限
//    private double profitMargins;           // 利润率（百分比）
//    private double operatingMargins;        // 营业利润率（百分比）
//    private double returnOnAssetsMargins;   // 资产收益率（百分比）
//    private double returnOnEquityMargins;   // 净资产收益率（百分比）
//    private double revenueMargins;          // 营收利润率
//    private double grossProfitMargins;      // 毛利率
//    private double ebitdaMargins;           // EBITDA利润率
//    private double netIncomeMargins;        // 净利润率
//    private double earningsMargins;         // 盈利率
//    private double epsMargins;              // 每股收益率
//    private double dividendYields;          // 股息收益率（复数）
//    private double beta3Years;              // 3年贝塔值
//    private double maxAges;                 // 最大年限（复数）
//    private double profitMargin5Years;      // 5年平均利润率

    public SingleStock() {
    }
}

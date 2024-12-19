package com.stock;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stock.utils.DBUtils;
import com.stock.utils.HttpUtils;
import com.stock.vo.DailyStockData;
import com.stock.vo.SingleStock;
import com.stock.vo.StockSpeed;
import com.stock.ui.StockMonitorUI;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockMonitorMain2 {
    /**
     * 静态Map集合用于存储历史数据，实现数据的连续监控和计算
     * previousInflowMap: 记录上一次的资金流入数据，用于计算资金流入速度
     * previousOutflowMap: 记录上一次的资金流出数据，用于计算资金流出速度
     * previousVolumeMap: 记录上一次的成交量数据，用于计算成交量变化速度
     * stockNameMap: 存储股票代码和名称的映射关系
     */
    private static Map<String, Double> previousInflowMap = new HashMap<>();
    private static Map<String, Double> previousOutflowMap = new HashMap<>();
    private static Map<String, Double> previousVolumeMap = new HashMap<>();
    private static Map<String, String> stockNameMap = new HashMap<>();

    // 添加历史数据存储
    private static Map<String, List<Double>> priceHistory = new HashMap<>();
    private static Map<String, List<Double>> volumeHistory = new HashMap<>();
    private static final int HISTORY_SIZE = 100; // 保存100个历史数据点

    // 添加新的静态变量用于存储指标历史数据
    private static Map<String, List<Double>> macdHistory = new HashMap<>();
    private static Map<String, List<Double>> rsiHistory = new HashMap<>();
    private static Map<String, List<Double>> kdjHistory = new HashMap<>();

    // 修改采样周期相关的常量
    private static final int SAMPLE_INTERVAL = 10;  // 采样间隔（秒）
    private static final int ANALYSIS_PERIOD = 60;  // 分析周期（秒）
    private static final int SAMPLES_PER_PERIOD = ANALYSIS_PERIOD / SAMPLE_INTERVAL;  // 每个周期的采样次数

    // 添加一分钟数据存储
    private static Map<String, List<Double>> minutePrices = new HashMap<>();
    private static Map<String, List<Double>> minuteInflows = new HashMap<>();
    private static Map<String, List<Double>> minuteOutflows = new HashMap<>();
    private static Map<String, List<Double>> minuteVolumes = new HashMap<>();

    // 添加历史数据缓存
    private static Map<String, List<DailyStockData>> historicalData = new HashMap<>();

    private static StockMonitorUI ui;

    public static void main(String[] args) throws Exception {
        // 启动JavaFX界面
        new Thread(() -> {
            StockMonitorUI.launch(StockMonitorUI.class, args);
        }).start();

        // 等待UI初始化完成
        Thread.sleep(2000);

        // 加载历史数据
        loadHistoricalData();
        
        // 创建定时执行器
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // 每10秒执行一次数据刷新
        scheduler.scheduleAtFixedRate(() -> {
            try {
                fetchAndCalculateSpeed();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, SAMPLE_INTERVAL, TimeUnit.SECONDS);
    }

    // 添加设置UI实例的方法
    public static void setUI(StockMonitorUI uiInstance) {
        ui = uiInstance;
    }

    /**
     * 核心方法：获取股票数据并计算资金流向速度
     * 1. 分别获取主力、超大单、大单、中单、小单的净流入
     * 2. 使用Math.max提取每个类型中的正值部分（净流入为正的部分）
     */
    private static void fetchAndCalculateSpeed() throws Exception {
        String dataUrl = "https://push2.eastmoney.com/api/qt/clist/get?cb=jQuery1123044856734513874996_1732590961078&fid=f62&po=1&pz=50&pn=1&np=1&fltt=2&invt=2&ut=b2884a393a59ad64002292a3e90d46a5&fs=m%3A0%2Bt%3A6%2Bf%3A!2%2Cm%3A0%2Bt%3A13%2Bf%3A!2%2Cm%3A0%2Bt%3A80%2Bf%3A!2%2Cm%3A1%2Bt%3A2%2Bf%3A!2%2Cm%3A1%2Bt%3A23%2Bf%3A!2%2Cm%3A0%2Bt%3A7%2Bf%3A!2%2Cm%3A1%2Bt%3A3%2Bf%3A!2&fields=f12%2Cf14%2Cf2%2Cf3%2Cf62%2Cf184%2Cf66%2Cf69%2Cf72%2Cf75%2Cf78%2Cf81%2Cf84%2Cf87%2Cf204%2Cf205%2Cf124%2Cf1%2Cf13";

        // 获取当前数据
        List<String> httpResuList = HttpUtils.fetchDataFromUrl(dataUrl);
        String data0 = httpResuList.get(0);
        String dataStr = data0.substring(data0.indexOf("(") + 1, data0.lastIndexOf(")"));
        JSONObject dataJson = JSONObject.parseObject(dataStr);
        JSONObject dataJSONObject = dataJson.getJSONObject("data");
        JSONArray dataJSONArray = dataJSONObject.getJSONArray("diff");
        //Integer total = dataJSONObject.getInteger("total");
        List<SingleStock> singleStockList = new ArrayList<>();
        for (int i = 0; i < dataJSONArray.size(); i++) {
            JSONObject jsonObject = dataJSONArray.getJSONObject(i);
            if (StringUtils.isEmpty(jsonObject.getString("f12")) || "-".equals(jsonObject.getString("f2"))) {
                continue;
            }
            SingleStock singleStock = new SingleStock();
            //股票代码
            singleStock.setCode(jsonObject.getString("f12"));
            //股票名称
            singleStock.setName(jsonObject.getString("f14"));
            //当前价格
            singleStock.setCurrentPrice(jsonObject.getDouble("f2"));
            //今日涨跌幅
            singleStock.setChangePercent(jsonObject.getDouble("f3"));
            //今日主力净流入
            singleStock.setZhuliNetInflow(jsonObject.getDouble("f62"));
            //今日主力净流入百分比
            singleStock.setZhuliNetInflowPercent(jsonObject.getDouble("f184"));
            //今日超大单净流入
            singleStock.setChaodadanNetInflow(jsonObject.getDouble("f66"));
            //今日超大单净流入百分比
            singleStock.setChaodadanNetInflowPercent(jsonObject.getDouble("f69"));
            //今日大单净流入
            singleStock.setBigdanNetInflow(jsonObject.getDouble("f72"));
            //今日大单净流入百分比
            singleStock.setBigdanNetInflowPercent(jsonObject.getDouble("f75"));
            //今日中单净流入
            singleStock.setZhongdanNetInflow(jsonObject.getDouble("f78"));
            //今日中单净流入百分比
            singleStock.setZhongdanNetInflowPercent(jsonObject.getDouble("f81"));
            //今日小单净流入
            singleStock.setXiaodanNetInflow(jsonObject.getDouble("f84"));
            //今日小单净流入百分比
            singleStock.setXiaodanNetInflowPercent(jsonObject.getDouble("f87"));
            singleStockList.add(singleStock);
        }

        // 用于存储当前数据
        Map<String, Double> currentInflowMap = new HashMap<>();
        Map<String, Double> currentOutflowMap = new HashMap<>();
        Map<String, Double> currentVolumeMap = new HashMap<>();
        List<StockSpeed> speedResults = new ArrayList<>();

        // 第一轮循环：计算基础数据和趋势分数
        for (SingleStock singleStock : singleStockList) {
            String code = singleStock.getCode();
            String name = singleStock.getName();

            /**
             * 计算总流入金额：
             * 1. 分别获取主力、超大单、大单、中单、小单的净流入
             * 2. 使用Math.max提取每个类型中的值部分（净流入为正的部分）
             * 3. 将所有正值相加得到总流入金额
             * 计算公式：totalInflow = Σ max(各类型净流入, 0)
             */
            double totalInflow = Math.max(singleStock.getZhuliNetInflow(), 0) +
                    Math.max(singleStock.getChaodadanNetInflow(), 0) +
                    Math.max(singleStock.getBigdanNetInflow(), 0) +
                    Math.max(singleStock.getZhongdanNetInflow(), 0) +
                    Math.max(singleStock.getXiaodanNetInflow(), 0);
            totalInflow = totalInflow / 10000;

            /**
             * 计算总流出金额：
             * 1. 分别获取主力、超大单、大单、中单、小单的净流入
             * 2. 使用Math.min提取每个类型中的负值部分
             * 3. 取绝对值后相加得到总流出金额
             * 单位：万元
             * 计算公式：totalOutflow = Σ |min(各类型净流入, 0)|
             */
            double totalOutflow = Math.abs(Math.min(singleStock.getZhuliNetInflow(), 0)) +
                    Math.abs(Math.min(singleStock.getChaodadanNetInflow(), 0)) +
                    Math.abs(Math.min(singleStock.getBigdanNetInflow(), 0)) +
                    Math.abs(Math.min(singleStock.getZhongdanNetInflow(), 0)) +
                    Math.abs(Math.min(singleStock.getXiaodanNetInflow(), 0));
            totalOutflow = totalOutflow / 10000;

            /**
             * 计算总成交量：
             * 总成交量 = 流入金额 + 流出金额
             * 这代表了市场的总体活跃度
             */
            double volume = totalInflow + totalOutflow;

            currentInflowMap.put(code, totalInflow);
            currentOutflowMap.put(code, totalOutflow);
            currentVolumeMap.put(code, volume);
            stockNameMap.put(code, name);

            /**
             * 速度计算逻辑：
             * 1. 计算两次采样间的差值（当前值 - 上次值）
             * 2. 除以时间间隔（10秒）得到每秒的变化率
             *
             * 资金流入速度(inSpeed) = (当前流入 - 次流入) / 10
             * 资金流出速度(outSpeed) = (当前流出 - 上次流出) / 10
             * 成交量变化速度(volumeSpeed) = (当前成交量 - 上次成交量) / 10
             *
             * 单位：万元/秒
             */
            if (previousInflowMap.containsKey(code) && previousOutflowMap.containsKey(code) && previousVolumeMap.containsKey(code)) {
                double inflowDiff = totalInflow - previousInflowMap.get(code);
                double outflowDiff = totalOutflow - previousOutflowMap.get(code);
                double volumeDiff = volume - previousVolumeMap.get(code);

                // 计算速度（万元/秒）
                double inSpeed = inflowDiff / 10.0;  // 10秒间隔
                double outSpeed = outflowDiff / 10.0;
                double volumeSpeed = volumeDiff / 10.0;

                // 计算上涨趋势分数 (0-100分)
                double rankUpTrendScore = calculateUpTrendScore(
                        inSpeed,
                        outSpeed,
                        volumeSpeed,
                        singleStock.getChangePercent(),
                        singleStock.getZhuliNetInflowPercent(),
                        singleStock.getChaodadanNetInflowPercent(),
                        code
                );

                // 计算下跌趋势分数 (0-100分)
                double rankDownTrendScore = calculateDownTrendScore(
                        inSpeed,
                        outSpeed,
                        volumeSpeed,
                        singleStock.getChangePercent(),
                        singleStock.getZhuliNetInflowPercent(),
                        singleStock.getChaodadanNetInflowPercent()
                );

                // 只添加有显著资金流动的股票
                if (Math.abs(inSpeed) > 10) {
                    StockSpeed speedResult = new StockSpeed(
                            code,
                            name,
                            inSpeed,
                            outSpeed,
                            singleStock.getChange(),
                            singleStock.getChangePercent(),
                            volumeSpeed,
                            rankUpTrendScore,
                            rankDownTrendScore
                    );
                    speedResult.setCurrentPrice(singleStock.getCurrentPrice());
                    speedResult.setNetSpeed(inSpeed - outSpeed);
                    speedResult.setZhuliNetInflow(singleStock.getZhuliNetInflow());
                    speedResult.setZhuliNetInflowPercent(singleStock.getZhuliNetInflowPercent());
                    speedResult.setBigOrderNetInflow(singleStock.getBigdanNetInflow());
                    speedResult.setBigOrderNetInflowPercent(singleStock.getBigdanNetInflowPercent());
                    speedResult.setMomentumScore(calculateMomentumScore(singleStock, inSpeed, outSpeed));
                    speedResult.setVolume(volume);
                    speedResult.setTurnoverRate(volume / singleStock.getCurrentPrice() / 10000);
                    speedResult.setAvgPrice(singleStock.getCurrentPrice());
                    speedResult.setMacd(calculateTechnicalScore(singleStock));
                    speedResult.setRsi(calculateTechnicalScore(singleStock));
                    speedResult.setKdj(calculateTechnicalScore(singleStock));
                    speedResults.add(speedResult);
                }
            }

            // 更新一分钟数据
            updateMinuteData(code, singleStock.getCurrentPrice(), totalInflow, totalOutflow, volume);

            // 保存数据到数据库
            saveStockData(singleStock, totalInflow, totalOutflow);
        }

        // 更新上一次的数据
        previousInflowMap = currentInflowMap;
        previousOutflowMap = currentOutflowMap;
        previousVolumeMap = currentVolumeMap;

        // 输出更新时间
        System.out.println("\n更新时间: " + new Date());

        if (CollectionUtils.isNotEmpty(speedResults)) {
            // 按照上涨趋势分数排序
            Collections.sort(speedResults, (a, b) ->
                    Double.compare(b.getRankUpTrendScore(), a.getRankUpTrendScore())
            );

            // 更新UI
            if (ui != null) {
                ui.updateRealTimeData(speedResults);
            }
        } else {
            System.out.println("无满足需求的更新数据");
        }
    }

    /**
     * 计算股票上涨趋势分数
     * 总分100分，由以下5个指标加权计算：
     * <p>
     * 1. 资金流入速度权重(30分)：
     * - 计算公式：30 * (流入速度 / (流入速度 + 流出速度))
     * - 流入速度越大，得分越高
     * <p>
     * 2. 涨跌幅权重(20分)：
     * - 计算公式：min(20, 涨跌幅 * 2)
     * - 正涨幅越大，得分越高，最高20分
     * <p>
     * 3. 主力净流入占比权重(25分)：
     * - 计算公式：min(25, 主力净流入占比 * 2.5)
     * - 主力净流入占比越大，得分越高，最高25分
     * <p>
     * 4. 超大单净流入占比权重(15分)：
     * - 计算公式：min(15, 超大单净流入占比 * 1.5)
     * - 超大单净流入占比越大，得分越高，最高15分
     * <p>
     * 5. 成交量变化权重(10分)：
     * - 计算公式：min(10, 成交量变化速度 / 1000)
     * - 成交量增速越大，得分越高，最高10分
     */
    private static double calculateUpTrendScore(
            double inSpeed,
            double outSpeed,
            double volumeSpeed,
            double changePercent,
            double zhuliNetInflowPercent,
            double chaodadanNetInflowPercent,
            String code) {
        
        double score = 0;
        
        // 获取当天累计数据
        DailyStockData dailyData = getDailyData(code);
        double dayInflow = dailyData.getInflow();
        double dayOutflow = dailyData.getOutflow();
        
        // 获取历史趋势数据
        List<DailyStockData> history = historicalData.get(code);
        double historicalScore = 0;
        if (history != null && !history.isEmpty()) {
            // 计算5日资金流向趋势
            int days = Math.min(5, history.size());
            double fiveDayInflow = 0;
            double fiveDayOutflow = 0;
            for (int i = history.size() - days; i < history.size(); i++) {
                DailyStockData data = history.get(i);
                fiveDayInflow += data.getInflow();
                fiveDayOutflow += data.getOutflow();
            }
            
            if (fiveDayInflow + fiveDayOutflow > 0) {
                historicalScore = 20 * (fiveDayInflow / (fiveDayInflow + fiveDayOutflow));
            }
        }
        
        // 1. 实时资金流向权重 (20分)
        if (inSpeed > outSpeed) {
            score += 20 * (inSpeed / (inSpeed + outSpeed));
        }
        
        // 2. 当天累计资金向权重 (20分)
        if (dayInflow > dayOutflow) {
            score += 20 * (dayInflow / (dayInflow + dayOutflow));
        }
        
        // 3. 历史资金流向趋势 (20分)
        score += historicalScore;
        
        // 4. 涨跌幅权重 (20分)
        if (changePercent > 0) {
            score += Math.min(20, changePercent * 2);
        }
        
        // 5. 主力净流入占比权重 (20分)
        if (zhuliNetInflowPercent > 0) {
            score += Math.min(20, zhuliNetInflowPercent * 2);
        }
        
        return Math.max(0, Math.min(100, score));
    }

    /**
     * 计算股票下跌趋势分数
     * 总分100分，计算逻辑与上涨趋势相反：
     * <p>
     * 1. 资金流出速度权重(30分)：
     * - 计算公式：30 * (流出速度 / (流入速度 + 流出速度))
     * - 流出速度越大，得分越高
     * <p>
     * 2. 涨跌幅权重(20分)：
     * - 计算公式：min(20, |涨跌| * 2)
     * - 负涨幅越大，得分越高，最高20分
     * <p>
     * 3. 主力净流入占比权重(25分)：
     * - 计算公式：min(25, |主力净流入占比| * 2.5)
     * - 主力净流出占比越大，得分越高，最高25分
     * <p>
     * 4. 超大单净流入占比权重(15分)：
     * - 计算公式：min(15, |超大单净流入占比| * 1.5)
     * - 超大单净流出占比越大，得分越高，最高15分
     * <p>
     * 5. 成交量变化权重(10分)：
     * - 计算公式：min(10, 成交量变化速度 / 1000)
     * - 成交量增速越大，得分越高，最高10分
     */
    private static double calculateDownTrendScore(
            double inSpeed,
            double outSpeed,
            double volumeSpeed,
            double changePercent,
            double zhuliNetInflowPercent,
            double chaodadanNetInflowPercent) {

        double score = 0;

        // 1. 资金流出速度权重 30分
        if (outSpeed > inSpeed) {
            score += 30 * (outSpeed / (inSpeed + outSpeed));
        }

        // 2. 涨跌幅权重 20分
        if (changePercent < 0) {
            score += Math.min(20, Math.abs(changePercent * 2));
        }

        // 3. 主力净流入占比权重 25分
        if (zhuliNetInflowPercent < 0) {
            score += Math.min(25, Math.abs(zhuliNetInflowPercent * 2.5));
        }

        // 4. 超大单净流入占比权重 15分
        if (chaodadanNetInflowPercent < 0) {
            score += Math.min(15, Math.abs(chaodadanNetInflowPercent * 1.5));
        }

        // 5. 成交量变化权重 10分
        if (volumeSpeed > 0) {
            score += Math.min(10, volumeSpeed / 1000);
        }

        return Math.min(100, score);
    }

    private static double calculateMomentumScore(SingleStock stock, double inSpeed, double outSpeed) {
        /**
         * 动量评分计算 (0-100分)
         * 1. 价格动量 (40分)：
         *    - 基于涨跌幅和价格变化速度
         * 2. 资金动量 (40分)：
         *    - 基于净流入速度和累计净流入
         * 3. 成交量动量 (20分)：
         *    - 基于成交量变化和换手率
         */
        double score = 0;

        // 1. 价格动量
        double priceScore = Math.min(40, Math.abs(stock.getChangePercent()) * 4);

        // 2. 资金动量
        double netSpeed = inSpeed - outSpeed;
        double fundScore = 40 * (netSpeed / (Math.abs(inSpeed) + Math.abs(outSpeed)));

        // 3. 成交量动量
        double volumeScore = Math.min(20, (inSpeed + outSpeed) / 1000);

        score = priceScore + fundScore + volumeScore;
        return Math.max(0, Math.min(100, score));
    }

    /**
     * 计算MACD指标
     * MACD = DIF - DEA
     * DIF = 12日EMA - 26日EMA
     * DEA = DIF的9日EMA
     */
    private static double calculateMACD(String code) {
        List<Double> prices = priceHistory.get(code);
        if (prices == null || prices.size() < 26) {
            return 0;
        }

        // 计算12日和26日EMA
        double ema12 = calculateEMA(prices, 12);
        double ema26 = calculateEMA(prices, 26);

        // 计算DIF
        double dif = ema12 - ema26;

        // 计算DEA (9日EMA of DIF)
        List<Double> difHistory = new ArrayList<>();
        for (int i = prices.size() - 9; i < prices.size(); i++) {
            double tempEma12 = calculateEMA(prices.subList(0, i + 1), 12);
            double tempEma26 = calculateEMA(prices.subList(0, i + 1), 26);
            difHistory.add(tempEma12 - tempEma26);
        }
        double dea = calculateEMA(difHistory, 9);

        // 计算MACD
        return (dif - dea) * 2;
    }

    /**
     * 计算RSI指标
     * RSI = 100 - [100 / (1 + RS)]
     * RS = 平均上涨点数 / 平均下跌点数
     */
    private static double calculateRSI(String code) {
        List<Double> prices = priceHistory.get(code);
        // 如果价格历史数据不足，返回默认值
        if (prices == null || prices.size() < 15) {  // 需要至少15个数据点
            return 50; // 默认值
        }

        double sumGain = 0;
        double sumLoss = 0;

        // 计算14日RSI，从最近的数据开始计算
        for (int i = prices.size() - 14; i < prices.size(); i++) {
            // 确保有前的数据用于计算变化
            if (i > 0) {
                double change = prices.get(i) - prices.get(i - 1);
                if (change > 0) {
                    sumGain += change;
                } else {
                    sumLoss -= change;  // 注意这里是减去change（因为change是负数）
                }
            }
        }

        // 计算平均值
        double avgGain = sumGain / 14.0;
        double avgLoss = sumLoss / 14.0;

        // 避免除以零
        if (avgLoss == 0) {
            return avgGain > 0 ? 100 : 50;
        }

        // 计算相对强弱值和RSI
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * 计算KDJ指标
     * K = 2/3 × 前一日K值 + 1/3 × 当日RSV
     * D = 2/3 × 前一日D值 + 1/3 × 当日K值
     * J = 3 × 当日K值 - 2 × 当日D值
     */
    private static double calculateKDJ(String code) {
        List<Double> prices = priceHistory.get(code);
        if (prices == null || prices.size() < 9) {
            return 50; // 默认值
        }

        // 计算9日RSV
        List<Double> last9 = prices.subList(prices.size() - 9, prices.size());
        double high = Collections.max(last9);
        double low = Collections.min(last9);
        double close = prices.get(prices.size() - 1);

        double rsv = ((close - low) / (high - low)) * 100;

        // 假设前一日K=50, D=50
        double k = (2.0 / 3.0 * 50) + (1.0 / 3.0 * rsv);
        double d = (2.0 / 3.0 * 50) + (1.0 / 3.0 * k);
        double j = (3 * k) - (2 * d);

        return j; // 返回J值作为KDJ指标
    }

    /**
     * 辅助方法：计算EMA
     */
    private static double calculateEMA(List<Double> prices, int period) {
        double multiplier = 2.0 / (period + 1);
        double ema = prices.get(0);

        for (int i = 1; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * 更新技术指标计算方法
     */
    private static double calculateTechnicalScore(SingleStock stock) {
        String code = stock.getCode();

        // 更新历史数据
        updateHistoryData(code, stock.getCurrentPrice(), stock.getTotalVolume());

        // 如果历史数据不足，返回中性值
        if (!priceHistory.containsKey(code) || priceHistory.get(code).size() < 26) {
            return 50;
        }

        // 计算各项技术指标
        double macd = calculateMACD(code);
        double rsi = calculateRSI(code);
        double kdj = calculateKDJ(code);

        // 检测交叉信号
        int macdSignal = detectMACDCross(code);
        int rsiSignal = detectRSISignal(code);
        int kdjSignal = detectKDJCross(code);

        double score = 0;

        // MACD得分 (40分)
        if (macd > 0) {
            score += Math.min(30, macd * 15);
            if (macdSignal == 1) {  // 金叉加分
                score += 10;
            }
        } else if (macdSignal == -1) { // 死叉减分
            score -= 10;
        }

        // RSI得分 (30分)
        if (rsi >= 30 && rsi <= 70) {
            score += 20;
            if (rsi >= 45 && rsi <= 55) {
                score += 10;  // 处于中性区域加分
            }
        } else if (rsi < 30) {
            score += 30 * (rsi / 30);
            if (rsiSignal == 1) {  // 超卖反弹信号
                score += 10;
            }
        } else {
            score += 30 * ((100 - rsi) / 30);
            if (rsiSignal == -1) { // 超买回落信号
                score -= 10;
            }
        }

        // KDJ得分 (30分)
        if (kdj >= 20 && kdj <= 80) {
            score += 20;
            if (kdjSignal == 1) {  // 金叉加分
                score += 10;
            }
        } else if (kdj < 20) {
            score += 30 * (kdj / 20);
            if (kdjSignal == 1) {  // 超卖反弹信号
                score += 10;
            }
        } else {
            score += 30 * ((100 - kdj) / 20);
            if (kdjSignal == -1) { // 超买回落信号
                score -= 10;
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    // 在 fetchAndCalculateSpeed 方法中更新历史数据的收集逻辑
    private static void updateHistoryData(String code, double price, double volume) {
        // 更新价格历史
        List<Double> prices = priceHistory.computeIfAbsent(code, k -> new ArrayList<>());
        prices.add(price);
        if (prices.size() > HISTORY_SIZE) {
            prices.remove(0);
        }

        // 更新成交量历史
        List<Double> volumes = volumeHistory.computeIfAbsent(code, k -> new ArrayList<>());
        volumes.add(volume);
        if (volumes.size() > HISTORY_SIZE) {
            volumes.remove(0);
        }

        // 只在有足够历史数据时才计算技术指标
        if (prices.size() >= 26) {  // 使用最大所需的历史数据长度
            // 计算并更新技术指标历史
            double macd = calculateMACD(code);
            double rsi = calculateRSI(code);
            double kdj = calculateKDJ(code);

            macdHistory.computeIfAbsent(code, k -> new ArrayList<>()).add(macd);
            rsiHistory.computeIfAbsent(code, k -> new ArrayList<>()).add(rsi);
            kdjHistory.computeIfAbsent(code, k -> new ArrayList<>()).add(kdj);

            // 维护历史数据大小
            if (macdHistory.get(code).size() > HISTORY_SIZE) {
                macdHistory.get(code).remove(0);
            }
            if (rsiHistory.get(code).size() > HISTORY_SIZE) {
                rsiHistory.get(code).remove(0);
            }
            if (kdjHistory.get(code).size() > HISTORY_SIZE) {
                kdjHistory.get(code).remove(0);
            }
        }
    }

    /**
     * 检测MACD金叉死叉信号
     * 金叉：MACD从负转正
     * 死叉：MACD从正转负
     */
    private static int detectMACDCross(String code) {
        List<Double> history = macdHistory.get(code);
        if (history == null || history.size() < 2) {
            return 0;
        }

        double current = history.get(history.size() - 1);
        double previous = history.get(history.size() - 2);

        if (previous < 0 && current > 0) {
            return 1;  // 金叉
        } else if (previous > 0 && current < 0) {
            return -1; // 死叉
        }
        return 0;     // 无信号
    }

    /**
     * 检测RSI超买超卖信号
     * 超买：RSI > 80
     * 超卖：RSI < 20
     */
    private static int detectRSISignal(String code) {
        List<Double> history = rsiHistory.get(code);
        if (history == null || history.size() < 2) {
            return 0;
        }

        double current = history.get(history.size() - 1);

        if (current > 80) {
            return -1; // 超买信号
        } else if (current < 20) {
            return 1;  // 超卖信号
        }
        return 0;     // 无信号
    }

    /**
     * 检测KDJ交叉信号
     * 金叉：J线上穿K线
     * 死叉：J线下穿K线
     */
    private static int detectKDJCross(String code) {
        List<Double> history = kdjHistory.get(code);
        if (history == null || history.size() < 2) {
            return 0;
        }

        double current = history.get(history.size() - 1);
        double previous = history.get(history.size() - 2);

        // 使用KDJ的J值判断趋势变化
        if (previous < 20 && current > 20) {
            return 1;  // 金叉
        } else if (previous > 80 && current < 80) {
            return -1; // 死叉
        }
        return 0;     // 无信号
    }

    // 添加一分钟数据更新方法
    private static void updateMinuteData(String code, double price, double inflow, double outflow, double volume) {
        // 更新价格
        minutePrices.computeIfAbsent(code, k -> new ArrayList<>()).add(price);
        if (minutePrices.get(code).size() > SAMPLES_PER_PERIOD) {
            minutePrices.get(code).remove(0);
        }

        // 更新资金流入
        minuteInflows.computeIfAbsent(code, k -> new ArrayList<>()).add(inflow);
        if (minuteInflows.get(code).size() > SAMPLES_PER_PERIOD) {
            minuteInflows.get(code).remove(0);
        }

        // 更新资金流出
        minuteOutflows.computeIfAbsent(code, k -> new ArrayList<>()).add(outflow);
        if (minuteOutflows.get(code).size() > SAMPLES_PER_PERIOD) {
            minuteOutflows.get(code).remove(0);
        }

        // 更新成交量
        minuteVolumes.computeIfAbsent(code, k -> new ArrayList<>()).add(volume);
        if (minuteVolumes.get(code).size() > SAMPLES_PER_PERIOD) {
            minuteVolumes.get(code).remove(0);
        }
    }


    // 添加新的方法用于保存股票数据到数据库
    private static void saveStockData(SingleStock stock, double totalInflow, double totalOutflow) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DBUtils.getConnection();
            String sql = "INSERT INTO single_stock_data (code, name, current_price, change_amount, change_percent, " +
                        "zhuli_net_inflow, zhuli_net_inflow_percent, total_net_inflow, total_net_inflow_percent, " +
                        "chaodadan_net_inflow, chaodadan_net_inflow_percent, bigdan_net_inflow, bigdan_net_inflow_percent, " +
                        "zhongdan_net_inflow, zhongdan_net_inflow_percent, xiaodan_net_inflow, xiaodan_net_inflow_percent, " +
                        "total_volume) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, stock.getCode());
            ps.setString(2, stock.getName());
            ps.setDouble(3, stock.getCurrentPrice());
            ps.setDouble(4, stock.getChange());
            ps.setDouble(5, stock.getChangePercent());
            ps.setDouble(6, stock.getZhuliNetInflow());
            ps.setDouble(7, stock.getZhuliNetInflowPercent());
            ps.setDouble(8, totalInflow - totalOutflow); // 总净流入
            ps.setDouble(9, (totalInflow - totalOutflow) / (totalInflow + totalOutflow) * 100); // 总净流入百分比
            ps.setDouble(10, stock.getChaodadanNetInflow());
            ps.setDouble(11, stock.getChaodadanNetInflowPercent());
            ps.setDouble(12, stock.getBigdanNetInflow());
            ps.setDouble(13, stock.getBigdanNetInflowPercent());
            ps.setDouble(14, stock.getZhongdanNetInflow());
            ps.setDouble(15, stock.getZhongdanNetInflowPercent());
            ps.setDouble(16, stock.getXiaodanNetInflow());
            ps.setDouble(17, stock.getXiaodanNetInflowPercent());
            ps.setDouble(18, totalInflow + totalOutflow); // 总成交量
            
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtils.close(conn, ps, null);
        }
    }

    // 获取当天历史数据
    private static DailyStockData getDailyData(String code) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtils.getConnection();
            String sql = "SELECT SUM(CASE WHEN total_net_inflow > 0 THEN total_net_inflow ELSE 0 END) as day_inflow, " +
                        "SUM(CASE WHEN total_net_inflow < 0 THEN ABS(total_net_inflow) ELSE 0 END) as day_outflow " +
                        "FROM single_stock_data WHERE code = ? AND DATE(create_time) = CURDATE()";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, code);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                return new DailyStockData(
                    rs.getDouble("day_inflow"),
                    rs.getDouble("day_outflow")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtils.close(conn, ps, rs);
        }
        return new DailyStockData(0, 0);
    }

    // 添加历史数据加载方法
    private static void loadHistoricalData() {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtils.getConnection();
            String sql = "SELECT code, current_price, total_net_inflow, total_volume, create_time " +
                        "FROM single_stock_data WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                        "ORDER BY code, create_time";
            
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            
            String currentCode = null;
            List<DailyStockData> dataList = null;
            
            while (rs.next()) {
                String code = rs.getString("code");
                if (!code.equals(currentCode)) {
                    if (currentCode != null) {
                        historicalData.put(currentCode, dataList);
                    }
                    currentCode = code;
                    dataList = new ArrayList<>();
                }
                
                double netInflow = rs.getDouble("total_net_inflow");
                double volume = rs.getDouble("total_volume");
                // 根据净流入计算流入和流出
                double inflow = netInflow > 0 ? netInflow : 0;
                double outflow = netInflow < 0 ? Math.abs(netInflow) : 0;
                
                dataList.add(new DailyStockData(inflow, outflow));
            }
            if (currentCode != null) {
                historicalData.put(currentCode, dataList);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtils.close(conn, ps, rs);
        }
    }

    private static double getYesterdayNetInflow(String code) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtils.getConnection();
            String sql = "SELECT SUM(total_net_inflow) as net_inflow " +
                        "FROM single_stock_data WHERE code = ? AND DATE(create_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, code);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getDouble("net_inflow");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtils.close(conn, ps, rs);
        }
        return 0;
    }

    private static double getTodayOutflow(String code) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtils.getConnection();
            String sql = "SELECT SUM(CASE WHEN total_net_inflow < 0 THEN ABS(total_net_inflow) ELSE 0 END) as outflow " +
                        "FROM single_stock_data WHERE code = ? AND DATE(create_time) = CURDATE()";
            
            ps = conn.prepareStatement(sql);
            ps.setString(1, code);
            rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getDouble("outflow");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBUtils.close(conn, ps, rs);
        }
        return 0;
    }
}
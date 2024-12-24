package com.stock;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stock.utils.DBUtils;
import com.stock.utils.HttpUtils;
import com.stock.vo.DailyStockData;
import com.stock.vo.SingleStock;
import com.stock.vo.StockSpeed;
import com.stock.ui.StockMonitorUI;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
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

    // 添加分钟数据存储
    private static Map<String, List<Double>> minutePrices = new HashMap<>();
    private static Map<String, List<Double>> minuteInflows = new HashMap<>();
    private static Map<String, List<Double>> minuteOutflows = new HashMap<>();
    private static Map<String, List<Double>> minuteVolumes = new HashMap<>();

    // 添加一分钟数据缓存
    private static Map<String, List<SingleStock>> minuteStockCache = new HashMap<>();
    private static Map<String, List<Double>> minuteInflowCache = new HashMap<>();
    private static Map<String, List<Double>> minuteOutflowCache = new HashMap<>();

    // 添加历史数据缓存
    private static Map<String, List<DailyStockData>> historicalData = new HashMap<>();

    private static StockMonitorUI ui;

    // 添加数据保存线程池
    private static final ExecutorService saveDataExecutor = Executors.newFixedThreadPool(4);

    public static void main(String[] args) throws Exception {
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("正在关闭应用...");
            saveDataExecutor.shutdown();
            try {
                if (!saveDataExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    saveDataExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                saveDataExecutor.shutdownNow();
            }
            DBUtils.shutdown();
            System.out.println("应用已关闭");
        }));
        
        // 启动JavaFX界面
        new Thread(() -> {
            StockMonitorUI.launch(StockMonitorUI.class, args);
        }).start();

        // 等待UI初始化完成
        Thread.sleep(2000);

        new Thread(() -> {
            // 加载历史数据
            loadHistoricalData();
        }).start();
        
        // 创建定时执行器
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

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
        // 判断当前时间是否在交易时间内
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int currentTime = hour * 100 + minute;  // 例如：9:30 = 930

        // 检查是否在交易时间内
        boolean isTradeTime = (currentTime >= 930 && currentTime <= 1130) ||  // 上午交易时间
                (currentTime >= 1300 && currentTime <= 1500);     // 下午交易时间

        if (!isTradeTime) {
            return;
        }
        String dataUrl = "https://push2.eastmoney.com/api/qt/clist/get?" +
                "cb=jQuery1123044856734513874996_1732590961078" +
                "&fid=f62" +
                "&po=1" +
                "&pz=5300" +
                "&pn=1" +
                "&np=1" +
                "&fltt=2" +
                "&invt=2" +
                "&ut=b2884a393a59ad64002292a3e90d46a5" +
                "&fs=m%3A0%2Bt%3A6%2Bf%3A!2%2Cm%3A0%2Bt%3A13%2Bf%3A!2%2Cm%3A0%2Bt%3A80%2Bf%3A!2%2Cm%3A1%2Bt%3A2%2Bf%3A!2%2Cm%3A1%2Bt%3A23%2Bf%3A!2%2Cm%3A0%2Bt%3A7%2Bf%3A!2%2Cm%3A1%2Bt%3A3%2Bf%3A!2&fields=f12%2Cf14%2Cf2%2Cf3%2Cf62%2Cf184%2Cf66%2Cf69%2Cf72%2Cf75%2Cf78%2Cf81%2Cf84%2Cf87%2Cf204%2Cf205%2Cf124%2Cf1%2Cf13";

        // 用于存储当前数据
        Map<String, Double> currentInflowMap = new HashMap<>();
        Map<String, Double> currentOutflowMap = new HashMap<>();
        Map<String, Double> currentVolumeMap = new HashMap<>();
        List<StockSpeed> speedResults = new ArrayList<>();

        // 获取当前数据
        List<String> httpResuList = HttpUtils.fetchDataFromUrl(dataUrl);
        String data0 = httpResuList.get(0);
        String dataStr = data0.substring(data0.indexOf("(") + 1, data0.lastIndexOf(")"));
        JSONObject dataJson = JSONObject.parseObject(dataStr);
        JSONObject dataJSONObject = dataJson.getJSONObject("data");
        JSONArray dataJSONArray = dataJSONObject.getJSONArray("diff");

        for (int i = 0; i < dataJSONArray.size(); i++) {
            JSONObject jsonObject = dataJSONArray.getJSONObject(i);
            if (StringUtils.isEmpty(jsonObject.getString("f12")) || "-".equals(jsonObject.getString("f2"))) {
                continue;
            }

            // 检查必要字段是否存在且有效
            SingleStock singleStock = new SingleStock();
            //股票代码
            singleStock.setCode(jsonObject.getString("f12"));
            //股票名称
            singleStock.setName(jsonObject.getString("f14"));
            //当前价格
            singleStock.setCurrentPrice(parseDouble(jsonObject, "f2", 0.0));
            //今日涨跌幅
            singleStock.setChangePercent(parseDouble(jsonObject, "f3", 0.0));
            //今日主力净流入
            singleStock.setZhuliNetInflow(parseDouble(jsonObject, "f62", 0.0));
            //今日主力净流入百分比
            singleStock.setZhuliNetInflowPercent(parseDouble(jsonObject, "f184", 0.0));
            //今日超大单净流入
            singleStock.setChaodadanNetInflow(parseDouble(jsonObject, "f66", 0.0));
            //今日超大单净流入百分比
            singleStock.setChaodadanNetInflowPercent(parseDouble(jsonObject, "f69", 0.0));
            //今日大单净流入
            singleStock.setBigdanNetInflow(parseDouble(jsonObject, "f72", 0.0));
            //今日大单净流入百分比
            singleStock.setBigdanNetInflowPercent(parseDouble(jsonObject, "f75", 0.0));
            //今日中单净流入
            singleStock.setZhongdanNetInflow(parseDouble(jsonObject, "f78", 0.0));
            //今日中单净流入百分比
            singleStock.setZhongdanNetInflowPercent(parseDouble(jsonObject, "f81", 0.0));
            //今日小单净流入
            singleStock.setXiaodanNetInflow(parseDouble(jsonObject, "f84", 0.0));
            //今日小单净流入百分比
            singleStock.setXiaodanNetInflowPercent(parseDouble(jsonObject, "f87", 0.0));

            // 计算总流入金额
            double totalInflow = Math.max(singleStock.getZhuliNetInflow(), 0) +
                    Math.max(singleStock.getChaodadanNetInflow(), 0) +
                    Math.max(singleStock.getBigdanNetInflow(), 0) +
                    Math.max(singleStock.getZhongdanNetInflow(), 0) +
                    Math.max(singleStock.getXiaodanNetInflow(), 0);

            // 计算总流出金额
            double totalOutflow = Math.abs(Math.min(singleStock.getZhuliNetInflow(), 0)) +
                    Math.abs(Math.min(singleStock.getChaodadanNetInflow(), 0)) +
                    Math.abs(Math.min(singleStock.getBigdanNetInflow(), 0)) +
                    Math.abs(Math.min(singleStock.getZhongdanNetInflow(), 0)) +
                    Math.abs(Math.min(singleStock.getXiaodanNetInflow(), 0));

            // 计算总成交量
            double volume = totalInflow + totalOutflow;

            // 更新当前数据
            currentInflowMap.put(singleStock.getCode(), totalInflow);
            currentOutflowMap.put(singleStock.getCode(), totalOutflow);
            currentVolumeMap.put(singleStock.getCode(), volume);
            stockNameMap.put(singleStock.getCode(), singleStock.getName());

            if (previousInflowMap.containsKey(singleStock.getCode()) && previousOutflowMap.containsKey(singleStock.getCode())
                    && previousVolumeMap.containsKey(singleStock.getCode())) {
                double inflowDiff = totalInflow - previousInflowMap.get(singleStock.getCode());
                double outflowDiff = totalOutflow - previousOutflowMap.get(singleStock.getCode());
                double volumeDiff = volume - previousVolumeMap.get(singleStock.getCode());

                // 计算速度（万元/秒）
                double inSpeed = inflowDiff / SAMPLE_INTERVAL;
                double outSpeed = outflowDiff / SAMPLE_INTERVAL;
                double volumeSpeed = volumeDiff / SAMPLE_INTERVAL;

                // 更新分钟数据
                updateMinuteData(singleStock.getCode(), singleStock.getCurrentPrice(), totalInflow, totalOutflow, volume);

                // 计算动量分数
                double momentumScore = calculateMomentumScore(singleStock, inSpeed, outSpeed);

                // 计算趋势分数
                double rankUpTrendScore = calculateUpTrendScore(
                        inSpeed,
                        outSpeed,
                        volumeSpeed,
                        singleStock.getChangePercent(),
                        singleStock.getZhuliNetInflowPercent(),
                        singleStock.getChaodadanNetInflowPercent(),
                        singleStock.getCode()
                );

                double rankDownTrendScore = calculateDownTrendScore(
                        inSpeed,
                        outSpeed,
                        volumeSpeed,
                        singleStock.getChangePercent(),
                        singleStock.getZhuliNetInflowPercent(),
                        singleStock.getChaodadanNetInflowPercent()
                );

                // 添加到结果列表
                if (Math.abs(inSpeed) > 0 || Math.abs(outSpeed) > 0) {
                    StockSpeed speedResult = new StockSpeed(
                            singleStock.getCode(),
                            singleStock.getName(),
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
                    speedResult.setMomentumScore(momentumScore);
                    speedResult.setVolume(volume);
                    speedResult.setTurnoverRate(volume / (singleStock.getCurrentPrice() * 100));
                    speedResult.setAvgPrice(singleStock.getCurrentPrice());
                    speedResult.setMacd(calculateTechnicalScore(singleStock));
                    speedResult.setRsi(calculateRSI(singleStock.getCode()));
                    speedResult.setKdj(calculateKDJ(singleStock.getCode()));
                    speedResults.add(speedResult);
                }

                // 保存数据到数据库
                saveStockData(singleStock, totalInflow, totalOutflow);
            }
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
        
        // 获取当日累计数据
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
        if (inSpeed > outSpeed && (inSpeed + outSpeed) > 0) {
            score += 20 * (inSpeed / (inSpeed + outSpeed));
        }
        
        // 2. 当天累计资金向权重 (20分)
        if (dayInflow > dayOutflow && (dayInflow + dayOutflow) > 0) {
            score += 20 * (dayInflow / (dayInflow + dayOutflow));
        }
        
        // 3. 历史资金流向趋势 (20分)
        score += historicalScore;
        
        // 4. 涨跌幅权重 (20分)
        if (changePercent > 0 && !Double.isNaN(changePercent)) {
            score += Math.min(20, changePercent * 2);
        }
        
        // 5. 主力净流入占比权重 (20分)
        if (zhuliNetInflowPercent > 0 && !Double.isNaN(zhuliNetInflowPercent)) {
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
        if (outSpeed > inSpeed && (inSpeed + outSpeed) > 0) {
            score += 30 * (outSpeed / (inSpeed + outSpeed));
        }

        // 2. 涨跌幅权重 20分
        if (changePercent < 0 && !Double.isNaN(changePercent)) {
            score += Math.min(20, Math.abs(changePercent * 2));
        }

        // 3. 主力净流入占比权重 25分
        if (zhuliNetInflowPercent < 0 && !Double.isNaN(zhuliNetInflowPercent)) {
            score += Math.min(25, Math.abs(zhuliNetInflowPercent * 2.5));
        }

        // 4. 超大单净流入占比权重 15分
        if (chaodadanNetInflowPercent < 0 && !Double.isNaN(chaodadanNetInflowPercent)) {
            score += Math.min(15, Math.abs(chaodadanNetInflowPercent * 1.5));
        }

        // 5. 成交量变化权重 10分
        if (volumeSpeed > 0 && !Double.isNaN(volumeSpeed)) {
            score += Math.min(10, volumeSpeed / 1000);
        }

        return Math.min(100, score);
    }

    private static double calculateMomentumScore(SingleStock stock, double inSpeed, double outSpeed) {
        double score = 0;

        // 1. 价格动量
        if (!Double.isNaN(stock.getChangePercent())) {
            score += Math.min(40, Math.abs(stock.getChangePercent()) * 4);
        }

        // 2. 资金动量
        double netSpeed = inSpeed - outSpeed;
        if ((Math.abs(inSpeed) + Math.abs(outSpeed)) > 0) {
            score += 40 * (netSpeed / (Math.abs(inSpeed) + Math.abs(outSpeed)));
        }

        // 3. 成交量动量
        if (!Double.isNaN(inSpeed) && !Double.isNaN(outSpeed)) {
            score += Math.min(20, (inSpeed + outSpeed) / 1000);
        }

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
     * K = 2/3 × 前日K值 + 1/3 × 当日RSV
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

            // 维护历史数据小
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

        // 更新资金入
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
        ResultSet rs = null;
        try {
            conn = DBUtils.getConnection();
            List<SingleStock> stocks = new ArrayList<>();
            stocks.add(stock);
            saveMinuteData(stock.getCode(), stocks, totalInflow, totalOutflow);
        } catch (SQLException e) {
            System.err.println("保存股票数据时发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBUtils.close(conn, ps, rs); // 修改：关闭连接
        }
    }

    private static void saveMinuteData(String code, List<SingleStock> stocks, double totalInflow, double totalOutflow) {
        // 提交到线程池执行
        saveDataExecutor.submit(() -> {
            Connection conn = null;
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                conn = DBUtils.getConnection();
                SingleStock lastStock = stocks.get(stocks.size() - 1);
                
                // 修复totalNetInflowPercent计算
                double totalNetInflowPercent = 0.0;
                double totalAmount = totalInflow + totalOutflow;
                if (totalAmount > 0) {
                    totalNetInflowPercent = (totalInflow - totalOutflow) / totalAmount * 100;
                }

                String sql = "INSERT INTO single_stock_data (code, name, current_price, change_amount, change_percent, " +
                        "zhuli_net_inflow, zhuli_net_inflow_percent, total_net_inflow, total_net_inflow_percent, " +
                        "chaodadan_net_inflow, chaodadan_net_inflow_percent, bigdan_net_inflow, bigdan_net_inflow_percent, " +
                        "zhongdan_net_inflow, zhongdan_net_inflow_percent, xiaodan_net_inflow, xiaodan_net_inflow_percent, " +
                        "total_volume) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                ps = conn.prepareStatement(sql);
                int paramIndex = 1;
                ps.setString(paramIndex++, code);
                ps.setString(paramIndex++, lastStock.getName());
                ps.setDouble(paramIndex++, lastStock.getCurrentPrice());
                ps.setDouble(paramIndex++, lastStock.getChange());
                ps.setDouble(paramIndex++, lastStock.getChangePercent());
                
                double zhuliSum = stocks.stream()
                    .mapToDouble(SingleStock::getZhuliNetInflow)
                    .filter(d -> !Double.isNaN(d))
                    .sum();
                ps.setDouble(paramIndex++, zhuliSum);
                
                double zhuliPercent = lastStock.getZhuliNetInflowPercent();
                ps.setDouble(paramIndex++, Double.isNaN(zhuliPercent) ? 0.0 : zhuliPercent);
                
                ps.setDouble(paramIndex++, totalInflow - totalOutflow);
                ps.setDouble(paramIndex++, totalNetInflowPercent);
                
                double chaodadanSum = stocks.stream()
                    .mapToDouble(SingleStock::getChaodadanNetInflow)
                    .filter(d -> !Double.isNaN(d))
                    .sum();
                ps.setDouble(paramIndex++, chaodadanSum);
                
                double chaodadanPercent = lastStock.getChaodadanNetInflowPercent();
                ps.setDouble(paramIndex++, Double.isNaN(chaodadanPercent) ? 0.0 : chaodadanPercent);
                
                double bigdanSum = stocks.stream()
                    .mapToDouble(SingleStock::getBigdanNetInflow)
                    .filter(d -> !Double.isNaN(d))
                    .sum();
                ps.setDouble(paramIndex++, bigdanSum);
                
                double bigdanPercent = lastStock.getBigdanNetInflowPercent();
                ps.setDouble(paramIndex++, Double.isNaN(bigdanPercent) ? 0.0 : bigdanPercent);
                
                double zhongdanSum = stocks.stream()
                    .mapToDouble(SingleStock::getZhongdanNetInflow)
                    .filter(d -> !Double.isNaN(d))
                    .sum();
                ps.setDouble(paramIndex++, zhongdanSum);
                
                double zhongdanPercent = lastStock.getZhongdanNetInflowPercent();
                ps.setDouble(paramIndex++, Double.isNaN(zhongdanPercent) ? 0.0 : zhongdanPercent);
                
                double xiaodanSum = stocks.stream()
                    .mapToDouble(SingleStock::getXiaodanNetInflow)
                    .filter(d -> !Double.isNaN(d))
                    .sum();
                ps.setDouble(paramIndex++, xiaodanSum);
                
                double xiaodanPercent = lastStock.getXiaodanNetInflowPercent();
                ps.setDouble(paramIndex++, Double.isNaN(xiaodanPercent) ? 0.0 : xiaodanPercent);
                
                ps.setDouble(paramIndex++, totalAmount);

                ps.executeUpdate();

                // 清空该股票的缓存
                synchronized (minuteStockCache) {
                    if (minuteStockCache.containsKey(code)) {
                        minuteStockCache.get(code).clear();
                    }
                }
                synchronized (minuteInflowCache) {
                    if (minuteInflowCache.containsKey(code)) {
                        minuteInflowCache.get(code).clear();
                    }
                }
                synchronized (minuteOutflowCache) {
                    if (minuteOutflowCache.containsKey(code)) {
                        minuteOutflowCache.get(code).clear();
                    }
                }

            } catch (SQLException e) {
                log.error("保存数据到数据库时发生错误: {} - {}", code, e.getMessage());
            } finally {
                DBUtils.close(conn, ps, rs);
            }
        });
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
            DBUtils.close(conn, ps, rs); // 修改：关闭连接
        }
        return new DailyStockData(0, 0);
    }

    // 添加历史数据载方法
    private static void loadHistoricalData() {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtils.getConnection();
            String sql = "SELECT s1.code, s1.current_price, s1.total_net_inflow, s1.total_volume, s1.create_time " +
                         "  FROM single_stock_data s1" +
                         " INNER JOIN (" +
                         "     SELECT code, DATE(create_time) as date, FLOOR(HOUR(create_time) * 6 + MINUTE(create_time) / 10) as time_slot," +
                         "         MAX(id) as max_id " +
                         "     FROM single_stock_data  " +
                         "     WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 5 DAY) " +
                         "     GROUP BY code, DATE(create_time), FLOOR(HOUR(create_time) * 6 + MINUTE(create_time) / 10) " +
                         " ) s2 ON s1.id = s2.max_id " +
                         " ORDER BY s1.code ASC, s1.create_time ASC;";
            
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
                //double volume = rs.getDouble("total_volume");
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
        log.info("历史数据载入完成, 共载入 {} 支股票数据", historicalData.size());
    }

    // 添加安全解析double的辅助方法
    private static double parseDouble(JSONObject json, String key, double defaultValue) {
        try {
            String value = json.getString(key);
            if (value == null || value.trim().equals("-")) {
                return defaultValue;
            }
            return json.getDoubleValue(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
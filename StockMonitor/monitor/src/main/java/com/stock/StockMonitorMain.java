package com.stock;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stock.utils.HttpUtils;
import com.stock.vo.SingleStock;
import com.stock.vo.StockSpeed;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockMonitorMain {
    // 用于存储上一次的资金流入数据
    private static Map<String, Double> previousInflowMap = new HashMap<>();
    // 用于存储上一次的资金流出数据
    private static Map<String, Double> previousOutflowMap = new HashMap<>();
    // 用于存储上一次的成交量数据
    private static Map<String, Double> previousVolumeMap = new HashMap<>();
    // 用于存储每只股票的名称
    private static Map<String, String> stockNameMap = new HashMap<>();

    public static void main(String[] args) throws Exception {
        // 创建定时执行器
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // 每10秒执行一次数据刷新
        scheduler.scheduleAtFixedRate(() -> {
            try {
                fetchAndCalculateSpeed();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

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
            
            // 计算流入金额（正值部分）
            double totalInflow = Math.max(singleStock.getZhuliNetInflow(), 0) +  
                    Math.max(singleStock.getChaodadanNetInflow(), 0) +    
                    Math.max(singleStock.getBigdanNetInflow(), 0) +    
                    Math.max(singleStock.getZhongdanNetInflow(), 0) +    
                    Math.max(singleStock.getXiaodanNetInflow(), 0);
                    
            // 计算流出金额（负值部分的绝对值）
            double totalOutflow = Math.abs(Math.min(singleStock.getZhuliNetInflow(), 0)) +  
                    Math.abs(Math.min(singleStock.getChaodadanNetInflow(), 0)) +    
                    Math.abs(Math.min(singleStock.getBigdanNetInflow(), 0)) +    
                    Math.abs(Math.min(singleStock.getZhongdanNetInflow(), 0)) +    
                    Math.abs(Math.min(singleStock.getXiaodanNetInflow(), 0));

            // 计算成交量 (流入 + 流出)
            double volume = totalInflow + totalOutflow;
            
            currentInflowMap.put(code, totalInflow);
            currentOutflowMap.put(code, totalOutflow);
            currentVolumeMap.put(code, volume);
            stockNameMap.put(code, name);

            // 计算速度和趋势
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
                    singleStock.getChaodadanNetInflowPercent()
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
                if (Math.abs(inSpeed) > 1500 || Math.abs(outSpeed) > 1500) {
                    speedResults.add(new StockSpeed(
                            code, 
                            name, 
                            inSpeed, 
                            outSpeed,
                            singleStock.getChange(),
                            singleStock.getChangePercent(),
                            volumeSpeed,
                            rankUpTrendScore,
                            rankDownTrendScore
                    ));
                }
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

            // 清屏
            System.out.print("\033[H\033[2J");
            System.out.flush();

            // 输出表头
            System.out.printf("%-8s %-8s %12s %12s %10s %8s %12s %8s %8s%n", 
                "代码", "名称", "流入速度", "流出速度", "涨跌额", "涨跌幅", "成交速度", "上涨分", "下跌分");
            System.out.println("------------------------------------------------------------------------------------");

            // 输出结果
            for (StockSpeed result : speedResults) {
                if (result.getChangePercent()>9.5) {
                    continue;
                }
                System.out.printf("%-8s %-8s %12.2f %12.2f %10.2f %8.2f%% %12.2f %8.2f %8.2f%n",
                        result.getCode(),
                        result.getName(),
                        result.getInSpeed(),
                        result.getOutSpeed(),
                        result.getChange(),
                        result.getChangePercent(),
                        result.getVolume(),
                        result.getRankUpTrendScore(),
                        result.getRankDownTrendScore()
                );
            }
        } else {
            System.out.println("无满足需求的更新数据");
        }
    }

    /**
     * 计算上涨趋势分数
     * @return 0-100的分数
     */
    private static double calculateUpTrendScore(
            double inSpeed, 
            double outSpeed, 
            double volumeSpeed,
            double changePercent,
            double zhuliNetInflowPercent,
            double chaodadanNetInflowPercent) {
        
        double score = 0;
        
        // 1. 资金流入速度权重 30分
        if (inSpeed > outSpeed) {
            score += 30 * (inSpeed / (inSpeed + outSpeed));
        }
        
        // 2. 涨跌幅权重 20分
        if (changePercent > 0) {
            score += Math.min(20, changePercent * 2);
        }
        
        // 3. 主力净流入占比权重 25分
        if (zhuliNetInflowPercent > 0) {
            score += Math.min(25, zhuliNetInflowPercent * 2.5);
        }
        
        // 4. 超大单净流入占比权重 15分
        if (chaodadanNetInflowPercent > 0) {
            score += Math.min(15, chaodadanNetInflowPercent * 1.5);
        }
        
        // 5. 成交量变化权重 10分
        if (volumeSpeed > 0) {
            score += Math.min(10, volumeSpeed / 1000);
        }
        
        return Math.min(100, score);
    }

    /**
     * 计算下跌趋势分数
     * @return 0-100的分数
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
}
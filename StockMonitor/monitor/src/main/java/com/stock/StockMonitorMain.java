package com.stock;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stock.utils.HttpUtils;
import com.stock.vo.SingleStock;
import com.stock.vo.StockSpeed;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockMonitorMain {
    // 用于存储上一次的资金流入数据
    private static Map<String, Double> previousInflowMap = new HashMap<>();
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
        Integer total = dataJSONObject.getInteger("total");
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
        // 当前时间
        long currentTime = System.currentTimeMillis();

        // 用于存储当前资金流入数据
        Map<String, Double> currentInflowMap = new HashMap<>();
        // 用于存储计算结果
        List<StockSpeed> speedResults = new ArrayList<>();

        for (int i = 0; i < singleStockList.size(); i++) {
            SingleStock singleStock = singleStockList.get(i);
            String code = singleStock.getCode();
            String name = singleStock.getName();
            // 计算总资金净流入（主力 + 超大单 + 大单 + 中单 + 小单）
            double totalInflow = singleStock.getZhuliNetInflow() +  // 主力净流入
                    singleStock.getChaodadanNetInflow() +    // 超大单净流入
                    singleStock.getBigdanNetInflow() +    // 大单净流入
                    singleStock.getZhongdanNetInflow() +    // 中单净流入
                    singleStock.getXiaodanNetInflow();     // 小单净流入

            currentInflowMap.put(code, totalInflow);
            stockNameMap.put(code, name);

            // 计算速度
            if (previousInflowMap.containsKey(code)) {
                double inflowDiff = totalInflow - previousInflowMap.get(code);
                // 计算速度（万元/秒）
                double speed = inflowDiff / 10.0; // 10秒间隔
                if (speed > 100) {
                    speedResults.add(new StockSpeed(code, name, speed));
                }
            }
        }

        // 更新上一次的数据
        previousInflowMap = currentInflowMap;
        if (CollectionUtils.isNotEmpty(speedResults)){
            // 按速度排序并输出结果
            Collections.sort(speedResults, (a, b) -> Double.compare(Math.abs(b.getSpeed()), Math.abs(a.getSpeed())));

            // 清屏
            System.out.print("\033[H\033[2J");
            System.out.flush();

            // 输出表头
            System.out.printf("%-10s %-10s %15s%n", "代码", "名称", "资金流速(万元/秒)");
            System.out.println("----------------------------------------");

            // 输出结果
            for (StockSpeed result : speedResults) {
                if (result.getSpeed() > 100) {
                    System.out.printf("%-10s %-10s %15.2f%n",
                            result.getCode(),
                            result.getName(),
                            result.getSpeed());
                }
            }

            // 输出更新时间
            System.out.println("\n更新时间: " + new Date());
        } else {
            System.out.println("无满足需求的更新数据");
        }
    }
}
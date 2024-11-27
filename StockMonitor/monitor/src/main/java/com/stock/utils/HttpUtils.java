package com.stock.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stock.vo.SingleStock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class HttpUtils {
    public static List<String> fetchDataFromUrl(String urlString) throws Exception {
        List<String> results = new ArrayList<>();
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    results.add(line.trim());
                }
            }
        } finally {
            conn.disconnect();
        }
        
        return results;
    }
    public static void main(String[] args) throws Exception {
        String url = "https://push2.eastmoney.com/api/qt/clist/get?cb=jQuery1123044856734513874996_1732590961078&fid=f62&po=1&pz=50&pn=1&np=1&fltt=2&invt=2&ut=b2884a393a59ad64002292a3e90d46a5&fs=m%3A0%2Bt%3A6%2Bf%3A!2%2Cm%3A0%2Bt%3A13%2Bf%3A!2%2Cm%3A0%2Bt%3A80%2Bf%3A!2%2Cm%3A1%2Bt%3A2%2Bf%3A!2%2Cm%3A1%2Bt%3A23%2Bf%3A!2%2Cm%3A0%2Bt%3A7%2Bf%3A!2%2Cm%3A1%2Bt%3A3%2Bf%3A!2&fields=f12%2Cf14%2Cf2%2Cf3%2Cf62%2Cf184%2Cf66%2Cf69%2Cf72%2Cf75%2Cf78%2Cf81%2Cf84%2Cf87%2Cf204%2Cf205%2Cf124%2Cf1%2Cf13";
        List<String> dataList = fetchDataFromUrl(url);
        String data0 = dataList.get(0);
        String dataStr = data0.substring(data0.indexOf("(")+1, data0.lastIndexOf(")"));
        JSONObject dataJson = JSONObject.parseObject(dataStr);
        JSONObject dataJSONObject = dataJson.getJSONObject("data");
        JSONArray dataJSONArray = dataJSONObject.getJSONArray("diff");
        Integer total = dataJSONObject.getInteger("total");
        List<SingleStock> singleStockList = new ArrayList<>();
        for(int i=0; i<dataJSONArray.size(); i++) {
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
            System.out.println(singleStock.getCode() + " " + singleStock.getName() + " " + singleStock.getCurrentPrice() + " " + singleStock.getChangePercent() + " " + singleStock.getZhuliNetInflow() + " " + singleStock.getZhuliNetInflowPercent() + " " + singleStock.getChaodadanNetInflow() + " " + singleStock.getChaodadanNetInflowPercent() + " " + singleStock.getBigdanNetInflow() + " " + singleStock.getBigdanNetInflowPercent() + " " + singleStock.getZhongdanNetInflow() + " " + singleStock.getZhongdanNetInflowPercent() + " " + singleStock.getXiaodanNetInflow() + " " + singleStock.getXiaodanNetInflowPercent());
        }
    }
} 
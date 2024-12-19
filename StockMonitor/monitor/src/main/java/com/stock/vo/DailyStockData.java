package com.stock.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DailyStockData {
    private double inflow;
    private double outflow;
} 
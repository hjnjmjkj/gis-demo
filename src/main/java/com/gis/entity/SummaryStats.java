package com.gis.entity;

import lombok.Data;

@Data
public class SummaryStats {
    /**
     * 平均值
     */
    private Float mean;
    /**
     * 最小值
     */
    private Float min;
    /**
     * 最大值
     */
    private Float max;
    /**
     * 方差
     */
    private Float stddev;
}

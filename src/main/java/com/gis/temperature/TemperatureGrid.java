package com.test;

import java.text.DecimalFormat;


public class TemperatureGrid {

    // 陕西省边界坐标范围（经纬度）
    private static final double MIN_LON = 105.29;  // 最西端经度
    private static final double MAX_LON = 111.15;  // 最东端经度
    private static final double MIN_LAT = 31.70;  // 最南端纬度
    private static final double MAX_LAT = 39.59;  // 最北端纬度

    // 网格分辨率（经纬度间隔）
    private static final double GRID_RESOLUTION = 0.25;

    public static void main(String[] args) {
//        生成模拟的陕西省气温网格数据
        double[][] temperatureGrid = generateTemperatureGrid();

    }

    /**
     * 生成模拟的陕西省气温网格数据
     */
    private static double[][] generateTemperatureGrid() {
        // 计算网格数量
        int rows = (int) Math.ceil((MAX_LAT - MIN_LAT) / GRID_RESOLUTION);
        int cols = (int) Math.ceil((MAX_LON - MIN_LON) / GRID_RESOLUTION);

        double[][] grid = new double[rows][cols];
        DecimalFormat df = new DecimalFormat("#.0");

        // 模拟气温数据（实际应用中应从数据源获取）
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // 计算当前网格中心的经纬度
                double lon = MIN_LON + j * GRID_RESOLUTION + GRID_RESOLUTION / 2;
                double lat = MIN_LAT + i * GRID_RESOLUTION + GRID_RESOLUTION / 2;

                // 模拟气温分布（南高北低，西安附近温度较高）
                double baseTemp = 25.0;  // 基础温度
                double latFactor = (lat - MIN_LAT) / (MAX_LAT - MIN_LAT) * 10.0;  // 纬度影响（北低南高）
                double xianFactor = 0.0;  // 西安附近温度升高

                // 西安经纬度：108.95°E, 34.27°N
                double distFromXian = Math.sqrt(
                        Math.pow(lon - 108.95, 2) + Math.pow(lat - 34.27, 2)
                );
                if (distFromXian < 1.0) {
                    xianFactor = (1.0 - distFromXian) * 5.0;  // 西安附近温度升高5度
                }

                // 计算温度并保留一位小数
                double temp = baseTemp - latFactor + xianFactor;
                grid[i][j] = Double.parseDouble(df.format(temp));
            }
        }

        return grid;
    }

}

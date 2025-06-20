package com.gis.gdal;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;

import java.util.Arrays;

public class DepressionAnalysis {
    public static int noDataValue = 0; // 假设NoData值为

    /**
     * 根据统计数据自动确定洼地阈值
     */
    public double calculateDepressionThreshold(Dataset dataset, ThresholdMethod method, double paramValue) {
        try {
            Band band = dataset.GetRasterBand(1);
            double[] min1 = new double[1];
            double[] max1 = new double[1];
            double[] mean1 = new double[1];
            double[] stdDev1 = new double[1];

            band.GetStatistics(false, true, min1, max1, mean1, stdDev1);
            System.out.println(min1[0] + " " + max1[0] + " " + mean1[0] + " " + stdDev1[0]);
            double min = min1[0];
            double max = max1[0];
            double mean = mean1[0];
            double stdDev = stdDev1[0];

// 方法1: 使用平均值减去1.5个标准差作为洼地阈值
            double depressionThreshold1 = mean - (1.5 * stdDev);

// 方法2: 最小值和平均值的加权平均
            double weight = 0.3;
            double depressionThreshold2 = (min * weight) + (mean * (1 - weight));

            System.out.println("建议的洼地阈值1: " + depressionThreshold1);
            System.out.println("建议的洼地阈值2: " + depressionThreshold2);

            System.out.println("DEM统计数据: 最小值=" + min + ", 最大值=" + max +
                    ", 平均值=" + mean + ", 标准差=" + stdDev);

            double threshold;

            switch (method) {
                case STANDARD_DEVIATION:
                    // 使用平均值减去n个标准差
                    threshold = mean - (paramValue * stdDev);
                    break;

                case WEIGHTED_AVERAGE:
                    // 最小值和平均值的加权平均
                    threshold = (min * paramValue) + (mean * (1 - paramValue));
                    break;

                case PERCENTILE:
                    // 使用分位数法
                    int width = dataset.getRasterXSize();
                    int height = dataset.getRasterYSize();
                    threshold = calculatePercentileThreshold(band, width, height, paramValue);
                    break;

                default:
                    threshold = mean - stdDev;
            }

            // 确保阈值在有效范围内
            threshold = Math.max(threshold, min);

            return threshold;

        } catch (Exception e) {
            System.err.println("计算洼地阈值时出错: " + e.getMessage());
            e.printStackTrace();
            return Double.NaN;
        }
    }

    /**
     * 计算分位数阈值
     */
    private double calculatePercentileThreshold(Band band, int width, int height, double percentile) {
        // 读取所有高程数据
        double[] buffer = new double[width * height];
        band.ReadRaster(0, 0, width, height, buffer);

        // 过滤掉NoData值(假设NoData为noDataValue)
        double[] validValues = Arrays.stream(buffer)
                .filter(val -> val > noDataValue)  // 假设NoData值为noDataValue
                .sorted()
                .toArray();

        // 计算分位数
        int index = (int)(validValues.length * percentile);
        return validValues[index];
    }

    /**
     * 识别洼地区域
     * @param dataset
     * @param threshold 洼地阈值
     * @return 洼地栅格数据 (1=洼地, 0=非洼地)
     */
    public byte[][] identifyDepressions(Dataset dataset, double threshold) {

        int width = dataset.getRasterXSize();
        int height = dataset.getRasterYSize();
        Band band = dataset.GetRasterBand(1);

        // 创建结果数组
        byte[][] depressions = new byte[height][width];

        // 读取DEM数据并标记洼地
        float[] buffer = new float[width];
        for (int y = 0; y < height; y++) {
            band.ReadRaster(0, y, width, 1, buffer);
            for (int x = 0; x < width; x++) {
                // 如果高程低于阈值，标记为洼地
                if (buffer[x] != noDataValue && buffer[x] < threshold) {
                    depressions[y][x] = 1;
                } else {
                    depressions[y][x] = 0;
                }
            }
        }
        return depressions;
    }

    /**
     * 阈值确定方法枚举
     */
    public enum ThresholdMethod {
        STANDARD_DEVIATION,  // 使用平均值减去n个标准差
        WEIGHTED_AVERAGE,    // 最小值和平均值的加权平均
        PERCENTILE           // 使用分位数
    }

    public static void main(String[] args) {
        // 注册GDAL
        gdal.AllRegister();
        DepressionAnalysis analysis = new DepressionAnalysis();
        String ascFilePath = "D:\\IdeaProjects\\gis\\src\\main\\resources\\input50x50.asc";
        // 打开DEM数据集
        try {
            Dataset dataset = gdal.Open(ascFilePath, gdalconstConstants.GA_ReadOnly);
            if (dataset == null) {
                System.err.println("无法打开ASC文件: " + ascFilePath);
            }
            // 尝试不同方法设置阈值
            double threshold1 = analysis.calculateDepressionThreshold(
                    dataset, ThresholdMethod.STANDARD_DEVIATION, 1.5);
            System.out.println("标准差方法阈值: " + threshold1);

            double threshold2 = analysis.calculateDepressionThreshold(
                    dataset, ThresholdMethod.WEIGHTED_AVERAGE, 0.3);
            System.out.println("加权平均方法阈值: " + threshold2);

            double threshold3 = analysis.calculateDepressionThreshold(
                    dataset, ThresholdMethod.PERCENTILE, 0.05);
            System.out.println("分位数方法阈值: " + threshold3);

            // 使用选定的阈值识别洼地
            byte[][] depressions = analysis.identifyDepressions(dataset, threshold3);

            // 打印洼地分布
            if (depressions != null) {
                System.out.println("洼地分布(部分):");
                for (int i = 0; i < depressions.length; i++) {
                    for (int j = 0; j < depressions[i].length; j++) {
                        System.out.print(depressions[i][j] + " ");
                    }
                    System.out.println();
                }
            }
            dataset.delete();
        } catch (Exception e) {
            System.err.println("识别洼地时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
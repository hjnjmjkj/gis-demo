package com.gis.gdal;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.DEMProcessingOptions;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.springframework.stereotype.Component;

import java.util.Vector;

@Component
public class SlopeAnalysis {

    /**
     * 计算ASC文件的坡度数据
     *
     * @param inputAscPath ASC输入文件路径
     * @return 二维数组表示坡度值
     */
    public double[][] getSlopeDataFromAsc(String inputAscPath) {
        // 注册所有GDAL驱动
        gdal.AllRegister();

        try {
            // 临时存储计算的坡度结果
            String tempSlopePath = "temp_slope.tif";

            // 打开ASC数据集
            Dataset demDataset = gdal.Open(inputAscPath, gdalconstConstants.GA_ReadOnly);
            if (demDataset == null) {
                System.err.println("无法打开ASC文件: " + inputAscPath);
                return null;
            }

            // 创建坡度计算选项
            Vector<String> slopeOptions = new Vector<>();
            slopeOptions.add("-alg");
            slopeOptions.add("Horn");
            slopeOptions.add("-compute_edges");

            // 使用正确的比例参数
            slopeOptions.add("-scale");
            slopeOptions.add("1.0");

            // 将坡度值设定为角度（不使用错误的 -s 参数）
            slopeOptions.add("-of");
            slopeOptions.add("GTiff");

            DEMProcessingOptions options = new DEMProcessingOptions(slopeOptions);

            // 计算坡度
            Dataset slopeDataset = gdal.DEMProcessing(
                    tempSlopePath,
                    demDataset,
                    "slope",
                    null,  // 不使用颜色文件
                    options
            );

            if (slopeDataset == null) {
                System.err.println("坡度计算失败");
                demDataset.delete();
                return null;
            }

            // 获取尺寸
            int width = slopeDataset.getRasterXSize();
            int height = slopeDataset.getRasterYSize();

            // 创建数组存储坡度数据
            double[][] slopeData = new double[height][width];

            // 读取坡度数据
            double[] buffer = new double[width];
            for (int y = 0; y < height; y++) {
                slopeDataset.GetRasterBand(1).ReadRaster(0, y, width, 1, buffer);
                for (int x = 0; x < width; x++) {
                    slopeData[y][x] = buffer[x];
                }
            }

            // 关闭数据集
            demDataset.delete();
            slopeDataset.delete();

            // 删除临时文件
            new java.io.File(tempSlopePath).delete();

            return slopeData;

        } catch (Exception e) {
            System.err.println("处理ASC文件时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 计算ASC文件的坡向数据
     *
     * @param inputAscPath ASC输入文件路径
     * @return 二维数组表示坡向值（0-360度）
     */
    public double[][] getAspectDataFromAsc(String inputAscPath) {
        gdal.AllRegister();

        try {
            // 临时存储计算的坡向结果
            String tempAspectPath = "temp_aspect.tif";

            // 打开ASC数据集
            Dataset demDataset = gdal.Open(inputAscPath, gdalconstConstants.GA_ReadOnly);
            if (demDataset == null) {
                System.err.println("无法打开ASC文件: " + inputAscPath);
                return null;
            }

            // 创建坡向计算选项
            Vector<String> aspectOptions = new Vector<>();
            aspectOptions.add("-alg");
            aspectOptions.add("Horn");
            aspectOptions.add("-compute_edges");
            aspectOptions.add("-zero_for_flat");
            aspectOptions.add("-of");
            aspectOptions.add("GTiff");

            DEMProcessingOptions options = new DEMProcessingOptions(aspectOptions);

            // 计算坡向
            Dataset aspectDataset = gdal.DEMProcessing(
                    tempAspectPath,
                    demDataset,
                    "aspect",
                    null,  // 不使用颜色文件
                    options
            );

            if (aspectDataset == null) {
                System.err.println("坡向计算失败");
                demDataset.delete();
                return null;
            }

            // 获取尺寸
            int width = aspectDataset.getRasterXSize();
            int height = aspectDataset.getRasterYSize();

            // 创建数组存储坡向数据
            double[][] aspectData = new double[height][width];

            // 读取坡向数据
            double[] buffer = new double[width];
            for (int y = 0; y < height; y++) {
                aspectDataset.GetRasterBand(1).ReadRaster(0, y, width, 1, buffer);
                for (int x = 0; x < width; x++) {
                    aspectData[y][x] = buffer[x];
                }
            }

            // 关闭数据集
            demDataset.delete();
            aspectDataset.delete();

            // 删除临时文件
            new java.io.File(tempAspectPath).delete();

            return aspectData;
        } catch (Exception e) {
            System.err.println("处理ASC坡向数据时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 计算ASC文件的TPI(地形位置指数)数据
     *
     * @param inputAscPath ASC输入文件路径
     * @return 二维数组表示TPI值
     */
    public double[][] getTPIDataFromAsc(String inputAscPath) {
        gdal.AllRegister();

        try {
            // 临时存储计算的TPI结果
            String tempTpiPath = "temp_tpi.tif";

            // 打开ASC数据集
            Dataset demDataset = gdal.Open(inputAscPath, gdalconstConstants.GA_ReadOnly);
            if (demDataset == null) {
                System.err.println("无法打开ASC文件: " + inputAscPath);
                return null;
            }

            // 创建TPI计算选项
            Vector<String> tpiOptions = new Vector<>();
            tpiOptions.add("-compute_edges");
            tpiOptions.add("-of");
            tpiOptions.add("GTiff");

            DEMProcessingOptions options = new DEMProcessingOptions(tpiOptions);

            // 计算TPI
            Dataset tpiDataset = gdal.DEMProcessing(
                    tempTpiPath,
                    demDataset,
                    "TPI",  // 使用TPI算法
                    null,   // 不使用颜色文件
                    options
            );

            if (tpiDataset == null) {
                System.err.println("TPI计算失败");
                demDataset.delete();
                return null;
            }

            // 获取尺寸
            int width = tpiDataset.getRasterXSize();
            int height = tpiDataset.getRasterYSize();

            // 创建数组存储TPI数据
            double[][] tpiData = new double[height][width];

            // 读取TPI数据
            double[] buffer = new double[width];
            for (int y = 0; y < height; y++) {
                tpiDataset.GetRasterBand(1).ReadRaster(0, y, width, 1, buffer);
                for (int x = 0; x < width; x++) {
                    tpiData[y][x] = buffer[x];
                }
            }

            // 关闭数据集
            demDataset.delete();
            tpiDataset.delete();

            // 删除临时文件
            new java.io.File(tempTpiPath).delete();

            return tpiData;

        } catch (Exception e) {
            System.err.println("计算TPI时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 同时计算ASC文件的坡度和坡向
     * @param inputAscPath ASC输入文件路径
     * @return 包含坡度和坡向数据的对象
     */
    public SlopeAspectData calculateSlopeAndAspect(String inputAscPath) {
        double[][] slopeData = getSlopeDataFromAsc(inputAscPath);
        double[][] aspectData = getAspectDataFromAsc(inputAscPath);

        return new SlopeAspectData(slopeData, aspectData);
    }

    /**
     * 同时计算ASC文件的坡度、坡向和TPI
     * @param inputAscPath ASC输入文件路径
     * @return 包含坡度、坡向和TPI数据的对象
     */
    public TerrainAnalysisData calculateTerrainData(String inputAscPath) {
        double[][] slopeData = getSlopeDataFromAsc(inputAscPath);
        double[][] aspectData = getAspectDataFromAsc(inputAscPath);
        double[][] tpiData = getTPIDataFromAsc(inputAscPath);

        return new TerrainAnalysisData(slopeData, aspectData, tpiData);
    }

    /**
     * 包含坡度和坡向数据的数据类
     */
    public static class SlopeAspectData {
        private final double[][] slopeData;
        private final double[][] aspectData;

        public SlopeAspectData(double[][] slopeData, double[][] aspectData) {
            this.slopeData = slopeData;
            this.aspectData = aspectData;
        }

        public double[][] getSlopeData() {
            return slopeData;
        }

        public double[][] getAspectData() {
            return aspectData;
        }
    }

    /**
     * 包含坡度、坡向和TPI数据的数据类
     */
    public static class TerrainAnalysisData {
        private final double[][] slopeData;
        private final double[][] aspectData;
        private final double[][] tpiData;

        public TerrainAnalysisData(double[][] slopeData, double[][] aspectData, double[][] tpiData) {
            this.slopeData = slopeData;
            this.aspectData = aspectData;
            this.tpiData = tpiData;
        }

        public double[][] getSlopeData() {
            return slopeData;
        }

        public double[][] getAspectData() {
            return aspectData;
        }
        
        public double[][] getTPIData() {
            return tpiData;
        }
    }

    /**
     * 打印完整的坡度和坡向数据
     * @param slopeData 坡度数据
     * @param aspectData 坡向数据
     */
    public void printAllSlopeAndAspectData(double[][] slopeData, double[][] aspectData) {
        if (slopeData == null || aspectData == null) {
            System.out.println("没有可用的数据");
            return;
        }

        System.out.println("=== 完整坡度数据 ===");
        for (int i = 0; i < slopeData.length; i++) {
            for (int j = 0; j < slopeData[i].length; j++) {
                System.out.printf("%.2f ", slopeData[i][j]);
            }
            System.out.println();
        }

        System.out.println("\n=== 完整坡向数据 ===");
        for (int i = 0; i < aspectData.length; i++) {
            for (int j = 0; j < aspectData[i].length; j++) {
                System.out.printf("%.2f ", aspectData[i][j]);
            }
            System.out.println();
        }
    }

    /**
     * 打印完整的TPI数据
     * @param tpiData TPI数据
     */
    public void printTPIData(double[][] tpiData) {
        if (tpiData == null) {
            System.out.println("没有可用的TPI数据");
            return;
        }

        System.out.println("=== 完整TPI数据 ===");
        for (int i = 0; i < tpiData.length; i++) {
            for (int j = 0; j < tpiData[i].length; j++) {
                System.out.printf("%.2f ", tpiData[i][j]);
            }
            System.out.println();
        }
    }
    
    /**
     * 打印完整的坡度、坡向和TPI数据
     */
    public void printAllTerrainData(double[][] slopeData, double[][] aspectData, double[][] tpiData) {
        // 打印坡度和坡向数据
        printAllSlopeAndAspectData(slopeData, aspectData);
        
        // 打印TPI数据
        printTPIData(tpiData);
    }

    public static void main(String[] args) {
        SlopeAnalysis slopeAnalysis = new SlopeAnalysis();
        String inputAscPath = "D:\\IdeaProjects\\gis\\src\\main\\resources\\input50x50.asc"; // 替换为实际的ASC文件路径

        // 计算地形分析数据
        TerrainAnalysisData result = slopeAnalysis.calculateTerrainData(inputAscPath);

        if (result != null) {
            System.out.println("地形分析数据计算成功");

            // 打印部分示例TPI数据
            if (result.getTPIData() != null) {
                double[][] tpiData = result.getTPIData();
                System.out.println("TPI数据示例:");
                int printLimit = Math.min(5, tpiData.length);
                for (int i = 0; i < printLimit; i++) {
                    for (int j = 0; j < Math.min(5, tpiData[i].length); j++) {
                        System.out.printf("%.2f ", tpiData[i][j]);
                    }
                    System.out.println();
                }
            }

            // 打印全部地形数据
            System.out.println("\n开始打印全部地形数据:");
            slopeAnalysis.printAllTerrainData(result.getSlopeData(), result.getAspectData(), result.getTPIData());
        }
    }
}

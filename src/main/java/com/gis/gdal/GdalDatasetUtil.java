package com.gis.gdal;

import org.gdal.gdal.*;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.Feature;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.Layer;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.sql.*;
import java.util.Arrays;
import java.util.Vector;

/**
 * GDAL 数据集工具类，提供打开、合并和管理数据集的方法
 */
public class GdalDatasetUtil {

    static {
        // 确保 GDAL 在使用前已初始化
        gdal.AllRegister();
    }

    /**
     * 打开栅格数据文件为只读数据集
     *
     * @param filePath 栅格文件路径
     * @return GDAL 数据集对象，失败返回 null
     */
    public static Dataset openReadOnly(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            System.err.println("文件路径不能为空");
            return null;
        }

        Dataset dataset = gdal.Open(filePath, gdalconstConstants.GA_ReadOnly);
        if (dataset == null) {
            System.err.println("无法打开文件: " + filePath);
        }
        return dataset;
    }

    /**
     * 打开栅格数据文件为可更新数据集
     *
     * @param filePath 栅格文件路径
     * @return GDAL 数据集对象，失败返回 null
     */
    public static Dataset openUpdate(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            System.err.println("文件路径不能为空");
            return null;
        }

        Dataset dataset = gdal.Open(filePath, gdalconstConstants.GA_Update);
        if (dataset == null) {
            System.err.println("无法以更新模式打开文件: " + filePath);
        }
        return dataset;
    }

    /**
     * 从目录中获取所有 TIFF 文件并创建虚拟数据集
     *
     * @param directoryPath TIFF 文件目录
     * @return 虚拟数据集，失败返回 null
     */
    public static Dataset createVRTFromDirectory(String directoryPath) {
        File dir = new File(directoryPath);
        if (!dir.isDirectory()) {
            System.err.println("提供的路径不是目录: " + directoryPath);
            return null;
        }

        File[] tiffFiles = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".tif") ||
                        name.toLowerCase().endsWith(".tiff"));

        if (tiffFiles == null || tiffFiles.length == 0) {
            System.err.println("目录中没有TIFF文件: " + directoryPath);
            return null;
        }

        System.out.println("找到 " + tiffFiles.length + " 个TIFF文件");

        // 先打开所有文件作为数据集
        Dataset[] datasets = new Dataset[tiffFiles.length];
        for (int i = 0; i < tiffFiles.length; i++) {
            datasets[i] = openReadOnly(tiffFiles[i].getAbsolutePath());
            if (datasets[i] == null) {
                // 关闭已打开的数据集
                for (int j = 0; j < i; j++) {
                    if (datasets[j] != null) {
                        datasets[j].delete();
                    }
                }
                System.err.println("无法打开文件: " + tiffFiles[i].getAbsolutePath());
                return null;
            }
        }

        Vector<String> buildVrtOptions = new Vector<>();
        buildVrtOptions.add("-resolution");
        buildVrtOptions.add("highest");

        // 使用Dataset[]调用BuildVRT
        Dataset vrtDataset = gdal.BuildVRT("", datasets, new BuildVRTOptions(buildVrtOptions));
        
        // 关闭原始数据集
        for (Dataset ds : datasets) {
            if (ds != null) {
                ds.delete();
            }
        }
        
        return vrtDataset;
    }

    /**
     * 合并目录中的所有 TIFF 文件为一个新的 TIFF 文件
     *
     * @param directoryPath 包含 TIFF 文件的目录
     * @param outputFilePath 输出 TIFF 文件路径
     * @return 合并后的数据集，失败返回 null
     */
    public static Dataset mergeTiffsInDirectory(String directoryPath, String outputFilePath) {
        Dataset vrtDataset = createVRTFromDirectory(directoryPath);
        if (vrtDataset == null) {
            return null;
        }

        try {
            Vector<String> translateOptions = new Vector<>();
            translateOptions.add("-of");
            translateOptions.add("GTiff");
            translateOptions.add("-co");
            translateOptions.add("COMPRESS=LZW");
            translateOptions.add("-co");
            translateOptions.add("TILED=YES");
            translateOptions.add("-co");
            translateOptions.add("BIGTIFF=IF_SAFER");

            Dataset outputDataset = gdal.Translate(outputFilePath, vrtDataset,
                    new TranslateOptions(translateOptions));

            // 为大文件创建概览层
            if (outputDataset != null) {
                outputDataset.BuildOverviews("NEAREST", new int[]{2, 4, 8, 16}, null);
            }

            vrtDataset.delete();
            return outputDataset;

        } catch (Exception e) {
            System.err.println("合并TIFF时出错: " + e.getMessage());
            vrtDataset.delete();
            return null;
        }
    }

    /**
     * 合并指定的多个 TIFF 文件为一个
     *
     * @param inputFiles TIFF 文件路径数组
     * @param outputFilePath 输出 TIFF 文件路径
     * @return 合并后的数据集，失败返回 null
     */
    public static Dataset mergeTiffFiles(String[] inputFiles, String outputFilePath) {
        if (inputFiles == null || inputFiles.length == 0) {
            System.err.println("输入文件列表为空");
            return null;
        }

        try {
            // 先打开所有文件作为数据集
            Dataset[] datasets = new Dataset[inputFiles.length];
            for (int i = 0; i < inputFiles.length; i++) {
                datasets[i] = openReadOnly(inputFiles[i]);
                if (datasets[i] == null) {
                    // 关闭已打开的数据集
                    for (int j = 0; j < i; j++) {
                        if (datasets[j] != null) {
                            datasets[j].delete();
                        }
                    }
                    System.err.println("无法打开文件: " + inputFiles[i]);
                    return null;
                }
            }

            Vector<String> buildVrtOptions = new Vector<>();
            buildVrtOptions.add("-resolution");
            buildVrtOptions.add("highest");

            // 使用Dataset[]调用BuildVRT
            Dataset vrtDataset = gdal.BuildVRT("", datasets, new BuildVRTOptions(buildVrtOptions));
            
            // 关闭原始数据集
            for (Dataset ds : datasets) {
                if (ds != null) {
                    ds.delete();
                }
            }
            
            if (vrtDataset == null) {
                System.err.println("创建VRT数据集失败");
                return null;
            }

            Vector<String> translateOptions = new Vector<>();
            translateOptions.add("-of");
            translateOptions.add("GTiff");
            translateOptions.add("-co");
            translateOptions.add("COMPRESS=LZW");
            translateOptions.add("-co");
            translateOptions.add("TILED=YES");
            translateOptions.add("-co");
            translateOptions.add("BIGTIFF=IF_SAFER");

            Dataset outputDataset = gdal.Translate(outputFilePath, vrtDataset,
                    new TranslateOptions(translateOptions));

            // 为大文件创建概览层
            if (outputDataset != null) {
                outputDataset.BuildOverviews("NEAREST", new int[]{2, 4, 8, 16}, null);
            }

            vrtDataset.delete();
            return outputDataset;

        } catch (Exception e) {
            System.err.println("合并TIFF时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 检查数据集的坐标系统类型
     *
     * @param dataset GDAL数据集
     * @return 如果是地理坐标系返回true，否则返回false
     */
    public static boolean isGeographicCoordinateSystem(Dataset dataset) {
        if (dataset == null) {
            return false;
        }

        SpatialReference srs = new SpatialReference(dataset.GetProjectionRef());
        boolean isGeographic = srs.IsGeographic() == 1;
        srs.delete();
        return isGeographic;
    }

    /**
     * 获取数据集的适当比例尺（用于坡度计算等）
     *
     * @param dataset GDAL数据集
     * @return 适合该数据集的比例尺值
     */
    public static double getAppropriateScale(Dataset dataset) {
        if (dataset == null) {
            return 1.0;
        }

        if (isGeographicCoordinateSystem(dataset)) {
            // 地理坐标系，需要根据纬度调整比例尺
            double[] extent = new double[6];
            dataset.GetGeoTransform(extent);
            double centerLat = extent[3] + extent[5] * dataset.getRasterYSize() / 2.0;
            // 1度纬度约111320米，需要根据纬度调整
            double scale = 1.0 / (111320.0 * Math.cos(Math.toRadians(centerLat)));
            return scale;
        } else {
            // 投影坐标系，单位一致
            return 1.0;
        }
    }

    /**
     * 安全关闭数据集
     *
     * @param dataset 要关闭的数据集
     */
    public static void closeDataset(Dataset dataset) {
        if (dataset != null) {
            dataset.delete();
        }
    }

    /**
     * 将数据集从WGS84 (EPSG:4326)重投���到Web墨卡托 (EPSG:3857)
     *
     * @param inputFilePath 输入文件路径 (EPSG:4326)
     * @param outputFilePath 输出文件路径 (EPSG:3857)
     * @return 重投影后的数据集，失败返回null
     */
    public static Dataset reprojectTo3857(String inputFilePath, String outputFilePath) {
        Dataset srcDataset = openReadOnly(inputFilePath);
        if (srcDataset == null) {
            System.err.println("无法打开源文件: " + inputFilePath);
            return null;
        }

        try {
            // 设置Warp选项
            Vector<String> warpOptions = new Vector<>();
            warpOptions.add("-t_srs");
            warpOptions.add("EPSG:3857");  // Web墨卡托投影
            warpOptions.add("-r");
            warpOptions.add("bilinear");   // 双线性重采样
            warpOptions.add("-of");
            warpOptions.add("GTiff");
            // 添加NoData值设置
            warpOptions.add("-dstnodata");
            warpOptions.add("-9999");
            warpOptions.add("-co");
            warpOptions.add("COMPRESS=LZW");
            warpOptions.add("-co");
            warpOptions.add("TILED=YES");
            warpOptions.add("-co");
            warpOptions.add("BIGTIFF=IF_SAFER");

            // 执行重投影
            WarpOptions options = new WarpOptions(warpOptions);
            Dataset[] srcDatasets = {srcDataset};
            Dataset dstDataset = gdal.Warp(outputFilePath, srcDatasets, options);

            // 关闭源数据集
            srcDataset.delete();

            // 如果重投影成功，为输出数据集创建概览层
            if (dstDataset != null) {
                dstDataset.BuildOverviews("NEAREST", new int[]{2, 4, 8, 16}, null);
                System.out.println("成功重投影到EPSG:3857: " + outputFilePath);
            } else {
                System.err.println("重投影失败");
            }

            return dstDataset;
        } catch (Exception e) {
            System.err.println("重投影过程中发生错误: " + e.getMessage());
            e.printStackTrace();
            srcDataset.delete();
            return null;
        }
    }

    /**
     * 将数据集从WGS84 (EPSG:4326)重投���到Web墨卡托 (EPSG:3857)
     *
     * @param inputFilePath 输入文件路径 (EPSG:4326)
     * @param outputFilePath 输出文件路径 (EPSG:3857)
     * @return 重投影后的数据集，失败返回null
     */
    public static Dataset reprojectToAsc3857(String inputFilePath, String outputFilePath) {
        Dataset srcDataset = openReadOnly(inputFilePath);
        if (srcDataset == null) {
            System.err.println("无法打开源文件: " + inputFilePath);
            return null;
        }

        try {
            // 设置Warp选项
            Vector<String> warpOptions = new Vector<>();
            warpOptions.add("-t_srs");
            warpOptions.add("EPSG:3857");  // Web墨卡托投影
            warpOptions.add("-r");
            warpOptions.add("bilinear");   // 双线性重采样
            warpOptions.add("-of");
            warpOptions.add("AAIGrid"); // 输出为ASC格式
            warpOptions.add("-dstnodata");   //-dstnodata -9999
            warpOptions.add("-9999"); // 设置无效值为-9999

            // 执行重投影
            WarpOptions options = new WarpOptions(warpOptions);
            Dataset[] srcDatasets = {srcDataset};
            Dataset dstDataset = gdal.Warp(outputFilePath, srcDatasets, options);

            // 关闭源数据集
            srcDataset.delete();

            // 如果重投影成功，为输出数据集创建概览层
            if (dstDataset == null) {
                System.err.println("重投影失败");
            }

            return dstDataset;
        } catch (Exception e) {
            System.err.println("重投影过程中发生错误: " + e.getMessage());
            e.printStackTrace();
            srcDataset.delete();
            return null;
        }
    }

    /**
     * 将数据集从一个坐标系重投影到另一个坐标系
     *
     * @param inputFilePath 输入文件路径
     * @param outputFilePath 输出文件路径
     * @param sourceSRS 源坐标系，如"EPSG:4326"，如果为null则使用输入数据集的原始坐标系
     * @param targetSRS 目标坐标系，如"EPSG:3857"
     * @param resampleAlg 重采样算法，如"bilinear"、"cubic"、"near"等
     * @return 重投影后的数据集，失败返回null
     */
    public static Dataset reprojectDataset(String inputFilePath, String outputFilePath,
                                          String sourceSRS, String targetSRS, String resampleAlg) {
        Dataset srcDataset = openReadOnly(inputFilePath);
        if (srcDataset == null) {
            System.err.println("无法打开源文件: " + inputFilePath);
            return null;
        }

        try {
            // 设置Warp选项
            Vector<String> warpOptions = new Vector<>();

            if (sourceSRS != null && !sourceSRS.isEmpty()) {
                warpOptions.add("-s_srs");
                warpOptions.add(sourceSRS);
            }

            warpOptions.add("-t_srs");
            warpOptions.add(targetSRS);

            warpOptions.add("-r");
            warpOptions.add(resampleAlg != null ? resampleAlg : "bilinear");

            warpOptions.add("-of");
            warpOptions.add("GTiff");
            warpOptions.add("-co");
            warpOptions.add("COMPRESS=LZW");
            warpOptions.add("-co");
            warpOptions.add("TILED=YES");
            warpOptions.add("-co");
            warpOptions.add("BIGTIFF=IF_SAFER");

            // 执行重投影
            WarpOptions options = new WarpOptions(warpOptions);
            Dataset[] srcDatasets = {srcDataset};
            Dataset dstDataset = gdal.Warp(outputFilePath, srcDatasets, options);

            // 关闭源数据集
            srcDataset.delete();

            // 如果重投影成功，为输出数据集创建概览层
            if (dstDataset != null) {
                dstDataset.BuildOverviews("NEAREST", new int[]{2, 4, 8, 16}, null);
                System.out.println("成功重投影到" + targetSRS + ": " + outputFilePath);
            } else {
                System.err.println("重投影失败");
            }

            return dstDataset;
        } catch (Exception e) {
            System.err.println("重投影过程中发生错误: " + e.getMessage());
            e.printStackTrace();
            srcDataset.delete();
            return null;
        }
    }

    /**
     * 将 Polygonize 生成的图层写入 PostGIS 数据库
     *
     * @param layer GDAL 生成的图层
     * @param jdbcUrl 数据库连接URL，例如 "jdbc:postgresql://localhost:5432/gisdb"
     * @param username 数据库用户名
     * @param password 数据库密码
     * @param tableName 目标表名
     * @param srid 空间参考ID（例如 3857 表示 Web墨卡托）
     * @return 是否成功写入
     */
    public static boolean writeLayerToPostGIS(Layer layer, String jdbcUrl,
                                       String username, String password,
                                       String tableName, int srid) {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            // 1. 建立数据库连接
            conn = DriverManager.getConnection(jdbcUrl, username, password);
            conn.setAutoCommit(false);

            // 2. 创建表（如果不存在）
            String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "id SERIAL PRIMARY KEY, " +
                    "name VARCHAR(50), " +
                    "area DOUBLE PRECISION, " +
                    "terrain_type INTEGER, " +
                    "geom GEOMETRY(POLYGON, " + srid + "))";

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
            }

            // 3. 准备插入语句
            String insertSQL = "INSERT INTO " + tableName +
                    " (name, area, terrain_type, geom) VALUES (?, ?, ?, ST_GeomFromText(?, ?))";
            pstmt = conn.prepareStatement(insertSQL);

            // 4. 遍历图层中的所有要素并插入数据库
            layer.ResetReading();
            Feature feature;
            int count = 0;
            int batchSize = 500; // 批处理大小

            while ((feature = layer.GetNextFeature()) != null) {
                try {
                    // 获取要素属性
                    int terrainTypeValue = feature.GetFieldAsInteger(0);
                    TerrainType type = TerrainType.fromValue(terrainTypeValue);
                    // UNKNOWN的数据不入库
                    if (TerrainType.UNKNOWN.equals(type)) {
                        continue;
                    }
                    String name = type != null ? type.name() : "UNKNOWN";

                    Geometry geom = feature.GetGeometryRef();

                    if (geom != null && !geom.IsEmpty()) {
                        // 计算面积
                        double area = geom.GetArea();

                        // 绑定参数
                        pstmt.setString(1, name);
                        pstmt.setDouble(2, area);
                        pstmt.setInt(3, terrainTypeValue);
                        pstmt.setString(4, geom.ExportToWkt());
                        pstmt.setInt(5, srid);

                        // 执行插入
                        pstmt.addBatch();

                        if (++count % batchSize == 0) {
                            pstmt.executeBatch();
                            System.out.println("已处理 " + count + " 个要素...");
                        }
                    }
                } finally {
                    // 释放要素资源
                    feature.delete();
                }
            }

            // 执行剩余的批处理
            if (count % batchSize != 0) {
                pstmt.executeBatch();
            }

            // 提交事务
            conn.commit();
            System.out.println("成功将 " + count + " 个要素写入数据库表 " + tableName);
            return true;

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            System.err.println("写入数据库失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // 关闭资源
            try {
                if (pstmt != null) pstmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 使用示例
     */
    public static void main(String[] args) {
        // 示例1：打开单个文件
//        Dataset ds = openReadOnly("D:\\吉奥\\商洛\\ASTGTM2_N33E109_dem_20250604092516.tiff");
//        if (ds != null) {
//            System.out.println("成功打开文件，大小: " + ds.getRasterXSize() + " x " + ds.getRasterYSize());
//            closeDataset(ds);
//        }

        // 示例2：合并目录中所有TIFF文件
        Dataset mergedDs = mergeTiffsInDirectory("D:\\吉奥\\商洛\\柞水", "D:\\吉奥\\商洛\\柞水\\output\\merged_output.tiff");
        if (mergedDs != null) {
            System.out.println("成功合并目录中的TIFF文件，大小: " +
                    mergedDs.getRasterXSize() + " x " + mergedDs.getRasterYSize());
            closeDataset(mergedDs);
        }

        // 示例3：重投影到Web墨卡托
        String inputFile = "D:\\吉奥\\商洛\\柞水\\output\\merged_output.tiff";
        String outputFile = "D:\\吉奥\\商洛\\柞水\\output\\merged_output2.tiff";
        Dataset reprojectedDs = reprojectTo3857(inputFile, outputFile);
        if (reprojectedDs != null) {
            System.out.println("成功重投影文件，大小: " +
                    reprojectedDs.getRasterXSize() + " x " + reprojectedDs.getRasterYSize());
            closeDataset(reprojectedDs);
        }
    }
}

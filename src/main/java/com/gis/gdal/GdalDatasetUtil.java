package com.gis.gdal;

import org.gdal.gdal.*;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.*;
import org.gdal.ogr.Driver;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/**
 * GDAL 数据集工具类，提供打开、合并和管理数据集的方法
 */
public class GdalDatasetUtil {

    static {
        // 确保 GDAL 在使用前已初始化
        gdal.AllRegister();
        ogr.RegisterAll();
        gdal.SetConfigOption("OGR_GEOMETRY_WKT_FORMATTER", "AXIS_AUTHORITY");
        gdal.SetConfigOption("OGR_GEOJSON_MAX_OBJ_SIZE", "500");
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
     * 读取 GeoJSON 文件，添加新字段并保存
     *
     * @param geoJsonPath GeoJSON 文件路径
     * @param newFieldName 新字段名称
     * @param newFieldValue 新字段默认值
     * @return 是否成功
     */
    public static boolean addFieldToGeoJSON(String geoJsonPath, String newFieldName, String newFieldValue) {
        try {
            // 打开现有的 GeoJSON 文件
            DataSource dataSource = ogr.Open(geoJsonPath, 1); // 1 表示可写入模式
            if (dataSource == null) {
                System.err.println("无法打开 GeoJSON 文件: " + geoJsonPath);
                return false;
            }

            Layer layer = dataSource.GetLayer(0);

            // 创建新字段
            FieldDefn newField = new FieldDefn(newFieldName, ogr.OFTString);
            newField.SetWidth(50);

            // 将新字段添加到图层
            if (layer.CreateField(newField) != 0) {
                System.err.println("创建字段失败: " + newFieldName);
                dataSource.delete();
                return false;
            }

            // 遍历所有要素并设置新字段的值
            layer.ResetReading();
            Feature feature;
            while ((feature = layer.GetNextFeature()) != null) {
                // 可以根据需要设置不同的值，这里示例设置一些默认值
                feature.SetField(newFieldName, newFieldValue);
                // 更新要素
                layer.SetFeature(feature);

                // 释放要素
                feature.delete();
            }

            // 同步更改并释放资源
            dataSource.SyncToDisk();
            dataSource.delete();

            System.out.println("已成功添加字段: " + newFieldName);
            return true;

        } catch (Exception e) {
            System.err.println("添加字段时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 根据 name 字段的值将 GeoJSON 文件拆分成多个文件
     *
     * @param sourceGeoJson 源 GeoJSON 文件路径
     * @param outputFolder  输出文件夹路径
     * @param fieldName     用于拆分的字段名称（例如 "name"）
     * @return 是否成功
     */
    public static boolean splitGeoJsonByField(String sourceGeoJson, String outputFolder, String fieldName) {
        try {
            // 注册所有驱动
            ogr.RegisterAll();

            // 打开源文件
            DataSource sourceDs = ogr.Open(sourceGeoJson, 0); // 0表示只读模式
            if (sourceDs == null) {
                System.err.println("无法打开源文件: " + sourceGeoJson);
                return false;
            }

            // 获取图层
            Layer sourceLayer = sourceDs.GetLayer(0);
            if (sourceLayer == null) {
                System.err.println("无法获取源文件图层");
                sourceDs.delete();
                return false;
            }

            // 获取字段索引
            FeatureDefn featureDefn = sourceLayer.GetLayerDefn();
            int fieldIndex = featureDefn.GetFieldIndex(fieldName);
            if (fieldIndex == -1) {
                System.err.println("字段 '" + fieldName + "' 不存在于源文件中");
                sourceDs.delete();
                return false;
            }

            // 收集所有唯一的字段值
            Set<String> uniqueValues = new HashSet<>();
            sourceLayer.ResetReading();
            Feature feature;
            while ((feature = sourceLayer.GetNextFeature()) != null) {
                String value = feature.GetFieldAsString(fieldIndex);
                uniqueValues.add(value);
                feature.delete();
            }

            // 为每个唯一值创建一个新的 GeoJSON 文件
            for (String value : uniqueValues) {
                String outputFile = outputFolder + File.separator + "split_" + fieldName + "_" + value + ".geojson";

                // 创建输出驱动和数据源
                Driver driver = ogr.GetDriverByName("GeoJSON");
                DataSource outputDs = driver.CreateDataSource(outputFile);
                if (outputDs == null) {
                    System.err.println("无法创建输出文件: " + outputFile);
                    continue;
                }

                // 复制图层结构
                Layer outputLayer = outputDs.CreateLayer(
                        "split_" + value,
                        sourceLayer.GetSpatialRef(),
                        sourceLayer.GetGeomType(),
                        new Vector<>()
                );

                // 复制字段定义
                for (int i = 0; i < featureDefn.GetFieldCount(); i++) {
                    outputLayer.CreateField(featureDefn.GetFieldDefn(i));
                }

                // 筛选并复制要素
                sourceLayer.ResetReading();
                while ((feature = sourceLayer.GetNextFeature()) != null) {
                    String featureValue = feature.GetFieldAsString(fieldIndex);

                    if (featureValue.equals(value)) {
                        // 创建新要素并复制几何和属性
                        Feature newFeature = new Feature(outputLayer.GetLayerDefn());
                        newFeature.SetGeometry(feature.GetGeometryRef());

                        // 复制所有字段值
                        for (int i = 0; i < featureDefn.GetFieldCount(); i++) {
                            newFeature.SetField(i, feature.GetFieldAsString(i));
                        }

                        // 添加到输出图层
                        outputLayer.CreateFeature(newFeature);
                        newFeature.delete();
                    }

                    feature.delete();
                }

                // 释放资源
                outputDs.SyncToDisk();
                outputDs.delete();
                System.out.println("已创建拆分文件: " + outputFile);
            }

            // 释放源数据源
            sourceDs.delete();
            return true;

        } catch (Exception e) {
            System.err.println("拆分 GeoJSON 时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 根据 name 字段的值将 GeoJSON 文件拆分成多个文件，排除name字段
     *
     * @param sourceGeoJson 源 GeoJSON 文件路径
     * @param outputFolder  输出文件夹路径
     * @param fieldName     用于拆分的字段名称（例如 "name"）
     * @return 是否成功
     */
    public static boolean splitGeoJsonByFieldWithoutName(String sourceGeoJson, String outputFolder, String city,String fieldName) {
        try {
            // 注册所有驱动
            ogr.RegisterAll();

            // 打开源文件
            DataSource sourceDs = ogr.Open(sourceGeoJson, 0); // 0表示只读模式
            if (sourceDs == null) {
                System.err.println("无法打开源文件: " + sourceGeoJson);
                return false;
            }

            // 获取图层
            Layer sourceLayer = sourceDs.GetLayer(0);
            if (sourceLayer == null) {
                System.err.println("无法获取源文件图层");
                sourceDs.delete();
                return false;
            }

            // 获取字段索引
            FeatureDefn featureDefn = sourceLayer.GetLayerDefn();
            int fieldIndex = featureDefn.GetFieldIndex(fieldName);
            if (fieldIndex == -1) {
                System.err.println("字段 '" + fieldName + "' 不存在于源文件中");
                sourceDs.delete();
                return false;
            }

            // 收集所有唯一的字段值
            Set<String> uniqueValues = new HashSet<>();
            sourceLayer.ResetReading();
            Feature feature;
            while ((feature = sourceLayer.GetNextFeature()) != null) {
                String value = feature.GetFieldAsString(fieldIndex);
                uniqueValues.add(value);
                feature.delete();
            }

            // 为每个唯一值创建一个新的 GeoJSON 文件
            for (String value : uniqueValues) {
                String outputFile = outputFolder + value + File.separator + city + "_" + value + ".json";

                // 创建输出驱动和数据源
                Driver driver = ogr.GetDriverByName("GeoJSON");
                DataSource outputDs = driver.CreateDataSource(outputFile);
                if (outputDs == null) {
                    System.err.println("无法创建输出文件: " + outputFile);
                    continue;
                }

                // 复制图层结构，但排除name字段
                Layer outputLayer = outputDs.CreateLayer(
                        "split_" + value,
                        sourceLayer.GetSpatialRef(),
                        sourceLayer.GetGeomType(),
                        new Vector<>()
                );

                // 复制除name字段外的所有字段定义
                for (int i = 0; i < featureDefn.GetFieldCount(); i++) {
                    FieldDefn fieldDefn = featureDefn.GetFieldDefn(i);
                    if (!fieldDefn.GetName().equals("name")) {
                        outputLayer.CreateField(fieldDefn);
                    }
                }

                // 筛选并复制要素
                sourceLayer.ResetReading();
                while ((feature = sourceLayer.GetNextFeature()) != null) {
                    String featureValue = feature.GetFieldAsString(fieldIndex);

                    if (featureValue.equals(value)) {
                        // 创建新要素并复制几何和属性
                        Feature newFeature = new Feature(outputLayer.GetLayerDefn());
                        newFeature.SetGeometry(feature.GetGeometryRef());

                        // 根据字段类型正确复制所有字段值（除name字段外）
                        for (int i = 0; i < featureDefn.GetFieldCount(); i++) {
                            FieldDefn fieldDefn = featureDefn.GetFieldDefn(i);
                            String currentFieldName = fieldDefn.GetName();

                            // 跳过name字段
                            if (currentFieldName.equals("name")) {
                                continue;
                            }

                            // 获取输出图层中对应的字段索引
                            int outFieldIndex = outputLayer.GetLayerDefn().GetFieldIndex(currentFieldName);
                            if (outFieldIndex == -1) {
                                continue; // 如果输出图层中没有该字段，则跳过
                            }

                            // 处理空值
                            if (feature.IsFieldNull(i)) {
                                continue; // 保持为默认值
                            }

                            // 根据字段类型设置值
                            int fieldType = fieldDefn.GetFieldType();
                            switch (fieldType) {
                                case ogr.OFTInteger:
                                    newFeature.SetField(outFieldIndex, feature.GetFieldAsInteger(i));
                                    break;
                                case ogr.OFTInteger64:
                                    newFeature.SetField(outFieldIndex, feature.GetFieldAsInteger64(i));
                                    break;
                                case ogr.OFTReal:
                                    newFeature.SetField(outFieldIndex, feature.GetFieldAsDouble(i));
                                    break;
                                case ogr.OFTString:
                                    newFeature.SetField(outFieldIndex, feature.GetFieldAsString(i));
                                    break;
                                default:
                                    // 对于其他类型，使用字符串形式
                                    newFeature.SetField(outFieldIndex, feature.GetFieldAsString(i));
                                    break;
                            }
                        }

                        // 添加到输出图层
                        outputLayer.CreateFeature(newFeature);
                        newFeature.delete();
                    }

                    feature.delete();
                }

                // 释放资源
                outputDs.SyncToDisk();
                outputDs.delete();
                System.out.println("已创建拆分文件: " + outputFile);
            }

            // 释放源数据源
            sourceDs.delete();
            return true;

        } catch (Exception e) {
            System.err.println("拆分 GeoJSON 时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void removeFeaturesWithNameZero(String geoJsonPath) {
        DataSource ds = ogr.Open(geoJsonPath, 1);
        if (ds == null) {
            System.err.println("无法打开GeoJSON: " + geoJsonPath);
            return;
        }
        Layer layer = ds.GetLayer(0);
        layer.ResetReading();
        Feature feature;
        long fid;
        java.util.List<Long> deleteFids = new java.util.ArrayList<>();
        while ((feature = layer.GetNextFeature()) != null) {
            if ("0".equals(feature.GetFieldAsString("name"))) {
                fid = feature.GetFID();
                deleteFids.add(fid);
            }
            feature.delete();
        }
        // 统一删除，避免遍历时修改
        for (Long id : deleteFids) {
            layer.DeleteFeature(id);
        }
        ds.SyncToDisk();
        ds.delete();
    }

    public static boolean fillGeoJsonWithElevationStats(
            String geoJsonPath, String tiffPath, String maxField, String minField) {
        DataSource ds = ogr.Open(geoJsonPath, 1);
        if (ds == null) {
            System.err.println("无法打开GeoJSON: " + geoJsonPath);
            return false;
        }
        Layer layer = ds.GetLayer(0);

        // 添加字段
        if (layer.FindFieldIndex(maxField, 1) == -1)
            layer.CreateField(new FieldDefn(maxField, ogr.OFTReal));
        if (layer.FindFieldIndex(minField, 1) == -1)
            layer.CreateField(new FieldDefn(minField, ogr.OFTReal));

        Dataset tiffDs = gdal.Open(tiffPath, gdalconstConstants.GA_ReadOnly);
        if (tiffDs == null) {
            System.err.println("无法打开TIFF: " + tiffPath);
            ds.delete();
            return false;
        }
        Band band = tiffDs.GetRasterBand(1);
        double[] geoTransform = tiffDs.GetGeoTransform();
        int width = tiffDs.getRasterXSize();
        int height = tiffDs.getRasterYSize();

        layer.ResetReading();
        Feature feature;
        int count = 0;
        while ((feature = layer.GetNextFeature()) != null) {
            // 跳过已存在min或max的要素
            if (feature.IsFieldSetAndNotNull(maxField) && feature.IsFieldSetAndNotNull(minField)) {
                feature.delete();
                continue;
            }
            // 获取要素几何
            Geometry geom = feature.GetGeometryRef();
            geom = geom.Buffer(0);// 确保几何是有效的
            double[] env = new double[4];
            geom.GetEnvelope(env); // [minX, maxX, minY, maxY]

            java.util.List<Double> values = new java.util.ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double px = geoTransform[0] + x * geoTransform[1] + y * geoTransform[2];
                    double py = geoTransform[3] + x * geoTransform[4] + y * geoTransform[5];
                    if (px < env[0] || px > env[1] || py < env[2] || py > env[3]) continue;
                    Geometry pt = new Geometry(ogr.wkbPoint);
                    pt.AddPoint(px, py);
                    if (geom.Contains(pt)) {
                        double[] buf = new double[1];
                        band.ReadRaster(x, y, 1, 1, buf);
                        double val = buf[0];
                        if (!Double.isNaN(val)) values.add(val);
                    }
                    pt.delete();
                }
            }
            if (!values.isEmpty()) {
                double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
                double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
                feature.SetField(maxField, max);
                feature.SetField(minField, min);
                layer.SetFeature(feature);
            }
            feature.delete();
            count++;
            System.out.println(geoJsonPath+"已处理 " + count + " 个要素...");
        }
        tiffDs.delete();
        ds.SyncToDisk();
        ds.delete();
        return true;
    }

    public static void printFeatureOfGeoJson(String geoJsonPath) {
        DataSource ds = ogr.Open(geoJsonPath, 0); // 只读模式
        if (ds == null) {
            System.err.println("无法打开GeoJSON: " + geoJsonPath);
            return;
        }
        int layerCount = ds.GetLayerCount();
        for (int i = 0; i < layerCount; i++) {
            Layer layer = ds.GetLayer(i);
            System.out.println("图层 " + i + " 名称: " + layer.GetName());
            layer.ResetReading();
            Feature feature;
            while ((feature = layer.GetNextFeature()) != null) {
                if (feature != null) {
                    System.out.println("几何：");
                    Geometry geom = feature.GetGeometryRef();
                    if (geom != null) {
                        System.out.println(geom.ExportToJson());
                    }
                    feature.delete();
                } else {
                    System.out.println("该图层没有要素。");
                }
            }
        }
        ds.delete();
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
        String filePath = "D:\\吉奥\\陕西\\安康";
        String output = filePath + "\\output";
        // 确保输出目录存在
        File outputDir = new File(output);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        String cityName = "ankang";
        // 示例2：合并目录中所有TIFF文件
        Dataset mergedDs = mergeTiffsInDirectory(filePath, filePath + "\\output\\" + cityName + ".tiff");
        if (mergedDs != null) {
            System.out.println("成功合并目录中的TIFF文件，大小: " +
                    mergedDs.getRasterXSize() + " x " + mergedDs.getRasterYSize());
            closeDataset(mergedDs);
        }

        // 示例3：重投影到Web墨卡托
        String inputFile = filePath + "\\output\\" +cityName + ".tiff";
        String outputFile = filePath + "\\output\\" +cityName + "3857.tiff";
        Dataset reprojectedDs = reprojectTo3857(inputFile, outputFile);
        if (reprojectedDs != null) {
            System.out.println("成功重投影文件，大小: " +
                    reprojectedDs.getRasterXSize() + " x " + reprojectedDs.getRasterYSize());
            closeDataset(reprojectedDs);
        }
    }
}

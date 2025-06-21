package com.gis.depression;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.*;
import org.gdal.osr.SpatialReference;

import java.util.*;

public class DepressionPolygonExtractor {

    private final double elevationStep; // 每次抬高的高度（米）
    private final int maxIterations;    // 迭代次数
    private final double minArea;       // 最小面积（km²）
    private final double maxArea;       // 最大面积（km²）
    private final Short noDataValue;   // 无数据值，可为null表示自动从栅格获取

    public DepressionPolygonExtractor(double elevationStep, int maxIterations, double minArea, double maxArea) {
        this(elevationStep, maxIterations, minArea, maxArea, null);
    }

    public DepressionPolygonExtractor(double elevationStep, int maxIterations, double minArea, double maxArea, Short noDataValue) {
        this.elevationStep = elevationStep;
        this.maxIterations = maxIterations;
        this.minArea = minArea;
        this.maxArea = maxArea;
        this.noDataValue = noDataValue;
    }

    public boolean extract(String inputAscPath, String outputGeoJSONPath) {
        gdal.AllRegister();
        ogr.RegisterAll();

        System.out.println("开始处理: " + inputAscPath);
        Dataset demDataset = gdal.Open(inputAscPath, gdalconstConstants.GA_ReadOnly);
        if (demDataset == null) {
            System.err.println("无法打开栅格文件: " + inputAscPath);
            return false;
        }
        int width = demDataset.getRasterXSize();
        int height = demDataset.getRasterYSize();
        double[] geoTransform = demDataset.GetGeoTransform();
        System.out.println("栅格尺寸: " + width + "x" + height);

        Band band = demDataset.GetRasterBand(1);

        // 使用正确的数据类型读取栅格
        short[] shortElevationData = new short[width * height];
        try {
            band.ReadRaster(0, 0, width, height, width, height, gdalconstConstants.GDT_Int16, shortElevationData);
        } catch (Exception e) {
            System.err.println("读取栅格数据失败: " + e.getMessage());
            demDataset.delete();
            return false;
        }

        // 将short数据转换为double以便后续处理
        short[] elevationData = new short[width * height];
        for (int i = 0; i < shortElevationData.length; i++) {
            elevationData[i] = shortElevationData[i];
        }

        // 释放short数组以节省内存
        shortElevationData = null;
        System.gc(); // 建议垃圾回收

        // 确定NoData值 - 使用类属性或从栅格获取
        short actualNoDataValue;
        if (this.noDataValue != null) {
            // 使用用户提供的无数据值
            actualNoDataValue = this.noDataValue;
            System.out.println("使用用户指定的无数据值: " + actualNoDataValue);
        } else {
            // 从栅格获取无数据值
            Double[] noDataValueArr = new Double[1];
            band.GetNoDataValue(noDataValueArr);
            // Int16数据类型通常使用-32768作为NoData值
            actualNoDataValue = (noDataValueArr[0] != null) ?Short.parseShort(noDataValueArr[0]+""): (short)-9999;
            System.out.println("从栅格获取的无数据值: " + actualNoDataValue);
        }

        // 计算全局最小高程，并处理NoData值
        Integer minElevation = Integer.MAX_VALUE;
        Integer maxElevation = Integer.MIN_VALUE;
        for (short elev : elevationData) {
            if (elev != actualNoDataValue) {
                minElevation = Math.min(minElevation, elev);
                maxElevation = Math.max(maxElevation, elev);
            }
        }

        if (minElevation == Integer.MAX_VALUE) {
            System.err.println("错误: 未能在栅格中找到任何有效的高程数据。");
            demDataset.delete();
            return false;
        }
        System.out.println("计算出的最小高程: " + minElevation);
        System.out.println("计算出的最大高程: " + maxElevation);
        System.out.println("高程范围: " + (maxElevation - minElevation) + " 米");

        // 结果多边形集合
        List<Geometry> lastPolygons = new ArrayList<>();

        org.gdal.gdal.Driver memDriver = gdal.GetDriverByName("MEM");


        for (int iter = 0; iter < maxIterations; iter++) {
            double threshold = minElevation + elevationStep * (iter + 1);
            System.out.println("\n--- 迭代 " + (iter + 1) + "/" + maxIterations + ", 阈值: " + threshold + " ---");

            // 生成掩码
            byte[] mask = new byte[width * height];
            int validCells = 0;
            for (int i = 0; i < elevationData.length; i++) {
                if (elevationData[i] != actualNoDataValue && elevationData[i] <= threshold) {
                    mask[i] = 1;
                    validCells++;
                } else {
                    mask[i] = 0;
                }
            }
            System.out.println("阈值内的有效像元数: " + validCells);
            if (validCells == 0) {
                System.out.println("当前迭代没有有效像元，跳过。");
                continue;
            }

            if (validCells == width * height) {
                System.out.println("所有像元都在阈值内，结果可能无意义，跳过。");
                continue;
            }

            Dataset maskDS = memDriver.Create("", width, height, 1, gdalconstConstants.GDT_Byte);
            maskDS.SetGeoTransform(geoTransform);
            maskDS.GetRasterBand(1).WriteRaster(0, 0, width, height, width, height, gdalconstConstants.GDT_Byte, mask);

            // 矢量化
            DataSource memDS = ogr.GetDriverByName("Memory").CreateDataSource("mem");
            SpatialReference srs = new SpatialReference();
            String projection = demDataset.GetProjection();
            if (projection != null && !projection.isEmpty()) {
                srs.ImportFromWkt(projection);
            } else {
                srs.ImportFromEPSG(3857);
                System.out.println("警告: 输入栅格没有空间参考，假设为 EPSG:3857。");
            }
            Layer layer = memDS.CreateLayer("depression", srs, ogr.wkbPolygon);

            // 创建字段
            FieldDefn dnField = new FieldDefn("DN", ogr.OFTInteger);
            layer.CreateField(dnField);

            // 获取字段索引
            int dnFieldIndex = layer.FindFieldIndex("DN", 1);

            // 矢量化时用字段索引，并使用掩膜
            try {
                gdal.Polygonize(maskDS.GetRasterBand(1), maskDS.GetRasterBand(1), layer, dnFieldIndex, new Vector<>());
                System.out.println("矢量化完成，生成了 " + layer.GetFeatureCount() + " 个要素。");
            } catch (Exception e) {
                System.err.println("矢量化失败: " + e.getMessage());
                maskDS.delete();
                layer.delete();
                memDS.delete();
                srs.delete();
                continue;
            }

            // 筛选面积并修复几何图形
            List<Geometry> validPolygons = new ArrayList<>();
            layer.ResetReading(); // 重置要素读取
            for (Feature f = layer.GetNextFeature(); f != null; f = layer.GetNextFeature()) {
                Geometry geom = f.GetGeometryRef();
                if (geom == null) {
                    f.delete();
                    continue;
                }

                // 使用Buffer(0)修复潜在的自相交问题
                Geometry fixedGeom = geom.Buffer(0);
                if (fixedGeom == null || fixedGeom.IsEmpty()) {
                    if (fixedGeom != null) fixedGeom.delete();
                    f.delete();
                    continue;
                }

                double area = fixedGeom.GetArea() / 1_000_000.0; // 面积单位为平方米，转换为平方公里
                if (Math.abs(area - minArea) < 1e-6 || Math.abs(area - maxArea) < 1e-6 || (area > minArea && area < maxArea)) {
                    validPolygons.add(fixedGeom); // Buffer(0)返回新对象，直接添加
                } else {
                    fixedGeom.delete(); // 如果不添加，则删除新创建的对象
                }
                f.delete();
            }
            System.out.println("筛选后符合面积条件的洼地数: " + validPolygons.size());

            // 迭代合并
            if (!lastPolygons.isEmpty() && !validPolygons.isEmpty()) {
                Iterator<Geometry> lastIter = lastPolygons.iterator();
                int removedCount = 0;
                while (lastIter.hasNext()) {
                    Geometry last = lastIter.next();
                    boolean isContained = false;
                    for (Geometry now : validPolygons) {
                        if (now.Contains(last)) {
                            isContained = true;
                            break;
                        }
                    }
                    if (isContained) {
                        lastIter.remove();
                        removedCount++;
                    }
                }
                System.out.println("因被包含而移除的旧洼地数: " + removedCount);
            }
            lastPolygons.addAll(validPolygons);
            System.out.println("当前总洼地数: " + lastPolygons.size());
            System.out.println("时间:" + new Date());

            // 释放
            maskDS.delete();
            layer.delete();
            memDS.delete();
            srs.delete();
        }

        System.out.println("\n所有迭代完成，正在写入 " + lastPolygons.size() + " 个洼地到 " + outputGeoJSONPath);
        // 输出到GeoJSON
        Driver geojsonDriver = ogr.GetDriverByName("GeoJSON");
        // 删除已存在的文件以避免错误
        if (new java.io.File(outputGeoJSONPath).exists()) {
            geojsonDriver.DeleteDataSource(outputGeoJSONPath);
        }
        DataSource outDS = geojsonDriver.CreateDataSource(outputGeoJSONPath);
        SpatialReference srs = new SpatialReference();
        String projection = demDataset.GetProjection();
        if (projection != null && !projection.isEmpty()) {
            srs.ImportFromWkt(projection);
        } else {
            srs.ImportFromEPSG(3857);
        }
        Layer outLayer = outDS.CreateLayer("depression", srs, ogr.wkbPolygon);

        FieldDefn areaField = new FieldDefn("area_km2", ogr.OFTReal);
        outLayer.CreateField(areaField);
        FieldDefn idField = new FieldDefn("id", ogr.OFTInteger);
        outLayer.CreateField(idField);

        int idCounter = 1;
        for (Geometry geom : lastPolygons) {
            Feature f = new Feature(outLayer.GetLayerDefn());
            f.SetGeometry(geom);
            f.SetField("id", idCounter++);
            f.SetField("area_km2", geom.GetArea() / 1_000_000.0);
            outLayer.CreateFeature(f);
            f.delete();
            geom.delete();
        }

        // 释放
        outLayer.delete();
        outDS.delete();
        srs.delete();
        demDataset.delete();

        System.out.println("处理完成。");
        return true;
    }

    /**
     * 读取一个GeoJSON文件，对其所有要素进行简化，然后保存到新的GeoJSON文件。
     * @param inputGeoJSONPath 输入的GeoJSON文件路径
     * @param outputGeoJSONPath 输出的简化后的GeoJSON文件路径
     * @param tolerance 简化容差。单位与输入文件的坐标系单位一致（例如，米）。
     */
    public void simplifyGeoJSON(String inputGeoJSONPath, String outputGeoJSONPath, double tolerance) {
        gdal.AllRegister();
        ogr.RegisterAll();

        System.out.println("\n开始简化GeoJSON: " + inputGeoJSONPath);
        System.out.println("简化容差: " + tolerance);

        // 打开输入数据源
        DataSource inDS = ogr.Open(inputGeoJSONPath, false);
        if (inDS == null) {
            System.err.println("无法打开输入的GeoJSON文件: " + inputGeoJSONPath);
            return;
        }
        Layer inLayer = inDS.GetLayer(0);

        // 创建输出数据源
        Driver driver = ogr.GetDriverByName("GeoJSON");
        if (new java.io.File(outputGeoJSONPath).exists()) {
            driver.DeleteDataSource(outputGeoJSONPath);
        }
        DataSource outDS = driver.CreateDataSource(outputGeoJSONPath);
        Layer outLayer = outDS.CreateLayer(inLayer.GetName(), inLayer.GetSpatialRef(), inLayer.GetGeomType());

        // 复制字段定义
        FeatureDefn inLayerDefn = inLayer.GetLayerDefn();
        for (int i = 0; i < inLayerDefn.GetFieldCount(); i++) {
            outLayer.CreateField(inLayerDefn.GetFieldDefn(i));
        }

        // 遍历要素，简化并写入
        inLayer.ResetReading();
        Feature inFeature;
        while ((inFeature = inLayer.GetNextFeature()) != null) {
            Geometry inGeom = inFeature.GetGeometryRef();
            if (inGeom == null) {
                inFeature.delete();
                continue;
            }

            // 简化几何图形
            Geometry outGeom = inGeom.Simplify(tolerance);
            if (outGeom == null || outGeom.IsEmpty()) {
                outGeom = inGeom.Clone(); // 保留原始几何
            }

            // 创建新要素并设置几何和属性
            Feature outFeature = new Feature(outLayer.GetLayerDefn());

            // 使用SetFrom方法复制所有属性
            outFeature.SetFrom(inFeature);

            // 用简化后的几何图形覆盖
            outFeature.SetGeometry(outGeom);

            // 写入新要素
            outLayer.CreateFeature(outFeature);

            // 释放资源
            if (outGeom != null) outGeom.delete();
            outFeature.delete();
            inFeature.delete();
        }

        System.out.println("简化完成，结果已保存到: " + outputGeoJSONPath);

        // 清理
        inDS.delete();
        outDS.delete();
    }

    public static void main(String[] args) {
        // 使用默认构造函数（无数据值自动从栅格获取）
        DepressionPolygonExtractor extractor = new DepressionPolygonExtractor(
                2.0, // 每次抬高5米
                500, // 迭代200次
                1.0, // 最小面积1km²
                20.0, // 最大面积10km²
                (short) 0 // 指定Int16类型的常用NoData值
        );

        // 或者指定无数据值的构造函数
        // DepressionPolygonExtractor extractor = new DepressionPolygonExtractor(
        //         5.0,           // 每次抬高5米
        //         200,           // 迭代200次
        //         1.0,           // 最小面积1km²
        //         10.0,          // 最大面积10km²
        //         -9999.0        // 指定无数据值为-9999.0
        // );

        String rawOutputPath = "D:\\吉奥\\陕西\\input\\30米经度\\dem3857_2_250.json";
        String simplifiedOutputPath = "D:\\吉奥\\陕西\\input\\30米经度\\dem3857_30_simplified_2_250.json";

        // 步骤1: 提取原始洼地多边形
        boolean success = extractor.extract("D:\\吉奥\\陕西\\input\\30米经度\\dem3857.tif", rawOutputPath);

        // 步骤2: 如果提取成功，则对结果进行简化
        double tolerance = 30; // 简化容差，单位为米。您可以根据需要调整此值。
        extractor.simplifyGeoJSON(rawOutputPath, simplifiedOutputPath, tolerance);
    }
}
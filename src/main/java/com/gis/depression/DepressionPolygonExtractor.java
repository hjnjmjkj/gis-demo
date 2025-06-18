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

    public DepressionPolygonExtractor(double elevationStep, int maxIterations, double minArea, double maxArea) {
        this.elevationStep = elevationStep;
        this.maxIterations = maxIterations;
        this.minArea = minArea;
        this.maxArea = maxArea;
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
        double[] elevationData = new double[width * height];
        band.ReadRaster(0, 0, width, height, width, height, gdalconstConstants.GDT_Float64, elevationData);

        // 计算全局最小高程，并处理NoData值
        Double[] noDataValueArr = new Double[1];
        band.GetNoDataValue(noDataValueArr);
        double noDataValue = (noDataValueArr[0] != null) ? noDataValueArr[0] : Double.NEGATIVE_INFINITY;

        double minElevation = Double.MAX_VALUE;
        for (double elev : elevationData) {
            if (elev != noDataValue) {
                minElevation = Math.min(minElevation, elev);
            }
        }

        if (minElevation == Double.MAX_VALUE) {
            System.err.println("错误: 未能在栅格中找到任何有效的高程数据。");
            demDataset.delete();
            return false;
        }
        System.out.println("计算出的最小高程: " + minElevation);


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
                if (elevationData[i] != noDataValue && elevationData[i] <= threshold) {
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
            gdal.Polygonize(maskDS.GetRasterBand(1), maskDS.GetRasterBand(1), layer, dnFieldIndex, new Vector<>());
            System.out.println("矢量化完成，生成了 " + layer.GetFeatureCount() + " 个要素。");

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
                if (area >= minArea && area <= maxArea) {
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

    public static void main(String[] args) {
        DepressionPolygonExtractor extractor = new DepressionPolygonExtractor(
                10.0, // 每次抬高10米
                50,    // 迭代5次
                1.0,  // 最小面积1km²
                10.0  // 最大面积10km²
        );
        extractor.extract("D:\\吉奥\\陕西\\input\\shanxi3857.tiff", "D:\\吉奥\\陕西\\input\\shanxi3857_depression.geojson");
    }
}
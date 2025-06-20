package com.gis.depression;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.*;
import org.gdal.osr.SpatialReference;

import java.util.*;

public class DepressionPolygonExtractor2 {

    private final double elevationStep; // 每次抬高的高度（米）
    private final int maxIterations;    // 迭代次数
    private final double minArea;       // 最小面积（km²）
    private final double maxArea;       // 最大面积（km²）
    private final int blockSize = 2048; // 分块处理的块大小，可以根据内存调整
    private final int sieveThreshold = 10; // 过滤掉小于10个像素的斑块

    public DepressionPolygonExtractor2(double elevationStep, int maxIterations, double minArea, double maxArea) {
        this.elevationStep = elevationStep;
        this.maxIterations = maxIterations;
        this.minArea = minArea;
        this.maxArea = maxArea;
    }

    public boolean extract(String inputAscPath, String outputGeoJSONPath) {
        gdal.AllRegister();
        ogr.RegisterAll();
        gdal.SetConfigOption("OGR_GEOMETRY_WKT_FORMATTER", "AXIS_AUTHORITY");
        gdal.SetConfigOption("OGR_GEOJSON_MAX_OBJ_SIZE", "500");

        System.out.println("开始处理: " + inputAscPath);
        Dataset demDataset = gdal.Open(inputAscPath, gdalconstConstants.GA_ReadOnly);
        if (demDataset == null) {
            System.err.println("无法打开栅格文件: " + inputAscPath);
            return false;
        }
        int width = demDataset.getRasterXSize();
        int height = demDataset.getRasterYSize();
        double[] geoTransform = demDataset.GetGeoTransform();
        String projection = demDataset.GetProjection();
        System.out.println("栅格尺寸: " + width + "x" + height);

        Band band = demDataset.GetRasterBand(1);
        Double[] noDataValueArr = new Double[1];
        band.GetNoDataValue(noDataValueArr);
        double noDataValue = (noDataValueArr[0] != null) ? noDataValueArr[0] : Double.NEGATIVE_INFINITY;

        // --- 修改部分 1: 分块计算全局最小高程 ---
        double minElevation = Double.MAX_VALUE;
        System.out.println("开始分块计算最小高程...");
        for (int y = 0; y < height; y += blockSize) {
            for (int x = 0; x < width; x += blockSize) {
                int blockXSize = Math.min(blockSize, width - x);
                int blockYSize = Math.min(blockSize, height - y);
                double[] elevationBlock = new double[blockXSize * blockYSize];
                band.ReadRaster(x, y, blockXSize, blockYSize, blockXSize, blockYSize, gdalconstConstants.GDT_Float64, elevationBlock);
                for (double elev : elevationBlock) {
                    if (elev != noDataValue) {
                        minElevation = Math.min(minElevation, elev);
                    }
                }
            }
        }

        if (minElevation == Double.MAX_VALUE) {
            System.err.println("错误: 未能在栅格中找到任何有效的高程数据。");
            demDataset.delete();
            return false;
        }
        System.out.println("计算出的最小高程: " + minElevation);
        minElevation =168;

        List<Geometry> lastPolygons = new ArrayList<>();
        org.gdal.gdal.Driver memDriver = gdal.GetDriverByName("MEM");

        for (int iter = 0; iter < maxIterations; iter++) {
            double threshold = minElevation + elevationStep * (iter + 1);
            System.out.println("\n--- 迭代 " + (iter + 1) + "/" + maxIterations + ", 阈值: " + threshold + " ---");

            // --- 修改部分 2: 为当前迭代创建一个内存图层来收集所有块的多边形 ---
            DataSource memDS = ogr.GetDriverByName("Memory").CreateDataSource("mem" + iter);
            SpatialReference srs = new SpatialReference();
            if (projection != null && !projection.isEmpty()) {
                srs.ImportFromWkt(projection);
            } else {
                srs.ImportFromEPSG(3857);
            }
            Layer layer = memDS.CreateLayer("depression_iter", srs, ogr.wkbPolygon);
            FieldDefn dnField = new FieldDefn("DN", ogr.OFTInteger);
            layer.CreateField(dnField);
            int dnFieldIndex = layer.FindFieldIndex("DN", 1);

            // --- 修改部分 3: 分块处理 ---
            for (int y = 0; y < height; y += blockSize) {
                for (int x = 0; x < width; x += blockSize) {
                    int blockXSize = Math.min(blockSize, width - x);
                    int blockYSize = Math.min(blockSize, height - y);

                    // 读取高程数据块
                    double[] elevationBlock = new double[blockXSize * blockYSize];
                    band.ReadRaster(x, y, blockXSize, blockYSize, blockXSize, blockYSize, gdalconstConstants.GDT_Float64, elevationBlock);

                    // 生成掩码
                    byte[] mask = new byte[blockXSize * blockYSize];
                    int validCells = 0;
                    for (int i = 0; i < elevationBlock.length; i++) {
                        if (elevationBlock[i] != noDataValue && elevationBlock[i] <= threshold) {
                            mask[i] = 1;
                            validCells++;
                        } else {
                            mask[i] = 0;
                        }
                    }

                    if (validCells == 0) continue;

                    // 为当前块创建内存栅格
                    Dataset maskDS = memDriver.Create("", blockXSize, blockYSize, 1, gdalconstConstants.GDT_Byte);
                    double[] blockGeoTransform = new double[6];
                    blockGeoTransform[0] = geoTransform[0] + x * geoTransform[1] + y * geoTransform[2];
                    blockGeoTransform[1] = geoTransform[1];
                    blockGeoTransform[2] = geoTransform[2];
                    blockGeoTransform[3] = geoTransform[3] + x * geoTransform[4] + y * geoTransform[5];
                    blockGeoTransform[4] = geoTransform[4];
                    blockGeoTransform[5] = geoTransform[5];
                    maskDS.SetGeoTransform(blockGeoTransform);
                    maskDS.GetRasterBand(1).WriteRaster(0, 0, blockXSize, blockYSize, blockXSize, blockYSize, gdalconstConstants.GDT_Byte, mask);

                    // 新增：应用SieveFilter过滤小面积斑块（小于10个像素）
                    Dataset filteredMaskDS = memDriver.Create("", blockXSize, blockYSize, 1, gdalconstConstants.GDT_Byte);
                    filteredMaskDS.SetGeoTransform(blockGeoTransform);
                    Band filteredMaskBand = filteredMaskDS.GetRasterBand(1);

                    // 使用SieveFilter过滤小斑块，sieveThreshold设为10个像素
                    int sieveResult = gdal.SieveFilter(
                        maskDS.GetRasterBand(1),   // 输入波段
                        null,                      // 掩码波段（null表示不使用掩码）
                        filteredMaskBand,          // 输出波段
                        sieveThreshold,            // 最小斑块大小（像素数）
                        4,                         // 连通性（4或8）
                        new Vector<>()             // 选项
                    );

                    if (sieveResult != 0) {
                        System.err.println("SieveFilter应用失败，错误代码: " + sieveResult);
                        // 如果失败，继续使用原始掩码
                        filteredMaskDS.delete();

                        // 矢量化原始掩码
                        gdal.Polygonize(maskDS.GetRasterBand(1), maskDS.GetRasterBand(1), layer, dnFieldIndex, new Vector<>());
                    } else {
                        // 矢量化过滤后的掩码
                        gdal.Polygonize(filteredMaskBand, filteredMaskBand, layer, dnFieldIndex, new Vector<>());
                        filteredMaskDS.delete();
                    }

                    maskDS.delete();
                }
            }
            System.out.println("矢量化完成，本轮共生成了 " + layer.GetFeatureCount() + " 个要素。");

            // --- 修改部分 4: 融合被切分的洼地 (兼容旧版GDAL) ---
            System.out.println("矢量化完成，本轮共生成了 " + layer.GetFeatureCount() + " 个要素。开始融合...");
            // 时间
            System.out.println("当前时间: " + new java.util.Date());

            System.out.println("矢量化完成，本轮共生成了 " + layer.GetFeatureCount() + " 个要素。开始融合...");

            // 步骤 4.1: 将所有碎片几何体收集到列表中
            List<Geometry> geometriesToUnion = new ArrayList<>();
            layer.ResetReading();
            for (Feature f = layer.GetNextFeature(); f != null; f = layer.GetNextFeature()) {
                Geometry geom = f.GetGeometryRef();
                if (geom != null && !geom.IsEmpty()) {
                    // 使用Buffer(0)修复可能的无效几何。Buffer会返回一个新对象，需要我们管理其内存。
                    Geometry fixedGeom = geom.Buffer(0);
                    if (fixedGeom != null && !fixedGeom.IsEmpty()) {
                        geometriesToUnion.add(fixedGeom);
                    } else if (fixedGeom != null) {
                        fixedGeom.delete(); // 释放无效的修复结果
                    }
                }
                f.delete(); // 释放要素，这也会使其引用的原始geom失效
            }
            layer.delete(); // 原始碎片图层不再需要
            memDS.delete(); // 释放内存数据源

            // 步骤 4.2: 执行分层融合 (Hierarchical Union) 以避免性能问题
            while (geometriesToUnion.size() > 1) {
                System.out.println("分层融合中，剩余几何体数量: " + geometriesToUnion.size());
                List<Geometry> nextLevelGeometries = new ArrayList<>();
                for (int i = 0; i < geometriesToUnion.size(); i += 2) {
                    Geometry geom1 = geometriesToUnion.get(i);
                    if (i + 1 < geometriesToUnion.size()) {
                        Geometry geom2 = geometriesToUnion.get(i + 1);
                        // Union操作会创建一个新的几何对象
                        Geometry unionResult = geom1.Union(geom2);
                        if (unionResult != null) {
                            nextLevelGeometries.add(unionResult);
                        }
                        // 释放已经被融合的旧几何对象
                        geom1.delete();
                        geom2.delete();
                    } else {
                        // 如果是奇数个，最后一个直接移到下一轮
                        nextLevelGeometries.add(geom1);
                    }
                }
                geometriesToUnion = nextLevelGeometries;
            }

            // 经过分层融合后，列表中最多只剩下一个最终结果
            Geometry unionedGeom = null;
            if (!geometriesToUnion.isEmpty()) {
                unionedGeom = geometriesToUnion.get(0);
            }
            System.out.println("融合完成。");

            // 步骤 4.3: 将融合后的结果（可能是MultiPolygon）拆分为独立的Polygon
            List<Geometry> currentPolygons = new ArrayList<>();
            if (unionedGeom != null && !unionedGeom.IsEmpty()) {
                if (unionedGeom.GetGeometryType() == ogr.wkbPolygon) {
                    currentPolygons.add(unionedGeom.Clone());
                } else if (unionedGeom.GetGeometryType() == ogr.wkbMultiPolygon) {
                    for (int i = 0; i < unionedGeom.GetGeometryCount(); i++) {
                        currentPolygons.add(unionedGeom.GetGeometryRef(i).Clone());
                    }
                }
                unionedGeom.delete(); // 释放最终的融合几何对象
            }
            System.out.println("融合后得到 " + currentPolygons.size() + " 个独立洼地。");


            // 步骤 4.4: 对融合后的完整洼地进行面积筛选
            List<Geometry> validPolygons = new ArrayList<>();
            for (Geometry geom : currentPolygons) {
                double area = geom.GetArea() / 1_000_000.0; // 转换为平方公里
                if (area >= minArea && area <= maxArea) {
                    validPolygons.add(geom);
                } else {
                    geom.delete(); // 释放不符合条件的几何对象内存
                }
            }
            System.out.println("筛选后符合面积条件的洼地数: " + validPolygons.size());


            // 迭代合并逻辑 (保持不变)
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

            // 释放当前迭代的资源
            layer.delete();
            memDS.delete();
            srs.delete();
        }

        System.out.println("\n所有迭代完成，正在写入 " + lastPolygons.size() + " 个洼地到 " + outputGeoJSONPath);
        // 输出到GeoJSON (保持不变)
        Driver geojsonDriver = ogr.GetDriverByName("GeoJSON");
        if (new java.io.File(outputGeoJSONPath).exists()) {
            geojsonDriver.DeleteDataSource(outputGeoJSONPath);
        }
        DataSource outDS = geojsonDriver.CreateDataSource(outputGeoJSONPath);
        SpatialReference outSrs = new SpatialReference();
        if (projection != null && !projection.isEmpty()) {
            outSrs.ImportFromWkt(projection);
        } else {
            outSrs.ImportFromEPSG(3857);
        }
        Layer outLayer = outDS.CreateLayer("depression", outSrs, ogr.wkbPolygon);

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
        outSrs.delete();
        demDataset.delete();

        System.out.println("处理完成。");
        return true;
    }

    // simplifyGeoJSON 和 main 方法保持不变
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
        DepressionPolygonExtractor2 extractor = new DepressionPolygonExtractor2(
                5.0, // 每次抬高5米
                200,    // 迭代200次
                1.0,  // 最小面积1km²
                20.0  // 最大面积10km²
        );

        String rawOutputPath = "D:\\吉奥\\陕西\\input\\30米经度\\shanxi3857.json";
        String simplifiedOutputPath = "D:\\吉奥\\陕西\\input\\30米经度\\shanxi3857_90_simplified.json";

        // 步骤1: 提取原始洼地多边形
        boolean success = extractor.extract("D:\\吉奥\\陕西\\input\\30米经度\\陕西省_DEM_30m分辨率_NASA数据3857.tif", rawOutputPath);
        if(success){
            // 步骤2: 如果提取成功，则对结果进行简化
            double tolerance = 30; // 简化容差，单位为米。您可以根据需要调整此值。
            extractor.simplifyGeoJSON(rawOutputPath, simplifiedOutputPath, tolerance);
        }
    }
}
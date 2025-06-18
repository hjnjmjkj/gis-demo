package com.gis.gdal;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.*;
import org.gdal.osr.SpatialReference;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TerrainGeoJSONExporter {

    private final SlopeAnalysis slopeAnalysis;

    // 地形分类阈值（与TerrainProcessor保持一致）
    private static final double S_PLAIN_MAX = 7.0;    // 平原最大坡度 (度)

    public TerrainGeoJSONExporter(SlopeAnalysis slopeAnalysis) {
        this.slopeAnalysis = slopeAnalysis;
    }

    /**
     * 将ASC文件中的指定地形类型导出为GeoJSON
     *
     * @param inputAscPath ASC文件路径
     * @param outputGeoJSONPath 输出的GeoJSON文件路径
     * @param terrainType 要导出的地形类型
     * @return 是否成功导出
     */
    /**
     * 将ASC文件中的指定地形类型导出为GeoJSON
     */
    public boolean exportTerrainToGeoJSON(String inputAscPath, String outputGeoJSONPath) {
        // 注册所有GDAL/OGR驱动
        gdal.AllRegister();
        ogr.RegisterAll();
        gdal.SetConfigOption("OGR_GEOMETRY_WKT_FORMATTER", "AXIS_AUTHORITY");
        gdal.SetConfigOption("OGR_GEOJSON_MAX_OBJ_SIZE", "500");

        try {
            // 1. 计算坡度
            double[][] slopeData = slopeAnalysis.getSlopeDataFromAsc(inputAscPath);
            if (slopeData == null) {
                System.err.println("坡度计算失败");
                return false;
            }

            // 2. 打开ASC数据集获取地理参考
            Dataset demDataset = gdal.Open(inputAscPath, gdalconstConstants.GA_ReadOnly);
            // 1. 首先读取DEM数据
            Band band = demDataset.GetRasterBand(1);
            double[] elevationData = new double[band.getXSize() * band.getYSize()];
            band.ReadRaster(0, 0, band.getXSize(), band.getYSize(), band.getXSize(), band.getYSize(), gdalconstConstants.GDT_Float64, elevationData);


            DepressionAnalysis analysis = new DepressionAnalysis();

            double threshold = analysis.calculateDepressionThreshold(
                    demDataset, DepressionAnalysis.ThresholdMethod.PERCENTILE, 0.05);

            //洼地阈值
            System.out.println("洼地阈值: " + threshold);

            if (demDataset == null) {
                System.err.println("无法打开ASC文件: " + inputAscPath);
                return false;
            }

            int width = demDataset.getRasterXSize();
            int height = demDataset.getRasterYSize();
            double[] geoTransform = demDataset.GetGeoTransform();

            // 3. 根据坡度对地形进行分类并创建临时栅格
            org.gdal.gdal.Driver memDriver = gdal.GetDriverByName("MEM");
            Dataset classifiedDS = memDriver.Create("", width, height, 1, gdalconstConstants.GDT_Byte);
            classifiedDS.SetGeoTransform(geoTransform);

            // 分类并写入数据 - 使用普通byte数组替代DirectByteBuffer
            byte[] classifiedData = new byte[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double slope = slopeData[y][x];
                    //读取高程值
                    double elevation = elevationData[y * width + x];

                    TerrainType type = classifyTerrain(slope,elevation,threshold);
                    classifiedData[y * width + x] = (byte)type.getValue();
                }
            }
            classifiedDS.GetRasterBand(1).WriteRaster(0, 0, width, height, width, height,
                    gdalconstConstants.GDT_Byte, classifiedData);

            // 使用SieveFilter过滤小联通区域
            Dataset filteredDS = memDriver.Create("", width, height, 1, gdalconstConstants.GDT_Byte);
            filteredDS.SetGeoTransform(geoTransform);

            // 使用SieveFilter合并小于阈值的连通区域
            // 参数含义: 源波段, 掩码波段(null), 目标波段, 阈值(像素数), 连通性(4或8), 选项
            gdal.SieveFilter(classifiedDS.GetRasterBand(1), null, filteredDS.GetRasterBand(1),
                    30, // 阈值：小于1个像素的区域将被合并
                    4, // 8-连通性
                    null); // 没有额外选项


            // 4. 创建GeoJSON数据源
            Driver driver = ogr.GetDriverByName("GeoJSON");
            DataSource dataSource = driver.CreateDataSource(outputGeoJSONPath);

            // 创建图层
            SpatialReference srs = new SpatialReference();
            srs.ImportFromEPSG(3857); // 假设使用WGS84坐标系
            Layer layer = dataSource.CreateLayer("terrain", srs, ogr.wkbPolygon);

            // 添加属性字段
            FieldDefn nameField = new FieldDefn("name", ogr.OFTString);
            nameField.SetWidth(50);
            layer.CreateField(nameField);

            FieldDefn areaField = new FieldDefn("area", ogr.OFTReal);
            layer.CreateField(areaField);

            // 添加地形类型字段
            FieldDefn typeField = new FieldDefn("terrain_type", ogr.OFTString);
            typeField.SetWidth(20);
            layer.CreateField(typeField);

            // 5. 使用过滤后的栅格进行矢量化
            gdal.Polygonize(filteredDS.GetRasterBand(1), null, layer, 0, new Vector<>());

            // 清理资源
            classifiedDS.delete();
            filteredDS.delete(); // 释放过滤后的数据集
            dataSource.delete();
            demDataset.delete();
            srs.delete();


            /*DataSource dataSource2 = ogr.Open(outputGeoJSONPath, 0);
            int i = dataSource2.GetLayerCount();
            System.out.println("GeoJSON图层数量: " + i);
            // 写入数据库
            String jdbcUrl = "jdbc:postgresql://127.0.0.1:5432/postgres";
            String username = "postgres";
            String password = "postgres";
            String tableName = "test.xian_polygons";
            boolean success = GdalDatasetUtil.writeLayerToPostGIS(dataSource2.GetLayer(0), jdbcUrl, username, password, tableName, 3857);
            if (success) {
                System.out.println("地形数据成功写入PostGIS数据库");
            } else {
                System.err.println("地形数据写入PostGIS数据库失败");
            }*/

            System.out.println("类型地形到: " + outputGeoJSONPath);
            return true;

        } catch (Exception e) {
            System.err.println("导出GeoJSON时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

    }

    /**
     * 根据坡度和高程分类地形
     *
     * @param slope 坡度
     * @param elevation 高程
     * @param threshold 阈值（目前未使用）
     * @return 地形类型
     */
    private TerrainType classifyTerrain(double slope,double elevation,double threshold) {
        //elevation的无效值
        if (elevation <=0) {
            return TerrainType.UNKNOWN; // 无效高程
        }
        if(elevation < threshold){
            return TerrainType.LOWLAND;
        }
        if (slope <= S_PLAIN_MAX) {
            return TerrainType.PLAIN; // 平原
        } else if (slope > S_PLAIN_MAX) {
            return TerrainType.MOUNTAIN; // 山地
        } else {
            return TerrainType.PLAIN; // 其他类型
        }
    }

    public static void main(String[] args) {
        //开始时间
        System.out.println("开始导出地形数据时间: " + new Date());
        SlopeAnalysis slopeAnalysis = new SlopeAnalysis();
        TerrainGeoJSONExporter exporter = new TerrainGeoJSONExporter(slopeAnalysis);

        String inputAscPath = "D:\\吉奥\\陕西\\咸阳\\output\\xianyang3857.tiff";

        // 导出平原区域
        exporter.exportTerrainToGeoJSON(inputAscPath,
                "D:\\吉奥\\陕西\\咸阳\\output\\xianyang3857.geojson");

        // 结束时间
        System.out.println("数据导出完成时间: " + new Date());

        GdalDatasetUtil.addFieldToGeoJSON("D:\\吉奥\\陕西\\咸阳\\output\\xianyang3857.geojson",
                "code","610400");

        // 导出山地区域
        /*exporter.exportTerrainToGeoJSON(inputAscPath,
                "D:\\IdeaProjects\\geo\\output\\mountain_terrain.geojson",
                TerrainType.MOUNTAIN);*/
    }
}

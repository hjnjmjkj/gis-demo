package com.gis.temperature;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.*;
import org.gdal.osr.SpatialReference;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TemperatureGeoJSONExporter {

    /**
     * 将温度ASC文件矢量化为GeoJSON
     *
     * @param inputAscPath ASC文件路径
     * @param outputGeoJSONPath 输出的GeoJSON文件路径
     * @return 是否成功导出
     */
    public boolean exportTemperatureToGeoJSON(String inputAscPath, String outputGeoJSONPath) {
        // 注册所有GDAL/OGR驱动
        gdal.AllRegister();
        ogr.RegisterAll();
        gdal.SetConfigOption("OGR_GEOMETRY_WKT_FORMATTER", "AXIS_AUTHORITY");
        gdal.SetConfigOption("OGR_GEOJSON_MAX_OBJ_SIZE", "500");

        try {
            // 打开ASC数据集
            Dataset tempDataset = gdal.Open(inputAscPath, gdalconstConstants.GA_ReadOnly);
            if (tempDataset == null) {
                System.err.println("无法打开ASC文件: " + inputAscPath);
                return false;
            }

            int width = tempDataset.getRasterXSize();
            int height = tempDataset.getRasterYSize();
            double[] geoTransform = tempDataset.GetGeoTransform();

            // 读取温度数据
            Band band = tempDataset.GetRasterBand(1);
            double[] temperatureData = new double[width * height];
            band.ReadRaster(0, 0, width, height, width, height, gdalconstConstants.GDT_Float64, temperatureData);

            // 对温度进行分类
            org.gdal.gdal.Driver memDriver = gdal.GetDriverByName("MEM");
            Dataset classifiedDS = memDriver.Create("", width, height, 1, gdalconstConstants.GDT_Byte);
            classifiedDS.SetGeoTransform(geoTransform);

            // 分类并写入数据
            byte[] classifiedData = new byte[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double temperature = temperatureData[y * width + x];
                    // 对温度进行分类
                    int tempClass = classifyTemperature(temperature);
                    classifiedData[y * width + x] = (byte)tempClass;
                }
            }
            classifiedDS.GetRasterBand(1).WriteRaster(0, 0, width, height, width, height,
                    gdalconstConstants.GDT_Byte, classifiedData);

            // 使用SieveFilter过滤小联通区域
            Dataset filteredDS = memDriver.Create("", width, height, 1, gdalconstConstants.GDT_Byte);
            filteredDS.SetGeoTransform(geoTransform);

            gdal.SieveFilter(classifiedDS.GetRasterBand(1), null, filteredDS.GetRasterBand(1),
                    5, // 阈值：小于30个像素的区域将被合并
                    4, // 4-连通性
                    null);

            // 创建GeoJSON数据源
            Driver driver = ogr.GetDriverByName("GeoJSON");
            DataSource dataSource = driver.CreateDataSource(outputGeoJSONPath);

            // 创建图层
            SpatialReference srs = new SpatialReference();
            srs.ImportFromEPSG(4326); // WGS84坐标系
            Layer layer = dataSource.CreateLayer("temperature", srs, ogr.wkbPolygon);

            // 添加属性字段
            FieldDefn tempRangeField = new FieldDefn("temp_range", ogr.OFTString);
            tempRangeField.SetWidth(50);
            layer.CreateField(tempRangeField);

            FieldDefn tempClassField = new FieldDefn("temp_class", ogr.OFTInteger);
            layer.CreateField(tempClassField);

            FieldDefn areaField = new FieldDefn("area", ogr.OFTReal);
            layer.CreateField(areaField);

            // 使用过滤后的栅格进行矢量化
            gdal.Polygonize(filteredDS.GetRasterBand(1), null, layer, 1, new Vector<>());

            // 更新属性值
            layer.ResetReading();
            Feature feature;
            while ((feature = layer.GetNextFeature()) != null) {
                int tempClass = feature.GetFieldAsInteger(1);
                String tempRange = getTemperatureRangeLabel(tempClass);
                feature.SetField("temp_range", tempRange);

                // 计算多边形面积
                Geometry geom = feature.GetGeometryRef();
                double area = geom.GetArea();
                feature.SetField("area", area);

                layer.SetFeature(feature);
                feature.delete();
            }

            // 清理资源
            classifiedDS.delete();
            filteredDS.delete();
            dataSource.delete();
            tempDataset.delete();
            srs.delete();

            System.out.println("温度数据已成功矢量化至: " + outputGeoJSONPath);
            return true;

        } catch (Exception e) {
            System.err.println("导出GeoJSON时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 对温度值进行分类
     *
     * @param temperature 温度值
     * @return 温度分类值
     */
    private int classifyTemperature(double temperature) {
        if (temperature < 20.0) return 1; // 低温
        return 2;
    }

    /**
     * 获取温度范围标签
     *
     * @param tempClass 温度分类值
     * @return 温度范围描述
     */
    private String getTemperatureRangeLabel(int tempClass) {
        switch (tempClass) {
            case 1: return "低温 (<20°C)";
            case 2: return "高温 (>=20°C)";
            default: return "未知";
        }
    }

    public static void main(String[] args) {
        System.out.println("开始导出温度数据时间: " + new Date());

        // 使用已有的ASC文件
        String ascFilePath = "D:\\吉奥\\温度\\temperature.asc";

        // 矢量化温度数据
        TemperatureGeoJSONExporter exporter = new TemperatureGeoJSONExporter();
        exporter.exportTemperatureToGeoJSON(ascFilePath,
                "D:\\吉奥\\温度\\temperature.geojson");

        System.out.println("温度数据导出完成时间: " + new Date());
    }
}
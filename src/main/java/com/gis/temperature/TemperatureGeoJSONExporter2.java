package com.gis.temperature;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.gce.arcgrid.ArcGridReader;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.process.raster.PolygonExtractionProcess;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.stereotype.Component;

import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileWriter;
import java.util.Date;

@Component
public class TemperatureGeoJSONExporter2 {

    /**
     * 将温度ASC文件矢量化为GeoJSON (使用GeoTools)
     *
     * @param inputAscPath      ASC文件路径
     * @param outputGeoJSONPath 输出的GeoJSON文件路径
     * @return 是否成功导出
     */
    public boolean exportTemperatureToGeoJSON(String inputAscPath, String outputGeoJSONPath) {
        ArcGridReader reader = null;
        try {
            // 1. 读取ASC栅格文件
            File ascFile = new File(inputAscPath);
            reader = new ArcGridReader(ascFile);
            GridCoverage2D coverage = reader.read(null);

            // 2. 对温度数据进行手动分类
            // 这种方法直接操作像素，比调用高层API更稳定
            RenderedImage image = coverage.getRenderedImage();
            Raster sourceRaster = image.getData();

            // 创建一个新的可写栅格用于存放分类结果，使用字节类型以提高效率
            WritableRaster classifiedRaster = Raster.createBandedRaster(DataBuffer.TYPE_BYTE,
                    image.getWidth(), image.getHeight(), 1, null);

            // 获取源数据的NoData值以便正确处理
            double noDataValue = -9999.0; // ArcGrid 默认值
            Object noDataProperty = coverage.getProperty("GC_NODATA");
            if (noDataProperty instanceof Number) {
                noDataValue = ((Number) noDataProperty).doubleValue();
            }

            // 遍历每个像素，进行分类并写入新栅格
            for (int y = 0; y < sourceRaster.getHeight(); y++) {
                for (int x = 0; x < sourceRaster.getWidth(); x++) {
                    double value = sourceRaster.getSampleDouble(x + sourceRaster.getMinX(), y + sourceRaster.getMinY(), 0);
                    byte classifiedValue;
                    if (Double.compare(value, noDataValue) == 0) {
                        classifiedValue = 0; // 将NoData区域分类为0
                    } else if (value < 20.0) {
                        classifiedValue = 1; // 低温类
                    } else {
                        classifiedValue = 2; // 高温类
                    }
                    classifiedRaster.setSample(x, y, 0, classifiedValue);
                }
            }

            // 基于手动分类的栅格创建新的GridCoverage
            GridCoverageFactory factory = new GridCoverageFactory();
            GridCoverage2D classifiedCoverage = factory.create("classified", classifiedRaster, coverage.getEnvelope());


            // 3. 将分类后的栅格数据矢量化（面提取）
            PolygonExtractionProcess process = new PolygonExtractionProcess();
            SimpleFeatureCollection rawPolygons = process.execute(classifiedCoverage, 0, true, null, null, null, null);

            // 4. 创建新的FeatureType以包含我们需要的属性
            SimpleFeatureType targetSchema = createFeatureType(rawPolygons.getSchema().getCoordinateReferenceSystem());
            ListFeatureCollection finalFeatures = new ListFeatureCollection(targetSchema);
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(targetSchema);

            // 5. 遍历提取的多边形，计算属性并添加到最终集合中
            try (SimpleFeatureIterator iterator = rawPolygons.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    int tempClass = ((Number) feature.getAttribute("value")).intValue();

                    // 跳过由NoData区域生成的多边形
                    if (tempClass == 0) {
                        continue;
                    }

                    Geometry geom = (Geometry) feature.getDefaultGeometry();
                    String tempRange = getTemperatureRangeLabel(tempClass);
                    double area = geom.getArea();

                    featureBuilder.add(geom);
                    featureBuilder.add(tempRange);
                    featureBuilder.add(tempClass);
                    featureBuilder.add(area);
                    finalFeatures.add(featureBuilder.buildFeature(null));
                }
            }

            // 6. 将最终的FeatureCollection写入GeoJSON文件
            FeatureJSON featureJSON = new FeatureJSON();
            try (FileWriter fileWriter = new FileWriter(outputGeoJSONPath)) {
                featureJSON.writeFeatureCollection(finalFeatures, fileWriter);
            }

            System.out.println("温度数据已成功矢量化至: " + outputGeoJSONPath);
            return true;

        } catch (Exception e) {
            System.err.println("导出GeoJSON时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    /**
     * 创建用于存储最终结果的SimpleFeatureType
     */
    private SimpleFeatureType createFeatureType(CoordinateReferenceSystem crs) throws Exception {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("temperature");
        if (crs == null) {
            crs = CRS.decode("EPSG:4326", true);
        }
        builder.setCRS(crs);
        builder.add("the_geom", Geometry.class);
        builder.add("temp_range", String.class);
        builder.add("temp_class", Integer.class);
        builder.add("area", Double.class);
        return builder.buildFeatureType();
    }

    /**
     * 获取温度范围标签
     */
    private String getTemperatureRangeLabel(int tempClass) {
        switch (tempClass) {
            case 1:
                return "低温 (<20°C)";
            case 2:
                return "高温 (>=20°C)";
            default:
                return "未知";
        }
    }

    public static void main(String[] args) {
        System.out.println("开始导出温度数据时间: " + new Date());
        String ascFilePath = "D:\\吉奥\\温度\\temperature.asc";
        TemperatureGeoJSONExporter2 exporter = new TemperatureGeoJSONExporter2();
        exporter.exportTemperatureToGeoJSON(ascFilePath, "D:\\吉奥\\温度\\temperature_v2.geojson");
        System.out.println("温度数据导出完成时间: " + new Date());
    }
}
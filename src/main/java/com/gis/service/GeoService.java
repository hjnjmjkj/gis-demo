package com.gis.service;

import com.gis.entity.SummaryStats;
import com.gis.mapper.GeoEntityMapper;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class GeoService {
    @Resource
    private GeoEntityMapper geoEntityMapper;

    static {
        // 确保 GDAL 在使用前已初始化
        gdal.AllRegister();
        ogr.RegisterAll();
        gdal.SetConfigOption("OGR_GEOMETRY_WKT_FORMATTER", "AXIS_AUTHORITY");
        gdal.SetConfigOption("OGR_GEOJSON_MAX_OBJ_SIZE", "500");
    }

    public void fillFeatureOfGeoJson(String geoJsonPath) {
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
                    geom = geom.Buffer(0);
                    if (geom != null) {
                        SummaryStats summaryStats = geoEntityMapper.getSummaryStats(geom.ExportToJson());
                        System.out.println(summaryStats);
                    }
                    feature.delete();
                } else {
                    System.out.println("该图层没有要素。");
                }
            }
        }
        ds.delete();
    }

    public boolean fillGeoJsonWithElevationStats(String geoJsonPath) {
        DataSource ds = ogr.Open(geoJsonPath, 1);
        if (ds == null) {
            System.err.println("无法打开GeoJSON: " + geoJsonPath);
            return false;
        }
        Layer layer = ds.GetLayer(0);

        // 添加字段
        if (layer.FindFieldIndex("max", 1) == -1)
            layer.CreateField(new FieldDefn("max", ogr.OFTReal));
        if (layer.FindFieldIndex("min", 1) == -1)
            layer.CreateField(new FieldDefn("min", ogr.OFTReal));


        layer.ResetReading();
        Feature feature;
        int count = 0;
        while ((feature = layer.GetNextFeature()) != null) {
            // 跳过已存在min或max的要素
            if (feature.IsFieldSetAndNotNull("max") && feature.IsFieldSetAndNotNull("min")) {
                feature.delete();
                continue;
            }
            // 获取要素几何
            Geometry geom = feature.GetGeometryRef();
            if(!geom.IsValid()){
                geom = geom.Buffer(0);// 确保几何是有效的
                feature.SetGeometry(geom);
            }
            if (geom != null) {
                SummaryStats summaryStats = geoEntityMapper.getSummaryStats(geom.ExportToJson());
                System.out.println(summaryStats);
                if (summaryStats != null) {
                    feature.SetField("max", summaryStats.getMax());
                    feature.SetField("min", summaryStats.getMin());
                    layer.SetFeature(feature);
                }
            }

            feature.delete();
            count++;
            System.out.println(geoJsonPath+"已处理 " + count + " 个要素...");
        }
        ds.SyncToDisk();
        ds.delete();
        return true;
    }


}

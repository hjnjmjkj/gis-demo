package com.gis.hangar;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.locationtech.jts.geom.Coordinate;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * GeoJSON工具类 - 使用Alibaba FastJSON实现GeoJSON格式输出
 */
public class GeoJSONUtils {

    /**
     * 将算法结果转换为GeoJSON格式
     */
    public static String convertToGeoJSON(List<SelectedHangar> hangars, List<DroneModel> droneModels, List<InspectionPoint> inspectionPoints) {
        JSONObject geoJSON = new JSONObject();
        geoJSON.put("type", "FeatureCollection");
        JSONArray features = new JSONArray();
        
        // 添加所有巡检点
        for (InspectionPoint point : inspectionPoints) {
            JSONObject pointFeature = new JSONObject();
            pointFeature.put("type", "Feature");
            
            JSONObject pointGeometry = new JSONObject();
            pointGeometry.put("type", "Point");
            
            JSONArray pointCoordinates = new JSONArray();
            pointCoordinates.add(point.getCoordinate().x);
            pointCoordinates.add(point.getCoordinate().y);
            
            pointGeometry.put("coordinates", pointCoordinates);
            pointFeature.put("geometry", pointGeometry);
            
            JSONObject pointProps = new JSONObject();
            pointProps.put("id", point.getId());
            pointProps.put("type", "inspectionPoint");
            pointProps.put("canBuildHangar", point.canBuildHangar());
            
            pointFeature.put("properties", pointProps);
            features.add(pointFeature);
        }

        // 添加所有机巢和覆盖范围
        for (SelectedHangar hangar : hangars) {
            // 添加机巢点
            JSONObject pointFeature = new JSONObject();
            pointFeature.put("type", "Feature");
            
            JSONObject pointGeometry = new JSONObject();
            pointGeometry.put("type", "Point");
            
            JSONArray pointCoordinates = new JSONArray();
            pointCoordinates.add(hangar.getHangarCoordinate().x);
            pointCoordinates.add(hangar.getHangarCoordinate().y);
            
            pointGeometry.put("coordinates", pointCoordinates);
            pointFeature.put("geometry", pointGeometry);
            
            JSONObject pointProps = new JSONObject();
            pointProps.put("hangarLocationId", hangar.getHangarLocationId());
            pointProps.put("droneModelName", hangar.getDroneModelName());
            pointProps.put("type", "hangar");
            
            pointFeature.put("properties", pointProps);
            features.add(pointFeature);

            // 添加无人机覆盖范围（圆形面）
            DroneModel droneModel = null;
            for (DroneModel dm : droneModels) {
                if (dm.getModelName().equals(hangar.getDroneModelName())) {
                    droneModel = dm;
                    break;
                }
            }
            
            if (droneModel != null) {
                JSONObject polygonFeature = new JSONObject();
                polygonFeature.put("type", "Feature");
                
                JSONObject polygonGeometry = new JSONObject();
                polygonGeometry.put("type", "Polygon");
                
                JSONArray polygonCoordinates = new JSONArray();
                polygonCoordinates.add(generateCircle(hangar.getHangarCoordinate(), droneModel.getRangeKm()));
                
                polygonGeometry.put("coordinates", polygonCoordinates);
                polygonFeature.put("geometry", polygonGeometry);
                
                JSONObject polygonProps = new JSONObject();
                polygonProps.put("hangarLocationId", hangar.getHangarLocationId());
                polygonProps.put("droneModelName", hangar.getDroneModelName());
                polygonProps.put("rangeKm", droneModel.getRangeKm());
                polygonProps.put("type", "coverage");
                
                polygonFeature.put("properties", polygonProps);
                features.add(polygonFeature);
            }
        }

        geoJSON.put("features", features);
        return geoJSON.toJSONString(); // 使用FastJSON的toJSONString方法
    }

    /**
     * 将GeoJSON字符串写入文件
     */
    public static void writeGeoJSONToFile(String geoJSON, String filePath) {
        try (FileWriter file = new FileWriter(filePath)) {
            file.write(geoJSON);
            System.out.println("成功将GeoJSON写入文件: " + filePath);
        } catch (IOException e) {
            System.err.println("写入GeoJSON文件时出错: " + e.getMessage());
        }
    }

    /**
     * 生成圆形多边形（以指定坐标为中心，指定半径）
     * 在墨卡托坐标系中使用欧几里得距离生成圆
     */
    private static JSONArray generateCircle(Coordinate center, double radiusKm) {
        int points = 64; // 圆的分段数
        double radiusMeters = radiusKm * 1000;
        JSONArray coordinates = new JSONArray();

        for (int i = 0; i <= points; i++) {
            double angle = Math.toRadians((360.0 / points) * i);
            // 在墨卡托坐标系中，直接使用平面几何计算即可
            double x = center.x + radiusMeters * Math.cos(angle);
            double y = center.y + radiusMeters * Math.sin(angle);
            
            JSONArray point = new JSONArray();
            point.add(x);
            point.add(y);
            
            coordinates.add(point);
        }

        return coordinates;
    }
}

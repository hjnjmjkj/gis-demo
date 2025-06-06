package com.gis.hangar;

import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.util.*;

public class Main {
    // 坐标转换功能 - WGS84经纬度到墨卡托投影
    private static MathTransform transform4326To3857;
    
    static {
        try {
            CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:4326");
            CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:3857");
            transform4326To3857 = CRS.findMathTransform(sourceCRS, targetCRS, true);
        } catch (Exception e) {
            System.err.println("初始化坐标转换失败: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        List<InspectionPoint> inspectionPoints = new ArrayList<>();
        inspectionPoints.add(new InspectionPoint("p1",13213977,3016150, true)); // 可以建机巢
        inspectionPoints.add(new InspectionPoint("p2",13218977,3016150, true)); // 可以建机巢
        inspectionPoints.add(new InspectionPoint("p3",13238977,3016150, false
        )); // 可以建机巢
        inspectionPoints.add(new InspectionPoint("p4",13240977,3016150, false)); // 不可以建机巢
        inspectionPoints.add(new InspectionPoint("p5",13243977,3016150, false)); // 不可以建机巢
        inspectionPoints.add(new InspectionPoint("p6",13244977,3016150, false)); // 不可以建机巢
        inspectionPoints.add(new InspectionPoint("p7",13245977,3016150, false)); // 不可以建机巢
        
        // 计算点之间的距离并输出，帮助理解覆盖范围
        System.out.println("--- 点之间的距离 ---");
        for (int i = 0; i < inspectionPoints.size(); i++) {
            for (int j = i + 1; j < inspectionPoints.size(); j++) {
                double dis = calculateDistance(inspectionPoints.get(i).getCoordinate(), 
                                             inspectionPoints.get(j).getCoordinate());
                System.out.printf("p%d到p%d距离: %.2f 米 (%.2f km)\n", 
                                 i+1, j+1, dis, dis/1000);
            }
        }

        // 2. 定义无人机型号
        List<DroneModel> droneModels = Arrays.asList(
                new DroneModel("DJI-M300-8KM", 8.0),  // 8km覆盖半径
                new DroneModel("Autel-EVO2-10KM", 10.0),  // 10km覆盖半径
                new DroneModel("DJI-M30-5KM", 5.0)   // 5km覆盖半径
        );

        // 3. 创建算法实例并执行
        System.out.println("\n--- 执行HangarPlacementAlgorithm2 (改进的ILP算法) ---");
        HangarPlacementAlgorithm5 algorithm2 = new HangarPlacementAlgorithm5(inspectionPoints, droneModels);
        List<SelectedHangar> result = algorithm2.findOptimalHangars();

        // 4. 打印结果
        System.out.println("\n--- 最优机巢布置结果 ---");
        if (result.isEmpty()) {
            System.out.println("未选择任何机巢或无需布置机巢。");
        } else {
            result.forEach(hangar -> System.out.println(
                    "机巢位置: 巡检点ID: " + hangar.getHangarLocationId() +
                            " (坐标: " + hangar.getHangarCoordinate().x + ", " + hangar.getHangarCoordinate().y + ")" +
                            "，无人机型号: " + hangar.getDroneModelName()
            ));
        }

        // 检查哪些点被覆盖了
        Map<String, Boolean> pointCoverage = new HashMap<>();
        for (InspectionPoint point : inspectionPoints) {
            pointCoverage.put(point.getId(), false);
        }
        
        for (SelectedHangar hangar : result) {
            DroneModel droneModel = droneModels.stream()
                .filter(dm -> dm.getModelName().equals(hangar.getDroneModelName()))
                .findFirst().orElse(null);
            
            if (droneModel != null) {
                for (InspectionPoint point : inspectionPoints) {
                    double distance = calculateDistance(hangar.getHangarCoordinate(), point.getCoordinate());
                    if (distance <= droneModel.getRangeKm() * 1000) {
                        pointCoverage.put(point.getId(), true);
                    }
                }
            }
        }

        // 输出覆盖统计
        System.out.println("\n--- 覆盖统计 ---");
        int coveredCount = (int) pointCoverage.values().stream().filter(covered -> covered).count();
        System.out.println("总点数: " + inspectionPoints.size());
        System.out.println("已覆盖点数: " + coveredCount + 
                          " (" + String.format("%.1f%%", 100.0 * coveredCount / inspectionPoints.size()) + ")");
        
        List<String> coveredPointIds = new ArrayList<>();
        List<String> uncoveredPointIds = new ArrayList<>();
        
        pointCoverage.forEach((id, covered) -> {
            if (covered) {
                coveredPointIds.add(id);
            } else {
                uncoveredPointIds.add(id);
            }
        });
        
        System.out.println("已覆盖点: " + coveredPointIds);
        
        if (!uncoveredPointIds.isEmpty()) {
            System.out.println("未覆盖点: " + uncoveredPointIds);
        } else {
            System.out.println("所有巡检点已成功覆盖，共使用了 " + result.size() + " 个机巢。");
        }

        // 5. 转换为GeoJSON并打印
        String geoJSON = GeoJSONUtils.convertToGeoJSON(result, droneModels, inspectionPoints);
        System.out.println("\n--- GeoJSON 输出 ---");
        System.out.println(geoJSON);

        // 6. 将GeoJSON写入文件
        String filePath = System.getProperty("user.home") + "\\Desktop\\drone_coverage.geojson";
        GeoJSONUtils.writeGeoJSONToFile(geoJSON, filePath);
    }
    
    private static double calculateDistance(Coordinate c1, Coordinate c2) {
        // 在Web墨卡托坐标系中，直接计算欧几里得距离
        double dx = c1.x - c2.x;
        double dy = c1.y - c2.y;
        return Math.sqrt(dx*dx + dy*dy); // 单位已经是米
    }
}

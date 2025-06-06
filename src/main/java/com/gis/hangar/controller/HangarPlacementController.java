package com.gis.hangar.controller;

import com.gis.hangar.*;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 机巢布置算法的REST API
 */
@RestController
@RequestMapping("/api/hangar")
public class HangarPlacementController {

    /**
     * 测试用的机巢布置优化API，使用硬编码的测试数据
     * 
     * @return 最优机巢布置方案
     */
    @GetMapping("/test")
    public ResponseEntity<?> optimizeHangarPlacementTest() {
        try {
            // 使用与Main类相同的测试数据
            List<InspectionPoint> inspectionPoints = new ArrayList<>();
            inspectionPoints.add(new InspectionPoint("p1", 13213977, 3016150, true)); // 可以建机巢
            inspectionPoints.add(new InspectionPoint("p2", 13218977, 3016150, true)); // 可以建机巢
            inspectionPoints.add(new InspectionPoint("p3", 13238977, 3016150, false)); // 不可以建机巢
            inspectionPoints.add(new InspectionPoint("p4", 13240977, 3016150, false)); // 不可以建机巢
            inspectionPoints.add(new InspectionPoint("p5", 13243977, 3016150, false)); // 不可以建机巢
            inspectionPoints.add(new InspectionPoint("p6", 13244977, 3016150, false)); // 不可以建机巢
            inspectionPoints.add(new InspectionPoint("p7", 13245977, 3016150, false)); // 不可以建机巢
            
            // 定义无人机型号
            List<DroneModel> droneModels = Arrays.asList(
                    new DroneModel("DJI-M300-8KM", 8.0),  // 8km覆盖半径
                    new DroneModel("Autel-EVO2-10KM", 10.0),  // 10km覆盖半径
                    new DroneModel("DJI-M30-5KM", 5.0)   // 5km覆盖半径
            );
            
            // 使用分支定界算法计算
            HangarPlacementAlgorithm5 algorithm = new HangarPlacementAlgorithm5(inspectionPoints, droneModels);
            List<SelectedHangar> result = algorithm.findOptimalHangars();
            
            // 计算覆盖统计
            Map<String, Boolean> pointCoverage = calculateCoverage(result, inspectionPoints, droneModels);
            
            // 构建响应
            HangarPlacementResponse response = new HangarPlacementResponse();
            response.setSelectedHangars(result.stream()
                    .map(h -> new SelectedHangarDTO(
                            h.getHangarLocationId(),
                            h.getDroneModelName(),
                            h.getHangarCoordinate().x,
                            h.getHangarCoordinate().y))
                    .collect(Collectors.toList()));
            
            int coveredCount = (int) pointCoverage.values().stream().filter(v -> v).count();
            response.setTotalPoints(inspectionPoints.size());
            response.setCoveredPoints(coveredCount);
            response.setCoverageRate((double) coveredCount / inspectionPoints.size());
            
            List<String> uncoveredPoints = pointCoverage.entrySet().stream()
                    .filter(e -> !e.getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            response.setUncoveredPoints(uncoveredPoints);
            
            // 生成GeoJSON表示
            String geoJSON = GeoJSONUtils.convertToGeoJSON(result, droneModels, inspectionPoints);
            response.setGeoJSON(geoJSON);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("处理测试请求时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 计算覆盖统计
     */
    private Map<String, Boolean> calculateCoverage(List<SelectedHangar> hangars, 
            List<InspectionPoint> points, List<DroneModel> droneModels) {
        Map<String, Boolean> coverage = new HashMap<>();
        for (InspectionPoint point : points) {
            coverage.put(point.getId(), false);
        }
        
        for (SelectedHangar hangar : hangars) {
            DroneModel droneModel = droneModels.stream()
                .filter(dm -> dm.getModelName().equals(hangar.getDroneModelName()))
                .findFirst().orElse(null);
            
            if (droneModel != null) {
                for (InspectionPoint point : points) {
                    double distance = calculateDistance(hangar.getHangarCoordinate(), point.getCoordinate());
                    if (distance <= droneModel.getRangeKm() * 1000) {
                        coverage.put(point.getId(), true);
                    }
                }
            }
        }
        
        return coverage;
    }
    
    private double calculateDistance(Coordinate c1, Coordinate c2) {
        double dx = c1.x - c2.x;
        double dy = c1.y - c2.y;
        return Math.sqrt(dx*dx + dy*dy);
    }
}

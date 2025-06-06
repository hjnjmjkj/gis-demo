package com.gis.hangar;
import org.locationtech.jts.geom.Coordinate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 机巢布置算法 - 使用贪心算法实现集合覆盖问题
 * 基于墨卡托坐标系统 (EPSG:3857)
 */
public class HangarPlacementAlgorithm4 {

    private final List<InspectionPoint> allInspectionPoints;
    private final List<DroneModel> availableDroneModels;

    public HangarPlacementAlgorithm4(List<InspectionPoint> inspectionPoints, List<DroneModel> droneModels) {
        this.allInspectionPoints = new ArrayList<>(inspectionPoints);
        this.availableDroneModels = new ArrayList<>(droneModels);
    }

    /**
     * 计算两点间的欧几里得距离（米）
     * 在墨卡托坐标系中可直接计算欧几里得距离
     */
    private double calculateDistanceMeters(Coordinate c1, Coordinate c2) {
        double dx = c1.x - c2.x;
        double dy = c1.y - c2.y;
        return Math.sqrt(dx * dx + dy * dy); // 单位是米
    }

    /**
     * 查找最优机巢布置方案
     * 使用分支定界法求解集合覆盖问题
     */

    public List<SelectedHangar> findOptimalHangars() {
        // 预计算覆盖关系矩阵
        boolean[][][] canCover = new boolean[allInspectionPoints.size()][allInspectionPoints.size()][availableDroneModels.size()];

        // 可建机巢的点列表
        List<InspectionPoint> potentialHangars = allInspectionPoints.stream()
                .filter(InspectionPoint::canBuildHangar)
                .collect(Collectors.toList());

        // 计算点索引映射
        Map<InspectionPoint, Integer> pointIndices = new HashMap<>();
        for (int i = 0; i < allInspectionPoints.size(); i++) {
            pointIndices.put(allInspectionPoints.get(i), i);
        }

        // 预计算覆盖关系
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            InspectionPoint hangar = potentialHangars.get(hi);
            for (int di = 0; di < availableDroneModels.size(); di++) {
                DroneModel drone = availableDroneModels.get(di);
                double range = drone.getRangeKm() * 1000; // 转换为米

                for (int pi = 0; pi < allInspectionPoints.size(); pi++) {
                    InspectionPoint point = allInspectionPoints.get(pi);
                    double distance = calculateDistanceMeters(hangar.getCoordinate(), point.getCoordinate());
                    canCover[hi][pi][di] = distance <= range;
                }
            }
        }

        // 保存当前最优解
        List<int[]> bestSolution = new ArrayList<>();
        int[] bestCost = {Integer.MAX_VALUE};
        boolean[] covered = new boolean[allInspectionPoints.size()];

        // 开始分支定界求解
        branchAndBound(potentialHangars, availableDroneModels, canCover,
                new ArrayList<>(), covered, 0, bestSolution, bestCost);

        // 构建结果
        List<SelectedHangar> selectedHangars = new ArrayList<>();
        for (int[] choice : bestSolution) {
            int hi = choice[0];
            int di = choice[1];
            InspectionPoint hangar = potentialHangars.get(hi);
            DroneModel drone = availableDroneModels.get(di);
            selectedHangars.add(new SelectedHangar(
                    hangar.getId(),
                    drone.getModelName(),
                    hangar.getCoordinate()
            ));
        }

        return selectedHangars;
    }

    private void branchAndBound(List<InspectionPoint> potentialHangars, List<DroneModel> drones,
                                boolean[][][] canCover, List<int[]> currentSolution,
                                boolean[] covered, int depth, List<int[]> bestSolution, int[] bestCost) {
        // 检查是否所有点都已覆盖
        boolean allCovered = true;
        for (boolean isCovered : covered) {
            if (!isCovered) {
                allCovered = false;
                break;
            }
        }

        // 找到一个解
        if (allCovered) {
            if (currentSolution.size() < bestCost[0]) {
                bestCost[0] = currentSolution.size();
                bestSolution.clear();
                bestSolution.addAll(currentSolution);
            }
            return;
        }

        // 剪枝：如果当前解的大小已经超过了最优解，则放弃
        if (currentSolution.size() >= bestCost[0]) {
            return;
        }

        // 找到第一个未覆盖的点
        int uncoveredPoint = -1;
        for (int i = 0; i < covered.length; i++) {
            if (!covered[i]) {
                uncoveredPoint = i;
                break;
            }
        }

        // 尝试所有可能覆盖该点的机巢-无人机组合
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            for (int di = 0; di < drones.size(); di++) {
                if (canCover[hi][uncoveredPoint][di]) {
                    // 记录新增覆盖的点
                    List<Integer> newlyCovered = new ArrayList<>();

                    // 更新覆盖状态
                    for (int pi = 0; pi < covered.length; pi++) {
                        if (!covered[pi] && canCover[hi][pi][di]) {
                            covered[pi] = true;
                            newlyCovered.add(pi);
                        }
                    }

                    // 添加当前选择到解
                    currentSolution.add(new int[]{hi, di});

                    // 递归搜索
                    branchAndBound(potentialHangars, drones, canCover, currentSolution,
                            covered, depth + 1, bestSolution, bestCost);

                    // 回溯
                    currentSolution.remove(currentSolution.size() - 1);
                    for (int pi : newlyCovered) {
                        covered[pi] = false;
                    }
                }
            }
        }
    }

}


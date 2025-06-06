package com.gis.hangar;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import org.locationtech.jts.geom.Coordinate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 机巢布置算法 - 使用贪心算法实现集合覆盖问题
 * 基于墨卡托坐标系统 (EPSG:3857)
 */
public class HangarPlacementAlgorithm5 {
    // 所有巡检点
    private final List<InspectionPoint> allInspectionPoints;
    // 可用的无人机型号
    private final List<DroneModel> availableDroneModels;

    public HangarPlacementAlgorithm5(List<InspectionPoint> inspectionPoints, List<DroneModel> droneModels) {
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
     * 计算两点间的欧几里得距离（米）
     * 在墨卡托坐标系中可直接计算欧几里得距离
     */

    /*private void branchAndBound(List<InspectionPoint> potentialHangars, List<DroneModel> drones,
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
    }*/

    /**
     * 使用整数线性规划(ILP)寻找真正的最优机巢布置方案
     * @return 选中的机巢和无人机列表
     */
    public List<SelectedHangar> findOptimalHangars() {
        // 加载原生库
        // 静态加载原生库
        try {
            com.google.ortools.Loader.loadNativeLibraries();
        } catch (Exception e) {
            System.err.println("无法加载OR-Tools原生库: " + e.getMessage());
            throw new RuntimeException("无法初始化OR-Tools，请确保正确安装", e);
        }

        // 预计算覆盖关系矩阵
        boolean[][][] canCover = new boolean[allInspectionPoints.size()][allInspectionPoints.size()][availableDroneModels.size()];

        // 可建机巢的点列表
        List<InspectionPoint> potentialHangars = allInspectionPoints.stream()
                .filter(InspectionPoint::canBuildHangar)
                .collect(Collectors.toList());

        // 为点和无人机建立索引映射
        Map<InspectionPoint, Integer> pointIndices = new HashMap<>();
        for (int i = 0; i < allInspectionPoints.size(); i++) {
            pointIndices.put(allInspectionPoints.get(i), i);
        }
        // 为机巢建立索引映射
        Map<InspectionPoint, Integer> hangarIndices = new HashMap<>();
        for (int i = 0; i < potentialHangars.size(); i++) {
            hangarIndices.put(potentialHangars.get(i), i);
        }

        // 预计算覆盖关系
        for (InspectionPoint hangar : potentialHangars) {
            int hi = hangarIndices.get(hangar);
            for (int di = 0; di < availableDroneModels.size(); di++) {
                DroneModel drone = availableDroneModels.get(di);
                double range = drone.getRangeKm() * 1000; // 转换为米

                for (InspectionPoint point : allInspectionPoints) {
                    int pi = pointIndices.get(point);
                    double distance = calculateDistanceMeters(hangar.getCoordinate(), point.getCoordinate());
                    canCover[hi][pi][di] = distance <= range;
                }
            }
        }

        // 创建求解器
        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            System.err.println("无法创建SCIP求解器，尝试使用SAT");
            solver = MPSolver.createSolver("SAT");
            if (solver == null) {
                throw new IllegalStateException("无法创建求解器。请确保OR-Tools库正确安装。");
            }
        }

        // 决策变量：x[h][d] = 1 表示在位置h建机巢并使用无人机型号d
        MPVariable[][] x = new MPVariable[potentialHangars.size()][availableDroneModels.size()];
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            for (int di = 0; di < availableDroneModels.size(); di++) {
                x[hi][di] = solver.makeBoolVar("x_" + hi + "_" + di);
            }
        }

        // 预先识别不可覆盖点
        Set<Integer> uncoverablePoints = new HashSet<>();
        for (int pi = 0; pi < allInspectionPoints.size(); pi++) {
            boolean canBeCovered = false;
            for (int hi = 0; hi < potentialHangars.size() && !canBeCovered; hi++) {
                for (int di = 0; di < availableDroneModels.size() && !canBeCovered; di++) {
                    if (canCover[hi][pi][di]) {
                        canBeCovered = true;
                    }
                }
            }
            if (!canBeCovered) {
                uncoverablePoints.add(pi);
                System.out.println("警告: 点 " + allInspectionPoints.get(pi).getId() + " 无法被任何机巢-无人机组合覆盖");
            }
        }

        // 修改约束1: 只约束可覆盖的点
        for (int pi = 0; pi < allInspectionPoints.size(); pi++) {
            if (!uncoverablePoints.contains(pi)) {
                MPConstraint coverage = solver.makeConstraint(1.0, Double.POSITIVE_INFINITY);
                for (int hi = 0; hi < potentialHangars.size(); hi++) {
                    for (int di = 0; di < availableDroneModels.size(); di++) {
                        if (canCover[hi][pi][di]) {
                            coverage.setCoefficient(x[hi][di], 1.0);
                        }
                    }
                }
            }
        }

        // 目标函数: 最小化机巢数量并优先选择半径小的无人机
        MPObjective objective = solver.objective();
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            for (int di = 0; di < availableDroneModels.size(); di++) {
                DroneModel drone = availableDroneModels.get(di);
                // 基础成本为1，加上与半径相关的额外权重
                double radiusWeight = drone.getRangeKm() / 10.0; // 调整系数使权重适当
                objective.setCoefficient(x[hi][di], 1.0 + radiusWeight);
            }
        }
        objective.setMinimization();

        // 求解
        MPSolver.ResultStatus status = solver.solve();

        // 处理结果
        List<SelectedHangar> selectedHangars = new ArrayList<>();
        if (status == MPSolver.ResultStatus.OPTIMAL || status == MPSolver.ResultStatus.FEASIBLE) {
            System.out.println("目标函数值 (机巢总数): " + objective.value());

            // 提取结果
            for (int hi = 0; hi < potentialHangars.size(); hi++) {
                for (int di = 0; di < availableDroneModels.size(); di++) {
                    if (x[hi][di].solutionValue() > 0.5) { // 解为1时
                        InspectionPoint hangar = potentialHangars.get(hi);
                        DroneModel drone = availableDroneModels.get(di);
                        selectedHangars.add(new SelectedHangar(
                                hangar.getId(),
                                drone.getModelName(),
                                hangar.getCoordinate()
                        ));
                        System.out.println("选定机巢位置: " + hangar.getId() +
                                "，使用无人机型号: " + drone.getModelName());
                    }
                }
            }
        } else {
            System.err.println("没有找到可行解");
        }

        return selectedHangars;
    }
}


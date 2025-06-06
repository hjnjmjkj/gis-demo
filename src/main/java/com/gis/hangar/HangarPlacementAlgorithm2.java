package com.gis.hangar;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import org.locationtech.jts.geom.Coordinate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 机巢布置算法 - 使用ILP和贪心算法回退
 * 基于墨卡托坐标系统 (EPSG:3857)
 */
public class HangarPlacementAlgorithm2 {

    private final List<InspectionPoint> allInspectionPoints;
    private final List<DroneModel> availableDroneModels;
    private final boolean enableLogging;

    public HangarPlacementAlgorithm2(List<InspectionPoint> inspectionPoints, List<DroneModel> droneModels) {
        this(inspectionPoints, droneModels, true);
    }

    public HangarPlacementAlgorithm2(List<InspectionPoint> inspectionPoints, List<DroneModel> droneModels, boolean enableLogging) {
        this.allInspectionPoints = new ArrayList<>(inspectionPoints);
        this.availableDroneModels = new ArrayList<>(droneModels);
        this.enableLogging = enableLogging;
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
     * 日志记录方法
     */
    private void log(String message) {
        if (enableLogging) {
            System.out.println("[ILP Algorithm] " + message);
        }
    }

    /**
     * 使用整数线性规划(ILP)寻找真正的最优机巢布置方案。
     * 如果ILP无法为强制覆盖点找到解，则提供部分覆盖解决方案。
     * @return 选中的机巢和无人机列表
     */
    public List<SelectedHangar> findOptimalHangars() {
        log("开始执行ILP算法...");
        
        // 加载原生库
        try {
            com.google.ortools.Loader.loadNativeLibraries();
        } catch (Exception e) {
            log("无法加载OR-Tools原生库: " + e.getMessage());
            // 回退到贪心
            log("由于OR-Tools加载失败，将直接使用贪心算法。");
            return findGreedyHangars();
        }

        // 可建机巢的点列表 (这些是潜在的机巢位置)
        List<InspectionPoint> potentialHangars = allInspectionPoints.stream()
                .filter(InspectionPoint::canBuildHangar)
                .collect(Collectors.toList());

        // 需要覆盖的所有巡检点 - 修改：现在考虑所有点，不仅是canBuildHangar=true的点
        List<InspectionPoint> pointsRequiringCoverage = allInspectionPoints;
        
        log("需要覆盖的巡检点数量: " + pointsRequiringCoverage.size());
        log("可用于建造机巢的位置数量: " + potentialHangars.size());
        
        boolean hasMandatoryPointsToCover = !pointsRequiringCoverage.isEmpty();

        if (potentialHangars.isEmpty() && hasMandatoryPointsToCover) {
            log("没有可用于建造机巢的地点，但有需要覆盖的巡检点。将使用贪心算法。");
            return findGreedyHangars();
        }
        
        if (potentialHangars.isEmpty() || !hasMandatoryPointsToCover) {
             log("没有可用于建造机巢的地点或没有需要覆盖的巡检点。");
             return new ArrayList<>();
        }

        // 预计算覆盖关系矩阵
        boolean[][][] canCover = new boolean[potentialHangars.size()][pointsRequiringCoverage.size()][availableDroneModels.size()];

        // 为点和无人机建立索引映射
        Map<InspectionPoint, Integer> pointIndices = new HashMap<>();
        for (int i = 0; i < pointsRequiringCoverage.size(); i++) {
            pointIndices.put(pointsRequiringCoverage.get(i), i);
        }

        Map<InspectionPoint, Integer> hangarSiteIndices = new HashMap<>();
        for (int i = 0; i < potentialHangars.size(); i++) {
            hangarSiteIndices.put(potentialHangars.get(i), i);
        }
        
        // 预计算覆盖关系和检查每个点可以被覆盖的可能性
        Set<Integer> uncoverablePoints = new HashSet<>();
        
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            InspectionPoint hangarSite = potentialHangars.get(hi);
            for (int di = 0; di < availableDroneModels.size(); di++) {
                DroneModel drone = availableDroneModels.get(di);
                double range = drone.getRangeKm() * 1000; // 转换为米

                for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
                    InspectionPoint pointToCover = pointsRequiringCoverage.get(pi);
                    double distance = calculateDistanceMeters(hangarSite.getCoordinate(), pointToCover.getCoordinate());
                    canCover[hi][pi][di] = distance <= range;
                }
            }
        }
        
        // 检查哪些点不可覆盖
        for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
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
                log("警告: 点 " + pointsRequiringCoverage.get(pi).getId() + " 无法被任何机巢-无人机组合覆盖");
            }
        }
        
        log("不可覆盖点数: " + uncoverablePoints.size() + " / " + pointsRequiringCoverage.size());
        
        // 首先尝试完全覆盖
        List<SelectedHangar> completeResult = tryCompleteILPSolution(potentialHangars, pointsRequiringCoverage, canCover, pointIndices);
        
        if (!completeResult.isEmpty()) {
            log("找到完全覆盖解决方案！");
            return completeResult;
        }
        
        log("无法找到完全覆盖解决方案，尝试部分覆盖...");
        
        // 如果完全覆盖不可能，尝试部分覆盖
        List<SelectedHangar> partialResult = tryPartialILPSolution(potentialHangars, pointsRequiringCoverage, canCover, pointIndices, uncoverablePoints);
        
        if (!partialResult.isEmpty()) {
            log("找到部分覆盖解决方案！");
            return partialResult;
        }
        
        // 如果ILP部分覆盖也失败，回退到贪心算法
        log("ILP无法找到部分覆盖解决方案，回退到贪心算法...");
        return findGreedyHangars();
    }
    
    /**
     * 尝试使用ILP找到完全覆盖解决方案
     */
    private List<SelectedHangar> tryCompleteILPSolution(
            List<InspectionPoint> potentialHangars, 
            List<InspectionPoint> pointsRequiringCoverage, 
            boolean[][][] canCover,
            Map<InspectionPoint, Integer> pointIndices) {
            
        // 创建ILP求解器
        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            log("无法创建SCIP求解器，尝试使用SAT");
            solver = MPSolver.createSolver("SAT");
            if (solver == null) {
                log("无法创建任何求解器。请确保OR-Tools库正确安装。");
                return new ArrayList<>();
            }
        }

        // 决策变量：x[h_idx][d_idx] = 1 表示在 potentialHangars[h_idx] 建机巢并使用无人机型号 availableDroneModels[d_idx]
        MPVariable[][] x = new MPVariable[potentialHangars.size()][availableDroneModels.size()];
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            for (int di = 0; di < availableDroneModels.size(); di++) {
                x[hi][di] = solver.makeBoolVar("x_" + hi + "_" + di);
            }
        }

        // 约束 1: 每个巡检点必须至少被一个机巢覆盖
        for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
            InspectionPoint currentPointToCover = pointsRequiringCoverage.get(pi);
            
            MPConstraint coverageConstraint = solver.makeConstraint(1.0, Double.POSITIVE_INFINITY, "Coverage_" + currentPointToCover.getId());
            boolean canBeCovered = false;
            
            for (int hi = 0; hi < potentialHangars.size(); hi++) {
                for (int di = 0; di < availableDroneModels.size(); di++) {
                    if (canCover[hi][pi][di]) {
                        coverageConstraint.setCoefficient(x[hi][di], 1.0);
                        canBeCovered = true;
                    }
                }
            }
            
            if (!canBeCovered) {
                log("警告: 点 " + currentPointToCover.getId() + " 无法被任何潜在机巢-无人机组合覆盖。ILP将无法找到完全覆盖解。");
                // 此处不中断，因为我们需要检查所有点
            }
        }

        // 目标函数: 最小化机巢数量
        MPObjective objective = solver.objective();
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            for (int di = 0; di < availableDroneModels.size(); di++) {
                objective.setCoefficient(x[hi][di], 1.0);
            }
        }
        objective.setMinimization();

        // 求解
        MPSolver.ResultStatus status = solver.solve();

        List<SelectedHangar> selectedHangarsResult = new ArrayList<>();
        if (status == MPSolver.ResultStatus.OPTIMAL || status == MPSolver.ResultStatus.FEASIBLE) {
            log("完全覆盖ILP找到解。目标函数值 (机巢总数): " + objective.value());

            for (int hi = 0; hi < potentialHangars.size(); hi++) {
                for (int di = 0; di < availableDroneModels.size(); di++) {
                    if (x[hi][di].solutionValue() > 0.5) { // 解为1时
                        InspectionPoint hangarLocation = potentialHangars.get(hi);
                        DroneModel drone = availableDroneModels.get(di);
                        selectedHangarsResult.add(new SelectedHangar(
                                hangarLocation.getId(),
                                drone.getModelName(),
                                hangarLocation.getCoordinate()
                        ));
                        log("选定机巢位置: " + hangarLocation.getId() +
                                "，使用无人机型号: " + drone.getModelName());
                    }
                }
            }
        } else {
            log("完全覆盖ILP未找到可行解。状态: " + status);
        }
        
        return selectedHangarsResult;
    }
    
    /**
     * 尝试使用ILP找到部分覆盖解决方案
     * 允许某些点不被覆盖，但尝试最大化覆盖点数
     */
    private List<SelectedHangar> tryPartialILPSolution(
            List<InspectionPoint> potentialHangars, 
            List<InspectionPoint> pointsRequiringCoverage, 
            boolean[][][] canCover,
            Map<InspectionPoint, Integer> pointIndices,
            Set<Integer> uncoverablePoints) {
            
        // 创建ILP求解器
        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            solver = MPSolver.createSolver("SAT");
            if (solver == null) {
                log("无法创建求解器。请确保OR-Tools库正确安装。");
                return new ArrayList<>();
            }
        }

        // 决策变量：x[h][d] = 1 表示在位置h建机巢并使用无人机型号d
        MPVariable[][] x = new MPVariable[potentialHangars.size()][availableDroneModels.size()];
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            for (int di = 0; di < availableDroneModels.size(); di++) {
                x[hi][di] = solver.makeBoolVar("x_" + hi + "_" + di);
            }
        }
        
        // 引入覆盖指示变量：y[p] = 1 表示点p被覆盖
        MPVariable[] y = new MPVariable[pointsRequiringCoverage.size()];
        for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
            y[pi] = solver.makeBoolVar("y_" + pi);
        }
        
        // 约束1：如果点被覆盖，那么至少有一个机巢-无人机组合覆盖它
        for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
            if (uncoverablePoints.contains(pi)) {
                // 对于不可覆盖的点，强制y[pi] = 0
                MPConstraint uncoverable = solver.makeConstraint(0, 0);
                uncoverable.setCoefficient(y[pi], 1);
                continue;
            }
            
            MPConstraint linkConstraint = solver.makeConstraint(-solver.infinity(), 0);
            linkConstraint.setCoefficient(y[pi], -1);
            
            for (int hi = 0; hi < potentialHangars.size(); hi++) {
                for (int di = 0; di < availableDroneModels.size(); di++) {
                    if (canCover[hi][pi][di]) {
                        linkConstraint.setCoefficient(x[hi][di], 1);
                    }
                }
            }
        }
        
        // 目标函数：最大化覆盖点数，次要目标是最小化机巢数
        MPObjective objective = solver.objective();
        
        // 覆盖一个点的权重远大于增加一个机巢的成本
        double pointWeight = 1000.0;
        double hangarWeight = 1.0;
        
        for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
            objective.setCoefficient(y[pi], pointWeight);
        }
        
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            for (int di = 0; di < availableDroneModels.size(); di++) {
                objective.setCoefficient(x[hi][di], -hangarWeight);
            }
        }
        objective.setMaximization();

        // 求解
        MPSolver.ResultStatus status = solver.solve();

        List<SelectedHangar> selectedHangars = new ArrayList<>();
        if (status == MPSolver.ResultStatus.OPTIMAL || status == MPSolver.ResultStatus.FEASIBLE) {
            // 计算覆盖的点数
            int coveredCount = 0;
            for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
                if (y[pi].solutionValue() > 0.5) {
                    coveredCount++;
                }
            }
            
            log("部分覆盖ILP解: 覆盖点数: " + coveredCount + " / " + (pointsRequiringCoverage.size() - uncoverablePoints.size()) + 
                " 可覆盖点 (" + String.format("%.1f%%", 100.0 * coveredCount / (pointsRequiringCoverage.size() - uncoverablePoints.size())) + 
                "，总体覆盖率: " + String.format("%.1f%%", 100.0 * coveredCount / pointsRequiringCoverage.size()) + ")");
            
            // 提取选定的机巢
            for (int hi = 0; hi < potentialHangars.size(); hi++) {
                for (int di = 0; di < availableDroneModels.size(); di++) {
                    if (x[hi][di].solutionValue() > 0.5) {
                        InspectionPoint hangarLocation = potentialHangars.get(hi);
                        DroneModel drone = availableDroneModels.get(di);
                        selectedHangars.add(new SelectedHangar(
                                hangarLocation.getId(),
                                drone.getModelName(),
                                hangarLocation.getCoordinate()
                        ));
                        log("选定机巢位置: " + hangarLocation.getId() +
                                "，使用无人机型号: " + drone.getModelName());
                    }
                }
            }
            
            // 输出未覆盖点信息
            List<String> uncoveredPointIds = new ArrayList<>();
            for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
                if (y[pi].solutionValue() < 0.5) {
                    uncoveredPointIds.add(pointsRequiringCoverage.get(pi).getId());
                }
            }
            
            if (!uncoveredPointIds.isEmpty()) {
                log("未覆盖点: " + uncoveredPointIds);
            }
        } else {
            log("部分覆盖ILP未能找到解。状态: " + status);
        }
        
        return selectedHangars;
    }

    /**
     * 贪心算法找出机巢布置方案，优先覆盖尽可能多的巡检点
     * @return 选中的机巢和无人机列表
     */
    private List<SelectedHangar> findGreedyHangars() {
        log("执行贪心算法...");
        List<SelectedHangar> selectedHangars = new ArrayList<>();
        
        // 考虑所有巡检点，不仅仅是canBuildHangar=true的点
        Set<InspectionPoint> pointsRequiringCoverage = new HashSet<>(allInspectionPoints);

        if (pointsRequiringCoverage.isEmpty()) {
            log("贪心算法: 没有需要覆盖的巡检点。");
            return selectedHangars;
        }

        List<InspectionPoint> potentialHangarSites = this.allInspectionPoints.stream()
                .filter(InspectionPoint::canBuildHangar) // 机巢只能建在允许建机巢的地点
                .collect(Collectors.toList());

        if (potentialHangarSites.isEmpty()) {
            log("贪心算法: 没有可用于建造机巢的地点，但有需要覆盖的巡检点。");
            return selectedHangars; // 无法放置任何机巢
        }

        // 已选用的机巢位置，避免重复选择同一位置
        Set<String> usedHangarSiteIds = new HashSet<>();

        while (!pointsRequiringCoverage.isEmpty()) {
            InspectionPoint bestHangarSite = null;
            DroneModel bestDroneModel = null;
            Set<InspectionPoint> bestCoverageSet = new HashSet<>();
            int maxCoveredCount = 0;

            for (InspectionPoint hangarSite : potentialHangarSites) {
                if (usedHangarSiteIds.contains(hangarSite.getId())) {
                    continue; // 跳过已用地点
                }

                for (DroneModel drone : this.availableDroneModels) {
                    Set<InspectionPoint> currentCoverageSet = new HashSet<>();
                    double rangeMeters = drone.getRangeKm() * 1000;

                    for (InspectionPoint pointToCover : pointsRequiringCoverage) {
                        if (calculateDistanceMeters(hangarSite.getCoordinate(), pointToCover.getCoordinate()) <= rangeMeters) {
                            currentCoverageSet.add(pointToCover);
                        }
                    }

                    if (currentCoverageSet.size() > maxCoveredCount) {
                        maxCoveredCount = currentCoverageSet.size();
                        bestHangarSite = hangarSite;
                        bestDroneModel = drone;
                        bestCoverageSet = currentCoverageSet;
                    }
                }
            }

            if (maxCoveredCount > 0 && bestHangarSite != null && bestDroneModel != null) {
                SelectedHangar chosen = new SelectedHangar(
                        bestHangarSite.getId(),
                        bestDroneModel.getModelName(),
                        bestHangarSite.getCoordinate()
                );
                selectedHangars.add(chosen);
                pointsRequiringCoverage.removeAll(bestCoverageSet);
                usedHangarSiteIds.add(bestHangarSite.getId()); // 标记此机巢点已使用

                log("贪心算法选定机巢: " + chosen.getHangarLocationId() +
                        " 使用无人机: " + chosen.getDroneModelName() +
                        ", 覆盖了 " + maxCoveredCount + " 个点。剩余未覆盖点: " + pointsRequiringCoverage.size());
            } else {
                // 没有机巢可以覆盖任何剩余的强制巡检点
                if (!pointsRequiringCoverage.isEmpty()) {
                    log("贪心算法: 无法覆盖所有巡检点。剩余未覆盖: " +
                            pointsRequiringCoverage.stream().map(InspectionPoint::getId).collect(Collectors.toList()));
                }
                break; // 退出循环
            }
        }
        
        // 计算覆盖率
        int totalPoints = allInspectionPoints.size();
        int coveredPoints = totalPoints - pointsRequiringCoverage.size();
        log("贪心算法覆盖率: " + coveredPoints + "/" + totalPoints + " (" + 
            String.format("%.2f%%", (double)coveredPoints/totalPoints*100) + ")");
            
        return selectedHangars;
    }
}

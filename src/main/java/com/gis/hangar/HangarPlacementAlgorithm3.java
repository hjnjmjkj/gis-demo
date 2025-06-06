package com.gis.hangar;
import org.locationtech.jts.geom.Coordinate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 机巢布置算法 - 使用增强的分支定界法实现集合覆盖问题
 * 基于墨卡托坐标系统 (EPSG:3857)
 */
public class HangarPlacementAlgorithm3 {

    private final List<InspectionPoint> allInspectionPoints;
    private final List<DroneModel> availableDroneModels;
    private final boolean enableLogging;

    // 性能监控变量
    private int nodesExplored = 0;
    private int nodesPruned = 0;
    private long startTime;
    
    // 添加跟踪未覆盖点的变量
    private Set<Integer> uncoverablePoints;

    public HangarPlacementAlgorithm3(List<InspectionPoint> inspectionPoints, List<DroneModel> droneModels) {
        this(inspectionPoints, droneModels, true);
    }

    public HangarPlacementAlgorithm3(List<InspectionPoint> inspectionPoints, List<DroneModel> droneModels, boolean enableLogging) {
        this.allInspectionPoints = new ArrayList<>(inspectionPoints);
        this.availableDroneModels = new ArrayList<>(droneModels);
        this.enableLogging = enableLogging;
        this.uncoverablePoints = new HashSet<>();
    }

    /**
     * 计算两点间的欧几里得距离（米）
     */
    private double calculateDistanceMeters(Coordinate c1, Coordinate c2) {
        double dx = c1.x - c2.x;
        double dy = c1.y - c2.y;
        return Math.sqrt(dx * dx + dy * dy); // 单位是米
    }

    /**
     * 使用增强的分支定界法找到最优机巢布置方案
     * @return 选中的机巢和无人机列表
     */
    public List<SelectedHangar> findOptimalHangars() {
        log("开始执行增强分支定界算法...");
        startTime = System.currentTimeMillis();
        uncoverablePoints = new HashSet<>(); // 重置不可覆盖点集合

        // 只考虑必须覆盖的点（canBuildHangar为true的点）
        List<InspectionPoint> pointsRequiringCoverage = allInspectionPoints.stream()
                .filter(InspectionPoint::canBuildHangar)
                .collect(Collectors.toList());

        // 如果没有需要覆盖的点，直接返回空列表
        if (pointsRequiringCoverage.isEmpty()) {
            log("没有需要覆盖的巡检点。");
            return new ArrayList<>();
        }

        // 潜在的机巢位置（可建机巢的点）
        List<InspectionPoint> potentialHangars = allInspectionPoints.stream()
                .filter(InspectionPoint::canBuildHangar)
                .collect(Collectors.toList());

        if (potentialHangars.isEmpty()) {
            log("错误：没有可用于建造机巢的地点，但有需要覆盖的巡检点。");
            return new ArrayList<>();
        }

        log("需要覆盖的巡检点数量: " + pointsRequiringCoverage.size());
        log("可用于建造机巢的位置数量: " + potentialHangars.size());

        // 预计算覆盖关系矩阵 [机巢位置][巡检点][无人机型号]
        boolean[][][] canCover = precomputeCoverageMatrix(potentialHangars, pointsRequiringCoverage);
        
        // 预先标记不可覆盖点
        identifyUncoverablePoints(canCover, pointsRequiringCoverage);

        // 使用贪心算法获取初始上界
        List<SelectedHangar> greedySolution = findGreedyHangars(potentialHangars, pointsRequiringCoverage);
        int initialUpperBound = greedySolution.size();
        log("贪心算法初始上界: " + initialUpperBound + " 个机巢");

        // 如果贪心算法找到了解，但最终结果为空，直接返回贪心结果
        if (initialUpperBound > 0) {
            log("使用贪心算法结果作为备选方案");
        }

        // 准备分支定界相关数据结构
        List<int[]> bestSolution = new ArrayList<>();
        int[] bestCost = {initialUpperBound}; // 使用贪心解作为初始上界
        boolean[] covered = new boolean[pointsRequiringCoverage.size()];
        
        // 将不可覆盖点标记为已覆盖，这样算法不会尝试去覆盖它们
        for (Integer pointIndex : uncoverablePoints) {
            covered[pointIndex] = true;
        }
        
        // 跟踪当前最大覆盖点数
        int[] maxPointsCovered = {0};
        int[] currentPointsCovered = {0};

        // 开始分支定界
        branchAndBound(
                potentialHangars, pointsRequiringCoverage, canCover,
                new ArrayList<>(), covered, 0, bestSolution, bestCost,
                new HashMap<>(), // 记忆化搜索表
                maxPointsCovered, currentPointsCovered
        );

        // 构建最终结果
        List<SelectedHangar> result = new ArrayList<>();
        for (int[] choice : bestSolution) {
            int hi = choice[0];
            int di = choice[1];
            InspectionPoint hangarLocation = potentialHangars.get(hi);
            DroneModel droneModel = availableDroneModels.get(di);
            result.add(new SelectedHangar(
                    hangarLocation.getId(),
                    droneModel.getModelName(),
                    hangarLocation.getCoordinate()
            ));
            log("选定机巢位置: " + hangarLocation.getId() + "，使用无人机型号: " + droneModel.getModelName());
        }

        long duration = System.currentTimeMillis() - startTime;
        log("分支定界算法完成。探索节点数: " + nodesExplored + ", 剪枝节点数: " + nodesPruned);
        log("算法执行时间: " + duration + "ms，找到最优解: " + result.size() + " 个机巢");
        
        // 报告覆盖情况
        int totalPoints = pointsRequiringCoverage.size();
        int coveredPoints = totalPoints - uncoverablePoints.size();
        log("覆盖率: " + coveredPoints + "/" + totalPoints + " 巡检点 (" + 
            String.format("%.2f%%", (double)coveredPoints/totalPoints*100) + ")");
        
        if (!uncoverablePoints.isEmpty()) {
            List<String> uncoverableIds = uncoverablePoints.stream()
                .map(idx -> pointsRequiringCoverage.get(idx).getId())
                .collect(Collectors.toList());
            log("警告: 无法覆盖的巡检点: " + uncoverableIds);
        }

        // 如果分支定界没有找到解但贪心找到了，则返回贪心结果
        if (result.isEmpty() && !greedySolution.isEmpty()) {
            log("分支定界未找到解，返回贪心算法结果");
            return greedySolution;
        }

        return result;
    }
    
    /**
     * 预先识别无法被任何机巢-无人机组合覆盖的点
     */
    private void identifyUncoverablePoints(boolean[][][] canCover, List<InspectionPoint> pointsRequiringCoverage) {
        for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
            boolean canBeCovered = false;
            for (int hi = 0; hi < canCover.length && !canBeCovered; hi++) {
                for (int di = 0; di < canCover[0][0].length && !canBeCovered; di++) {
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
    }

    /**
     * 预计算覆盖关系矩阵
     */
    private boolean[][][] precomputeCoverageMatrix(List<InspectionPoint> potentialHangars,
                                                   List<InspectionPoint> pointsRequiringCoverage) {
        boolean[][][] canCover = new boolean[potentialHangars.size()][pointsRequiringCoverage.size()][availableDroneModels.size()];

        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            InspectionPoint hangar = potentialHangars.get(hi);
            for (int di = 0; di < availableDroneModels.size(); di++) {
                DroneModel drone = availableDroneModels.get(di);
                double rangeMeters = drone.getRangeKm() * 1000; // 转换为米

                for (int pi = 0; pi < pointsRequiringCoverage.size(); pi++) {
                    InspectionPoint point = pointsRequiringCoverage.get(pi);
                    double distance = calculateDistanceMeters(hangar.getCoordinate(), point.getCoordinate());
                    canCover[hi][pi][di] = distance <= rangeMeters;
                }
            }
        }

        return canCover;
    }

    /**
     * 递归的分支定界核心函数，加强版
     */
    private void branchAndBound(
            List<InspectionPoint> potentialHangars,
            List<InspectionPoint> pointsRequiringCoverage,
            boolean[][][] canCover,
            List<int[]> currentSolution,
            boolean[] covered,
            int depth,
            List<int[]> bestSolution,
            int[] bestCost,
            Map<String, Integer> memo,
            int[] maxPointsCovered,
            int[] currentPointsCovered) {

        nodesExplored++;
        
        // 计算当前已覆盖的点数
        int coveredCount = 0;
        for (int i = 0; i < covered.length; i++) {
            if (covered[i] && !uncoverablePoints.contains(i)) {
                coveredCount++;
            }
        }
        currentPointsCovered[0] = coveredCount;

        // 检查是否所有可覆盖点都已覆盖
        boolean allCoverable = true;
        for (int i = 0; i < covered.length; i++) {
            if (!covered[i] && !uncoverablePoints.contains(i)) {
                allCoverable = false;
                break;
            }
        }

        // 找到一个有效解
        if (allCoverable) {
            // 如果当前解覆盖的点数更多或相同但机巢数更少，则更新最佳解
            if ((currentPointsCovered[0] > maxPointsCovered[0]) || 
                (currentPointsCovered[0] == maxPointsCovered[0] && currentSolution.size() < bestCost[0])) {
                maxPointsCovered[0] = currentPointsCovered[0];
                bestCost[0] = currentSolution.size();
                bestSolution.clear();
                bestSolution.addAll(currentSolution);
                log("找到更优解，覆盖点数: " + maxPointsCovered[0] + "，机巢数: " + bestCost[0]);
            }
            return;
        }

        // 剪枝1: 当前解的大小已经超过或等于最优解，则放弃
        if (currentSolution.size() >= bestCost[0]) {
            nodesPruned++;
            return;
        }

        // 剪枝2: 通过记忆化搜索避免重复状态
        String stateKey = getStateKey(covered);
        if (memo.containsKey(stateKey) && memo.get(stateKey) <= currentSolution.size()) {
            nodesPruned++;
            return;
        }
        memo.put(stateKey, currentSolution.size());

        // 剪枝3: 乐观估计 - 如果当前未覆盖的点数除以单个机巢-无人机能覆盖的最大点数
        // 加上当前机巢数已经大于等于已知的最优解，则剪枝
        int uncoveredCount = 0;
        for (int i = 0; i < covered.length; i++) {
            if (!covered[i] && !uncoverablePoints.contains(i)) uncoveredCount++;
        }

        int maxPointsCoveredByOne = getMaxPointsCoveredByOne(potentialHangars, pointsRequiringCoverage, canCover, covered);
        if (maxPointsCoveredByOne > 0) {
            int optimisticEstimate = currentSolution.size() + (int)Math.ceil((double)uncoveredCount / maxPointsCoveredByOne);
            if (optimisticEstimate >= bestCost[0]) {
                nodesPruned++;
                return;
            }
        }

        // 选择最难覆盖的点（被最少机巢-无人机组合覆盖的点）
        int hardestPoint = selectHardestPoint(potentialHangars, pointsRequiringCoverage, canCover, covered);
        if (hardestPoint == -1) {
            // 剩余的点都无法覆盖，此分支已达到最大覆盖
            // 检查当前解是否比最优解更好
            if ((currentPointsCovered[0] > maxPointsCovered[0]) || 
                (currentPointsCovered[0] == maxPointsCovered[0] && currentSolution.size() < bestCost[0])) {
                maxPointsCovered[0] = currentPointsCovered[0];
                bestCost[0] = currentSolution.size();
                bestSolution.clear();
                bestSolution.addAll(currentSolution);
                log("找到部分覆盖解，覆盖点数: " + maxPointsCovered[0] + "，机巢数: " + bestCost[0]);
            }
            return;
        }

        // 获取并排序所有可以覆盖这个难点的机巢-无人机组合
        List<int[]> candidates = getCandidatesForPoint(
                potentialHangars, pointsRequiringCoverage, canCover, covered, hardestPoint);

        // 按照每个组合能覆盖的未覆盖点数量降序排序
        candidates.sort((a, b) -> {
            int coveredByA = countAdditionalCoverage(potentialHangars, pointsRequiringCoverage, canCover, covered, a[0], a[1]);
            int coveredByB = countAdditionalCoverage(potentialHangars, pointsRequiringCoverage, canCover, covered, b[0], b[1]);
            return Integer.compare(coveredByB, coveredByA); // 降序
        });

        // 尝试所有候选组合
        for (int[] candidate : candidates) {
            int hi = candidate[0];
            int di = candidate[1];

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
            branchAndBound(potentialHangars, pointsRequiringCoverage, canCover,
                    currentSolution, covered, depth + 1, bestSolution, bestCost, memo,
                    maxPointsCovered, currentPointsCovered);

            // 回溯
            currentSolution.remove(currentSolution.size() - 1);
            for (int pi : newlyCovered) {
                covered[pi] = false;
            }
        }
    }

    /**
     * 选择最难覆盖的点（被最少机巢-无人机组合覆盖的点）
     * 修改为跳过不可覆盖的点
     */
    private int selectHardestPoint(List<InspectionPoint> potentialHangars,
                                   List<InspectionPoint> pointsRequiringCoverage,
                                   boolean[][][] canCover, boolean[] covered) {
        int hardestPoint = -1;
        int minCoverageOptions = Integer.MAX_VALUE;

        for (int pi = 0; pi < covered.length; pi++) {
            if (covered[pi]) continue; // 跳过已覆盖的点

            int coverageOptions = 0;
            for (int hi = 0; hi < potentialHangars.size(); hi++) {
                for (int di = 0; di < availableDroneModels.size(); di++) {
                    if (canCover[hi][pi][di]) {
                        coverageOptions++;
                    }
                }
            }

            // 如果这个点无法被任何机巢-无人机组合覆盖，标记为不可覆盖并跳过
            if (coverageOptions == 0) {
                uncoverablePoints.add(pi);
                covered[pi] = true; // 将不可覆盖点标记为已覆盖，避免再次考虑
                continue;
            }

            if (coverageOptions < minCoverageOptions) {
                minCoverageOptions = coverageOptions;
                hardestPoint = pi;
            }
        }

        return hardestPoint;
    }

    /**
     * 获取可以覆盖指定点的所有机巢-无人机组合
     */
    private List<int[]> getCandidatesForPoint(List<InspectionPoint> potentialHangars,
                                              List<InspectionPoint> pointsRequiringCoverage,
                                              boolean[][][] canCover, boolean[] covered, int pointIndex) {
        List<int[]> candidates = new ArrayList<>();

        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            for (int di = 0; di < availableDroneModels.size(); di++) {
                if (canCover[hi][pointIndex][di]) {
                    candidates.add(new int[]{hi, di});
                }
            }
        }

        return candidates;
    }

    /**
     * 计算一个机巢-无人机组合能新覆盖多少未覆盖的点
     */
    private int countAdditionalCoverage(List<InspectionPoint> potentialHangars,
                                        List<InspectionPoint> pointsRequiringCoverage,
                                        boolean[][][] canCover, boolean[] covered, int hi, int di) {
        int count = 0;
        for (int pi = 0; pi < covered.length; pi++) {
            if (!covered[pi] && canCover[hi][pi][di]) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取单个机巢-无人机能覆盖的最大点数
     */
    private int getMaxPointsCoveredByOne(List<InspectionPoint> potentialHangars,
                                         List<InspectionPoint> pointsRequiringCoverage,
                                         boolean[][][] canCover, boolean[] covered) {
        int max = 0;
        for (int hi = 0; hi < potentialHangars.size(); hi++) {
            for (int di = 0; di < availableDroneModels.size(); di++) {
                int count = countAdditionalCoverage(potentialHangars, pointsRequiringCoverage, canCover, covered, hi, di);
                max = Math.max(max, count);
            }
        }
        return max;
    }

    /**
     * 获取当前覆盖状态的唯一标识符，用于记忆化搜索
     */
    private String getStateKey(boolean[] covered) {
        StringBuilder sb = new StringBuilder();
        for (boolean b : covered) {
            sb.append(b ? '1' : '0');
        }
        return sb.toString();
    }

    /**
     * 使用贪心算法生成初始解，作为分支定界的上界
     */
    private List<SelectedHangar> findGreedyHangars(List<InspectionPoint> potentialHangars,
                                                   List<InspectionPoint> pointsRequiringCoverage) {
        List<SelectedHangar> selectedHangars = new ArrayList<>();
        Set<InspectionPoint> uncoveredPoints = new HashSet<>();
        
        // 只添加可覆盖的点到未覆盖集合
        for (int i = 0; i < pointsRequiringCoverage.size(); i++) {
            if (!uncoverablePoints.contains(i)) {
                uncoveredPoints.add(pointsRequiringCoverage.get(i));
            }
        }

        while (!uncoveredPoints.isEmpty()) {
            InspectionPoint bestHangarSite = null;
            DroneModel bestDroneModel = null;
            Set<InspectionPoint> bestCoveredPoints = null;
            int maxCovered = -1;

            for (InspectionPoint hangarSite : potentialHangars) {
                for (DroneModel drone : availableDroneModels) {
                    Set<InspectionPoint> coveredPoints = new HashSet<>();
                    double rangeMeters = drone.getRangeKm() * 1000;

                    for (InspectionPoint point : uncoveredPoints) {
                        double distance = calculateDistanceMeters(hangarSite.getCoordinate(), point.getCoordinate());
                        if (distance <= rangeMeters) {
                            coveredPoints.add(point);
                        }
                    }

                    if (coveredPoints.size() > maxCovered) {
                        maxCovered = coveredPoints.size();
                        bestHangarSite = hangarSite;
                        bestDroneModel = drone;
                        bestCoveredPoints = coveredPoints;
                    }
                }
            }

            if (maxCovered > 0) {
                selectedHangars.add(new SelectedHangar(
                        bestHangarSite.getId(),
                        bestDroneModel.getModelName(),
                        bestHangarSite.getCoordinate()
                ));
                uncoveredPoints.removeAll(bestCoveredPoints);
            } else {
                break; // 无法继续覆盖更多点
            }
        }

        return selectedHangars;
    }

    /**
     * 日志记录方法
     */
    private void log(String message) {
        if (enableLogging) {
            System.out.println("[BnB Algorithm] " + message);
        }
    }
}

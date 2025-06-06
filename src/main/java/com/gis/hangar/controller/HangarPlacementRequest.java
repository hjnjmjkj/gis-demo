package com.gis.hangar.controller;

import java.util.List;

/**
 * 机巢布置请求模型
 */
public class HangarPlacementRequest {
    private List<InspectionPointDTO> inspectionPoints;
    private List<DroneModelDTO> droneModels;
    private String algorithm = "branchandbound"; // 默认使用分支定界算法

    public List<InspectionPointDTO> getInspectionPoints() {
        return inspectionPoints;
    }

    public void setInspectionPoints(List<InspectionPointDTO> inspectionPoints) {
        this.inspectionPoints = inspectionPoints;
    }

    public List<DroneModelDTO> getDroneModels() {
        return droneModels;
    }

    public void setDroneModels(List<DroneModelDTO> droneModels) {
        this.droneModels = droneModels;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * 巡检点DTO
     */
    public static class InspectionPointDTO {
        private String id;
        private double x;
        private double y;
        private boolean canBuildHangar;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public boolean isCanBuildHangar() {
            return canBuildHangar;
        }

        public void setCanBuildHangar(boolean canBuildHangar) {
            this.canBuildHangar = canBuildHangar;
        }
    }

    /**
     * 无人机型号DTO
     */
    public static class DroneModelDTO {
        private String modelName;
        private double rangeKm;

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public double getRangeKm() {
            return rangeKm;
        }

        public void setRangeKm(double rangeKm) {
            this.rangeKm = rangeKm;
        }
    }
}

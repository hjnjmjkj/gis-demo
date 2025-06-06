package com.gis.hangar;

// 无人机型号
public class DroneModel {
    private String modelName;
    private double rangeKm;  // 覆盖半径（公里）

    public DroneModel(String modelName, double rangeKm) {
        this.modelName = modelName;
        this.rangeKm = rangeKm;
    }

    public String getModelName() {
        return modelName;
    }

    public double getRangeKm() {
        return rangeKm;
    }

    @Override
    public String toString() {
        return "DroneModel{" +
                "modelName='" + modelName + '\'' +
                ", rangeKm=" + rangeKm +
                '}';
    }
}

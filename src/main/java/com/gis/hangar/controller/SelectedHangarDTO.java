package com.gis.hangar.controller;

/**
 * 选定机巢的数据传输对象
 */
public class SelectedHangarDTO {
    private String hangarLocationId;
    private String droneModelName;
    private double x;
    private double y;

    public SelectedHangarDTO() {
    }

    public SelectedHangarDTO(String hangarLocationId, String droneModelName, double x, double y) {
        this.hangarLocationId = hangarLocationId;
        this.droneModelName = droneModelName;
        this.x = x;
        this.y = y;
    }

    public String getHangarLocationId() {
        return hangarLocationId;
    }

    public void setHangarLocationId(String hangarLocationId) {
        this.hangarLocationId = hangarLocationId;
    }

    public String getDroneModelName() {
        return droneModelName;
    }

    public void setDroneModelName(String droneModelName) {
        this.droneModelName = droneModelName;
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
}

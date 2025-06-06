package com.gis.hangar;

import org.locationtech.jts.geom.Coordinate;

// 选择的机巢信息
public class SelectedHangar {
    private String hangarLocationId;
    private String droneModelName;
    private Coordinate hangarCoordinate;  // 墨卡托坐标

    public SelectedHangar(String hangarLocationId, String droneModelName, Coordinate hangarCoordinate) {
        this.hangarLocationId = hangarLocationId;
        this.droneModelName = droneModelName;
        this.hangarCoordinate = hangarCoordinate;
    }

    public String getHangarLocationId() {
        return hangarLocationId;
    }

    public String getDroneModelName() {
        return droneModelName;
    }

    public Coordinate getHangarCoordinate() {
        return hangarCoordinate;
    }

    @Override
    public String toString() {
        return "SelectedHangar{" +
                "hangarLocationId='" + hangarLocationId + '\'' +
                ", droneModelName='" + droneModelName + '\'' +
                ", hangarCoordinate=" + hangarCoordinate +
                '}';
    }
}

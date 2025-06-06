package com.gis.hangar;
import org.locationtech.jts.geom.Coordinate;

// 巡检点类 - 使用墨卡托坐标系统
public class InspectionPoint {
    private String id;
    private Coordinate coordinate;
    private boolean canBuildHangar; // 标识是否可以建机巢

    public InspectionPoint(String id, double x, double y) {
        this.id = id;
        this.coordinate = new Coordinate(x, y);  // 使用墨卡托坐标，单位为米
        this.canBuildHangar = true; // 默认可以建机巢
    }

    public InspectionPoint(String id, double x, double y, boolean canBuildHangar) {
        this.id = id;
        this.coordinate = new Coordinate(x, y);  // 使用墨卡托坐标，单位为米
        this.canBuildHangar = canBuildHangar;
    }

    public String getId() {
        return id;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }
    
    public boolean canBuildHangar() {
        return canBuildHangar;
    }
    
    public void setCanBuildHangar(boolean canBuildHangar) {
        this.canBuildHangar = canBuildHangar;
    }

    @Override
    public String toString() {
        return "InspectionPoint{" +
                "id='" + id + '\'' +
                ", coordinate=" + coordinate +
                ", canBuildHangar=" + canBuildHangar +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InspectionPoint that = (InspectionPoint) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

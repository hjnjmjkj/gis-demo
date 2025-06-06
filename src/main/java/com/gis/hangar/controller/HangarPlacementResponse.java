package com.gis.hangar.controller;

import java.util.List;

/**
 * 机巢布置响应模型
 */
public class HangarPlacementResponse {
    private List<SelectedHangarDTO> selectedHangars;
    private int totalPoints;
    private int coveredPoints;
    private double coverageRate;
    private List<String> uncoveredPoints;
    private String geoJSON;

    public List<SelectedHangarDTO> getSelectedHangars() {
        return selectedHangars;
    }

    public void setSelectedHangars(List<SelectedHangarDTO> selectedHangars) {
        this.selectedHangars = selectedHangars;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getCoveredPoints() {
        return coveredPoints;
    }

    public void setCoveredPoints(int coveredPoints) {
        this.coveredPoints = coveredPoints;
    }

    public double getCoverageRate() {
        return coverageRate;
    }

    public void setCoverageRate(double coverageRate) {
        this.coverageRate = coverageRate;
    }

    public List<String> getUncoveredPoints() {
        return uncoveredPoints;
    }

    public void setUncoveredPoints(List<String> uncoveredPoints) {
        this.uncoveredPoints = uncoveredPoints;
    }

    public String getGeoJSON() {
        return geoJSON;
    }

    public void setGeoJSON(String geoJSON) {
        this.geoJSON = geoJSON;
    }
}

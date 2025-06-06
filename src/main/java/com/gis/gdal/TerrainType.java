package com.gis.gdal;

/**
 * 地形类型枚举
 */
public enum TerrainType {
    PLAIN("平原", 1),
    MOUNTAIN("山地", 2),
    LOWLAND("低洼地", 3),
    UNKNOWN("未知", 0);

    private final String name;
    private final int value;

    TerrainType(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    public static TerrainType fromValue(int value) {
        for (TerrainType type : TerrainType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }
}

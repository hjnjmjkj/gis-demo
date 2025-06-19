package com.gis.temperature;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

public class TemperatureGrid2 {

    private static final double MIN_LON = 105.29;
    private static final double MAX_LON = 111.15;
    private static final double MIN_LAT = 31.70;
    private static final double MAX_LAT = 39.59;
    private static final double GRID_RESOLUTION = 0.25;

    public static void main(String[] args) {
        double[][] temperatureGrid = generateTemperatureGrid();
        writeAscFile(temperatureGrid, "D:\\吉奥\\温度\\temperature.asc");
    }

    private static double[][] generateTemperatureGrid() {
        int rows = (int) Math.ceil((MAX_LAT - MIN_LAT) / GRID_RESOLUTION);
        int cols = (int) Math.ceil((MAX_LON - MIN_LON) / GRID_RESOLUTION);

        double[][] grid = new double[rows][cols];
        DecimalFormat df = new DecimalFormat("#.0");

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double lon = MIN_LON + j * GRID_RESOLUTION + GRID_RESOLUTION / 2;
                double lat = MIN_LAT + i * GRID_RESOLUTION + GRID_RESOLUTION / 2;
                double baseTemp = 25.0;
                double latFactor = (lat - MIN_LAT) / (MAX_LAT - MIN_LAT) * 10.0;
                double xianFactor = 0.0;
                double distFromXian = Math.sqrt(
                        Math.pow(lon - 108.95, 2) + Math.pow(lat - 34.27, 2)
                );
                if (distFromXian < 1.0) {
                    xianFactor = (1.0 - distFromXian) * 5.0;
                }
                double temp = baseTemp - latFactor + xianFactor;
                grid[i][j] = Double.parseDouble(df.format(temp));
            }
        }
        return grid;
    }

    // 写asc文件
    private static void writeAscFile(double[][] grid, String filePath) {
        int nrows = grid.length;
        int ncols = grid[0].length;
        double xllcorner = MIN_LON;
        double yllcorner = MIN_LAT;
        double cellsize = GRID_RESOLUTION;
        double nodata = -9999;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("ncols         " + ncols + "\n");
            writer.write("nrows         " + nrows + "\n");
            writer.write("xllcorner     " + xllcorner + "\n");
            writer.write("yllcorner     " + yllcorner + "\n");
            writer.write("cellsize      " + cellsize + "\n");
            writer.write("NODATA_value  " + nodata + "\n");

            // 从上到下写入（asc要求第一行为最北边）
            for (int i = nrows - 1; i >= 0; i--) {
                for (int j = 0; j < ncols; j++) {
                    writer.write(grid[i][j] + (j == ncols - 1 ? "" : " "));
                }
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
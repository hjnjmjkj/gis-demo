package com.gis.terrain;

import com.gis.gdal.TerrainType;
import org.apache.commons.io.FileUtils;
import org.opengis.referencing.operation.TransformException;

import java.io.File;
import java.io.IOException;

public class TerrainMain {
    public static void main(String[] args) throws TransformException, IOException {
        System.out.println("开始处理地形数据...");
        
        TerrainProcessor terrainProcessor = new TerrainProcessor();
        
        // 处理ASC文件并生成带有地形类型的新ASC文件
        //String inputFilePath = "input50x50.asc";
        //String outputFilePath = "D:\\IdeaProjects\\geo\\output50x50.asc";
        
        //terrainProcessor.processAndGenerateTerrainAsc(inputFilePath, outputFilePath);

        String geoJson = terrainProcessor.processTifToGeoJson(TerrainType.PLAIN);
        //将geojson写入文件
        File geoJsonFile = new File("D:\\IdeaProjects\\geo\\output\\terrain_plain.geojson");
        //写入磁盘
        FileUtils.writeStringToFile(geoJsonFile, geoJson, "UTF-8");




        //System.out.println("地形分类完成，ASC文件已生成: " + outputFilePath);
    }
}

package com.gis.terrain;

import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class TerrainService {
    
    private final TerrainProcessor terrainProcessor;
    
    @Autowired
    public TerrainService(TerrainProcessor terrainProcessor) {
        this.terrainProcessor = terrainProcessor;
    }
    
    /**
     * 生成地形分类ASC文件
     */
    public void generateTerrainAscFile() {
        try {
            String inputFilePath = "input50x50.asc";
            String outputFilePath = "D:\\IdeaProjects\\gis\\output50x50.asc";
            terrainProcessor.processAndGenerateTerrainAsc(inputFilePath, outputFilePath);
        } catch (IOException | TransformException e) {
            throw new RuntimeException("处理地形数据失败: " + e.getMessage(), e);
        }
    }
}

package com.gis.terrain;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/terrain")
public class TerrainController {
    
    private final TerrainService terrainService;
    
    @Autowired
    public TerrainController(TerrainService terrainService) {
        this.terrainService = terrainService;
    }
    
    /**
     * 生成地形分类ASC文件
     * @return 处理结果
     */
    @GetMapping(value = "/generate-asc")
    public ResponseEntity<String> generateTerrainAsc() {
        try {
            terrainService.generateTerrainAscFile();
            return ResponseEntity.ok("地形分类ASC文件已生成");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("生成ASC文件失败: " + e.getMessage());
        }
    }
}

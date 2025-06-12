package com.gis.terrain;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/terrain")
@Tag(name = "地形管理", description = "地形数据生成和管理相关接口")
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
    /**
     * 生成地形分类ASC文件
     * @return 处理结果
     */
    @Operation(summary = "生成地形分类ASC文件", description = "根据已有数据生成地形分类ASC格式文件")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "文件生成成功"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @GetMapping(value = "/generate-asc")
    public ResponseEntity<String> generateTerrainAsc(String id) {
        try {
            terrainService.generateTerrainAscFile();
            return ResponseEntity.ok("地形分类ASC文件已生成");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("生成ASC文件失败: " + e.getMessage());
        }
    }
}

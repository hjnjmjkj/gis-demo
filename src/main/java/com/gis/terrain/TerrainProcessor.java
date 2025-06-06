package com.gis.terrain;

import com.gis.gdal.TerrainType;
import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.gce.arcgrid.ArcGridReader;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.operation.TransformException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.List;

@Component
public class TerrainProcessor {

    private final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

    // 地形分类阈值（移除海拔限制，只保留坡度限制）
    private static final double S_PLAIN_MAX = 7.0;    // 平原最大坡度 (度)
    private static final double S_MOUNTAIN_MIN = 8.0;  // 山地最小坡度 (度)

    /**
     * 处理输入ASC文件并生成带有地形类型的ASC文件
     */
    public void processAndGenerateTerrainAsc(String inputFilePath, String outputFilePath) throws IOException, TransformException {
        // 读取ASC文件
        File demFile = new ClassPathResource(inputFilePath).getFile();
        ArcGridReader demReader = new ArcGridReader(demFile);
        GridCoverage2D demCoverage = demReader.read(null);

        // 获取高程数据
        RenderedImage demImage = demCoverage.getRenderedImage();
        Raster demRaster = demImage.getData();
        
        int width = demImage.getWidth();
        int height = demImage.getHeight();
        
        // 创建存储坡度值的数组
        double[][] slopeData = new double[height][width];
        
        // 手动计算坡度
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // 获取周围像素的高程值
                double center = demRaster.getSampleDouble(x, y, 0);
                double left = demRaster.getSampleDouble(x - 1, y, 0);
                double right = demRaster.getSampleDouble(x + 1, y, 0);
                double top = demRaster.getSampleDouble(x, y - 1, 0);
                double bottom = demRaster.getSampleDouble(x, y + 1, 0);
                
                // 计算X和Y方向的高程梯度
                double dzdx = (right - left) / 2.0; // X 方向差分
                double dzdy = (bottom - top) / 2.0; // Y 方向差分
                
                // 计算坡度（角度）
                double slope = Math.toDegrees(Math.atan(Math.sqrt(dzdx * dzdx + dzdy * dzdy)));
                slopeData[y][x] = slope;
            }
        }
        
        // 边缘像素的坡度设置为相邻像素的值
        for (int y = 0; y < height; y++) {
            if (y == 0 || y == height - 1) {
                for (int x = 0; x < width; x++) {
                    slopeData[y][x] = (y == 0 && y + 1 < height) ? slopeData[y + 1][x] : 
                                       (y == height - 1 && y - 1 >= 0) ? slopeData[y - 1][x] : 0;
                }
            } else {
                slopeData[y][0] = slopeData[y][1];
                slopeData[y][width - 1] = slopeData[y][width - 2];
            }
        }
        
        // 创建地形分类栅格，使用TerrainType的值
        int[][] terrainGrid = new int[height][width];


        
        // 进行地形分类
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double elevation = demRaster.getSampleDouble(x, y, 0); // 获取高程值
                double slope = slopeData[y][x];  // 获取计算的坡度值

                TerrainType terrainType = classifyTerrain(x, y, elevation, slope, demRaster, width, height);
                terrainGrid[y][x] = terrainType.getValue(); // 存储地形类型的值
            }
        }
        
        // 获取ASC文件的元数据
        GridEnvelope gridRange = demCoverage.getGridGeometry().getGridRange();
        DirectPosition2D worldPos = (DirectPosition2D) demCoverage.getGridGeometry().gridToWorld(new GridCoordinates2D(0, height-1));
        double xllcorner = worldPos.x;
        double yllcorner = worldPos.y;

        // 生成ASC文件
        try (FileWriter writer = new FileWriter(outputFilePath)) {
            // 写入头部信息
            writer.write("NCOLS " + width + "\n");
            writer.write("NROWS " + height + "\n");
            writer.write("XLLCORNER " + xllcorner + "\n");
            writer.write("YLLCORNER " + yllcorner + "\n");
            writer.write("CELLSIZE " + 10 + "\n");
            writer.write("NODATA_VALUE -9999\n");
            
            // 写入数据，从上到下，从左到右
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    writer.write(terrainGrid[y][x] + " ");
                }
                writer.write("\n");
            }
        }
        
        // 释放资源
        demReader.dispose();
    }

    /**
     * 处理TIF文件并生成GeoJSON
     * @return GeoJSON字符串
     */
    public String processTifToGeoJson(TerrainType t) throws IOException, TransformException {
        // 读取TIF文件
        File demFile = new ClassPathResource("input50x50.asc").getFile();
        ArcGridReader demReader = new ArcGridReader(demFile);
        GridCoverage2D demCoverage = demReader.read(null);

        // 获取高程数据
        RenderedImage demImage = demCoverage.getRenderedImage();
        Raster demRaster = demImage.getData();
        
        int width = demImage.getWidth();
        int height = demImage.getHeight();
        
        // 创建存储坡度值的数组
        double[][] slopeData = new double[height][width];
        
        // 手动计算坡度
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // 获取周围像素的高程值
                double center = demRaster.getSampleDouble(x, y, 0);
                double left = demRaster.getSampleDouble(x - 1, y, 0);
                double right = demRaster.getSampleDouble(x + 1, y, 0);
                double top = demRaster.getSampleDouble(x, y - 1, 0);
                double bottom = demRaster.getSampleDouble(x, y + 1, 0);
                
                // 计算X和Y方向的高程梯度
                double dzdx = (right - left) / 2.0; // X 方向差分
                double dzdy = (bottom - top) / 2.0; // Y 方向差分
                
                // 计算坡度（角度）
                double slope = Math.toDegrees(Math.atan(Math.sqrt(dzdx * dzdx + dzdy * dzdy)));
                slopeData[y][x] = slope;
            }
        }
        
        // 边缘像素的坡度设置为相邻像素的值
        for (int y = 0; y < height; y++) {
            if (y == 0 || y == height - 1) {
                for (int x = 0; x < width; x++) {
                    slopeData[y][x] = (y == 0 && y + 1 < height) ? slopeData[y + 1][x] : 
                                       (y == height - 1 && y - 1 >= 0) ? slopeData[y - 1][x] : 0;
                }
            } else {
                slopeData[y][0] = slopeData[y][1];
                slopeData[y][width - 1] = slopeData[y][width - 2];
            }
        }
        
        // 创建地形分类栅格
        TerrainType[][] terrainGrid = new TerrainType[height][width];
        
        // 进行地形分类
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double elevation = demRaster.getSampleDouble(x, y, 0); // 获取高程值
                double slope = slopeData[y][x];  // 获取计算的坡度值
                
                TerrainType terrainType = classifyTerrain(x, y, elevation, slope, demRaster, width, height);
                terrainGrid[y][x] = terrainType;
            }
        }
        
        // 为每种地形类型创建分区
        Map<TerrainType, List<List<int[]>>> terrainRegions = new HashMap<>();
        for (TerrainType type : TerrainType.values()) {
            if (type != TerrainType.UNKNOWN) {
                terrainRegions.put(type, new ArrayList<>());
            }
        }
        
        // 用于标记已处理的像素
        boolean[][] visited = new boolean[height][width];
        
        // 使用洪水填充算法识别连续区域
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[y][x] && terrainGrid[y][x] != TerrainType.UNKNOWN) {
                    TerrainType currentType = terrainGrid[y][x];
                    List<int[]> region = new ArrayList<>();
                    
                    // 洪水填充算法
                    Queue<int[]> queue = new LinkedList<>();
                    queue.add(new int[]{x, y});
                    visited[y][x] = true;
                    
                    while (!queue.isEmpty()) {
                        int[] point = queue.poll();
                        int px = point[0];
                        int py = point[1];
                        region.add(new int[]{px, py});
                        
                        // 检查四个相邻像素
                        int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
                        for (int[] dir : directions) {
                            int nx = px + dir[0];
                            int ny = py + dir[1];
                            
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height && 
                                !visited[ny][nx] && terrainGrid[ny][nx] == currentType) {
                                queue.add(new int[]{nx, ny});
                                visited[ny][nx] = true;
                            }
                        }
                    }
                    
                    // 只有当区域足够大时才添加（过滤小噪点）
                    if (region.size() > 5) {
                        terrainRegions.get(currentType).add(region);
                    }
                }
            }
        }

        DirectPosition2D worldPos = (DirectPosition2D) demCoverage.getGridGeometry().gridToWorld(new GridCoordinates2D(0, height-1));
        double xllcorner = worldPos.x;
        double yllcorner = worldPos.y;
        try (FileWriter writer = new FileWriter("D:\\IdeaProjects\\geo\\output\\slopeData50x50.asc")) {
            // 写入头部信息
            writer.write("NCOLS " + width + "\n");
            writer.write("NROWS " + height + "\n");
            writer.write("XLLCORNER " + xllcorner + "\n");
            writer.write("YLLCORNER " + yllcorner + "\n");
            writer.write("CELLSIZE " + 10 + "\n");
            writer.write("NODATA_VALUE -9999\n");

            // 写入数据，从上到下，从左到右
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    writer.write(slopeData[y][x] + " ");
                }
                writer.write("\n");
            }
        }

        // 生成ASC文件
        try (FileWriter writer = new FileWriter("D:\\IdeaProjects\\geo\\output\\output50x50.asc")) {
            // 写入头部信息
            writer.write("NCOLS " + width + "\n");
            writer.write("NROWS " + height + "\n");
            writer.write("XLLCORNER " + xllcorner + "\n");
            writer.write("YLLCORNER " + yllcorner + "\n");
            writer.write("CELLSIZE " + 10 + "\n");
            writer.write("NODATA_VALUE -9999\n");

            // 写入数据，从上到下，从左到右
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    writer.write(terrainGrid[y][x].getValue() + " ");
                }
                writer.write("\n");
            }
        }
        
        // 创建SimpleFeatureType
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("TerrainRegion");
        typeBuilder.add("geometry", Polygon.class);
        typeBuilder.add("type", String.class);
        typeBuilder.add("name", String.class);
        typeBuilder.add("area", Double.class);
        SimpleFeatureType featureType = typeBuilder.buildFeatureType();
        
        // 创建FeatureCollection
        DefaultFeatureCollection featureCollection = new DefaultFeatureCollection();
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
        
        // 为每种地形类型的每个区域创建多边形
        for (Map.Entry<TerrainType, List<List<int[]>>> entry : terrainRegions.entrySet()) {
            TerrainType type = entry.getKey();
            if(!type.equals(t)){
                continue;
            }
            List<List<int[]>> regions = entry.getValue();
            
            for (List<int[]> region : regions) {
                // 提取区域边界 - 使用JTS方法
                Geometry boundaryGeometry = extractBoundaryUsingJTS(region, demCoverage);
                
                // 如果返回的几何对象不是多边形，跳过
                if (!(boundaryGeometry instanceof Polygon)) {
                    continue;
                }
                
                Polygon polygon = (Polygon) boundaryGeometry;
                
                // 计算面积
                double area = polygon.getArea();
                
                // 设置属性并添加到FeatureCollection
                featureBuilder.add(polygon);
                featureBuilder.add(type.name());
                featureBuilder.add(type.getName());
                featureBuilder.add(area);
                SimpleFeature feature = featureBuilder.buildFeature(null);
                featureCollection.add(feature);
            }
        }
        
        // 转换为GeoJSON
        FeatureJSON featureJSON = new FeatureJSON();
        StringWriter writer = new StringWriter();
        if(featureCollection.size()==0){
            return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }
        featureJSON.writeFeatureCollection(featureCollection, writer);
        
        // 释放资源
        demReader.dispose();
        
        return writer.toString();
    }
    
    /**
     * 使用JTS库提取区域边界
     */
    private Geometry extractBoundaryUsingJTS(List<int[]> region, GridCoverage2D coverage) throws TransformException {
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        
        // 将栅格点转换为坐标
        List<Coordinate> coordinates = new ArrayList<>();
        for (int[] point : region) {
            int x = point[0];
            int y = point[1];
            
            // 将栅格坐标转为地理坐标
            GridCoordinates2D gridPos = new GridCoordinates2D(x, y);
            DirectPosition2D worldPos = (DirectPosition2D) coverage.getGridGeometry().gridToWorld(gridPos);
            coordinates.add(new Coordinate(worldPos.x, worldPos.y));
        }
        
        // 生成凸包或凹包(根据需要选择)
        Coordinate[] coordArray = coordinates.toArray(new Coordinate[0]);
        ConvexHull convexHull = new ConvexHull(coordArray, geometryFactory);
        Geometry hull = convexHull.getConvexHull();
        
        // 如果需要更精细的轮廓，可考虑使用凹壳(需要额外依赖)
        // ConcaveHull concaveHull = new ConcaveHull(geometryFactory.createMultiPoint(coordArray), 0.9);
        // Geometry hull = concaveHull.getConcaveHull();
        
        return hull;
    }
    
    /**
     * 在点集中找到距离给定点最近的点
     */
    private int[] findClosestPoint(int[] point, List<int[]> points) {
        int[] closest = null;
        double minDist = Double.MAX_VALUE;
        
        for (int[] p : points) {
            double dist = Math.sqrt(Math.pow(p[0] - point[0], 2) + Math.pow(p[1] - point[1], 2));
            if (dist < minDist) {
                minDist = dist;
                closest = p;
            }
        }
        
        return closest;
    }
    
    /**
     * 根据坡度和周围环境对地形进行分类（移除海拔限制）
     */
    private TerrainType classifyTerrain(int x, int y, double elevation, double slope, Raster demRaster, int width, int height) {
        if (slope <= S_PLAIN_MAX) {
            return TerrainType.PLAIN; // 平原（仅基于坡度判断）
        } else if (slope > S_PLAIN_MAX) {
            return TerrainType.MOUNTAIN; // 山地（仅基于坡度判断）
        }
        
        // 坡度介于平原和山之间的区域，默认归类为平原
        return TerrainType.PLAIN;
    }
}

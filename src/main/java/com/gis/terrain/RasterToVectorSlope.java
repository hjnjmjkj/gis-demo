package com.gis.terrain;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.ProcessException;
import org.geotools.process.raster.PolygonExtractionProcess;
import org.geotools.referencing.CRS;
//import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import javax.media.jai.Interpolation;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ConstantDescriptor;
import javax.media.jai.operator.ScaleDescriptor;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RasterToVectorSlope {

    /*public static void main(String[] args) {
        File slopeRasterFile = new File("path/to/your/slope_degrees.tif"); // 输入坡度栅格文件
        File outputGeoJSONFile = new File("path/to/output/slope_lt_7deg.geojson");
        double slopeThreshold = 7.0; // 坡度阈值

        try {
            // 1. 读取坡度栅格数据
            AbstractGridFormat format = GridFormatFinder.findFormat(slopeRasterFile);
            GridCoverage2DReader reader = format.getReader(slopeRasterFile);
            GridCoverage2D slopeCoverage = reader.read(null);
            RenderedImage slopeImage = slopeCoverage.getRenderedImage();
            CoordinateReferenceSystem crs = slopeCoverage.getCoordinateReferenceSystem2D();

            // 2. 创建二值掩膜 (坡度 < 7度为1，否则为0)
            GridCoverage2D binaryCoverage = createBinaryMask(slopeCoverage, slopeThreshold);
            RenderedImage binaryImage = binaryCoverage.getRenderedImage();

            // (可选) 打印二值图像信息，用于调试
            System.out.println("Binary Image Width: " + binaryImage.getWidth());
            System.out.println("Binary Image Height: " + binaryImage.getHeight());
            System.out.println("Binary Image MinX: " + binaryImage.getMinX());
            System.out.println("Binary Image MinY: " + binaryImage.getMinY());

            // 3. 矢量化 (Polygonize)
            // GeoTools的PolygonExtractionProcess期望输入是单波段的二值图像 (0和1)
            // 确保binaryImage是合适的类型
            PolygonExtractionProcess polygonizer = new PolygonExtractionProcess();
            SimpleFeatureCollection vectorizedFeatures = polygonizer.execute(binaryCoverage, 0, true, null, null, null, null);

            if (vectorizedFeatures == null || vectorizedFeatures.isEmpty()) {
                System.out.println("No features were vectorized. Check the binary mask.");
                return;
            }
            System.out.println("Number of vectorized features: " + vectorizedFeatures.size());


            // 4. 创建 FeatureCollection (如果需要进一步处理或转换属性)
            // vectorizedFeatures已经是SimpleFeatureCollection了，可以直接使用
            // 如果需要修改属性或schema，可以迭代处理
            List<SimpleFeature> features = new ArrayList<>();
            SimpleFeatureType originalSchema = vectorizedFeatures.getSchema();
            SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
            typeBuilder.setName("SlopeArea");
            typeBuilder.setCRS(crs); // 设置正确的CRS
            typeBuilder.add("geometry", MultiPolygon.class); // 或者 Polygon.class，取决于矢量化结果
            typeBuilder.add("slope_condition", String.class);
            SimpleFeatureType newSchema = typeBuilder.buildFeatureType();

            try (org.geotools.feature.FeatureIterator<SimpleFeature> iterator = vectorizedFeatures.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature originalFeature = iterator.next();
                    Geometry geom = (Geometry) originalFeature.getDefaultGeometry();
                    // PolygonExtractionProcess 可能会产生非常小的多边形或无效几何
                    // 可以添加几何清理步骤
                    if (geom != null && geom.isValid() && geom.getArea() > 0.000001) { // 示例：过滤掉非常小的区域
                        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(newSchema);
                        featureBuilder.add(geom);
                        featureBuilder.add("slope_lt_7_deg");
                        features.add(featureBuilder.buildFeature(null));
                    }
                }
            }
            SimpleFeatureCollection finalFeatureCollection = new ListFeatureCollection(newSchema, features);


            // 5. 导出为 GeoJSON
            if (finalFeatureCollection.isEmpty()) {
                System.out.println("No valid features to write to GeoJSON.");
                return;
            }
            //exportToGeoJSON(finalFeatureCollection, outputGeoJSONFile);

            System.out.println("GeoJSON file created successfully at: " + outputGeoJSONFile.getAbsolutePath());

        } catch (IOException | ProcessException e) {
            e.printStackTrace();
        } finally {
            // 清理资源 (如果reader需要关闭)
        }
    }*/

    /**
     * 创建一个二值掩膜栅格，其中坡度小于阈值的像元为1，否则为0。
     */
/*    private static GridCoverage2D createBinaryMask(GridCoverage2D sourceCoverage, double threshold) {
        RenderedImage sourceImage = sourceCoverage.getRenderedImage();
        WritableRaster sourceRaster = sourceImage.getWritableTile(sourceImage.getMinTileX(), sourceImage.getMinTileY());
        // 如果是多波段图像，确保你操作的是正确的波段
        // 假设坡度栅格是单波段的

        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        // 创建一个新的WritableRaster用于二值图像，类型为BYTE (0或1)
        WritableRaster binaryRaster = java.awt.image.Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, 1, null);

        double[] pixelValue = new double[sourceRaster.getNumBands()]; // 用于读取原始像素值

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sourceRaster.getPixel(x + sourceRaster.getMinX(), y + sourceRaster.getMinY(), pixelValue);
                // 假设坡度值在第一个波段
                if (pixelValue[0] < threshold && pixelValue[0] != sourceCoverage.getSampleDimension(0).getNoDataValues()[0]) { // 检查NoData
                    binaryRaster.setSample(x, y, 0, 1); // 小于阈值，设为1
                } else {
                    binaryRaster.setSample(x, y, 0, 0); // 大于等于阈值或NoData，设为0
                }
            }
        }

        GridCoverageFactory factory = new GridCoverageFactory();
        return factory.create("binaryMask", binaryRaster, sourceCoverage.getEnvelope());
    }


    *//**
     * 将 SimpleFeatureCollection 导出为 GeoJSON 文件。
     *//*
    private static void exportToGeoJSON(SimpleFeatureCollection featureCollection, File geoJSONFile) throws IOException {
        GeoJSONDataStore geoJsonStore = new GeoJSONDataStore(geoJSONFile.toURI().toURL());
        geoJsonStore.createSchema(featureCollection.getSchema());

        Transaction transaction = new DefaultTransaction("create");
        try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer =
                     geoJsonStore.getFeatureWriterAppend(featureCollection.getSchema().getTypeName(), transaction)) {
            try (org.geotools.feature.FeatureIterator<SimpleFeature> iterator = featureCollection.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    SimpleFeature newFeature = writer.next();
                    newFeature.setAttributes(feature.getAttributes());
                    ((Geometry) newFeature.getDefaultGeometry()).setUserData(feature.getDefaultGeometryProperty().getUserData()); // 复制CRS等
                    writer.write();
                }
            }
            transaction.commit();
        } catch (Exception e) {
            transaction.rollback();
            throw new IOException("Error writing GeoJSON file", e);
        } finally {
            transaction.close();
            geoJsonStore.dispose();
        }
    }*/
}
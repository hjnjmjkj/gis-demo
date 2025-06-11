package com.gis.gdal;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class TiffUtil {
    public static boolean importTiffToPostGIS(String tiffFilePath, String jdbcUrl, String username, String password,
                                              String tableName, int srid) {
        try {
            // 注册 GDAL 驱动
            gdal.AllRegister();

            // 打开 TIFF 文件
            Dataset dataset = gdal.Open(tiffFilePath, gdalconstConstants.GA_ReadOnly);
            if (dataset == null) {
                System.err.println("无法打开 TIFF 文件: " + tiffFilePath);
                return false;
            }

            // 获取栅格信息
            int width = dataset.getRasterXSize();
            int height = dataset.getRasterYSize();
            double[] geoTransform = dataset.GetGeoTransform();

            // 构建 SQL 创建表语句
            StringBuilder createTableSQL = new StringBuilder();
            createTableSQL.append("CREATE TABLE IF NOT EXISTS ").append(tableName)
                    .append(" (rid SERIAL PRIMARY KEY, rast raster);");

            // 构建 SQL 插入语句 - 使用 raster2pgsql 方式
            StringBuilder insertSQL = new StringBuilder();
            insertSQL.append("INSERT INTO ").append(tableName)
                    .append(" (rast) SELECT rast FROM st_readrast(?, ?);");

            // 连接数据库执行 SQL
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                 Statement stmt = conn.createStatement()) {

                // 确保 PostGIS 扩展已启用
                stmt.execute("CREATE EXTENSION IF NOT EXISTS postgis;");
                // 确保 PostGIS 栅格扩展已启用
                stmt.execute("CREATE EXTENSION IF NOT EXISTS postgis_raster;");

                // 创建表
                stmt.execute(createTableSQL.toString());

                // 保存 TIFF 文件到临时文件，以便 PostGIS 读取
                File tempFile = File.createTempFile("gdal_import_", ".tiff");
                dataset.GetDriver().CopyFiles(tiffFilePath, tempFile.getAbsolutePath());

                // 使用栅格导入函数
                String rasterImportSQL = "INSERT INTO " + tableName + " (rast) " +
                        "SELECT rast FROM ST_ReadRaster('" + tempFile.getAbsolutePath() + "', " + srid + ");";
                stmt.execute(rasterImportSQL);

                // 清理临时文件
                tempFile.delete();

                // 设置索引和约束的 SQL
                String addConstraintsSQL = "SELECT AddRasterConstraints('" +
                        tableName.split("\\.")[0] + "', '" +
                        tableName.split("\\.")[1] + "', 'rast');";

                String createIndexSQL = "CREATE INDEX " + tableName.replace(".", "_") +
                        "_idx ON " + tableName +
                        " USING gist (ST_ConvexHull(rast));";

                // 添加空间索引和约束
                stmt.execute(addConstraintsSQL);
                stmt.execute(createIndexSQL);

                System.out.println("TIFF 文件成功导入 PostGIS: " + tableName);
                return true;
            }
        } catch (Exception e) {
            System.err.println("导入 TIFF 到 PostGIS 时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        // 导入TIFF到PostGIS
        String tiffFilePath = "D:\\吉奥\\陕西\\咸阳\\output\\xianyang3857.tiff";
        String jdbcUrl = "jdbc:postgresql://172.27.234.162:5432/postgres";
        String username = "postgres";
        String password = "postgres";
        String tableName = "test.xianyang_dem";

        boolean imported = TiffUtil.importTiffToPostGIS(
                tiffFilePath, jdbcUrl, username, password, tableName, 3857);

        if (imported) {
            System.out.println("TIFF文件成功导入到PostGIS数据库");
        } else {
            System.err.println("TIFF文件导入PostGIS数据库失败");
        }
    }
}

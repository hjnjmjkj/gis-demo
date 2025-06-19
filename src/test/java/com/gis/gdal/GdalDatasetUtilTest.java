package com.gis.gdal;


import com.gis.mapper.GeoEntityMapper;
import com.gis.service.GeoService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GdalDatasetUtilTest {
    @Resource
    private GeoService geoService;

    //初始化一个list里面保护xianyang、xian、shanxi
    public static final String[] citys = {
            "ankang",
            "baoji",
            "hanzhong",
            "xianyang",
            "shangluo",
            "tongchuan",
            "weinan",
            "xian",
            "yanan",
            "yulin"
    };

    public static final Map<String, String> CITY_CODE_MAP = new HashMap<String, String>() {{
        put("ankang", "610900");    // 安康市
        put("baoji", "610300");     // 宝鸡市
        put("hanzhong", "610700");  // 汉中市
        put("xianyang", "610400");  // 咸阳市
        put("shangluo", "611000");  // 商洛市
        put("tongchuan", "610200"); // 铜川市
        put("weinan", "610500");    // 渭南市
        put("xian", "610100");      // 西安市
        //put("shanxi", "610000");    // 陕西省（非市级，使用省级代码610000）
        put("yanan", "610600");     // 延安市
        put("yulin", "610800");     // 榆林市
    }};

    /**
     * 向量化
     */
    @Test
    public void terrainGeoJSONExporterTest(){
        SlopeAnalysis slopeAnalysis = new SlopeAnalysis();
        TerrainGeoJSONExporter exporter = new TerrainGeoJSONExporter(slopeAnalysis);
        String path = "D:\\吉奥\\陕西\\out\\陕西地形tiff3857\\";
        for (String city : citys) {
            // 导出平原区域
            exporter.exportTerrainToGeoJSON(path +city +"3857.tiff",
                    path + "geojson\\" + city + "3857.json");
        }
    }

    /**
     * 添加区域编码
     */
    @Test
    public void addFieldToGeoJSON() {
        String path = "D:\\吉奥\\陕西\\out\\陕西地形tiff3857\\geojson\\";
        for (Map.Entry<String, String> stringStringEntry : CITY_CODE_MAP.entrySet()) {
            String city = stringStringEntry.getKey();
            String code = stringStringEntry.getValue();
            GdalDatasetUtil.addFieldToGeoJSON(path + city + "3857.json","areacode",code);
        }
    }

    /**
     * 删除name为0的要素
     */
    @Test
    public void removeFeaturesWithNameZero(){
        for (String city : citys) {
            GdalDatasetUtil.removeFeaturesWithNameZero("D:\\吉奥\\陕西\\out\\陕西地形tiff3857\\geojson\\"+city+"3857.json");
            System.out.println(city);
        }
    }

    /**
     * 计算最大值最小值
     */
    @Test
    public void fillGeoJsonWithElevationStatsTest(){
        for (String city : citys) {
            geoService.fillGeoJsonWithElevationStats("D:\\吉奥\\陕西\\out\\陕西地形tiff3857\\geojson\\" + city + "3857.json");
            System.out.println(city);
        }
    }

    /**
     * 洼地geojson处理
     */
    @Test
    public void fillShanxiGeoJsonWithElevationStatsTest(){
        geoService.fillGeoJsonWithElevationStats("D:\\吉奥\\陕西\\input\\平滑处理\\shanxi3857_90_simplified.json");
        GdalDatasetUtil.addFieldToGeoJSON("D:\\吉奥\\陕西\\input\\平滑处理\\shanxi3857_90_simplified.json","areacode", "610000");
        GdalDatasetUtil.removePropertiesFromGeoJSON("D:\\吉奥\\陕西\\input\\平滑处理\\shanxi3857_90_simplified.json",
                "D:\\吉奥\\陕西\\input\\平滑处理\\shanxi3857_90_simplified2.json",
                new String[]{"area_km2","id"});
    }

    @Test
    public void splitGeoJsonByField(){
        for (String city : citys) {
            GdalDatasetUtil.splitGeoJsonByFieldWithoutName("D:\\吉奥\\陕西\\out\\陕西地形tiff3857\\geojson\\" + city + "3857.json",
                    "D:\\吉奥\\陕西\\out\\陕西地形tiff3857\\geojson\\", city,"name");
        }
    }

    @Test
    public void printFeatureOfGeoJson(){
        GdalDatasetUtil.printFeatureOfGeoJson("D:\\吉奥\\陕西\\out\\陕西地形tiff3857\\geojson\\baoji3857.json");
    }

}
package com.gis.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GeoServiceTest {
    @Resource
    private GeoService geoService;
    @Test
    public void fillGeoJsonWithElevationStatsTest() {
        geoService.fillGeoJsonWithElevationStats("D:\\吉奥\\陕西\\out\\陕西地形tiff3857\\geojson\\baoji3857.json");
    }
}
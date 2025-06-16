package com.gis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gis.entity.GeoEntity; // 修正导入路径
import com.gis.entity.SummaryStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GeoEntityMapper extends BaseMapper<GeoEntity> {
    // 可以添加自定义的空间查询方法

    SummaryStats getSummaryStats(@Param("geojson") String geojson);
}

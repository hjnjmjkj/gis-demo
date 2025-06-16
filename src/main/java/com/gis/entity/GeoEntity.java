package com.gis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gis.handler.GeometryTypeHandler; // 修正导入路径
import lombok.Data;
import org.locationtech.jts.geom.Geometry;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "geo_entity")
public class GeoEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    @TableField(value = "geometry", typeHandler = GeometryTypeHandler.class)
    private Geometry geometry;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

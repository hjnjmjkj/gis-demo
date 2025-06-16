package com.gis.handler;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.postgis.PGgeometry;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(Geometry.class)
public class GeometryTypeHandler extends BaseTypeHandler<Geometry> {
    private final WKTReader wkbReader = new WKTReader();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Geometry parameter, JdbcType jdbcType) throws SQLException {
        String wkt = ps.toString();
        PGgeometry pGgeometry = new PGgeometry();
        ps.setObject(i, pGgeometry, java.sql.Types.OTHER);
    }

    @Override
    public Geometry getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toGeometry(rs.getString(columnName));
    }

    @Override
    public Geometry getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toGeometry(rs.getString(columnIndex));
    }

    @Override
    public Geometry getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toGeometry(cs.getString(columnIndex));
    }

    private Geometry toGeometry(String value) {
        try {
            if(StringUtils.isBlank(value)){
                return null;
            }
            PGgeometry pGgeometry = new PGgeometry(value);
            if(pGgeometry == null) {
                return null;
            }
            return wkbReader.read(pGgeometry.toString());
        } catch (ParseException e) {
            throw new RuntimeException("Error parsing geometry", e);
        } catch (SQLException e) {
            throw new RuntimeException("Error parsing geometry", e);
        }
    }

}

package com.kaoyan.assistant.common;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcValues {

    private JdbcValues() {
    }

    public static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new SQLException("Column " + column + " is not numeric: " + value.getClass().getName());
    }
}

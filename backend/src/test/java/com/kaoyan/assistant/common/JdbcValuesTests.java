package com.kaoyan.assistant.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcValuesTests {

    @Test
    void convertsJdbcDecimalToNullableDouble() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("ratio")).thenReturn(new BigDecimal("1.25"));

        assertThat(JdbcValues.nullableDouble(resultSet, "ratio")).isEqualTo(1.25d);
    }

    @Test
    void preservesNullNumericValues() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("ratio")).thenReturn(null);

        assertThat(JdbcValues.nullableDouble(resultSet, "ratio")).isNull();
    }
}

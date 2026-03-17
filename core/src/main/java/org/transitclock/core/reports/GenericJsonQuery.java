/* (C)2023 */
package org.transitclock.core.reports;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.transitclock.domain.GenericQuery;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
public class GenericJsonQuery extends GenericQuery {

    private final StringBuilder strBuilder;
    private final List<String> columnNames;
    private boolean firstRow;

    private GenericJsonQuery(String agencyId) throws SQLException {
        super(agencyId);
        strBuilder = new StringBuilder();
        columnNames = new ArrayList<>();
        firstRow = true;
    }

    /**
     * Does SQL query and returns JSON formatted results.
     */
    public static String getJsonString(String agencyId, String sql, Object... parameters) {
        // Add the rows from the query to the JSON string
        try {
            GenericJsonQuery query = new GenericJsonQuery(agencyId);

            // Start the JSON
            query.strBuilder.append("{\"data\": [\n");

            query.doQuery(sql, parameters);

            // Finish up the JSON
            query.strBuilder.append("]}");

            return query.strBuilder.toString();
        } catch (SQLException e) {
            return e.getMessage();
        }
    }

    @Override
    protected void addColumn(String columnName, int type) {
        // Keep track of names of all columns
        columnNames.add(columnName);
    }

    private void addRowElement(int i, double value) {
        strBuilder.append(value);
    }

    private void addRowElement(int i, long value) {
        strBuilder.append(value);
    }

    private void addRowElement(int i, String value) {
        strBuilder.append("\"").append(value).append("\"");
    }

    private void addStringWithoutDQuotation(int i, String value) {
        strBuilder.append(value);
    }

    private void addRowElement(int i, Timestamp value) {
        strBuilder.append("\"").append(value).append("\"");
    }

    @Override
    @SneakyThrows(SQLException.class)
    protected void addRow(List<Object> values) {
        if (!firstRow) {
            strBuilder.append(",\n");
        }
        firstRow = false;

        strBuilder.append('{');

        // Add each cell in the row
        boolean firstElementInRow = true;
        for (int i = 0; i < values.size(); ++i) {
            Object o = values.get(i);
            // Can't output null attributes
            if (o == null) continue;

            if (!firstElementInRow) strBuilder.append(",");
            firstElementInRow = false;

            // Output name of attribute
            strBuilder.append("\"").append(columnNames.get(i)).append("\":");

            // Output value of attribute
            if (o instanceof Double || o instanceof Float) {
                addRowElement(i, ((double) o));
            } else if (o instanceof Number) {
                addRowElement(i, ((Number) o).longValue());
            } else if (o instanceof String) {
                if (o.toString().startsWith("["))
                    addStringWithoutDQuotation(i, (String) o);
                else addRowElement(i, (String) o);
            } else if (o instanceof Timestamp) {
                addRowElement(i, ((Timestamp) o));
            } else if (o instanceof Date) {
                addRowElement(i, String.valueOf(o));
            } else if (o instanceof Array arr) {
                String[] strArray = Arrays.stream((Object[]) arr.getArray())
                        .map(String::valueOf)
                        .toArray(String[]::new);
                addStringWithoutDQuotation(i, Arrays.toString(strArray));
            }
        }
        strBuilder.append('}');
    }
}

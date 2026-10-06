package dev.mesbase.server.managed;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** JDBC 元数据专用 Mapper；无业务表 CRUD、无写 SQL，不参与业务 MyBatis 分页。 */
public class DatasourceDdlExtraMapper {
    public List<String> selectTableDefinitions(DataSource source) throws SQLException {
        List<String> definitions = new ArrayList<>();
        try (Connection connection = source.getConnection()) {
            List<String> tables = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME LIMIT 1001")) {
                statement.setQueryTimeout(30);
                try (ResultSet rows = statement.executeQuery()) { while (rows.next()) tables.add(rows.getString(1)); }
            }
            if(tables.size()>1000)throw new SQLException("Too many tables for one DDL snapshot");
            int characters=0;
            // SHOW CREATE 不能绑定标识符。名称仅来自数据库元数据，且按 MySQL 标识符转义。
            for (String table : tables) {
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(30);
                    try (ResultSet rows = statement.executeQuery("SHOW CREATE TABLE `"+table.replace("`","``")+"`")) {
                        if (!rows.next()) throw new SQLException("Missing table definition: "+table);
                        String ddl=rows.getString(2);characters+=ddl.length();
                        if(characters>16*1024*1024)throw new SQLException("DDL snapshot exceeds size limit");
                        definitions.add(ddl);
                    }
                }
            }
        }
        return definitions;
    }
}

package com.hou.exam.config;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 给已有数据库补新增的字段。schema.sql 的 CREATE TABLE IF NOT EXISTS 不会改动已存在的表。
 * 注入 JdbcTemplate 的 Bean 会排在 schema.sql 执行之后创建，且早于 Web 服务启动。
 */
@Component
public class SchemaUpgrade {

    public SchemaUpgrade(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "question", "images");
        addColumnIfMissing(jdbc, "submission_answer", "images");
    }

    private void addColumnIfMissing(JdbcTemplate jdbc, String table, String column) {
        try {
            jdbc.queryForList("SELECT " + column + " FROM " + table + " WHERE 1 = 0");
        } catch (DataAccessException e) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " TEXT NULL");
        }
    }
}

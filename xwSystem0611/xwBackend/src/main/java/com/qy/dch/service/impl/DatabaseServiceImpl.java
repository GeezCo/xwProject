package com.qy.dch.service.impl;

import com.qy.dch.dto.FieldInfoDTO;
import com.qy.dch.request.AddFieldRequest;
import com.qy.dch.request.ModifyFieldRequest;
import com.qy.dch.request.DeleteFieldRequest;
import com.qy.dch.service.DatabaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据库管理服务实现类
 */
@Slf4j
@Service
public class DatabaseServiceImpl implements DatabaseService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    /** 允许操作的表白名单 */
    private static final Set<String> ALLOWED_TABLES = new HashSet<>(Arrays.asList("origin_text"));

    /** 核心字段（禁止删除/修改） */
    private static final Set<String> CORE_FIELDS = new HashSet<>(Arrays.asList("sid", "title", "content", "times", "type"));

    // __CONTINUE_1__

    /**
     * 获取表结构信息
     */
    @Override
    public List<FieldInfoDTO> getTableStructure(String tableName) {
        validateTableName(tableName);

        String dbName = extractDatabaseName();
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, " +
                     "COLUMN_DEFAULT, IS_NULLABLE, COLUMN_COMMENT, COLUMN_KEY " +
                     "FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                     "ORDER BY ORDINAL_POSITION";

        return jdbcTemplate.query(sql, new Object[]{dbName, tableName}, (rs, rowNum) -> {
            FieldInfoDTO field = new FieldInfoDTO();
            field.setFieldName(rs.getString("COLUMN_NAME"));
            field.setDataType(rs.getString("DATA_TYPE"));
            field.setMaxLength(rs.getInt("CHARACTER_MAXIMUM_LENGTH"));
            if (rs.wasNull()) {
                field.setMaxLength(null);
            }
            field.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
            field.setIsNullable(rs.getString("IS_NULLABLE"));
            field.setComment(rs.getString("COLUMN_COMMENT"));
            field.setColumnKey(rs.getString("COLUMN_KEY"));
            return field;
        });
    }

    // __CONTINUE_2__

    /**
     * 新增字段
     */
    @Override
    @Transactional
    public void addField(AddFieldRequest request) {
        validateTableName(request.getTableName());
        validateFieldName(request.getFieldName());

        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(request.getTableName());
        sql.append(" ADD COLUMN ").append(request.getFieldName());
        sql.append(" ").append(request.getDataType());

        if ("VARCHAR".equalsIgnoreCase(request.getDataType()) && request.getLength() != null) {
            sql.append("(").append(request.getLength()).append(")");
        }

        if (request.getNullable() != null && !request.getNullable()) {
            sql.append(" NOT NULL");
        } else {
            sql.append(" DEFAULT NULL");
        }

        if (request.getComment() != null && !request.getComment().isEmpty()) {
            sql.append(" COMMENT '").append(escapeSql(request.getComment())).append("'");
        }

        log.info("执行DDL: {}", sql);
        jdbcTemplate.execute(sql.toString());
        log.info("字段添加成功: table={}, field={}", request.getTableName(), request.getFieldName());
    }

    // __CONTINUE_3__

    /**
     * 修改字段
     */
    @Override
    @Transactional
    public void modifyField(ModifyFieldRequest request) {
        validateTableName(request.getTableName());
        validateFieldName(request.getFieldName());

        if (CORE_FIELDS.contains(request.getFieldName())) {
            throw new RuntimeException("禁止修改核心字段: " + request.getFieldName());
        }

        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(request.getTableName());
        sql.append(" MODIFY COLUMN ").append(request.getFieldName());
        sql.append(" ").append(request.getDataType());

        if ("VARCHAR".equalsIgnoreCase(request.getDataType()) && request.getLength() != null) {
            sql.append("(").append(request.getLength()).append(")");
        }

        if (request.getComment() != null && !request.getComment().isEmpty()) {
            sql.append(" COMMENT '").append(escapeSql(request.getComment())).append("'");
        }

        log.info("执行DDL: {}", sql);
        jdbcTemplate.execute(sql.toString());
        log.info("字段修改成功: table={}, field={}", request.getTableName(), request.getFieldName());
    }

    /**
     * 删除字段
     */
    @Override
    @Transactional
    public void deleteField(DeleteFieldRequest request) {
        validateTableName(request.getTableName());
        validateFieldName(request.getFieldName());

        if (CORE_FIELDS.contains(request.getFieldName())) {
            throw new RuntimeException("禁止删除核心字段: " + request.getFieldName());
        }

        String sql = "ALTER TABLE " + request.getTableName() +
                     " DROP COLUMN " + request.getFieldName();

        log.info("执行DDL: {}", sql);
        jdbcTemplate.execute(sql);
        log.info("字段删除成功: table={}, field={}", request.getTableName(), request.getFieldName());
    }

    // __CONTINUE_4__

    /**
     * 验证表名（白名单）
     */
    private void validateTableName(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            throw new RuntimeException("不允许操作的表: " + tableName);
        }
    }

    /**
     * 验证字段名
     */
    private void validateFieldName(String fieldName) {
        if (fieldName == null || !fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new RuntimeException("字段名格式不合法: " + fieldName);
        }
    }

    /**
     * 转义SQL字符串（防止SQL注入）
     */
    private String escapeSql(String str) {
        if (str == null) return "";
        return str.replace("'", "''").replace("\\", "\\\\");
    }

    /**
     * 从数据源URL提取数据库名
     */
    private String extractDatabaseName() {
        // jdbc:mysql://host:port/dbname?params
        try {
            String url = datasourceUrl;
            int start = url.lastIndexOf("/") + 1;
            int end = url.indexOf("?");
            if (end == -1) end = url.length();
            return url.substring(start, end);
        } catch (Exception e) {
            log.warn("提取数据库名失败，使用默认值", e);
            return "uygur_project";
        }
    }
}

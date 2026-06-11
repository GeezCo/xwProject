package com.qy.dch.request;

import lombok.Data;

/**
 * 新增字段请求
 */
@Data
public class AddFieldRequest {
    /** 表名 */
    private String tableName;

    /** 字段名 */
    private String fieldName;

    /** 数据类型 (VARCHAR/INT/TEXT/TINYINT/DATETIME等) */
    private String dataType;

    /** 长度（VARCHAR使用） */
    private Integer length;

    /** 默认值 */
    private String defaultValue;

    /** 是否可空 */
    private Boolean nullable;

    /** 字段注释 */
    private String comment;
}

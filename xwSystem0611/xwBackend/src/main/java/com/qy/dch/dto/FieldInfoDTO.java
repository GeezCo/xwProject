package com.qy.dch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段信息 DTO
 * <p>
 * 用于返回数据库表字段的详细信息
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldInfoDTO {
    /** 字段名 */
    private String fieldName;

    /** 数据类型 (VARCHAR/INT/TEXT等) */
    private String dataType;

    /** 最大长度 (VARCHAR类型) */
    private Integer maxLength;

    /** 默认值 */
    private String defaultValue;

    /** 是否可空 (YES/NO) */
    private String isNullable;

    /** 字段注释 */
    private String comment;

    /** 键类型 (PRI=主键, MUL=索引) */
    private String columnKey;
}

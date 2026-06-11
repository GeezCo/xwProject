package com.qy.dch.request;

import lombok.Data;

/**
 * 修改字段请求
 */
@Data
public class ModifyFieldRequest {
    /** 表名 */
    private String tableName;

    /** 字段名 */
    private String fieldName;

    /** 新数据类型 */
    private String dataType;

    /** 新长度 */
    private Integer length;

    /** 新注释 */
    private String comment;
}

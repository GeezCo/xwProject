package com.qy.dch.request;

import lombok.Data;

/**
 * 删除字段请求
 */
@Data
public class DeleteFieldRequest {
    /** 表名 */
    private String tableName;

    /** 字段名 */
    private String fieldName;
}

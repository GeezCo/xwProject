package com.intel.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * MySQL数据导入请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MySQLImportRequest {

    /**
     * 导入类型：all（全量）、byType（按类型）、byIds（按ID列表）
     */
    @NotNull(message = "导入类型不能为空")
    @Builder.Default
    private String type = "all";

    /**
     * 类型ID（当type=byType时必填）
     */
    private Integer typeId;

    /**
     * ID列表（当type=byIds时必填）
     */
    private List<Integer> ids;
}

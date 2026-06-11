package com.intel.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文档上传响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {

    /**
     * 文档ID
     */
    private String documentId;

    /**
     * 文件名
     */
    private String filename;

    /**
     * 切片数量
     */
    private Integer chunks;

    /**
     * 状态
     */
    private String status;

    /**
     * 处理耗时（毫秒）
     */
    private Long elapsedTime;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;
}

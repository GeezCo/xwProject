package com.qy.dch.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 报文数据（用于融合接口）
 * <p>
 * 包含报文的内容和抽取结果
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportData {
    /** 报文ID（标识用） */
    private Long id;

    /** 报文标题 */
    private String title;

    /** 报文正文内容 */
    private String content;

    /** 报文时间 */
    private String times;

    /** 报文分类类型 */
    private Integer type;

    /** 抽取结果 */
    private ExtractionResult extractionResult;

    /**
     * 抽取结果结构
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExtractionResult {
        /** 事件列表 */
        private List<Map<String, Object>> events;

        /** 实体信息 */
        private Map<String, Object> entities;

        /** 标签列表 */
        private List<String> labels;
    }
}
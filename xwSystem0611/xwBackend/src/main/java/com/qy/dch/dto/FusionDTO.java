package com.qy.dch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 报文融合结果数据传输对象
 * <p>
 * 包含融合报告的所有信息，包括标题、摘要、时间线、
 * 详细内容、关键实体、综合标签等。
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FusionDTO {
    /** 数据库主键ID */
    private Long id;

    /** 融合报告ID */
    private Long fusionId;

    /** 报告标题（自动生成或用户编辑） */
    private String title;

    /** 报告摘要 */
    private String summary;

    /** 事件时间线（JSON格式存储） */
    private String timeline;

    /** 详细内容（分章节） */
    private String content;

    /** 关键实体（JSON格式存储） */
    private String entities;

    /** 综合标签（JSON格式存储） */
    private String labels;

    /** 参与融合的报文ID列表 */
    private String sourceIds;

    /** 使用的大模型 */
    private String modelUsed;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
}
package com.qy.dch.dto;

import lombok.Data;
import java.util.Date;

/**
 * 事件分析结果 DTO
 */
@Data
public class EventAnalysisDTO {
    /** 主键ID */
    private Integer id;

    /** 原始报文ID */
    private Integer originTextId;

    /** 事件时间 */
    private String eventTime;

    /** 事件地点 */
    private String eventLocation;

    /** 事件内容 */
    private String eventContent;

    /** 事件分析 */
    private String eventAnalysis;

    /** 分析日期 */
    private String analysisDate;

    /** 创建时间 */
    private Date createTime;

    /** 来源报文标题（关联查询） */
    private String sourceTitle;
}

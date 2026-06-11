package com.qy.dch.request;

import lombok.Data;
import java.util.List;

/**
 * 事件分析查询请求
 */
@Data
public class EventAnalysisQueryRequest {
    /** 开始日期 */
    private String startDate;

    /** 结束日期 */
    private String endDate;

    /** 关键词列表（OR关系） */
    private List<String> keywords;
}

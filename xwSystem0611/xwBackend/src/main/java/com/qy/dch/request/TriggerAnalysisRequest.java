package com.qy.dch.request;

import lombok.Data;

/**
 * 触发事件分析请求
 */
@Data
public class TriggerAnalysisRequest {
    /** 开始日期 */
    private String startDate;

    /** 结束日期 */
    private String endDate;
}

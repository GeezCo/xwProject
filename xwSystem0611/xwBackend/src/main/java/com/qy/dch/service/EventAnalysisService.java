package com.qy.dch.service;

import com.qy.dch.dto.EventAnalysisDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 事件分析服务
 */
public interface EventAnalysisService {

    /**
     * 分析指定日期范围内的报文
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 分析结果统计
     */
    Map<String, Object> analyzeReportsByDate(LocalDate startDate, LocalDate endDate);

    /**
     * 查询已分析的事件（按关键词筛选）
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param keywords 关键词列表
     * @return 事件列表
     */
    List<EventAnalysisDTO> queryEvents(LocalDate startDate, LocalDate endDate, List<String> keywords);

    /**
     * 查询分析任务状态
     * @param date 日期
     * @return 状态信息
     */
    Map<String, Object> getAnalysisStatus(LocalDate date);
}

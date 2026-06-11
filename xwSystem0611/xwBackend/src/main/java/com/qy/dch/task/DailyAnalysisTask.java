package com.qy.dch.task;

import com.qy.dch.service.EventAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 每日事件分析定时任务
 */
@Component
@Slf4j
public class DailyAnalysisTask {

    @Autowired
    private EventAnalysisService eventAnalysisService;

    /**
     * 每日凌晨2点自动分析前一天的新增报文
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyAnalysis() {
        log.info("========== 开始每日事件分析任务 ==========");
        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            Map<String, Object> result = eventAnalysisService.analyzeReportsByDate(yesterday, yesterday);
            log.info("每日事件分析任务完成: {}", result);
        } catch (Exception e) {
            log.error("每日事件分析任务失败", e);
        }

        log.info("========== 每日事件分析任务结束 ==========");
    }
}

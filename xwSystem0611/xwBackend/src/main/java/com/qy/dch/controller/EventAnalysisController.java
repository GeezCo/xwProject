package com.qy.dch.controller;

import com.qy.dch.common.ResultVO;
import com.qy.dch.dto.EventAnalysisDTO;
import com.qy.dch.request.EventAnalysisQueryRequest;
import com.qy.dch.request.TriggerAnalysisRequest;
import com.qy.dch.service.EventAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 事件分析接口
 */
@RestController
@RequestMapping("/api/eventAnalysis")
@Slf4j
public class EventAnalysisController {

    @Autowired
    private EventAnalysisService eventAnalysisService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 手动触发分析任务
     */
    @PostMapping("/trigger")
    public ResultVO triggerAnalysis(@RequestBody TriggerAnalysisRequest request) {
        log.info("手动触发分析: {} 至 {}", request.getStartDate(), request.getEndDate());

        LocalDate startDate = LocalDate.parse(request.getStartDate(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(request.getEndDate(), DATE_FORMATTER);

        // 异步执行
        CompletableFuture.runAsync(() -> {
            try {
                eventAnalysisService.analyzeReportsByDate(startDate, endDate);
            } catch (Exception e) {
                log.error("分析任务执行失败", e);
            }
        });

        return ResultVO.success("分析任务已启动，请稍后查看结果");
    }

    /**
     * 查询事件（按主题筛选）
     */
    @PostMapping("/query")
    public ResultVO queryEvents(@RequestBody EventAnalysisQueryRequest request) {
        log.info("查询事件: {} 至 {}, 关键词数: {}",
                request.getStartDate(), request.getEndDate(),
                request.getKeywords() != null ? request.getKeywords().size() : 0);

        LocalDate startDate = LocalDate.parse(request.getStartDate(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(request.getEndDate(), DATE_FORMATTER);

        List<EventAnalysisDTO> events = eventAnalysisService.queryEvents(
                startDate, endDate, request.getKeywords());

        return ResultVO.success(events);
    }

    /**
     * 查询分析任务状态
     */
    @GetMapping("/status")
    public ResultVO getAnalysisStatus(@RequestParam String date) {
        log.info("查询分析状态: {}", date);

        LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
        Map<String, Object> status = eventAnalysisService.getAnalysisStatus(localDate);

        return ResultVO.success(status);
    }
}

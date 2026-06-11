package com.qy.dch.service.impl;

import com.qy.dch.dto.EventAnalysisDTO;
import com.qy.dch.dto.OriginTextDTO;
import com.qy.dch.mapper.EventAnalysisMapper;
import com.qy.dch.mapper.UygurMapper;
import com.qy.dch.service.EventAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 事件分析服务实现
 */
@Service
@Slf4j
public class EventAnalysisServiceImpl implements EventAnalysisService {

    @Autowired
    private EventAnalysisMapper eventAnalysisMapper;

    @Autowired
    private UygurMapper uygurMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${algorithm.service.url}")
    private String algorithmServiceUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public Map<String, Object> analyzeReportsByDate(LocalDate startDate, LocalDate endDate) {
        log.info("开始分析日期范围: {} 至 {}", startDate, endDate);

        // 1. 查询日期范围内的报文
        List<OriginTextDTO> reports = uygurMapper.getReportsByDateRange(
                startDate.format(DATE_FORMATTER),
                endDate.format(DATE_FORMATTER),
                null // 不限制抽取状态
        );

        int total = reports.size();
        int success = 0;
        int failed = 0;
        int skipped = 0;

        log.info("共查询到 {} 条报文", total);

        // 2. 逐条调用算法服务分析
        for (int i = 0; i < reports.size(); i++) {
            OriginTextDTO report = reports.get(i);
            log.info("处理报文 {}/{}: sid={}, title={}", i + 1, total, report.getSid(), report.getTitle());

            // 检查是否已分析
            int existCount = eventAnalysisMapper.countByOriginTextId(report.getSid());
            if (existCount > 0) {
                log.info("报文 {} 已分析，跳过", report.getSid());
                skipped++;
                continue;
            }

            try {
                // 调用算法服务 eventSplit 接口
                Map<String, Object> response = callEventSplitApi(report.getContent());

                @SuppressWarnings("unchecked")
                List<Map<String, String>> events = (List<Map<String, String>>) response.get("events");

                if (events == null || events.isEmpty()) {
                    log.warn("报文 {} 未识别出事件", report.getSid());
                    failed++;
                    continue;
                }

                // 3. 保存事件到数据库
                String analysisDateStr = report.getTimes() != null && !report.getTimes().trim().isEmpty()
                        ? report.getTimes()
                        : startDate.format(DATE_FORMATTER);

                for (Map<String, String> event : events) {
                    EventAnalysisDTO dto = new EventAnalysisDTO();
                    dto.setOriginTextId(report.getSid());
                    dto.setEventTime(event.get("time"));
                    dto.setEventLocation(event.get("location"));
                    dto.setEventContent(event.get("event_content"));
                    dto.setEventAnalysis(event.get("event_analysis"));
                    dto.setAnalysisDate(analysisDateStr);

                    eventAnalysisMapper.insertOrUpdate(dto);
                }

                success++;
                log.info("报文 {} 分析完成，识别 {} 个事件", report.getSid(), events.size());

            } catch (Exception e) {
                log.error("报文 {} 分析失败: {}", report.getSid(), e.getMessage(), e);
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("failed", failed);
        result.put("skipped", skipped);

        log.info("分析完成: 总计={}, 成功={}, 失败={}, 跳过={}", total, success, failed, skipped);
        return result;
    }

    // __CONTINUE__

    @Override
    public List<EventAnalysisDTO> queryEvents(LocalDate startDate, LocalDate endDate, List<String> keywords) {
        log.info("查询事件: {} 至 {}, 关键词数: {}", startDate, endDate, keywords != null ? keywords.size() : 0);
        return eventAnalysisMapper.queryByDateAndKeywords(
                startDate.format(DATE_FORMATTER),
                endDate.format(DATE_FORMATTER),
                keywords
        );
    }

    @Override
    public Map<String, Object> getAnalysisStatus(LocalDate date) {
        int count = eventAnalysisMapper.countByDate(date.format(DATE_FORMATTER));
        Map<String, Object> status = new HashMap<>();
        status.put("date", date.format(DATE_FORMATTER));
        status.put("eventCount", count);
        status.put("analyzed", count > 0);
        return status;
    }

    /**
     * 调用算法服务的 eventSplit 接口
     */
    private Map<String, Object> callEventSplitApi(String text) {
        String url = algorithmServiceUrl + "/eventSplit";
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("text", text);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
        return response;
    }
}


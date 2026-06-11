package com.qy.dch.task;

import com.qy.dch.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RagIndexingTask {

    @Autowired
    private RagService ragService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyIndexing() {
        log.info("========== 开始每日 RAG 索引任务 ==========");
        try {
            ragService.scheduledIndexing();
        } catch (Exception e) {
            log.error("每日 RAG 索引任务失败", e);
        }
        log.info("========== 每日 RAG 索引任务结束 ==========");
    }
}

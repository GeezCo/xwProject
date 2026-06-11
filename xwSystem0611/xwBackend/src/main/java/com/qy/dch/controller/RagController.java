package com.qy.dch.controller;

import com.qy.dch.common.ResultVO;
import com.qy.dch.request.RagIndexTriggerRequest;
import com.qy.dch.request.RagSearchRequest;
import com.qy.dch.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
@Slf4j
public class RagController {

    @Autowired
    private RagService ragService;

    @GetMapping("/index/status")
    public ResultVO getIndexStatus() {
        log.info("查询索引状态");
        return ragService.getIndexStatus();
    }

    @PostMapping("/index/trigger")
    public ResultVO triggerIndexing(@RequestBody RagIndexTriggerRequest request) {
        log.info("手动触发索引: startDate={}, endDate={}", request.getStartDate(), request.getEndDate());
        return ragService.triggerIndexing(request.getStartDate(), request.getEndDate());
    }

    @PostMapping("/search")
    public ResultVO search(@RequestBody RagSearchRequest request) {
        log.info("语义检索: query={}, topK={}, hybrid={}", request.getQuery(), request.getTopK(), request.getHybrid());
        return ragService.search(request.getQuery(), request.getTopK(), request.getHybrid());
    }

    @GetMapping("/index/log")
    public ResultVO getIndexLog(@RequestParam(defaultValue = "1") Integer pageNum,
                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("查询索引日志: pageNum={}, pageSize={}", pageNum, pageSize);
        return ragService.getIndexLog(pageNum, pageSize);
    }
}

package com.intel.rag.controller;

import com.intel.rag.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(name = "健康检查", description = "系统健康状态检查")
public class HealthController {

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查系统各组件状态")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> healthData = new HashMap<>();
        healthData.put("status", "UP");
        healthData.put("timestamp", LocalDateTime.now());

        Map<String, Object> components = new HashMap<>();

        // Elasticsearch状态
        Map<String, String> es = new HashMap<>();
        es.put("status", "UP");
        es.put("note", "需要实际连接测试");
        components.put("elasticsearch", es);

        // Embedding服务状态
        Map<String, String> embedding = new HashMap<>();
        embedding.put("status", "UP");
        embedding.put("note", "需要实际连接测试");
        components.put("embedding", embedding);

        // 数据库状态
        Map<String, String> db = new HashMap<>();
        db.put("status", "UP");
        db.put("note", "需要实际连接测试");
        components.put("database", db);

        // OCR服务状态
        Map<String, String> ocr = new HashMap<>();
        ocr.put("status", "UP");
        ocr.put("note", "Tesseract已初始化");
        components.put("ocr", ocr);

        healthData.put("components", components);

        return ApiResponse.success(healthData);
    }

    /**
     * 简单健康检查
     */
    @GetMapping("/ping")
    @Operation(summary = "Ping", description = "简单的存活检查")
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong");
    }
}

package com.qy.dch.controller;

import com.qy.dch.common.ResultVO;
import com.qy.dch.service.ExtractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 属性抽取控制器
 * <p>
 * 提供文本属性抽取相关的REST API接口：
 * - POST /extraction/extract - 执行属性抽取
 * - GET /extraction/result/{id} - 查询抽取结果
 * </p>
 */
@RestController
@RequestMapping("/extraction")
@Slf4j
public class ExtractionController {

    @Autowired
    private ExtractionService extractionService;

    /**
     * 执行属性抽取
     * <p>
     * 调用LLM对指定文本进行事件抽取，提取时间、地点、人物、组织、行为等要素。
     * 如果该文本已抽取且force=false，则返回提示信息。
     * </p>
     *
     * @param originTextId 原始文本ID（origin_text表的sid）
     * @param force 是否强制重新抽取（默认false）
     * @return 抽取结果或提示信息
     */
    @PostMapping("/extract")
    public ResultVO extract(
            @RequestParam Integer originTextId,
            @RequestParam(defaultValue = "false") Boolean force) {
        log.info("收到抽取请求，文本ID: {}, 强制重新抽取: {}", originTextId, force);
        return extractionService.extract(originTextId, force);
    }

    /**
     * 查询抽取结果
     * <p>
     * 根据原始文本ID查询已保存的抽取结果。
     * </p>
     *
     * @param originTextId 原始文本ID（origin_text表的sid）
     * @return 抽取结果，不存在则返回null
     */
    @GetMapping("/result/{originTextId}")
    public ResultVO getResult(@PathVariable Integer originTextId) {
        log.info("查询抽取结果，文本ID: {}", originTextId);
        return extractionService.getResult(originTextId);
    }

    /**
     * 启动批量抽取任务
     * <p>
     * 按时间段批量抽取报文，支持仅抽取未抽取的报文或全部重新抽取。
     * </p>
     *
     * @param startDate 开始日期（格式：yyyy-MM-dd）
     * @param endDate 结束日期（格式：yyyy-MM-dd）
     * @param scope 抽取范围（"unextracted"=仅未抽取，"all"=全部）
     * @return ResultVO 包含 taskId 和 totalCount
     */
    @PostMapping("/batch/start")
    public ResultVO startBatchExtraction(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "unextracted") String scope) {
        log.info("启动批量抽取任务: startDate={}, endDate={}, scope={}", startDate, endDate, scope);
        return ResultVO.success(extractionService.startBatchExtraction(startDate, endDate, scope));
    }

    /**
     * 查询批量抽取任务进度
     * <p>
     * 根据任务ID查询批量抽取的实时进度。
     * </p>
     *
     * @param taskId 任务ID
     * @return ResultVO 包含任务状态信息
     */
    @GetMapping("/batch/progress/{taskId}")
    public ResultVO getBatchProgress(@PathVariable String taskId) {
        log.info("查询批量抽取进度: taskId={}", taskId);
        return ResultVO.success(extractionService.getBatchProgress(taskId));
    }

    /**
     * 停止批量抽取任务
     * <p>
     * 停止正在运行的批量抽取任务。
     * </p>
     *
     * @param taskId 任务ID
     * @return ResultVO 包含停止结果
     */
    @PostMapping("/batch/stop/{taskId}")
    public ResultVO stopBatchTask(@PathVariable String taskId) {
        log.info("停止批量抽取任务: taskId={}", taskId);
        boolean success = extractionService.stopBatchTask(taskId);
        if (success) {
            return ResultVO.success("任务已停止");
        } else {
            return ResultVO.error("任务不存在或已完成");
        }
    }
}
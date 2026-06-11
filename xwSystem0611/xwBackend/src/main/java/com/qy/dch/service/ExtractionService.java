package com.qy.dch.service;

import com.qy.dch.common.ResultVO;
import com.qy.dch.dto.ExtractionResultDTO;

import java.util.Map;

/**
 * 属性抽取服务接口
 * <p>
 * 提供基于LLM的文本属性抽取功能，包括：
 * - 从原始文本中抽取事件（时间、地点、人物、组织、行为）
 * - 抽取结果的查询和管理
 * - 批量抽取任务管理
 * </p>
 */
public interface ExtractionService {

    /**
     * 执行属性抽取
     *
     * @param originTextId 原始文本ID
     * @param force 是否强制重新抽取（true：即使已抽取也重新执行）
     * @return 抽取结果或提示信息
     */
    ResultVO extract(Integer originTextId, Boolean force);

    /**
     * 查询抽取结果
     *
     * @param originTextId 原始文本ID
     * @return 抽取结果DTO，不存在则返回null
     */
    ResultVO getResult(Integer originTextId);

    /**
     * 启动批量抽取任务
     *
     * @param startDate 开始日期（格式：yyyy-MM-dd）
     * @param endDate 结束日期（格式：yyyy-MM-dd）
     * @param scope 抽取范围（"unextracted"=仅未抽取，"all"=全部）
     * @return Map包含 taskId 和 totalCount
     */
    Map<String, Object> startBatchExtraction(String startDate, String endDate, String scope);

    /**
     * 查询批量抽取任务进度
     *
     * @param taskId 任务ID
     * @return Map包含任务状态信息
     */
    Map<String, Object> getBatchProgress(String taskId);

    /**
     * 停止批量抽取任务
     *
     * @param taskId 任务ID
     * @return 是否成功停止
     */
    boolean stopBatchTask(String taskId);
}
package com.qy.dch.service;

import java.util.List;
import java.util.Map;

/**
 * 数据统计看板服务接口
 * <p>
 * 提供系统数据统计功能：
 * - 核心指标统计（总报文数、已抽取数、融合报告数等）
 * - 分类分布统计
 * - 模态类型分布统计
 * - 最近融合报告列表
 * </p>
 */
public interface DashboardService {

    /**
     * 获取核心指标概览
     * 包含：总报文数、已抽取数、融合报告数、标签总数
     *
     * @return Map包含各项统计指标
     */
    Map<String, Object> getOverview();

    /**
     * 获取各分类下的报文数量分布
     *
     * @return List<Map> 每个Map包含 type（分类ID）和 count（数量）
     */
    List<Map<String, Object>> getCategoryDistribution();

    /**
     * 获取各模态类型下的报文数量分布
     *
     * @return List<Map> 每个Map包含 modalType（模态类型）和 count（数量）
     */
    List<Map<String, Object>> getModalDistribution();

    /**
     * 获取最近的融合报告列表
     *
     * @param limit 返回数量限制（默认10）
     * @return 融合报告列表
     */
    List<Map<String, Object>> getRecentFusions(int limit);
}

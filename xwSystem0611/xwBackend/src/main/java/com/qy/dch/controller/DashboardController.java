package com.qy.dch.controller;

import com.qy.dch.common.ResultVO;
import com.qy.dch.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 数据统计看板控制器
 * <p>
 * 提供以下接口：
 * - GET /api/dashboard/overview：核心指标概览
 * - GET /api/dashboard/categoryDistribution：分类分布统计
 * - GET /api/dashboard/modalDistribution：模态类型分布统计
 * - GET /api/dashboard/recentFusions：最近融合报告列表
 * </p>
 */
@RestController
@RequestMapping("/api/dashboard")
@Slf4j
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 获取核心指标概览
     * 包含：总报文数、已抽取数、融合报告数、标签总数
     *
     * @return ResultVO 包含核心指标数据
     */
    @GetMapping("/overview")
    public ResultVO getOverview() {
        log.info("getOverview: 查询核心指标");
        return ResultVO.success(dashboardService.getOverview());
    }

    /**
     * 获取各分类下的报文数量分布
     *
     * @return ResultVO 包含分类分布数据
     */
    @GetMapping("/categoryDistribution")
    public ResultVO getCategoryDistribution() {
        log.info("getCategoryDistribution: 查询分类分布");
        return ResultVO.success(dashboardService.getCategoryDistribution());
    }

    /**
     * 获取各模态类型下的报文数量分布
     *
     * @return ResultVO 包含模态分布数据
     */
    @GetMapping("/modalDistribution")
    public ResultVO getModalDistribution() {
        log.info("getModalDistribution: 查询模态分布");
        return ResultVO.success(dashboardService.getModalDistribution());
    }

    /**
     * 获取最近的融合报告列表
     *
     * @param limit 返回数量限制（可选，默认10）
     * @return ResultVO 包含融合报告列表
     */
    @GetMapping("/recentFusions")
    public ResultVO getRecentFusions(@RequestParam(defaultValue = "10") Integer limit) {
        log.info("getRecentFusions: 查询最近 {} 条融合报告", limit);
        return ResultVO.success(dashboardService.getRecentFusions(limit));
    }
}

package com.qy.dch.service.impl;

import com.qy.dch.dto.FusionDTO;
import com.qy.dch.dto.TextTypeDTO;
import com.qy.dch.mapper.FusionMapper;
import com.qy.dch.mapper.UygurMapper;
import com.qy.dch.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计看板服务实现类
 */
@Service
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private UygurMapper uygurMapper;

    @Autowired
    private FusionMapper fusionMapper;

    /**
     * 获取核心指标概览
     */
    @Override
    public Map<String, Object> getOverview() {
        log.info("getOverview: 查询核心指标");

        Map<String, Object> overview = new HashMap<>();

        // 总报文数
        int totalReports = uygurMapper.countTextListAll();
        overview.put("totalReports", totalReports);

        // 抽取统计
        Map<String, Object> extractionStats = uygurMapper.getExtractionStats();
        overview.put("extractedReports", extractionStats.get("extracted"));
        overview.put("unextractedReports", extractionStats.get("not_extracted"));

        // 融合报告数
        int fusionCount = fusionMapper.selectFusionCount();
        overview.put("fusionReports", fusionCount);

        // 标签总数（暂时返回0，后续可从extraction_result统计）
        overview.put("totalLabels", 0);

        log.info("核心指标: 总报文={}, 已抽取={}, 融合报告={}",
                totalReports, extractionStats.get("extracted"), fusionCount);

        return overview;
    }

    /**
     * 获取各分类下的报文数量分布
     * 将分类ID替换为分类名称，方便前端图表直接展示
     */
    @Override
    public List<Map<String, Object>> getCategoryDistribution() {
        log.info("getCategoryDistribution: 查询分类分布");

        List<Map<String, Object>> distribution = uygurMapper.countByType();

        List<TextTypeDTO> allCategories = uygurMapper.getCategories();
        Map<Integer, String> idToNameMap = new HashMap<>();
        for (TextTypeDTO cat : allCategories) {
            idToNameMap.put(cat.getId(), cat.getTypeName());
        }

        for (Map<String, Object> item : distribution) {
            Object typeObj = item.get("type");
            if (typeObj instanceof Number) {
                Integer typeId = ((Number) typeObj).intValue();
                String typeName = idToNameMap.getOrDefault(typeId, "未分类");
                item.put("type", typeName);
            } else if (typeObj == null) {
                item.put("type", "未分类");
            }
        }

        log.info("分类分布: {} 个分类", distribution.size());
        return distribution;
    }

    /**
     * 获取各模态类型下的报文数量分布
     */
    @Override
    public List<Map<String, Object>> getModalDistribution() {
        log.info("getModalDistribution: 查询模态分布");
        List<Map<String, Object>> distribution = uygurMapper.countByModalType();
        log.info("模态分布: {} 种模态", distribution.size());
        return distribution;
    }

    /**
     * 获取最近的融合报告列表
     */
    @Override
    public List<Map<String, Object>> getRecentFusions(int limit) {
        log.info("getRecentFusions: 查询最近 {} 条融合报告", limit);

        List<FusionDTO> fusions = fusionMapper.selectFusionList(0, limit);
        List<Map<String, Object>> result = new ArrayList<>();

        for (FusionDTO fusion : fusions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", fusion.getId());
            item.put("fusionId", fusion.getFusionId());
            item.put("title", fusion.getTitle());
            item.put("summary", fusion.getSummary());
            item.put("createTime", fusion.getCreateTime());
            result.add(item);
        }

        log.info("返回 {} 条融合报告", result.size());
        return result;
    }
}

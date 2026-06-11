package com.qy.dch.service;

import com.qy.dch.dto.FusionDTO;
import com.qy.dch.request.FusionCreateRequest;

import java.util.List;
import java.util.Map;

/**
 * 报文融合服务接口
 * <p>
 * 定义融合报告相关业务操作：
 * - 创建融合报告（调用LLM）
 * - 保存/查询融合报告
 * - 导出融合报告
 * </p>
 */
public interface FusionService {

    /**
     * 创建融合报告
     * 根据选中的报文ID列表，调用LLM生成融合报告
     *
     * @param request 创建请求，包含报文ID列表等参数
     * @return 生成的融合报告数据
     */
    FusionDTO createFusion(FusionCreateRequest request);

    /**
     * 保存融合报告到数据库
     *
     * @param fusionDTO 融合报告数据
     * @return 保存后的融合报告（包含生成的ID）
     */
    FusionDTO saveFusion(FusionDTO fusionDTO);

    /**
     * 分页查询融合报告列表
     *
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 融合报告列表
     */
    List<FusionDTO> getFusionList(Integer pageNum, Integer pageSize);

    /**
     * 查询指定融合报告详情
     *
     * @param id 融合报告ID
     * @return 融合报告详情，不存在则返回null
     */
    FusionDTO getFusionDetail(Long id);

    /**
     * 根据目标名称搜索相关报文
     * 在已抽取报文的entities_json中搜索包含目标名称的报文
     *
     * @param targetName 目标名称（如"第七舰队"、"B-52轰炸机"）
     * @param maxReports 最多返回报文数（默认10）
     * @return Map包含 reports（报文列表）和 total（匹配数量）
     */
    Map<String, Object> searchByTarget(String targetName, int maxReports);
}
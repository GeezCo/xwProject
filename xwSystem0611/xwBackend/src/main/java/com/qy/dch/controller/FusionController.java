package com.qy.dch.controller;

import com.qy.dch.common.ResultVO;
import com.qy.dch.dto.FusionDTO;
import com.qy.dch.request.FusionCreateRequest;
import com.qy.dch.service.FusionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 报文融合控制器
 * <p>
 * 提供以下接口：
 * - POST /api/fusion/create：创建融合报告
 * - POST /api/fusion/save：保存融合报告到数据库
 * - GET  /api/fusion/list：查询已保存的融合报告列表
 * - GET  /api/fusion/detail/{id}：查询指定融合报告详情
 * - GET  /api/fusion/export/pdf/{id}：导出融合报告为PDF
 * - GET  /api/fusion/export/word/{id}：导出融合报告为Word
 * </p>
 */
@RestController
@RequestMapping("/api/fusion")
@Slf4j
public class FusionController {

    @Autowired
    FusionService fusionService;

    /**
     * 创建融合报告
     * 调用大模型对多篇报文进行融合分析，生成结构化报告
     *
     * @param request 请求参数，包含 reports（报文数据列表）、fusionType（融合类型）、customTitle（自定义标题）
     * @return ResultVO 包含融合报告数据
     */
    @PostMapping("/create")
    public ResultVO createFusion(@RequestBody FusionCreateRequest request) {
        log.info("createFusion: reports数量={}, fusionType={}",
            request.getReports() != null ? request.getReports().size() : 0,
            request.getFusionType());
        FusionDTO fusionDTO = fusionService.createFusion(request);
        return ResultVO.success(fusionDTO);
    }

    /**
     * 保存融合报告到数据库
     *
     * @param fusionDTO 融合报告数据
     * @return ResultVO 包含保存结果
     */
    @PostMapping("/save")
    public ResultVO saveFusion(@RequestBody FusionDTO fusionDTO) {
        log.info("saveFusion: title={}", fusionDTO.getTitle());
        FusionDTO savedFusion = fusionService.saveFusion(fusionDTO);
        return ResultVO.success(savedFusion);
    }

    /**
     * 查询已保存的融合报告列表
     *
     * @param pageNum 页码（可选，默认1）
     * @param pageSize 每页条数（可选，默认10）
     * @return ResultVO 包含融合报告列表
     */
    @GetMapping("/list")
    public ResultVO getFusionList(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                   @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        log.info("getFusionList: pageNum={}, pageSize={}", pageNum, pageSize);
        return ResultVO.success(fusionService.getFusionList(pageNum, pageSize));
    }

    /**
     * 查询指定融合报告详情
     *
     * @param id 融合报告ID
     * @return ResultVO 包含融合报告详情数据
     */
    @GetMapping("/detail/{id}")
    public ResultVO getFusionDetail(@PathVariable Long id) {
        log.info("getFusionDetail: id={}", id);
        FusionDTO fusionDTO = fusionService.getFusionDetail(id);
        if (fusionDTO == null) {
            return ResultVO.error("融合报告不存在");
        }
        return ResultVO.success(fusionDTO);
    }

    /**
     * 导出融合报告为PDF
     *
     * @param id 融合报告ID
     * @return ResultVO 包含PDF文件下载信息
     */
    @GetMapping("/export/pdf/{id}")
    public ResultVO exportPDF(@PathVariable Long id) {
        log.info("exportPDF: id={}", id);
        FusionDTO fusionDTO = fusionService.getFusionDetail(id);
        if (fusionDTO == null) {
            return ResultVO.error("融合报告不存在");
        }
        // TODO: 实现PDF导出功能
        return ResultVO.success("PDF导出功能开发中，报告标题：" + fusionDTO.getTitle());
    }

    /**
     * 导出融合报告为Word
     *
     * @param id 融合报告ID
     * @return ResultVO 包含Word文件下载信息
     */
    @GetMapping("/export/word/{id}")
    public ResultVO exportWord(@PathVariable Long id) {
        log.info("exportWord: id={}", id);
        FusionDTO fusionDTO = fusionService.getFusionDetail(id);
        if (fusionDTO == null) {
            return ResultVO.error("融合报告不存在");
        }
        // TODO: 实现Word导出功能
        return ResultVO.success("Word导出功能开发中，报告标题：" + fusionDTO.getTitle());
    }

    /**
     * 根据目标名称搜索相关报文
     * 在已抽取报文的entities_json中搜索包含目标名称的报文
     *
     * @param targetName 目标名称（如"第七舰队"、"B-52轰炸机"）
     * @param maxReports 最多返回报文数（可选，默认10）
     * @return ResultVO 包含 reports（报文列表）和 total（匹配数量）
     */
    @PostMapping("/searchByTarget")
    public ResultVO searchByTarget(@RequestParam String targetName,
                                     @RequestParam(defaultValue = "10") Integer maxReports) {
        log.info("searchByTarget: targetName={}, maxReports={}", targetName, maxReports);
        if (targetName == null || targetName.trim().isEmpty()) {
            return ResultVO.error("目标名称不能为空");
        }
        return ResultVO.success(fusionService.searchByTarget(targetName.trim(), maxReports));
    }
}
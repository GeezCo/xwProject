package com.qy.dch.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qy.dch.common.ResultVO;
import com.qy.dch.dto.ExtractionResultDTO;
import com.qy.dch.dto.FusionDTO;
import com.qy.dch.dto.OriginTextDTO;
import com.qy.dch.mapper.FusionMapper;
import com.qy.dch.mapper.UygurMapper;
import com.qy.dch.mapper.ExtractionMapper;
import com.qy.dch.request.FusionCreateRequest;
import com.qy.dch.request.ReportData;
import com.qy.dch.service.ExtractionService;
import com.qy.dch.service.FusionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报文融合服务实现类
 * <p>
 * 实现融合报告相关业务逻辑：
 * - 创建融合报告：先获取报文详情和抽取结果（未抽取则自动抽取），再调用算法服务融合
 * - 保存融合报告：将融合结果存储到数据库
 * - 查询融合报告：从数据库读取已保存的报告
 * </p>
 */
@Service
@Slf4j
public class FusionServiceImpl implements FusionService {

    @Autowired
    FusionMapper fusionMapper;

    @Autowired
    UygurMapper uygurMapper;

    @Autowired
    ExtractionMapper extractionMapper;

    @Autowired
    ExtractionService extractionService;

    /** 算法服务URL，从配置文件读取 */
    @Value("${algorithm.service.url:http://localhost:5001}")
    private String algorithmServiceUrl;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 创建融合报告
     * 流程：
     * 1. 验证参数
     * 2. 检查每篇报文是否已抽取，未抽取则自动执行抽取
     * 3. 构建完整的报文数据（获取内容和抽取结果）
     * 4. 调用算法服务融合
     */
    @Override
    public FusionDTO createFusion(FusionCreateRequest request) {
        log.info("createFusion: reports数量={}, fusionType={}",
            request.getReports() != null ? request.getReports().size() : 0,
            request.getFusionType());

        List<ReportData> reports = request.getReports();
        if (reports == null || reports.size() < 2) {
            log.warn("报文数量不足，至少需要2篇");
            return null;
        }
        if (reports.size() > 20) {
            log.warn("报文数量过多，最多支持20篇");
            return null;
        }

        // 检查并补充抽取结果
        for (ReportData report : reports) {
            if (report.getId() != null) {
                // 检查是否有抽取结果
                if (report.getExtractionResult() == null ||
                    report.getExtractionResult().getEvents() == null ||
                    report.getExtractionResult().getEvents().isEmpty()) {

                    log.info("报文 {} 未抽取，自动执行抽取", report.getId());

                    // 调用抽取服务
                    ResultVO extractResult = extractionService.extract(report.getId().intValue(), false);
                    if (extractResult != null && extractResult.getCode() == 1) {
                        ExtractionResultDTO extractionDTO = (ExtractionResultDTO) extractResult.getData();
                        if (extractionDTO != null) {
                            // 构建 extractionResult
                            ReportData.ExtractionResult er = new ReportData.ExtractionResult();
                            // JSONArray 转 List<Map<String, Object>>
                            JSONArray eventsArray = extractionDTO.getEvents();
                            List<Map<String, Object>> eventsList = new ArrayList<>();
                            for (int i = 0; i < eventsArray.size(); i++) {
                                eventsList.add(eventsArray.getJSONObject(i).getInnerMap());
                            }
                            er.setEvents(eventsList);
                            er.setEntities(new HashMap<>());
                            // JSONArray 转 List<String>
                            JSONArray labelsArray = extractionDTO.getLabels();
                            List<String> labelsList = new ArrayList<>();
                            for (int i = 0; i < labelsArray.size(); i++) {
                                labelsList.add(labelsArray.getString(i));
                            }
                            er.setLabels(labelsList);
                            report.setExtractionResult(er);
                            log.info("报文 {} 自动抽取完成，事件数={}", report.getId(),
                                extractionDTO.getTotalEvents());
                        }
                    } else {
                        log.warn("报文 {} 抽取失败，继续使用空结果", report.getId());
                    }
                }
            }
        }

        // 调用算法服务获取融合结果
        try {
            JSONObject algorithmResult = callAlgorithmService(request);
            if (algorithmResult != null && algorithmResult.getInteger("code") == 1) {
                JSONObject data = algorithmResult.getJSONObject("data");
                FusionDTO fusionDTO = parseAlgorithmResult(data, reports);
                log.info("融合报告生成完成: title={}", fusionDTO.getTitle());
                return fusionDTO;
            } else {
                log.error("算法服务返回失败: {}", algorithmResult);
                // 返回默认内容作为兜底
                return createDefaultFusion(reports, request.getCustomTitle());
            }
        } catch (Exception e) {
            log.error("调用算法服务失败: {}", e.getMessage());
            // 返回默认内容作为兜底
            return createDefaultFusion(reports, request.getCustomTitle());
        }
    }

    /**
     * 调用算法服务获取融合结果
     * 直接传入完整的报文数据（包含内容和抽取结果）
     */
    private JSONObject callAlgorithmService(FusionCreateRequest request) throws Exception {
        String url = algorithmServiceUrl + "/fusion/create";

        // 构建请求体（新格式：包含完整的报文数据）
        JSONObject requestBody = new JSONObject();

        // 构建 reports 数组
        JSONArray reportsArray = new JSONArray();
        for (ReportData report : request.getReports()) {
            JSONObject reportObj = new JSONObject();
            reportObj.put("id", report.getId());
            reportObj.put("title", report.getTitle());
            reportObj.put("content", report.getContent());
            reportObj.put("times", report.getTimes());
            reportObj.put("type", report.getType());

            // 抽取结果
            if (report.getExtractionResult() != null) {
                JSONObject extractionObj = new JSONObject();
                extractionObj.put("events", report.getExtractionResult().getEvents());
                extractionObj.put("entities", report.getExtractionResult().getEntities());
                extractionObj.put("labels", report.getExtractionResult().getLabels());
                reportObj.put("extractionResult", extractionObj);
            }

            reportsArray.add(reportObj);
        }

        requestBody.put("reports", reportsArray);
        requestBody.put("fusionType", request.getFusionType() != null ? request.getFusionType() : "standard");
        if (request.getCustomTitle() != null) {
            requestBody.put("customTitle", request.getCustomTitle());
        }

        log.info("调用算法服务: {}, 报文数量: {}", url, request.getReports().size());
        log.info("请求体大小: {} 字符", requestBody.toJSONString().length());

        HttpURLConnection conn = null;
        try {
            URL urlObj = new URL(url);
            conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            // 融合处理需要较长时间（LLM调用约5-8分钟），设置10分钟超时
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(600000);

            // 发送请求体
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toJSONString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            log.info("算法服务响应码: {}", responseCode);

            if (responseCode == 200) {
                // 读取响应
                StringBuilder response = new StringBuilder();
                try (java.io.InputStream is = conn.getInputStream()) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                log.info("算法服务响应长度: {}", response.length());
                return JSON.parseObject(response.toString());
            } else {
                log.error("算法服务响应异常: {}", responseCode);
                return null;
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 解析算法服务返回的融合结果
     */
    private FusionDTO parseAlgorithmResult(JSONObject data, List<ReportData> reports) {
        FusionDTO fusionDTO = new FusionDTO();

        // 基本信息
        fusionDTO.setFusionId(data.getLong("fusionId"));
        fusionDTO.setTitle(data.getString("title"));
        fusionDTO.setSummary(data.getString("summary"));
        fusionDTO.setContent(data.getString("content"));
        fusionDTO.setModelUsed(data.getString("modelUsed"));
        fusionDTO.setCreateTime(data.getString("createTime"));
        fusionDTO.setUpdateTime(data.getString("updateTime"));

        // 时间线 - 直接保存JSON字符串
        Object timelineObj = data.get("timeline");
        if (timelineObj != null) {
            if (timelineObj instanceof JSONArray) {
                fusionDTO.setTimeline(JSON.toJSONString(timelineObj));
            } else if (timelineObj instanceof String) {
                fusionDTO.setTimeline((String) timelineObj);
            }
        } else {
            fusionDTO.setTimeline("[]");
        }

        // 实体 - 算法返回的可能是数组（新版表格行）或字典（兼容旧版），统一序列化为 JSON 字符串
        Object entitiesObj = data.get("entities");
        if (entitiesObj != null) {
            if (entitiesObj instanceof String) {
                fusionDTO.setEntities((String) entitiesObj);
            } else {
                fusionDTO.setEntities(JSON.toJSONString(entitiesObj));
            }
        } else {
            fusionDTO.setEntities("[]");
        }

        // 标签 - 直接保存JSON字符串
        Object labelsObj = data.get("labels");
        if (labelsObj != null) {
            if (labelsObj instanceof JSONArray) {
                fusionDTO.setLabels(JSON.toJSONString(labelsObj));
            } else if (labelsObj instanceof String) {
                fusionDTO.setLabels((String) labelsObj);
            }
        } else {
            fusionDTO.setLabels("[]");
        }

        // 来源报文ID
        List<Long> sourceIds = new ArrayList<>();
        for (ReportData report : reports) {
            if (report.getId() != null) {
                sourceIds.add(report.getId());
            }
        }
        fusionDTO.setSourceIds(JSON.toJSONString(sourceIds));

        return fusionDTO;
    }

    /**
     * 创建默认融合结果（兜底方案）
     */
    private FusionDTO createDefaultFusion(List<ReportData> reports, String customTitle) {
        log.warn("使用默认融合结果（算法服务不可用）");

        FusionDTO fusionDTO = new FusionDTO();
        fusionDTO.setFusionId(System.currentTimeMillis());
        fusionDTO.setTitle(customTitle != null ? customTitle : "融合报告（算法服务暂不可用）");
        fusionDTO.setSummary("本报告综合了" + reports.size() + "篇报文内容。由于算法服务暂时不可用，显示默认内容。");

        fusionDTO.setTimeline("[]");
        fusionDTO.setContent("## 一、事件概述\n\n算法服务暂时不可用，请稍后再试。\n\n## 说明\n\n融合功能需要算法服务支持，请确认算法服务已启动（端口5001）。");
        fusionDTO.setEntities("{\"persons\":[],\"organizations\":[],\"equipment\":[]}");
        fusionDTO.setLabels("[\"融合\", \"报告\"]");

        List<Long> sourceIds = new ArrayList<>();
        for (ReportData report : reports) {
            if (report.getId() != null) {
                sourceIds.add(report.getId());
            }
        }
        fusionDTO.setSourceIds(JSON.toJSONString(sourceIds));
        fusionDTO.setModelUsed("default");
        fusionDTO.setCreateTime(DATE_FORMAT.format(new Date()));
        fusionDTO.setUpdateTime(DATE_FORMAT.format(new Date()));

        return fusionDTO;
    }

    /**
     * 保存融合报告到数据库
     */
    @Override
    public FusionDTO saveFusion(FusionDTO fusionDTO) {
        log.info("saveFusion: title={}", fusionDTO.getTitle());

        String now = DATE_FORMAT.format(new Date());
        if (fusionDTO.getCreateTime() == null) {
            fusionDTO.setCreateTime(now);
        }
        fusionDTO.setUpdateTime(now);

        int rows = fusionMapper.insertFusion(fusionDTO);
        if (rows > 0) {
            log.info("融合报告保存成功: fusionId={}", fusionDTO.getFusionId());
            return fusionDTO;
        } else {
            log.error("融合报告保存失败");
            return null;
        }
    }

    /**
     * 分页查询融合报告列表
     */
    @Override
    public List<FusionDTO> getFusionList(Integer pageNum, Integer pageSize) {
        log.info("getFusionList: pageNum={}, pageSize={}", pageNum, pageSize);
        Integer offset = (pageNum - 1) * pageSize;
        return fusionMapper.selectFusionList(offset, pageSize);
    }

    /**
     * 查询指定融合报告详情
     */
    @Override
    public FusionDTO getFusionDetail(Long id) {
        log.info("getFusionDetail: id={}", id);
        return fusionMapper.selectFusionById(id);
    }

    /**
     * 根据目标名称搜索相关报文
     * 流程：
     * 1. 在extraction_result的entities_json中搜索包含目标名称的报文ID
     * 2. 批量查询这些报文的详情
     * 3. 批量查询这些报文的抽取结果
     * 4. 组装返回
     */
    @Override
    public Map<String, Object> searchByTarget(String targetName, int maxReports) {
        log.info("searchByTarget: targetName={}, maxReports={}", targetName, maxReports);

        Map<String, Object> result = new HashMap<>();

        // 1. 搜索包含目标名称的报文ID
        List<Integer> matchedIds = extractionMapper.searchByEntityKeyword(targetName, maxReports);
        log.info("找到 {} 篇包含目标 '{}' 的报文", matchedIds.size(), targetName);

        if (matchedIds.isEmpty()) {
            result.put("reports", new ArrayList<>());
            result.put("total", 0);
            return result;
        }

        // 2. 批量查询报文详情
        List<ReportData> reports = new ArrayList<>();
        for (Integer id : matchedIds) {
            OriginTextDTO text = uygurMapper.selectById(id.longValue());
            if (text != null) {
                ReportData report = new ReportData();
                report.setId(text.getSid().longValue());
                report.setTitle(text.getTitle());
                report.setContent(text.getContent());
                report.setTimes(text.getTimes());
                report.setType(text.getType());

                // 3. 查询抽取结果
                ExtractionResultDTO extractionDTO = extractionMapper.selectByOriginTextId(id);
                if (extractionDTO != null) {
                    ReportData.ExtractionResult er = new ReportData.ExtractionResult();

                    // 转换 events
                    JSONArray eventsArray = extractionDTO.getEvents();
                    List<Map<String, Object>> eventsList = new ArrayList<>();
                    for (int i = 0; i < eventsArray.size(); i++) {
                        eventsList.add(eventsArray.getJSONObject(i).getInnerMap());
                    }
                    er.setEvents(eventsList);

                    // 转换 entities
                    er.setEntities(new HashMap<>());

                    // 转换 labels
                    JSONArray labelsArray = extractionDTO.getLabels();
                    List<String> labelsList = new ArrayList<>();
                    for (int i = 0; i < labelsArray.size(); i++) {
                        labelsList.add(labelsArray.getString(i));
                    }
                    er.setLabels(labelsList);

                    report.setExtractionResult(er);
                }

                reports.add(report);
            }
        }

        result.put("reports", reports);
        result.put("total", reports.size());
        log.info("返回 {} 篇报文数据", reports.size());

        return result;
    }
}
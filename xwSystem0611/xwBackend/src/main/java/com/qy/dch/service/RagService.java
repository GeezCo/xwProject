package com.qy.dch.service;

import com.qy.dch.common.ResultVO;

public interface RagService {

    ResultVO getIndexStatus();

    ResultVO triggerIndexing(String startDate, String endDate);

    ResultVO search(String query, Integer topK, Boolean hybrid);

    ResultVO getIndexLog(Integer pageNum, Integer pageSize);

    void scheduledIndexing();

    /**
     * 上传文档（DOCX）→ 解析 → 分块 → 向量化 → 索引到 ES
     *
     * @param file 上传的 multipart 文件
     * @param withOcr 是否启用 OCR（若 DOCX 含图片）
     * @return ResultVO，data 含 docId / chunkCount / elapsedMs
     */
    com.qy.dch.common.ResultVO uploadAndIndex(org.springframework.web.multipart.MultipartFile file, boolean withOcr);

    /**
     * 仅解析返回文本（调试用，不入库不向量化）
     */
    com.qy.dch.common.ResultVO parseOnly(org.springframework.web.multipart.MultipartFile file, boolean withOcr);

    /**
     * 查询单次上传文档的状态
     */
    com.qy.dch.common.ResultVO getDocumentStatus(String docId);
}

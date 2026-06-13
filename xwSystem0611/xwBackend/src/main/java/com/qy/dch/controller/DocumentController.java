package com.qy.dch.controller;

import com.qy.dch.common.ResultVO;
import com.qy.dch.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rag/document")
@Slf4j
public class DocumentController {

    @Autowired
    private RagService ragService;

    @PostMapping("/upload")
    public ResultVO upload(@RequestParam("file") MultipartFile file) {
        log.info("文档上传: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        return ragService.uploadAndIndex(file, false);
    }

    @PostMapping("/upload/mixed")
    public ResultVO uploadMixed(@RequestParam("file") MultipartFile file) {
        log.info("文档上传（含 OCR）: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        return ragService.uploadAndIndex(file, true);
    }

    @PostMapping("/parse")
    public ResultVO parse(@RequestParam("file") MultipartFile file,
                          @RequestParam(value = "withOcr", defaultValue = "false") boolean withOcr) {
        log.info("仅解析（不入库）: filename={}, withOcr={}", file.getOriginalFilename(), withOcr);
        return ragService.parseOnly(file, withOcr);
    }

    @GetMapping("/status/{docId}")
    public ResultVO status(@PathVariable("docId") String docId) {
        return ragService.getDocumentStatus(docId);
    }
}

package com.qy.dch.controller;

import com.qy.dch.dto.TextTypeDTO;
import com.qy.dch.mapper.UygurMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 测试控制器 - 用于调试编码问题
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private UygurMapper uygurMapper;

    @GetMapping("/queryCategory")
    public Object queryCategory(@RequestParam String name) {
        log.info("查询分类: name={}", name);
        log.info("name 长度: {}", name.length());
        log.info("name UTF-8字节: {}", Arrays.toString(name.getBytes(StandardCharsets.UTF_8)));
        log.info("name ISO-8859-1字节: {}", Arrays.toString(name.getBytes(StandardCharsets.ISO_8859_1)));

        TextTypeDTO result = uygurMapper.getCategoryByNameAndParent(name, null);

        if (result == null) {
            return "未找到分类: " + name;
        }

        return result;
    }

    /**
     * 测试 multipart 表单参数编码
     */
    @PostMapping("/testMultipart")
    public Object testMultipart(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("name") String name) {

        log.info("======= Multipart 编码测试 =======");
        log.info("接收到的字符串: [{}]", name);
        log.info("字符串长度: {}", name.length());
        log.info("UTF-8 字节数组: {}", Arrays.toString(name.getBytes(StandardCharsets.UTF_8)));
        log.info("ISO-8859-1 字节数组: {}", Arrays.toString(name.getBytes(StandardCharsets.ISO_8859_1)));

        // 尝试转码
        try {
            String fixed = new String(name.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            log.info("ISO-8859-1 → UTF-8 转码后: [{}]", fixed);
        } catch (Exception e) {
            log.error("转码失败", e);
        }

        return "接收到的参数: " + name;
    }
}

package com.intel.rag.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 测试配置
 */
@Configuration
@ConditionalOnProperty(name = "test.mode", havingValue = "true")
public class TestConfiguration {
    // 测试环境配置
}

package com.qy.dch.rag.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Autowired
    private OcrProperties ocrProperties;

    @Bean(name = "ocrExecutor")
    public Executor ocrExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = ocrProperties.getThreadPoolSize();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ocr-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }
}

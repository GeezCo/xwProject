package com.qy.dch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 维吾尔语项目（uygur-project）Spring Boot 启动类
 * <p>
 * 项目包含三大功能模块：
 * 1. 维吾尔语文本管理 —— 文本数据导入、分类查询、分页列表
 * 2. LLM大模型文档生成 —— 调用Qwen大模型生成内容并导出Word
 * 3. GPU服务器资源监控 —— 通过SSH采集GPU/CPU/内存/磁盘指标
 * 4. 事件分析定时任务 —— 每日自动分析报文事件
 * </p>
 */
@SpringBootApplication
@EnableScheduling
public class DchApplication {

	/**
	 * 应用程序入口方法
	 * @param args 命令行参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(DchApplication.class, args);
	}

}

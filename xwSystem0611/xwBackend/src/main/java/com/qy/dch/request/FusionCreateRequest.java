package com.qy.dch.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建融合报告请求参数（新格式）
 * <p>
 * 包含完整的报文数据列表（内容和抽取结果），算法服务不再需要查询数据库。
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FusionCreateRequest {
    /** 报文数据列表（最少2篇，最多10篇），包含内容和抽取结果 */
    private List<ReportData> reports;

    /** 融合类型：standard(标准) */
    private String fusionType;

    /** 自定义标题（可选） */
    private String customTitle;
}
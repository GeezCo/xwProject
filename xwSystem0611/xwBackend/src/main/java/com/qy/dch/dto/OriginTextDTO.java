package com.qy.dch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 原始文本数据传输对象
 * <p>
 * 对应数据库origin_text表，存储维吾尔语原始文本数据，
 * 包含标题、正文内容、时间标签和所属分类等信息。
 * 用于JSON文件导入和文本列表查询。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OriginTextDTO {
    /** 文本序号ID（主键，自增） */
    private Integer sid;
    /** 文本标题 */
    private String title;
    /** 文本正文内容 */
    private String content;
    /** 时间标签（多个时间以逗号分隔的字符串） */
    private String times;
    /** 文本分类ID（关联text_type表） */
    private Integer type;
    /** 报文模态类型（文字报/图文报/声像报） */
    private String modalType;
    /** 是否已抽取：0-未抽取，1-已抽取 */
    private Integer isExtracted;
    /** 分类标签（从extraction_result表关联查询） */
    private String labelsJson;
    /** 图片文件名列表（JSON数组字符串，如 ["188_1.jpg","188_2.jpg"]） */
    private String images;
    /** 发送单位名称 */
    private String sendUnitName;
    /** 简报类型名称 */
    private String briefTypeName;
}

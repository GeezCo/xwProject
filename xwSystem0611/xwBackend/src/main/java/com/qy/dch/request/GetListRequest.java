package com.qy.dch.request;

import lombok.Data;
import java.util.List;

/**
 * 文本列表查询请求参数
 * <p>
 * 封装前端请求文本列表时的参数，包括分页信息和可选的分类筛选条件。
 * 支持多个分类ID和多个报文模态的筛选。
 * </p>
 */
@Data
public class GetListRequest {
    /** 当前页码（从1开始） */
    private int pageNum;

    /** 每页显示条数 */
    private int pageSize;

    /** 文本分类ID（可选，为null时查询全部） */
    private Integer typeId;

    /** 多个文本分类ID（可选，支持批量筛选） */
    private List<Integer> typeIds;

    /** 报文模态类型（可选：文字报/图文报/声像报） */
    private String modalType;

    /** 多个报文模态类型（可选，支持批量筛选） */
    private List<String> modalTypes;

    /** 关键词列表（可选，多个关键词 OR 关系，匹配 title 或 content） */
    private List<String> keywords;

    /** 开始时间（可选，格式：yyyy-MM-dd） */
    private String startTime;

    /** 结束时间（可选，格式：yyyy-MM-dd） */
    private String endTime;

}
